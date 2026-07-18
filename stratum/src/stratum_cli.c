#include "stratum_client.h"
#include "stratum_continuous.h"
#include "stratum_miner.h"
#include "stratum_serial.h"
#include "stratum_work_state.h"

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define SHARE_QUEUE_CAP 16
#define CONTINUOUS_NEGOTIATION_TIMEOUT_MS 250

typedef struct {
    char job_id[STRATUM_JOB_ID_MAX];
    char extranonce2[TANGMINER_MAX_EXTRANONCE2_HEX];
    char ntime[STRATUM_HEX_4_MAX];
    char nonce[9];
    stratum_work_snapshot_t work_snapshot;
} queued_share_t;

typedef struct {
    bool quiet;
    bool submit;
    bool continuous_results;
    char serial_port[256];
    int serial_baud;
    int serial_timeout_ms;

    pthread_mutex_t lock;
    pthread_cond_t work_ready;
    bool shutdown;
    bool has_work;
    stratum_work_state_t work_state;
    stratum_notify_t active_notify;
    tangminer_builder_t builder;

    tangminer_serial_t serial;
    bool serial_open;
    pthread_t miner_thread;
    bool miner_thread_started;
    bool miner_failed;

    queued_share_t shares[SHARE_QUEUE_CAP];
    size_t share_head;
    size_t share_count;
} cli_state_t;

static void usage(const char* prog) {
    fprintf(stderr,
            "usage: %s --host HOST --port PORT --user USER [--pass PASS]\n"
            "       [--serial-port PORT] [--serial-baud BAUD] [--serial-timeout-ms MS]\n"
            "       [--fpga-target quick23|quick21|quick26|quick14|quick3]\n"
            "       [--host-round-skip] [--continuous-results]\n"
            "       [--suggest-difficulty N] [--miner-name NAME] [--no-submit] [--quiet]\n",
            prog);
}

static bool copy_arg(char* out, size_t out_size, const char* value) {
    const size_t len = strlen(value);
    if (len >= out_size) {
        return false;
    }
    memcpy(out, value, len + 1);
    return true;
}

static void hash_to_hex(const uint8_t hash[32], char out[65]) {
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < 32; ++i) {
        out[i * 2U] = digits[hash[i] >> 4];
        out[i * 2U + 1U] = digits[hash[i] & 0x0f];
    }
    out[64] = '\0';
}

static bool queue_share(
    cli_state_t* state,
    const tangminer_work_t* work,
    const char nonce_hex[9],
    const stratum_work_snapshot_t* work_snapshot) {
    if (state->share_count >= SHARE_QUEUE_CAP) {
        fprintf(stderr, "error: share queue full; dropping share job=%s nonce=%s\n", work->job_id, nonce_hex);
        return false;
    }
    const size_t index = (state->share_head + state->share_count) % SHARE_QUEUE_CAP;
    queued_share_t* share = &state->shares[index];
    snprintf(share->job_id, sizeof(share->job_id), "%s", work->job_id);
    snprintf(share->extranonce2, sizeof(share->extranonce2), "%s", work->extranonce2_hex);
    snprintf(share->ntime, sizeof(share->ntime), "%s", work->ntime);
    snprintf(share->nonce, sizeof(share->nonce), "%s", nonce_hex);
    share->work_snapshot = *work_snapshot;
    ++state->share_count;
    return true;
}

static bool pop_share(cli_state_t* state, queued_share_t* out) {
    if (state->share_count == 0) {
        return false;
    }
    *out = state->shares[state->share_head];
    state->share_head = (state->share_head + 1U) % SHARE_QUEUE_CAP;
    --state->share_count;
    return true;
}

static bool miner_open_serial(cli_state_t* state) {
    if (state->serial_open) {
        return true;
    }
    if (tangminer_serial_open(&state->serial, state->serial_port, state->serial_baud) != 0) {
        fprintf(stderr, "error: failed to open serial port %s\n", state->serial_port);
        return false;
    }
    pthread_mutex_lock(&state->lock);
    state->serial_open = true;
    pthread_mutex_unlock(&state->lock);
    return true;
}

static bool monotonic_milliseconds(uint64_t* milliseconds) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
        return false;
    }
    *milliseconds =
        (uint64_t)now.tv_sec * 1000U + (uint64_t)now.tv_nsec / 1000000U;
    return true;
}

static void miner_mark_failed(cli_state_t* state) {
    pthread_mutex_lock(&state->lock);
    state->miner_failed = true;
    pthread_mutex_unlock(&state->lock);
}

static bool miner_shutdown_requested(cli_state_t* state) {
    pthread_mutex_lock(&state->lock);
    const bool shutdown = state->shutdown;
    pthread_mutex_unlock(&state->lock);
    return shutdown;
}

static void handle_candidate(
    cli_state_t* state,
    const tangminer_work_t* work,
    const stratum_work_snapshot_t* work_snapshot,
    uint32_t nonce) {
    pthread_mutex_lock(&state->lock);
    if (!stratum_work_snapshot_is_valid(&state->work_state, work_snapshot)) {
        pthread_mutex_unlock(&state->lock);
        return;
    }

    uint8_t hash[32];
    bool meets_share = false;
    bool meets_block = false;
    char nonce_hex[9];
    char submit_nonce_hex[9];
    char hash_hex[65];
    tangminer_validate_nonce(work, nonce, hash, &meets_share, &meets_block);
    tangminer_nonce_to_hex(nonce, nonce_hex);
    tangminer_nonce_to_submit_hex(nonce, submit_nonce_hex);
    hash_to_hex(hash, hash_hex);

    if (!state->quiet || meets_share || meets_block) {
        printf("candidate job=%s nonce=%s extranonce2=%s share=%s block=%s hash=%s\n",
               work->job_id,
               nonce_hex,
               work->extranonce2_hex,
               meets_share ? "yes" : "no",
               meets_block ? "yes" : "no",
               hash_hex);
    }
    if (meets_share && state->submit) {
        queue_share(state, work, submit_nonce_hex, work_snapshot);
    }
    pthread_mutex_unlock(&state->lock);
}

static void miner_legacy_loop(cli_state_t* state) {
    while (true) {
        stratum_notify_t notify;
        tangminer_work_t work;
        stratum_work_snapshot_t work_snapshot;

        pthread_mutex_lock(&state->lock);
        while (!state->shutdown && !state->has_work) {
            pthread_cond_wait(&state->work_ready, &state->lock);
        }
        if (state->shutdown) {
            pthread_mutex_unlock(&state->lock);
            break;
        }
        notify = state->active_notify;
        work_snapshot = stratum_work_state_snapshot(&state->work_state);
        if (!tangminer_build_work(&state->builder, &notify, &work)) {
            fprintf(stderr, "error: failed to build TangMiner work for job %s\n", notify.job_id);
            pthread_mutex_unlock(&state->lock);
            continue;
        }
        pthread_mutex_unlock(&state->lock);

        if (tangminer_serial_send_job(&state->serial, work.packet) != 0) {
            fprintf(stderr, "error: failed to send TangMiner job\n");
            miner_mark_failed(state);
            break;
        }

        uint32_t nonce = 0;
        const tangminer_serial_event_t event =
            tangminer_serial_read_event(&state->serial, &nonce, state->serial_timeout_ms);
        if (event == TANGMINER_SERIAL_EVENT_FOUND) {
            handle_candidate(state, &work, &work_snapshot, nonce);
            continue;
        }
        if (event == TANGMINER_SERIAL_EVENT_TIMEOUT) {
            if (!miner_shutdown_requested(state)) {
                fprintf(stderr, "error: no TangMiner candidate for job %s\n", notify.job_id);
            }
            continue;
        }
        if (event == TANGMINER_SERIAL_EVENT_STOPPED && miner_shutdown_requested(state)) {
            break;
        }

        fprintf(stderr, "error: unexpected TangMiner serial response in legacy mode\n");
        miner_mark_failed(state);
        break;
    }
}

static void miner_continuous_loop(cli_state_t* state) {
    tangminer_tag_map_t tag_map;
    bool sent_work = false;
    uint16_t next_tag = 0;
    uint64_t sent_generation = 0;
    uint64_t last_send_ms = 0;
    tangminer_tag_map_init(&tag_map);

    while (true) {
        tangminer_work_t work;
        stratum_work_snapshot_t work_snapshot;
        bool send_work = false;
        uint64_t now_ms = 0;

        pthread_mutex_lock(&state->lock);
        while (!state->shutdown && !state->has_work) {
            pthread_cond_wait(&state->work_ready, &state->lock);
        }
        if (state->shutdown) {
            pthread_mutex_unlock(&state->lock);
            break;
        }
        if (!monotonic_milliseconds(&now_ms)) {
            pthread_mutex_unlock(&state->lock);
            fprintf(stderr, "error: failed to read monotonic clock\n");
            miner_mark_failed(state);
            break;
        }

        work_snapshot = stratum_work_state_snapshot(&state->work_state);
        const uint64_t elapsed_ms =
            sent_work && now_ms >= last_send_ms ? now_ms - last_send_ms : 0;
        if (tangminer_continuous_job_due(
                sent_work, sent_generation, work_snapshot.generation, elapsed_ms)) {
            const stratum_notify_t notify = state->active_notify;
            if (!tangminer_build_work(&state->builder, &notify, &work)) {
                fprintf(stderr, "error: failed to build TangMiner work for job %s\n", notify.job_id);
                pthread_mutex_unlock(&state->lock);
                continue;
            }
            send_work = true;
        }
        pthread_mutex_unlock(&state->lock);

        if (send_work) {
            const uint16_t tag = next_tag++;
            if (tangminer_serial_send_tagged_job(&state->serial, tag, work.packet) != 0) {
                fprintf(stderr, "error: failed to send tagged TangMiner job\n");
                miner_mark_failed(state);
                break;
            }
            tangminer_tag_map_store(&tag_map, tag, &work, &work_snapshot);
            sent_work = true;
            sent_generation = work_snapshot.generation;
            last_send_ms = now_ms;
        }

        uint16_t result_tag = 0;
        uint32_t nonce = 0;
        const tangminer_serial_event_t event = tangminer_serial_read_tagged_event(
            &state->serial, &result_tag, &nonce, TANGMINER_CONTINUOUS_POLL_MS);
        if (event == TANGMINER_SERIAL_EVENT_TIMEOUT) {
            continue;
        }
        if (event == TANGMINER_SERIAL_EVENT_TAGGED_FOUND) {
            tangminer_tagged_work_t tagged_work;
            if (!tangminer_tag_map_find(&tag_map, result_tag, &tagged_work)) {
                if (!state->quiet) {
                    fprintf(stderr, "warning: ignoring result for unknown TangMiner tag %u\n",
                            (unsigned)result_tag);
                }
                continue;
            }
            handle_candidate(
                state, &tagged_work.work, &tagged_work.work_snapshot, nonce);
            continue;
        }
        if (event == TANGMINER_SERIAL_EVENT_STOPPED && miner_shutdown_requested(state)) {
            break;
        }

        fprintf(stderr, "error: unexpected TangMiner serial response in continuous mode\n");
        miner_mark_failed(state);
        break;
    }
}

static void* miner_thread_main(void* arg) {
    cli_state_t* state = (cli_state_t*)arg;
    if (!miner_open_serial(state)) {
        miner_mark_failed(state);
        return NULL;
    }

    if (state->continuous_results) {
        miner_continuous_loop(state);
    } else {
        miner_legacy_loop(state);
    }
    return NULL;
}

static void drain_shares(void* user_data, stratum_session_t* session) {
    cli_state_t* state = (cli_state_t*)user_data;
    while (true) {
        queued_share_t share;
        pthread_mutex_lock(&state->lock);
        const bool found = pop_share(state, &share);
        const bool work_valid = found &&
            stratum_work_snapshot_is_valid(&state->work_state, &share.work_snapshot);
        pthread_mutex_unlock(&state->lock);
        if (!found) {
            return;
        }
        if (!work_valid) {
            if (!state->quiet) {
                printf("discarded stale share job=%s nonce=%s\n", share.job_id, share.nonce);
            }
            continue;
        }
        if (stratum_client_submit_share(session, share.job_id, share.extranonce2, share.ntime, share.nonce) == 0) {
            printf("submitted job=%s nonce=%s extranonce2=%s\n", share.job_id, share.nonce, share.extranonce2);
        } else {
            fprintf(stderr, "error: share submit failed\n");
            return;
        }
    }
}

static void on_connected(void* user_data) {
    (void)user_data;
    printf("connected\n");
}

static void on_subscribed(void* user_data, const char* extranonce1, uint32_t extranonce2_size) {
    cli_state_t* state = (cli_state_t*)user_data;
    pthread_mutex_lock(&state->lock);
    if (!tangminer_builder_set_extranonce(&state->builder, extranonce1, extranonce2_size)) {
        fprintf(stderr, "error: invalid extranonce from pool\n");
    }
    stratum_work_state_note_update(&state->work_state, true);
    pthread_cond_signal(&state->work_ready);
    pthread_mutex_unlock(&state->lock);
    printf("subscribed extranonce1=%s extranonce2_size=%u\n", extranonce1, extranonce2_size);
}

static void on_authorized(void* user_data, bool accepted) {
    (void)user_data;
    printf("authorized=%s\n", accepted ? "true" : "false");
}

static void on_difficulty(void* user_data, double difficulty) {
    cli_state_t* state = (cli_state_t*)user_data;
    pthread_mutex_lock(&state->lock);
    tangminer_builder_set_difficulty(&state->builder, difficulty);
    stratum_work_state_note_update(&state->work_state, false);
    pthread_cond_signal(&state->work_ready);
    pthread_mutex_unlock(&state->lock);
    printf("difficulty=%.8g\n", difficulty);
}

static void on_notify(void* user_data, stratum_session_t* session, const stratum_notify_t* notify) {
    (void)session;
    cli_state_t* state = (cli_state_t*)user_data;
    printf("job id=%s clean=%s branches=%zu ntime=%s nbits=%s prev=%s\n",
           notify->job_id,
           notify->clean_jobs ? "true" : "false",
           notify->merkle_branch_count,
           notify->ntime,
           notify->nbits,
           notify->prev_hash);
    pthread_mutex_lock(&state->lock);
    state->active_notify = *notify;
    state->has_work = true;
    stratum_work_state_note_update(&state->work_state, notify->clean_jobs);
    pthread_cond_signal(&state->work_ready);
    pthread_mutex_unlock(&state->lock);
}

static void on_line(void* user_data, const char* line) {
    cli_state_t* state = (cli_state_t*)user_data;
    if (!state->quiet) {
        printf("recv %s\n", line);
    }
}

static void on_error(void* user_data, const char* message) {
    (void)user_data;
    fprintf(stderr, "error: %s\n", message);
}

int main(int argc, char** argv) {
    stratum_config_t config;
    cli_state_t state;
    memset(&config, 0, sizeof(config));
    memset(&state, 0, sizeof(state));
    pthread_mutex_init(&state.lock, NULL);
    pthread_cond_init(&state.work_ready, NULL);
    tangminer_builder_init(&state.builder);
    stratum_work_state_init(&state.work_state);
    state.submit = true;
    state.serial_baud = 115200;
    state.serial_timeout_ms = 30000;
    state.serial.fd = -1;
    config.port = 3333;
    config.io_timeout_ms = 500;
    copy_arg(config.password, sizeof(config.password), "x");
    copy_arg(config.miner_name, sizeof(config.miner_name), "TangMiner/0.1");

    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--host") == 0 && i + 1 < argc) {
            if (!copy_arg(config.host, sizeof(config.host), argv[++i])) {
                fprintf(stderr, "host too long\n");
                return 2;
            }
        } else if (strcmp(argv[i], "--port") == 0 && i + 1 < argc) {
            const long port = strtol(argv[++i], NULL, 10);
            if (port <= 0 || port > 65535) {
                fprintf(stderr, "invalid port\n");
                return 2;
            }
            config.port = (uint16_t)port;
        } else if (strcmp(argv[i], "--user") == 0 && i + 1 < argc) {
            if (!copy_arg(config.user, sizeof(config.user), argv[++i])) {
                fprintf(stderr, "user too long\n");
                return 2;
            }
        } else if (strcmp(argv[i], "--pass") == 0 && i + 1 < argc) {
            if (!copy_arg(config.password, sizeof(config.password), argv[++i])) {
                fprintf(stderr, "password too long\n");
                return 2;
            }
        } else if (strcmp(argv[i], "--miner-name") == 0 && i + 1 < argc) {
            if (!copy_arg(config.miner_name, sizeof(config.miner_name), argv[++i])) {
                fprintf(stderr, "miner name too long\n");
                return 2;
            }
        } else if (strcmp(argv[i], "--suggest-difficulty") == 0 && i + 1 < argc) {
            config.suggested_difficulty = strtod(argv[++i], NULL);
        } else if (strcmp(argv[i], "--serial-port") == 0 && i + 1 < argc) {
            if (!copy_arg(state.serial_port, sizeof(state.serial_port), argv[++i])) {
                fprintf(stderr, "serial port path too long\n");
                return 2;
            }
        } else if (strcmp(argv[i], "--serial-baud") == 0 && i + 1 < argc) {
            state.serial_baud = (int)strtol(argv[++i], NULL, 10);
        } else if (strcmp(argv[i], "--serial-timeout-ms") == 0 && i + 1 < argc) {
            state.serial_timeout_ms = (int)strtol(argv[++i], NULL, 10);
        } else if (strcmp(argv[i], "--fpga-target") == 0 && i + 1 < argc) {
            if (!tangminer_builder_set_fpga_target(&state.builder, argv[++i])) {
                fprintf(stderr, "invalid fpga target\n");
                return 2;
            }
        } else if (strcmp(argv[i], "--host-round-skip") == 0) {
            state.builder.host_round_skip = true;
        } else if (strcmp(argv[i], "--continuous-results") == 0) {
            state.continuous_results = true;
        } else if (strcmp(argv[i], "--no-submit") == 0) {
            state.submit = false;
        } else if (strcmp(argv[i], "--quiet") == 0) {
            config.quiet = true;
            state.quiet = true;
        } else {
            usage(argv[0]);
            return 2;
        }
    }

    if (config.host[0] == '\0' || config.user[0] == '\0') {
        usage(argv[0]);
        return 2;
    }

    if (state.serial_port[0] != '\0') {
        if (state.continuous_results) {
            if (!miner_open_serial(&state)) {
                pthread_cond_destroy(&state.work_ready);
                pthread_mutex_destroy(&state.lock);
                return 1;
            }
            const int negotiation = tangminer_serial_negotiate_continuous(
                &state.serial, CONTINUOUS_NEGOTIATION_TIMEOUT_MS);
            if (negotiation < 0) {
                fprintf(stderr, "error: TangMiner continuous-results negotiation failed\n");
                tangminer_serial_close(&state.serial);
                pthread_cond_destroy(&state.work_ready);
                pthread_mutex_destroy(&state.lock);
                return 1;
            }
            if (negotiation == 0) {
                state.continuous_results = false;
                fprintf(stderr,
                        "warning: TangMiner continuous results unsupported; using legacy mode\n");
            } else {
                printf("continuous_results=enabled\n");
            }
        }

        if (pthread_create(&state.miner_thread, NULL, miner_thread_main, &state) != 0) {
            fprintf(stderr, "error: failed to start miner thread\n");
            if (state.serial_open) {
                tangminer_serial_close(&state.serial);
            }
            pthread_cond_destroy(&state.work_ready);
            pthread_mutex_destroy(&state.lock);
            return 1;
        }
        state.miner_thread_started = true;
    }

    const stratum_callbacks_t callbacks = {
        .user_data = &state,
        .connected = on_connected,
        .subscribed = on_subscribed,
        .authorized = on_authorized,
        .difficulty = on_difficulty,
        .notify = on_notify,
        .idle = drain_shares,
        .line = on_line,
        .error = on_error,
    };
    const int rc = stratum_client_run_once(&config, &callbacks);

    pthread_mutex_lock(&state.lock);
    state.shutdown = true;
    const bool serial_was_open = state.serial_open;
    pthread_cond_signal(&state.work_ready);
    pthread_mutex_unlock(&state.lock);
    if (serial_was_open && tangminer_serial_send_stop(&state.serial) != 0) {
        fprintf(stderr, "error: failed to stop TangMiner serial worker\n");
        miner_mark_failed(&state);
    }
    if (state.miner_thread_started) {
        pthread_join(state.miner_thread, NULL);
    }
    if (state.serial_open) {
        tangminer_serial_close(&state.serial);
    }
    const bool miner_failed = state.miner_failed;
    pthread_cond_destroy(&state.work_ready);
    pthread_mutex_destroy(&state.lock);
    return rc == 0 && !miner_failed ? 0 : 1;
}
