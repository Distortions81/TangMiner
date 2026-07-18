#include "stratum_work_state.h"

void stratum_work_state_init(stratum_work_state_t* state) {
    state->latest_generation = 0;
    state->invalidation_epoch = 0;
}

void stratum_work_state_note_update(stratum_work_state_t* state, bool invalidates_inflight) {
    ++state->latest_generation;
    if (invalidates_inflight) {
        ++state->invalidation_epoch;
    }
}

stratum_work_snapshot_t stratum_work_state_snapshot(const stratum_work_state_t* state) {
    const stratum_work_snapshot_t snapshot = {
        .generation = state->latest_generation,
        .invalidation_epoch = state->invalidation_epoch,
    };
    return snapshot;
}

bool stratum_work_snapshot_is_latest(
    const stratum_work_state_t* state, const stratum_work_snapshot_t* snapshot) {
    return snapshot->generation == state->latest_generation;
}

bool stratum_work_snapshot_is_valid(
    const stratum_work_state_t* state, const stratum_work_snapshot_t* snapshot) {
    return snapshot->invalidation_epoch == state->invalidation_epoch;
}
