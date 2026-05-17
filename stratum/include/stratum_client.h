#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define STRATUM_HOST_MAX 128
#define STRATUM_USER_MAX 160
#define STRATUM_PASS_MAX 80
#define STRATUM_MINER_NAME_MAX 80
#define STRATUM_LINE_MAX 4096
#define STRATUM_JOB_ID_MAX 96
#define STRATUM_HEX_32_MAX 65
#define STRATUM_HEX_4_MAX 9
#define STRATUM_BRANCH_MAX 16

typedef struct {
    char host[STRATUM_HOST_MAX];
    uint16_t port;
    char user[STRATUM_USER_MAX];
    char password[STRATUM_PASS_MAX];
    char miner_name[STRATUM_MINER_NAME_MAX];
    double suggested_difficulty;
    int io_timeout_ms;
    bool quiet;
} stratum_config_t;

typedef struct {
    char job_id[STRATUM_JOB_ID_MAX];
    char prev_hash[STRATUM_HEX_32_MAX];
    char coinbase1[STRATUM_LINE_MAX];
    char coinbase2[STRATUM_LINE_MAX];
    char merkle_branches[STRATUM_BRANCH_MAX][STRATUM_HEX_32_MAX];
    size_t merkle_branch_count;
    char version[STRATUM_HEX_4_MAX];
    char nbits[STRATUM_HEX_4_MAX];
    char ntime[STRATUM_HEX_4_MAX];
    bool clean_jobs;
} stratum_notify_t;

typedef struct stratum_session stratum_session_t;

typedef struct {
    void* user_data;
    void (*connected)(void* user_data);
    void (*subscribed)(void* user_data, const char* extranonce1, uint32_t extranonce2_size);
    void (*authorized)(void* user_data, bool accepted);
    void (*difficulty)(void* user_data, double difficulty);
    void (*notify)(void* user_data, stratum_session_t* session, const stratum_notify_t* notify);
    void (*idle)(void* user_data, stratum_session_t* session);
    void (*line)(void* user_data, const char* line);
    void (*error)(void* user_data, const char* message);
} stratum_callbacks_t;

int stratum_client_run_once(const stratum_config_t* config, const stratum_callbacks_t* callbacks);
int stratum_client_submit_share(
    stratum_session_t* session,
    const char* job_id,
    const char* extranonce2,
    const char* ntime,
    const char* nonce);
