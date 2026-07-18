#include "stratum_continuous.h"

#include <string.h>

void tangminer_continuous_encode_job(
    uint16_t tag,
    const uint8_t legacy_packet[TANGMINER_JOB_PACKET_LEN],
    uint8_t packet[TANGMINER_CONTINUOUS_JOB_PACKET_LEN]) {
    packet[0] = 'T';
    packet[1] = 'N';
    packet[2] = 'Q';
    packet[3] = (uint8_t)(tag >> 8);
    packet[4] = (uint8_t)tag;
    memcpy(packet + 5, legacy_packet + 3, TANGMINER_JOB_PACKET_LEN - 3U);
}

bool tangminer_continuous_decode_result(
    const uint8_t* response,
    size_t response_len,
    uint16_t* tag,
    uint32_t* nonce) {
    if (response == NULL || tag == NULL || nonce == NULL ||
        response_len != TANGMINER_CONTINUOUS_RESULT_LEN || response[0] != 'R') {
        return false;
    }
    *tag = (uint16_t)(((uint16_t)response[1] << 8) | response[2]);
    *nonce = ((uint32_t)response[3] << 24) | ((uint32_t)response[4] << 16) |
             ((uint32_t)response[5] << 8) | response[6];
    return true;
}

void tangminer_tag_map_init(tangminer_tag_map_t* map) {
    memset(map, 0, sizeof(*map));
}

void tangminer_tag_map_store(
    tangminer_tag_map_t* map,
    uint16_t tag,
    const tangminer_work_t* work,
    const stratum_work_snapshot_t* work_snapshot) {
    tangminer_tagged_work_t* entry = &map->entries[map->next_entry];
    entry->valid = true;
    entry->tag = tag;
    entry->work = *work;
    entry->work_snapshot = *work_snapshot;
    map->next_entry = (map->next_entry + 1U) % TANGMINER_CONTINUOUS_TAG_CAPACITY;
}

bool tangminer_tag_map_find(
    const tangminer_tag_map_t* map,
    uint16_t tag,
    tangminer_tagged_work_t* tagged_work) {
    for (size_t i = 0; i < TANGMINER_CONTINUOUS_TAG_CAPACITY; ++i) {
        const tangminer_tagged_work_t* entry = &map->entries[i];
        if (entry->valid && entry->tag == tag) {
            if (tagged_work != NULL) {
                *tagged_work = *entry;
            }
            return true;
        }
    }
    return false;
}

bool tangminer_continuous_job_due(
    bool sent_work,
    uint64_t sent_generation,
    uint64_t latest_generation,
    uint64_t elapsed_ms) {
    return !sent_work || sent_generation != latest_generation ||
           elapsed_ms >= TANGMINER_CONTINUOUS_ROLL_MS;
}
