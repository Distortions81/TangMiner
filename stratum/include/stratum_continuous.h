#ifndef STRATUM_CONTINUOUS_H
#define STRATUM_CONTINUOUS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "stratum_miner.h"
#include "stratum_work_state.h"

#define TANGMINER_CONTINUOUS_JOB_PACKET_LEN (TANGMINER_JOB_PACKET_LEN + 2U)
#define TANGMINER_CONTINUOUS_RESULT_LEN 7U
#define TANGMINER_CONTINUOUS_TAG_CAPACITY 8U
#define TANGMINER_CONTINUOUS_POLL_MS 25
#define TANGMINER_CONTINUOUS_ROLL_MS 60000U

typedef struct {
    bool valid;
    uint16_t tag;
    tangminer_work_t work;
    stratum_work_snapshot_t work_snapshot;
} tangminer_tagged_work_t;

typedef struct {
    tangminer_tagged_work_t entries[TANGMINER_CONTINUOUS_TAG_CAPACITY];
    size_t next_entry;
} tangminer_tag_map_t;

void tangminer_continuous_encode_job(
    uint16_t tag,
    const uint8_t legacy_packet[TANGMINER_JOB_PACKET_LEN],
    uint8_t packet[TANGMINER_CONTINUOUS_JOB_PACKET_LEN]);
bool tangminer_continuous_decode_result(
    const uint8_t* response,
    size_t response_len,
    uint16_t* tag,
    uint32_t* nonce);

void tangminer_tag_map_init(tangminer_tag_map_t* map);
void tangminer_tag_map_store(
    tangminer_tag_map_t* map,
    uint16_t tag,
    const tangminer_work_t* work,
    const stratum_work_snapshot_t* work_snapshot);
bool tangminer_tag_map_find(
    const tangminer_tag_map_t* map,
    uint16_t tag,
    tangminer_tagged_work_t* tagged_work);

bool tangminer_continuous_job_due(
    bool sent_work,
    uint64_t sent_generation,
    uint64_t latest_generation,
    uint64_t elapsed_ms);

#endif
