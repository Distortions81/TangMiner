import hashlib
import random

import cocotb
from cocotb.clock import Clock
from cocotb.triggers import ReadOnly, RisingEdge, Timer

from tangminer_emulator import (
    ALL_ONES_TARGET,
    GENESIS_HEADER,
    build_job_from_header,
    round_skip_payload_fields,
)


MASK32 = 0xFFFFFFFF
SHA256_IV7 = 0x5BE0CD19

# The engine registers 61 first-pass rounds, one feed-forward boundary, 61
# second-pass rounds, and the externally visible candidate. A start pulse
# flushes the old valids; the first round register accepts on the following
# edge, yielding this exact start-edge-to-candidate-edge latency.
PIPELINE_REGISTER_STAGES = 61 + 1 + 61 + 1
EXPECTED_START_TO_FIRST_CANDIDATE = PIPELINE_REGISTER_STAGES


def _clock(signal, period, unit):
    try:
        return Clock(signal, period, unit=unit)
    except TypeError:
        return Clock(signal, period, units=unit)


async def _sample_edge(dut):
    await RisingEdge(dut.clk)
    await ReadOnly()


async def _leave_read_only():
    try:
        await Timer(1, unit="ps")
    except TypeError:
        await Timer(1, units="ps")


def _sha256d_header(header_prefix: bytes, nonce: int) -> bytes:
    header = header_prefix + (nonce & MASK32).to_bytes(4, "big")
    return hashlib.sha256(hashlib.sha256(header).digest()).digest()


def _expected_candidate_low32(header_prefix: bytes, nonce: int) -> int:
    # The round-skipped second pass exposes E after round 60. That word shifts
    # into H during rounds 61..63, so adding IV[7] yields digest word 7.
    digest_word7 = int.from_bytes(_sha256d_header(header_prefix, nonce)[28:32], "big")
    return (digest_word7 - SHA256_IV7) & MASK32


def _quick26_matches(header_prefix: bytes, nonce: int) -> bool:
    digest_word7 = int.from_bytes(_sha256d_header(header_prefix, nonce)[28:32], "big")
    return (digest_word7 & 0x00FFFFFF) == 0 and (digest_word7 >> 30) == 0


def _job_fields(header_prefix: bytes):
    job = build_job_from_header(header_prefix + bytes(4), ALL_ONES_TARGET)
    tail2, w16, w17, prefix_state = round_skip_payload_fields(job)
    return {
        "midstate": int.from_bytes(job.midstate, "big"),
        "roundSkipPrefixState": int.from_bytes(prefix_state, "big"),
        "roundSkipTail2": int.from_bytes(tail2, "big"),
        "roundSkipW16": int.from_bytes(w16, "big"),
        "roundSkipW17": int.from_bytes(w17, "big"),
    }


async def _drive_job(dut, header_prefix: bytes, start_nonce: int, stride: int):
    for name, value in _job_fields(header_prefix).items():
        getattr(dut, name).value = value
    dut.candidateMode.value = 4
    dut.startNonce.value = start_nonce & MASK32
    dut.nonceStride.value = stride & MASK32


async def _pulse(dut, signal):
    signal.value = 1
    await _sample_edge(dut)
    valid_at_pulse = int(dut.candidateValid.value)
    running_at_pulse = int(dut.running.value)
    await _leave_read_only()
    signal.value = 0
    return valid_at_pulse, running_at_pulse


def _check_candidate(dut, header_prefix: bytes, expected_nonce: int):
    actual_nonce = int(dut.candidateNonce.value)
    actual_low32 = int(dut.candidateLow32.value)
    assert actual_nonce == expected_nonce, (
        f"candidate nonce mismatch: got 0x{actual_nonce:08x}, "
        f"expected 0x{expected_nonce:08x}"
    )
    expected_low32 = _expected_candidate_low32(header_prefix, expected_nonce)
    assert actual_low32 == expected_low32, (
        f"nonce 0x{expected_nonce:08x}: candidateLow32 0x{actual_low32:08x}, "
        f"expected 0x{expected_low32:08x}"
    )


async def _wait_for_first_candidate(dut, expected_latency: int):
    for cycles_after_start in range(1, expected_latency + 1):
        await _sample_edge(dut)
        valid = int(dut.candidateValid.value)
        if cycles_after_start < expected_latency:
            assert valid == 0, (
                f"candidate became valid after only {cycles_after_start} cycles; "
                f"expected {expected_latency}"
            )
        else:
            assert valid == 1, (
                f"first candidate missing at exact latency {expected_latency}"
            )


async def _collect_contiguous(
    dut,
    header_prefix: bytes,
    first_nonce: int,
    stride: int,
    count: int,
    first_already_sampled: bool = True,
):
    nonce = first_nonce & MASK32
    for index in range(count):
        if index != 0 or not first_already_sampled:
            await _sample_edge(dut)
        assert int(dut.candidateValid.value) == 1, (
            f"candidateValid gap at stream index {index}"
        )
        _check_candidate(dut, header_prefix, nonce)
        assert not _quick26_matches(header_prefix, nonce), (
            "test vector unexpectedly matches the fixed quick-26 stop filter"
        )
        nonce = (nonce + stride) & MASK32


async def _start_job_and_wait(dut, header_prefix: bytes, start_nonce: int, stride: int):
    await _leave_read_only()
    await _drive_job(dut, header_prefix, start_nonce, stride)
    valid_at_start, _ = await _pulse(dut, dut.start)
    assert valid_at_start == 0, "start/job replacement did not flush an old candidate"
    await _wait_for_first_candidate(dut, EXPECTED_START_TO_FIRST_CANDIDATE)


@cocotb.test()
async def unrolled_pipeline_is_bit_exact_and_flushes_control_events(dut):
    cocotb.start_soon(_clock(dut.clk, 10, "ns").start())

    dut.reset.value = 1
    dut.start.value = 0
    dut.stop.value = 0
    dut.midstate.value = 0
    dut.candidateMode.value = 4
    dut.roundSkipPrefixState.value = 0
    dut.roundSkipTail2.value = 0
    dut.roundSkipW16.value = 0
    dut.roundSkipW17.value = 0
    dut.startNonce.value = 0
    dut.nonceStride.value = 1
    for _ in range(5):
        await _sample_edge(dut)
        assert int(dut.candidateValid.value) == 0

    await _leave_read_only()
    dut.reset.value = 0

    rng = random.Random(0x1385A256)
    random_header_prefix = bytes(rng.getrandbits(8) for _ in range(76))
    genesis_prefix = GENESIS_HEADER[:76]

    # Randomized start and odd stride exercise back-to-back independent nonces.
    random_start = rng.getrandbits(32)
    random_stride = 0x9E3779B1
    await _start_job_and_wait(dut, random_header_prefix, random_start, random_stride)
    _check_candidate(dut, random_header_prefix, random_start)
    await _collect_contiguous(
        dut,
        random_header_prefix,
        random_start,
        random_stride,
        count=24,
    )

    # Replace an active job. There must be a full empty latency window, with no
    # candidates from the preceding job leaking out.
    replacement_nonce = 0x10203040
    await _start_job_and_wait(dut, genesis_prefix, replacement_nonce, 3)
    _check_candidate(dut, genesis_prefix, replacement_nonce)
    await _collect_contiguous(
        dut,
        genesis_prefix,
        replacement_nonce,
        3,
        count=12,
    )

    # Stop must invalidate the whole pipe immediately and keep it quiet. A
    # later start fills from scratch and resumes at one nonce per cycle.
    await _leave_read_only()
    valid_at_stop, running_at_stop = await _pulse(dut, dut.stop)
    assert valid_at_stop == 0, "stop did not clear candidateValid"
    assert running_at_stop == 0, "stop did not clear running"
    for _ in range(8):
        await _sample_edge(dut)
        assert int(dut.candidateValid.value) == 0
        assert int(dut.running.value) == 0

    restart_nonce = rng.getrandbits(32)
    await _start_job_and_wait(dut, random_header_prefix, restart_nonce, 5)
    _check_candidate(dut, random_header_prefix, restart_nonce)
    await _collect_contiguous(
        dut,
        random_header_prefix,
        restart_nonce,
        5,
        count=10,
    )

    # The nonce adder is modulo 2^32. Verify wrap while keeping every output
    # cycle occupied.
    wrap_start = 0xFFFFFFFA
    wrap_stride = 7
    await _start_job_and_wait(dut, genesis_prefix, wrap_start, wrap_stride)
    _check_candidate(dut, genesis_prefix, wrap_start)
    await _collect_contiguous(
        dut,
        genesis_prefix,
        wrap_start,
        wrap_stride,
        count=10,
    )
