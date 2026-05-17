#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "stratum_client.h"

#define TANGMINER_JOB_PACKET_LEN 79
#define TANGMINER_MAX_EXTRANONCE1_HEX 128
#define TANGMINER_MAX_EXTRANONCE2_LEN 16
#define TANGMINER_MAX_EXTRANONCE2_HEX ((TANGMINER_MAX_EXTRANONCE2_LEN * 2) + 1)

typedef struct {
    uint8_t header[80];
    uint8_t share_target[32];
    uint8_t block_target[32];
    uint8_t packet[TANGMINER_JOB_PACKET_LEN];
    char job_id[STRATUM_JOB_ID_MAX];
    char ntime[STRATUM_HEX_4_MAX];
    char extranonce2_hex[TANGMINER_MAX_EXTRANONCE2_HEX];
} tangminer_work_t;

typedef struct {
    char extranonce1_hex[TANGMINER_MAX_EXTRANONCE1_HEX];
    uint8_t extranonce1[TANGMINER_MAX_EXTRANONCE1_HEX / 2];
    size_t extranonce1_len;
    uint32_t extranonce2_size;
    uint8_t extranonce2[TANGMINER_MAX_EXTRANONCE2_LEN];
    uint8_t fpga_target[32];
    double difficulty;
    uint64_t jobs_built;
} tangminer_builder_t;

void tangminer_builder_init(tangminer_builder_t* builder);
bool tangminer_builder_set_extranonce(
    tangminer_builder_t* builder, const char* extranonce1_hex, uint32_t extranonce2_size);
void tangminer_builder_set_difficulty(tangminer_builder_t* builder, double difficulty);
bool tangminer_builder_set_fpga_target(tangminer_builder_t* builder, const char* name);
bool tangminer_build_work(
    tangminer_builder_t* builder, const stratum_notify_t* notify, tangminer_work_t* out);
bool tangminer_validate_nonce(
    const tangminer_work_t* work, uint32_t nonce, uint8_t hash[32], bool* meets_share, bool* meets_block);
void tangminer_nonce_to_hex(uint32_t nonce, char out[9]);
void tangminer_nonce_to_submit_hex(uint32_t nonce, char out[9]);
