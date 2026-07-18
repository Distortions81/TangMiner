#include "stratum_continuous.h"

#include <stdio.h>
#include <string.h>

static int failures;

static void check(int condition, const char* message) {
    if (!condition) {
        fprintf(stderr, "FAIL: %s\n", message);
        ++failures;
    }
}

static void test_tagged_wire_format(void) {
    uint8_t legacy[TANGMINER_JOB_PACKET_LEN];
    uint8_t packet[TANGMINER_CONTINUOUS_JOB_PACKET_LEN];
    for (size_t i = 0; i < sizeof(legacy); ++i) {
        legacy[i] = (uint8_t)i;
    }
    legacy[0] = 'T';
    legacy[1] = 'N';
    legacy[2] = 'J';

    tangminer_continuous_encode_job(0x1234U, legacy, packet);
    check(packet[0] == 'T' && packet[1] == 'N' && packet[2] == 'Q',
          "continuous job command");
    check(packet[3] == 0x12 && packet[4] == 0x34, "continuous job big-endian tag");
    check(memcmp(packet + 5, legacy + 3, TANGMINER_JOB_PACKET_LEN - 3U) == 0,
          "continuous job preserves legacy payload");

    static const uint8_t response[TANGMINER_CONTINUOUS_RESULT_LEN] = {
        'R', 0xab, 0xcd, 0x12, 0x34, 0x56, 0x78,
    };
    uint16_t tag = 0;
    uint32_t nonce = 0;
    check(tangminer_continuous_decode_result(response, sizeof(response), &tag, &nonce),
          "decode continuous result");
    check(tag == 0xabcdU, "continuous result big-endian tag");
    check(nonce == 0x12345678U, "continuous result big-endian nonce");

    uint8_t invalid[sizeof(response)];
    memcpy(invalid, response, sizeof(invalid));
    invalid[0] = 'F';
    check(!tangminer_continuous_decode_result(invalid, sizeof(invalid), &tag, &nonce),
          "reject legacy result in continuous mode");
    check(!tangminer_continuous_decode_result(response, sizeof(response) - 1U, &tag, &nonce),
          "reject truncated continuous result");
}

static void test_eight_entry_tag_map(void) {
    tangminer_tag_map_t map;
    tangminer_tag_map_init(&map);

    for (uint16_t tag = 0; tag < 10; ++tag) {
        tangminer_work_t work;
        const stratum_work_snapshot_t snapshot = {
            .generation = (uint64_t)tag + 100U,
            .invalidation_epoch = (uint64_t)tag + 200U,
        };
        memset(&work, 0, sizeof(work));
        snprintf(work.job_id, sizeof(work.job_id), "job-%u", tag);
        work.header[0] = (uint8_t)tag;
        tangminer_tag_map_store(&map, tag, &work, &snapshot);
    }

    tangminer_tagged_work_t tagged_work;
    check(!tangminer_tag_map_find(&map, 0, &tagged_work), "oldest tag is evicted");
    check(!tangminer_tag_map_find(&map, 1, &tagged_work), "second-oldest tag is evicted");
    for (uint16_t tag = 2; tag < 10; ++tag) {
        check(tangminer_tag_map_find(&map, tag, &tagged_work), "retained tag is found");
        check(tagged_work.tag == tag, "retained tag value");
        check(tagged_work.work.header[0] == (uint8_t)tag, "retained work matches tag");
        check(tagged_work.work_snapshot.generation == (uint64_t)tag + 100U,
              "retained snapshot matches tag");
    }
}

static void test_job_schedule(void) {
    check(TANGMINER_CONTINUOUS_POLL_MS == 25, "continuous serial poll is 25 ms");
    check(tangminer_continuous_job_due(false, 0, 0, 0), "initial work is due");
    check(tangminer_continuous_job_due(true, 4, 5, 0), "new generation is due");
    check(!tangminer_continuous_job_due(true, 5, 5, 59999),
          "job does not roll before 60 seconds");
    check(tangminer_continuous_job_due(true, 5, 5, 60000),
          "job rolls at 60 seconds");
}

int main(void) {
    test_tagged_wire_format();
    test_eight_entry_tag_map();
    test_job_schedule();
    return failures == 0 ? 0 : 1;
}
