#include "stratum_client.h"
#include "stratum_miner.h"
#include "stratum_serial.h"

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SHARE_QUEUE_CAP 16

typedef struct {
    char job_id[STRATUM_JOB_ID_MAX];
    char extranonce2[TANGMINER_MAX_EXTRANONCE2_HEX];
    char ntime[STRATUM_HEX_4_MAX];
    char nonce[9];
} queued_share_t;

typedef struct {
    bool quiet;
    bool submit;
    char serial_port[256];
    int serial_baud;
    int serial_timeout_ms;

    pthread_mutex_t lock;
    pthread_cond_t work_ready;
    bool shutdown;
    bool has_work;
    uint64_t generation;
    stratum_notify_t active_notify;
    tangminer_builder_t builder;

    tangminer_serial_t serial;
    bool serial_open;
    pthread_t miner_thread;
    bool miner_thread_started;

    queued_share_t shares[SHARE_QUEUE_CAP];
    size_t share_head;
    size_t share_count;
} cli_state_t;

static void usage(const char* prog) {
    fprintf(stderr,
            "usage: %s --host HOST --port PORT --user USER [--pass PASS]\n"
            "       [--serial-port PORT] [--serial-baud BAUD] [--serial-timeout-ms MS]\n"
            "       [--fpga-target quick23|quick21|quick26|quick3|all-ones|share]\n"
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

static bool queue_share(cli_state_t* state, const tangminer_work_t* work, const char nonce_hex[9]) {
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
    state->serial_open = true;
    return true;
}

static void* miner_thread_main(void* arg) {
    cli_state_t* state = (cli_state_t*)arg;
    if (!miner_open_serial(state)) {
        return NULL;
    }

    while (true) {
        stratum_notify_t notify;
        tangminer_work_t work;
        uint64_t generation = 0;

        pthread_mutex_lock(&state->lock);
        while (!state->shutdown && !state->has_work) {
            pthread_cond_wait(&state->work_ready, &state->lock);
        }
        if (state->shutdown) {
            pthread_mutex_unlock(&state->lock);
            break;
        }
        notify = state->active_notify;
        generation = state->generation;
        if (!tangminer_build_work(&state->builder, &notify, &work)) {
            fprintf(stderr, "error: failed to build TangMiner work for job %s\n", notify.job_id);
            pthread_mutex_unlock(&state->lock);
            continue;
        }
        pthread_mutex_unlock(&state->lock);

        if (tangminer_serial_send_job(&state->serial, work.packet) != 0) {
            fprintf(stderr, "error: failed to send TangMiner job\n");
            break;
        }

        uint32_t nonce = 0;
        const int rc = tangminer_serial_read_found(&state->serial, &nonce, state->serial_timeout_ms);
        if (rc <= 0) {
            fprintf(stderr, "error: no TangMiner candidate for job %s\n", notify.job_id);
            continue;
        }

        uint8_t hash[32];
        bool meets_share = false;
        bool meets_block = false;
        char nonce_hex[9];
        char submit_nonce_hex[9];
        char hash_hex[65];
        tangminer_validate_nonce(&work, nonce, hash, &meets_share, &meets_block);
        tangminer_nonce_to_hex(nonce, nonce_hex);
        tangminer_nonce_to_submit_hex(nonce, submit_nonce_hex);
        hash_to_hex(hash, hash_hex);

        pthread_mutex_lock(&state->lock);
        const bool stale = generation != state->generation;
        if (!stale) {
            printf("candidate job=%s nonce=%s extranonce2=%s share=%s block=%s hash=%s\n",
                   work.job_id,
                   nonce_hex,
                   work.extranonce2_hex,
                   meets_share ? "yes" : "no",
                   meets_block ? "yes" : "no",
                   hash_hex);
            if (meets_share && state->submit) {
                queue_share(state, &work, submit_nonce_hex);
            }
        }
        pthread_mutex_unlock(&state->lock);
    }

    return NULL;
}

static void drain_shares(void* user_data, stratum_session_t* session) {
    cli_state_t* state = (cli_state_t*)user_data;
    while (true) {
        queued_share_t share;
        pthread_mutex_lock(&state->lock);
        const bool found = pop_share(state, &share);
        pthread_mutex_unlock(&state->lock);
        if (!found) {
            return;
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
    ++state->generation;
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
    ++state->generation;
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
    ++state->generation;
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
        if (pthread_create(&state.miner_thread, NULL, miner_thread_main, &state) != 0) {
            fprintf(stderr, "error: failed to start miner thread\n");
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
    pthread_cond_signal(&state.work_ready);
    pthread_mutex_unlock(&state.lock);
    if (state.serial_open) {
        tangminer_serial_stop(&state.serial);
    }
    if (state.miner_thread_started) {
        pthread_join(state.miner_thread, NULL);
    }
    if (state.serial_open) {
        tangminer_serial_close(&state.serial);
    }
    pthread_cond_destroy(&state.work_ready);
    pthread_mutex_destroy(&state.lock);
    return rc == 0 ? 0 : 1;
}
