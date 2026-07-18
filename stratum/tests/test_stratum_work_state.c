#include "stratum_work_state.h"

#include <stdio.h>

static int failures;

static void check(int condition, const char* message) {
    if (!condition) {
        fprintf(stderr, "FAIL: %s\n", message);
        ++failures;
    }
}

static void test_non_clean_update_preserves_inflight_work(void) {
    stratum_work_state_t state;
    stratum_work_state_init(&state);
    stratum_work_state_note_update(&state, true);
    const stratum_work_snapshot_t inflight = stratum_work_state_snapshot(&state);

    stratum_work_state_note_update(&state, false);

    check(!stratum_work_snapshot_is_latest(&state, &inflight),
          "non-clean update makes newer work available");
    check(stratum_work_snapshot_is_valid(&state, &inflight),
          "non-clean update preserves in-flight candidate validity");

    const stratum_work_snapshot_t replacement = stratum_work_state_snapshot(&state);
    check(stratum_work_snapshot_is_latest(&state, &replacement),
          "next work snapshot switches to latest non-clean job");
    check(stratum_work_snapshot_is_valid(&state, &replacement),
          "latest non-clean work remains valid");
}

static void test_clean_update_invalidates_inflight_and_queued_work(void) {
    stratum_work_state_t state;
    stratum_work_state_init(&state);
    stratum_work_state_note_update(&state, true);
    const stratum_work_snapshot_t inflight = stratum_work_state_snapshot(&state);
    const stratum_work_snapshot_t queued = inflight;

    stratum_work_state_note_update(&state, true);

    check(!stratum_work_snapshot_is_valid(&state, &inflight),
          "clean update rejects in-flight old-job candidate");
    check(!stratum_work_snapshot_is_valid(&state, &queued),
          "clean update rejects queued old-job share");

    const stratum_work_snapshot_t replacement = stratum_work_state_snapshot(&state);
    check(stratum_work_snapshot_is_latest(&state, &replacement),
          "next work snapshot switches to latest clean job");
    check(stratum_work_snapshot_is_valid(&state, &replacement),
          "latest clean work is valid");
}

int main(void) {
    test_non_clean_update_preserves_inflight_work();
    test_clean_update_invalidates_inflight_and_queued_work();
    return failures == 0 ? 0 : 1;
}
