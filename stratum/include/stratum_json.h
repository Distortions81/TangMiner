#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef struct {
    const char* start;
    size_t len;
} stratum_span_t;

typedef enum {
    STRATUM_METHOD_NONE = 0,
    STRATUM_METHOD_NOTIFY,
    STRATUM_METHOD_SET_DIFFICULTY,
    STRATUM_METHOD_SET_EXTRANONCE,
    STRATUM_METHOD_CLIENT_RECONNECT,
    STRATUM_METHOD_MINING_PING,
} stratum_method_t;

stratum_method_t stratum_json_method(const char* line);
bool stratum_json_find_array_for_key(const char* line, const char* key, stratum_span_t* out);
size_t stratum_json_split_array(stratum_span_t array, stratum_span_t* values, size_t max_values);
bool stratum_json_unquote(stratum_span_t span, stratum_span_t* out);
bool stratum_json_copy(stratum_span_t span, char* out, size_t out_size);
bool stratum_json_parse_id(const char* line, int* out);
bool stratum_json_parse_bool_result(const char* line, bool* out);
bool stratum_json_response_has_error(const char* line);
bool stratum_json_append_string(char* out, size_t out_size, size_t* offset, const char* value);
bool stratum_json_append_text(char* out, size_t out_size, size_t* offset, const char* value);
bool stratum_json_span_to_u32(stratum_span_t span, uint32_t* out);
bool stratum_json_span_to_double(stratum_span_t span, double* out);
