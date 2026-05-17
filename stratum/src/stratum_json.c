#include "stratum_json.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void trim_span(stratum_span_t* span) {
    while (span->len > 0 &&
           (span->start[0] == ' ' || span->start[0] == '\t' || span->start[0] == '\n' ||
            span->start[0] == '\r')) {
        ++span->start;
        --span->len;
    }
    while (span->len > 0) {
        const char ch = span->start[span->len - 1];
        if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') {
            break;
        }
        --span->len;
    }
}

stratum_method_t stratum_json_method(const char* line) {
    const char* method = strstr(line, "\"method\"");
    if (method == NULL) {
        return STRATUM_METHOD_NONE;
    }
    const char* colon = strchr(method, ':');
    if (colon == NULL) {
        return STRATUM_METHOD_NONE;
    }
    ++colon;
    while (*colon == ' ' || *colon == '\t') {
        ++colon;
    }
    if (*colon != '"') {
        return STRATUM_METHOD_NONE;
    }
    ++colon;
    if (strncmp(colon, "mining.notify\"", 14) == 0) {
        return STRATUM_METHOD_NOTIFY;
    }
    if (strncmp(colon, "mining.set_difficulty\"", 22) == 0) {
        return STRATUM_METHOD_SET_DIFFICULTY;
    }
    if (strncmp(colon, "mining.set_extranonce\"", 22) == 0) {
        return STRATUM_METHOD_SET_EXTRANONCE;
    }
    if (strncmp(colon, "client.reconnect\"", 17) == 0) {
        return STRATUM_METHOD_CLIENT_RECONNECT;
    }
    if (strncmp(colon, "mining.ping\"", 12) == 0) {
        return STRATUM_METHOD_MINING_PING;
    }
    return STRATUM_METHOD_NONE;
}

bool stratum_json_find_array_for_key(const char* line, const char* key, stratum_span_t* out) {
    const char* cursor = strstr(line, key);
    if (cursor == NULL) {
        return false;
    }
    const char* bracket = strchr(cursor, '[');
    if (bracket == NULL) {
        return false;
    }
    int depth = 0;
    bool in_string = false;
    bool escaped = false;
    for (const char* p = bracket; *p != '\0'; ++p) {
        const char ch = *p;
        if (in_string) {
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                in_string = false;
            }
            continue;
        }
        if (ch == '"') {
            in_string = true;
        } else if (ch == '[') {
            ++depth;
        } else if (ch == ']') {
            --depth;
            if (depth == 0) {
                out->start = bracket + 1;
                out->len = (size_t)(p - bracket - 1);
                return true;
            }
        }
    }
    return false;
}

size_t stratum_json_split_array(stratum_span_t array, stratum_span_t* values, size_t max_values) {
    size_t count = 0;
    int depth = 0;
    bool in_string = false;
    bool escaped = false;
    trim_span(&array);
    if (array.len == 0) {
        return 0;
    }
    const char* item = array.start;
    const char* end = array.start + array.len;
    for (const char* p = array.start; p <= end; ++p) {
        const char ch = p < end ? *p : ',';
        if (in_string) {
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                in_string = false;
            }
            continue;
        }
        if (ch == '"') {
            in_string = true;
        } else if (ch == '[') {
            ++depth;
        } else if (ch == ']') {
            --depth;
        } else if ((ch == ',' && depth == 0) || p == end) {
            if (count < max_values) {
                values[count] = (stratum_span_t){
                    .start = item,
                    .len = (size_t)(p - item),
                };
                trim_span(&values[count]);
            }
            ++count;
            item = p + 1;
        }
    }
    return count;
}

bool stratum_json_unquote(stratum_span_t span, stratum_span_t* out) {
    trim_span(&span);
    if (span.len < 2 || span.start[0] != '"' || span.start[span.len - 1] != '"') {
        return false;
    }
    for (size_t i = 1; i + 1 < span.len; ++i) {
        if (span.start[i] == '\\') {
            return false;
        }
    }
    out->start = span.start + 1;
    out->len = span.len - 2;
    return true;
}

bool stratum_json_copy(stratum_span_t span, char* out, size_t out_size) {
    if (out_size == 0 || span.len >= out_size) {
        return false;
    }
    memcpy(out, span.start, span.len);
    out[span.len] = '\0';
    return true;
}

bool stratum_json_parse_id(const char* line, int* out) {
    const char* id = strstr(line, "\"id\"");
    if (id == NULL) {
        return false;
    }
    const char* colon = strchr(id, ':');
    if (colon == NULL) {
        return false;
    }
    ++colon;
    while (*colon == ' ' || *colon == '\t') {
        ++colon;
    }
    if (strncmp(colon, "null", 4) == 0) {
        return false;
    }
    int value = 0;
    bool found = false;
    while (*colon >= '0' && *colon <= '9') {
        found = true;
        value = (value * 10) + (*colon - '0');
        ++colon;
    }
    if (!found) {
        return false;
    }
    *out = value;
    return true;
}

bool stratum_json_parse_bool_result(const char* line, bool* out) {
    const char* result = strstr(line, "\"result\"");
    if (result == NULL) {
        return false;
    }
    const char* colon = strchr(result, ':');
    if (colon == NULL) {
        return false;
    }
    ++colon;
    while (*colon == ' ' || *colon == '\t') {
        ++colon;
    }
    if (strncmp(colon, "true", 4) == 0) {
        *out = true;
        return true;
    }
    if (strncmp(colon, "false", 5) == 0 || strncmp(colon, "null", 4) == 0) {
        *out = false;
        return true;
    }
    return false;
}

bool stratum_json_response_has_error(const char* line) {
    const char* error = strstr(line, "\"error\"");
    if (error == NULL) {
        return false;
    }
    const char* colon = strchr(error, ':');
    if (colon == NULL) {
        return false;
    }
    ++colon;
    while (*colon == ' ' || *colon == '\t') {
        ++colon;
    }
    return strncmp(colon, "null", 4) != 0;
}

bool stratum_json_append_text(char* out, size_t out_size, size_t* offset, const char* value) {
    const size_t len = strlen(value);
    if (*offset + len >= out_size) {
        return false;
    }
    memcpy(out + *offset, value, len);
    *offset += len;
    out[*offset] = '\0';
    return true;
}

bool stratum_json_append_string(char* out, size_t out_size, size_t* offset, const char* value) {
    if (!stratum_json_append_text(out, out_size, offset, "\"")) {
        return false;
    }
    for (const char* p = value; *p != '\0'; ++p) {
        char escaped[3] = {'\\', *p, '\0'};
        if (*p == '"' || *p == '\\') {
            if (!stratum_json_append_text(out, out_size, offset, escaped)) {
                return false;
            }
        } else {
            char raw[2] = {*p, '\0'};
            if (!stratum_json_append_text(out, out_size, offset, raw)) {
                return false;
            }
        }
    }
    return stratum_json_append_text(out, out_size, offset, "\"");
}

bool stratum_json_span_to_u32(stratum_span_t span, uint32_t* out) {
    uint32_t value = 0;
    bool found = false;
    trim_span(&span);
    for (size_t i = 0; i < span.len; ++i) {
        const char ch = span.start[i];
        if (ch < '0' || ch > '9') {
            return false;
        }
        found = true;
        value = value * 10U + (uint32_t)(ch - '0');
    }
    if (!found) {
        return false;
    }
    *out = value;
    return true;
}

bool stratum_json_span_to_double(stratum_span_t span, double* out) {
    char buffer[64];
    char* end = NULL;
    trim_span(&span);
    if (!stratum_json_copy(span, buffer, sizeof(buffer))) {
        return false;
    }
    const double value = strtod(buffer, &end);
    if (end == buffer || *end != '\0') {
        return false;
    }
    *out = value;
    return true;
}
