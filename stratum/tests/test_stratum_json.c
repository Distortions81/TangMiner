#include "stratum_json.h"
#include "stratum_messages.h"

#include <stdio.h>
#include <string.h>

static int failures;

static void check(int condition, const char* message) {
    if (!condition) {
        fprintf(stderr, "FAIL: %s\n", message);
        ++failures;
    }
}

int main(void) {
    const char* subscribe =
        "{\"id\":1,\"result\":[[[\"mining.set_difficulty\",\"x\"],[\"mining.notify\",\"y\"]],"
        "\"abcd\",4],\"error\":null}";
    char extranonce1[16];
    uint32_t extranonce2_size = 0;
    check(stratum_parse_subscribe_response(
              subscribe, extranonce1, sizeof(extranonce1), &extranonce2_size),
          "parse subscribe response");
    check(strcmp(extranonce1, "abcd") == 0, "subscribe extranonce1");
    check(extranonce2_size == 4, "subscribe extranonce2 size");

    const char* diff = "{\"id\":null,\"method\":\"mining.set_difficulty\",\"params\":[1024]}";
    double difficulty = 0.0;
    check(stratum_json_method(diff) == STRATUM_METHOD_SET_DIFFICULTY, "difficulty method");
    check(stratum_parse_set_difficulty(diff, &difficulty), "parse difficulty");
    check(difficulty == 1024.0, "difficulty value");

    const char* notify =
        "{\"id\":null,\"method\":\"mining.notify\",\"params\":[\"job1\","
        "\"0000000000000000000000000000000000000000000000000000000000000001\","
        "\"aa\",\"bb\",[\"1111111111111111111111111111111111111111111111111111111111111111\"],"
        "\"20000000\",\"170fffff\",\"65abcdef\",true]}";
    stratum_notify_t job;
    check(stratum_parse_notify(notify, &job), "parse notify");
    check(strcmp(job.job_id, "job1") == 0, "notify job id");
    check(job.merkle_branch_count == 1, "notify branch count");
    check(strcmp(job.version, "20000000") == 0, "notify version");
    check(job.clean_jobs, "notify clean flag");

    char line[160];
    check(stratum_build_authorize(line, sizeof(line), 3, "user\"x", "p\\w"), "build authorize");
    check(strstr(line, "\"user\\\"x\"") != NULL, "authorize escapes quote");
    check(strstr(line, "\"p\\\\w\"") != NULL, "authorize escapes slash");

    return failures == 0 ? 0 : 1;
}
