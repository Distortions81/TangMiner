#include "stratum_client.h"

#include <stdio.h>
#include <string.h>

#include "stratum_json.h"
#include "stratum_messages.h"
#include "stratum_transport.h"

#define STRATUM_ID_SUBSCRIBE 1U
#define STRATUM_ID_SUGGEST_DIFFICULTY 2U
#define STRATUM_ID_AUTHORIZE 3U

struct stratum_session {
    stratum_transport_t* transport;
    const stratum_config_t* config;
    uint32_t next_submit_id;
};

static void emit_error(const stratum_callbacks_t* callbacks, const char* message) {
    if (callbacks != NULL && callbacks->error != NULL) {
        callbacks->error(callbacks->user_data, message);
    }
}

static int send_line(stratum_transport_t* transport, const char* line) {
    return stratum_transport_send_all(transport, line, strlen(line));
}

static int send_startup_messages(stratum_transport_t* transport, const stratum_config_t* config) {
    char line[512];
    if (!stratum_build_subscribe(line, sizeof(line), STRATUM_ID_SUBSCRIBE, config->miner_name) ||
        send_line(transport, line) != 0) {
        return -1;
    }
    if (config->suggested_difficulty > 0.0) {
        if (!stratum_build_suggest_difficulty(
                line, sizeof(line), STRATUM_ID_SUGGEST_DIFFICULTY, config->suggested_difficulty) ||
            send_line(transport, line) != 0) {
            return -1;
        }
    }
    if (!stratum_build_authorize(
            line, sizeof(line), STRATUM_ID_AUTHORIZE, config->user, config->password) ||
        send_line(transport, line) != 0) {
        return -1;
    }
    return 0;
}

int stratum_client_submit_share(
    stratum_session_t* session,
    const char* job_id,
    const char* extranonce2,
    const char* ntime,
    const char* nonce) {
    char line[640];
    if (session == NULL || session->transport == NULL) {
        return -1;
    }
    if (!stratum_build_submit(line,
                              sizeof(line),
                              session->next_submit_id++,
                              session->config->user,
                              job_id,
                              extranonce2,
                              ntime,
                              nonce)) {
        return -1;
    }
    return send_line(session->transport, line);
}

static int handle_response(const char* line, const stratum_callbacks_t* callbacks) {
    int id = 0;
    if (!stratum_json_parse_id(line, &id)) {
        return 0;
    }
    if (stratum_json_response_has_error(line)) {
        emit_error(callbacks, "pool returned an error response");
    }
    if (id == (int)STRATUM_ID_SUBSCRIBE) {
        char extranonce1[128];
        uint32_t extranonce2_size = 0;
        if (stratum_parse_subscribe_response(
                line, extranonce1, sizeof(extranonce1), &extranonce2_size)) {
            if (callbacks != NULL && callbacks->subscribed != NULL) {
                callbacks->subscribed(callbacks->user_data, extranonce1, extranonce2_size);
            }
        }
    } else if (id == (int)STRATUM_ID_AUTHORIZE) {
        bool accepted = false;
        if (stratum_json_parse_bool_result(line, &accepted) &&
            callbacks != NULL && callbacks->authorized != NULL) {
            callbacks->authorized(callbacks->user_data, accepted);
        }
    }
    return 0;
}

static int handle_method(
    stratum_session_t* session, const char* line, const stratum_callbacks_t* callbacks) {
    switch (stratum_json_method(line)) {
        case STRATUM_METHOD_NOTIFY: {
            stratum_notify_t notify;
            if (!stratum_parse_notify(line, &notify)) {
                emit_error(callbacks, "failed to parse mining.notify");
                return 0;
            }
            if (callbacks != NULL && callbacks->notify != NULL) {
                callbacks->notify(callbacks->user_data, session, &notify);
            }
            return 0;
        }
        case STRATUM_METHOD_SET_DIFFICULTY: {
            double difficulty = 0.0;
            if (stratum_parse_set_difficulty(line, &difficulty) &&
                callbacks != NULL && callbacks->difficulty != NULL) {
                callbacks->difficulty(callbacks->user_data, difficulty);
            }
            return 0;
        }
        case STRATUM_METHOD_SET_EXTRANONCE: {
            char extranonce1[128];
            uint32_t extranonce2_size = 0;
            if (stratum_parse_set_extranonce(
                    line, extranonce1, sizeof(extranonce1), &extranonce2_size)) {
                if (callbacks != NULL && callbacks->subscribed != NULL) {
                    callbacks->subscribed(callbacks->user_data, extranonce1, extranonce2_size);
                }
            }
            return 0;
        }
        case STRATUM_METHOD_MINING_PING: {
            int id = 0;
            char pong[96];
            if (stratum_json_parse_id(line, &id) && id >= 0 &&
                stratum_build_pong(pong, sizeof(pong), (uint32_t)id)) {
                return send_line(session->transport, pong);
            }
            return 0;
        }
        case STRATUM_METHOD_CLIENT_RECONNECT:
            emit_error(callbacks, "pool requested client.reconnect");
            return -1;
        case STRATUM_METHOD_NONE:
            return handle_response(line, callbacks);
    }
    return 0;
}

static int handle_line(
    stratum_session_t* session, char* line, const stratum_callbacks_t* callbacks) {
    size_t len = strlen(line);
    while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r')) {
        line[len - 1] = '\0';
        --len;
    }
    if (callbacks != NULL && callbacks->line != NULL) {
        callbacks->line(callbacks->user_data, line);
    }
    return handle_method(session, line, callbacks);
}

int stratum_client_run_once(const stratum_config_t* config, const stratum_callbacks_t* callbacks) {
    stratum_transport_t transport;
    stratum_session_t session;
    char rx[512];
    char line[STRATUM_LINE_MAX];
    size_t line_len = 0;
    const int timeout_ms = config->io_timeout_ms > 0 ? config->io_timeout_ms : 5000;

    if (stratum_transport_connect(&transport, config->host, config->port, timeout_ms) != 0) {
        emit_error(callbacks, "connect failed");
        return -1;
    }
    if (callbacks != NULL && callbacks->connected != NULL) {
        callbacks->connected(callbacks->user_data);
    }
    if (send_startup_messages(&transport, config) != 0) {
        stratum_transport_close(&transport);
        emit_error(callbacks, "failed to send startup messages");
        return -1;
    }
    session = (stratum_session_t){
        .transport = &transport,
        .config = config,
        .next_submit_id = 10,
    };

    for (;;) {
        const int n = stratum_transport_recv(&transport, rx, sizeof(rx));
        if (n < 0) {
            emit_error(callbacks, "connection closed");
            stratum_transport_close(&transport);
            return -1;
        }
        if (n == 0) {
            if (callbacks != NULL && callbacks->idle != NULL) {
                callbacks->idle(callbacks->user_data, &session);
            }
            continue;
        }
        for (int i = 0; i < n; ++i) {
            if (rx[i] == '\n') {
                line[line_len] = '\0';
                if (line_len > 0 && handle_line(&session, line, callbacks) != 0) {
                    stratum_transport_close(&transport);
                    return -1;
                }
                if (callbacks != NULL && callbacks->idle != NULL) {
                    callbacks->idle(callbacks->user_data, &session);
                }
                line_len = 0;
            } else if (line_len + 1 < sizeof(line)) {
                line[line_len++] = rx[i];
            } else {
                line_len = 0;
                emit_error(callbacks, "received line exceeds STRATUM_LINE_MAX");
            }
        }
    }
}
