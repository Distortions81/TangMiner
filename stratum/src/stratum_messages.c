#include "stratum_messages.h"

#include <stdio.h>
#include <string.h>

#include "stratum_json.h"

bool stratum_build_subscribe(char* out, size_t out_size, uint32_t id, const char* miner_name) {
    size_t offset = 0;
    out[0] = '\0';
    char id_text[16];
    snprintf(id_text, sizeof(id_text), "%u", id);
    return stratum_json_append_text(out, out_size, &offset, "{\"id\":") &&
           stratum_json_append_text(out, out_size, &offset, id_text) &&
           stratum_json_append_text(out, out_size, &offset, ",\"method\":\"mining.subscribe\",\"params\":[") &&
           stratum_json_append_string(out, out_size, &offset, miner_name) &&
           stratum_json_append_text(out, out_size, &offset, "]}\n");
}

bool stratum_build_suggest_difficulty(char* out, size_t out_size, uint32_t id, double difficulty) {
    size_t offset = 0;
    char id_text[16];
    char diff_text[40];
    out[0] = '\0';
    snprintf(id_text, sizeof(id_text), "%u", id);
    snprintf(diff_text, sizeof(diff_text), "%.8g", difficulty);
    return stratum_json_append_text(out, out_size, &offset, "{\"id\":") &&
           stratum_json_append_text(out, out_size, &offset, id_text) &&
           stratum_json_append_text(
               out, out_size, &offset, ",\"method\":\"mining.suggest_difficulty\",\"params\":[") &&
           stratum_json_append_text(out, out_size, &offset, diff_text) &&
           stratum_json_append_text(out, out_size, &offset, "]}\n");
}

bool stratum_build_authorize(
    char* out, size_t out_size, uint32_t id, const char* user, const char* password) {
    size_t offset = 0;
    char id_text[16];
    out[0] = '\0';
    snprintf(id_text, sizeof(id_text), "%u", id);
    return stratum_json_append_text(out, out_size, &offset, "{\"id\":") &&
           stratum_json_append_text(out, out_size, &offset, id_text) &&
           stratum_json_append_text(out, out_size, &offset, ",\"method\":\"mining.authorize\",\"params\":[") &&
           stratum_json_append_string(out, out_size, &offset, user) &&
           stratum_json_append_text(out, out_size, &offset, ",") &&
           stratum_json_append_string(out, out_size, &offset, password) &&
           stratum_json_append_text(out, out_size, &offset, "]}\n");
}

bool stratum_build_pong(char* out, size_t out_size, uint32_t id) {
    size_t offset = 0;
    char id_text[16];
    out[0] = '\0';
    snprintf(id_text, sizeof(id_text), "%u", id);
    return stratum_json_append_text(out, out_size, &offset, "{\"id\":") &&
           stratum_json_append_text(out, out_size, &offset, id_text) &&
           stratum_json_append_text(out, out_size, &offset, ",\"result\":\"pong\",\"error\":null}\n");
}

bool stratum_build_submit(
    char* out,
    size_t out_size,
    uint32_t id,
    const char* user,
    const char* job_id,
    const char* extranonce2,
    const char* ntime,
    const char* nonce) {
    size_t offset = 0;
    char id_text[16];
    out[0] = '\0';
    snprintf(id_text, sizeof(id_text), "%u", id);
    return stratum_json_append_text(out, out_size, &offset, "{\"id\":") &&
           stratum_json_append_text(out, out_size, &offset, id_text) &&
           stratum_json_append_text(out, out_size, &offset, ",\"method\":\"mining.submit\",\"params\":[") &&
           stratum_json_append_string(out, out_size, &offset, user) &&
           stratum_json_append_text(out, out_size, &offset, ",") &&
           stratum_json_append_string(out, out_size, &offset, job_id) &&
           stratum_json_append_text(out, out_size, &offset, ",") &&
           stratum_json_append_string(out, out_size, &offset, extranonce2) &&
           stratum_json_append_text(out, out_size, &offset, ",") &&
           stratum_json_append_string(out, out_size, &offset, ntime) &&
           stratum_json_append_text(out, out_size, &offset, ",") &&
           stratum_json_append_string(out, out_size, &offset, nonce) &&
           stratum_json_append_text(out, out_size, &offset, "]}\n");
}

bool stratum_parse_subscribe_response(
    const char* line, char* extranonce1, size_t extranonce1_size, uint32_t* extranonce2_size) {
    stratum_span_t result;
    stratum_span_t values[4];
    stratum_span_t extranonce1_hex;
    if (!stratum_json_find_array_for_key(line, "\"result\"", &result)) {
        return false;
    }
    if (stratum_json_split_array(result, values, 4) < 3) {
        return false;
    }
    if (!stratum_json_unquote(values[1], &extranonce1_hex) ||
        !stratum_json_copy(extranonce1_hex, extranonce1, extranonce1_size) ||
        !stratum_json_span_to_u32(values[2], extranonce2_size)) {
        return false;
    }
    return true;
}

bool stratum_parse_set_difficulty(const char* line, double* difficulty) {
    stratum_span_t params;
    stratum_span_t values[1];
    if (!stratum_json_find_array_for_key(line, "\"params\"", &params)) {
        return false;
    }
    if (stratum_json_split_array(params, values, 1) < 1) {
        return false;
    }
    return stratum_json_span_to_double(values[0], difficulty);
}

bool stratum_parse_set_extranonce(
    const char* line, char* extranonce1, size_t extranonce1_size, uint32_t* extranonce2_size) {
    stratum_span_t params;
    stratum_span_t values[2];
    stratum_span_t extranonce1_hex;
    if (!stratum_json_find_array_for_key(line, "\"params\"", &params)) {
        return false;
    }
    if (stratum_json_split_array(params, values, 2) < 2) {
        return false;
    }
    return stratum_json_unquote(values[0], &extranonce1_hex) &&
           stratum_json_copy(extranonce1_hex, extranonce1, extranonce1_size) &&
           stratum_json_span_to_u32(values[1], extranonce2_size);
}

bool stratum_parse_notify(const char* line, stratum_notify_t* out) {
    stratum_span_t params;
    stratum_span_t values[9];
    stratum_span_t text;
    memset(out, 0, sizeof(*out));
    if (!stratum_json_find_array_for_key(line, "\"params\"", &params)) {
        return false;
    }
    if (stratum_json_split_array(params, values, 9) < 9) {
        return false;
    }
    if (!stratum_json_unquote(values[0], &text) ||
        !stratum_json_copy(text, out->job_id, sizeof(out->job_id)) ||
        !stratum_json_unquote(values[1], &text) ||
        !stratum_json_copy(text, out->prev_hash, sizeof(out->prev_hash)) ||
        !stratum_json_unquote(values[2], &text) ||
        !stratum_json_copy(text, out->coinbase1, sizeof(out->coinbase1)) ||
        !stratum_json_unquote(values[3], &text) ||
        !stratum_json_copy(text, out->coinbase2, sizeof(out->coinbase2)) ||
        !stratum_json_unquote(values[5], &text) ||
        !stratum_json_copy(text, out->version, sizeof(out->version)) ||
        !stratum_json_unquote(values[6], &text) ||
        !stratum_json_copy(text, out->nbits, sizeof(out->nbits)) ||
        !stratum_json_unquote(values[7], &text) ||
        !stratum_json_copy(text, out->ntime, sizeof(out->ntime))) {
        return false;
    }

    if (values[4].len >= 2 && values[4].start[0] == '[') {
        stratum_span_t branch_array = {
            .start = values[4].start + 1,
            .len = values[4].len - 2,
        };
        stratum_span_t branches[STRATUM_BRANCH_MAX + 1];
        const size_t count = stratum_json_split_array(branch_array, branches, STRATUM_BRANCH_MAX + 1);
        if (count > STRATUM_BRANCH_MAX) {
            return false;
        }
        out->merkle_branch_count = count;
        for (size_t i = 0; i < count; ++i) {
            if (!stratum_json_unquote(branches[i], &text) ||
                !stratum_json_copy(text, out->merkle_branches[i], sizeof(out->merkle_branches[i]))) {
                return false;
            }
        }
    }

    out->clean_jobs = values[8].len == 4 && strncmp(values[8].start, "true", 4) == 0;
    return true;
}
