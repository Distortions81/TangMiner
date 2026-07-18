#ifndef STRATUM_WORK_STATE_H
#define STRATUM_WORK_STATE_H

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    uint64_t latest_generation;
    uint64_t invalidation_epoch;
} stratum_work_state_t;

typedef struct {
    uint64_t generation;
    uint64_t invalidation_epoch;
} stratum_work_snapshot_t;

void stratum_work_state_init(stratum_work_state_t* state);
void stratum_work_state_note_update(stratum_work_state_t* state, bool invalidates_inflight);
stratum_work_snapshot_t stratum_work_state_snapshot(const stratum_work_state_t* state);
bool stratum_work_snapshot_is_latest(
    const stratum_work_state_t* state, const stratum_work_snapshot_t* snapshot);
bool stratum_work_snapshot_is_valid(
    const stratum_work_state_t* state, const stratum_work_snapshot_t* snapshot);

#endif
