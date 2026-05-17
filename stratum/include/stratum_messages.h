#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "stratum_client.h"

bool stratum_build_subscribe(char* out, size_t out_size, uint32_t id, const char* miner_name);
bool stratum_build_suggest_difficulty(char* out, size_t out_size, uint32_t id, double difficulty);
bool stratum_build_authorize(
    char* out, size_t out_size, uint32_t id, const char* user, const char* password);
bool stratum_build_pong(char* out, size_t out_size, uint32_t id);
bool stratum_build_submit(
    char* out,
    size_t out_size,
    uint32_t id,
    const char* user,
    const char* job_id,
    const char* extranonce2,
    const char* ntime,
    const char* nonce);

bool stratum_parse_subscribe_response(
    const char* line, char* extranonce1, size_t extranonce1_size, uint32_t* extranonce2_size);
bool stratum_parse_set_difficulty(const char* line, double* difficulty);
bool stratum_parse_set_extranonce(
    const char* line, char* extranonce1, size_t extranonce1_size, uint32_t* extranonce2_size);
bool stratum_parse_notify(const char* line, stratum_notify_t* out);
