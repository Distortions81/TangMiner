import os

import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge

from tangminer_emulator import (
    ALL_ONES_TARGET,
    GENESIS_EXPECTED_HASH_NONCE_ZERO,
    GENESIS_HEADER,
    QUICK3_TARGET,
    bitcoin_hash,
    build_job_from_header,
    encode_job_payload,
    format_rate,
    meets_target,
    share_difficulty,
    target_difficulty,
)


CLKS_PER_BIT = int(os.environ.get("CLKS_PER_BIT", "8"))
HARDWARE_CLOCK_HZ = int(os.environ.get("HARDWARE_CLOCK_HZ", "111000000"))
LANE_COUNT = int(os.environ.get("LANE_COUNT", "4"))


def _clock(signal, period, unit):
    try:
        return Clock(signal, period, unit=unit)
    except TypeError:
        return Clock(signal, period, units=unit)


async def _ticks(dut, cycles):
    for _ in range(cycles):
        await RisingEdge(dut.clk)


async def _start_clock(dut):
    cocotb.start_soon(_clock(dut.clk, 37, "ns").start())
    dut.uart_rx_pin.value = 1
    await _ticks(dut, 32)
    assert int(dut.uart_tx_pin.value) == 1


async def _uart_write_byte(dut, value):
    dut.uart_rx_pin.value = 0
    await _ticks(dut, CLKS_PER_BIT)

    for bit in range(8):
        dut.uart_rx_pin.value = (value >> bit) & 1
        await _ticks(dut, CLKS_PER_BIT)

    dut.uart_rx_pin.value = 1
    await _ticks(dut, CLKS_PER_BIT)


async def _uart_write(dut, payload):
    for byte in payload:
        await _uart_write_byte(dut, byte)


async def _wait_for_tx_start(dut, max_cycles=20_000):
    for _ in range(max_cycles):
        if dut.uart_tx_pin.value.is_resolvable and int(dut.uart_tx_pin.value) == 0:
            return
        await RisingEdge(dut.clk)
    raise AssertionError("timed out waiting for UART TX start bit")


async def _uart_read_byte(dut):
    await _wait_for_tx_start(dut)
    await _ticks(dut, CLKS_PER_BIT + CLKS_PER_BIT // 2)

    value = 0
    for bit in range(8):
        value |= int(dut.uart_tx_pin.value) << bit
        await _ticks(dut, CLKS_PER_BIT)

    assert int(dut.uart_tx_pin.value) == 1
    await _ticks(dut, max(1, CLKS_PER_BIT // 2))
    return value


async def _uart_read(dut, length):
    return bytes([await _uart_read_byte(dut) for _ in range(length)])


def _resolve_signal(dut, *names):
    for name in names:
        try:
            signal = getattr(dut, name)
            signal.value
            return signal
        except (AttributeError, ValueError):
            pass
    raise AssertionError(f"none of these internal signals were found: {', '.join(names)}")


async def _wait_for_nonce(dut, nonce_signal, target, start_cycle, max_cycles=5_000):
    cycle = start_cycle
    for _ in range(max_cycles):
        if nonce_signal.value.is_resolvable and int(nonce_signal.value) >= target:
            return cycle
        await RisingEdge(dut.clk)
        cycle += 1
    raise AssertionError(f"timed out waiting for nonce {target}")


async def _wait_for_nonce_value(dut, nonce_signal, value, start_cycle, max_cycles=5_000):
    cycle = start_cycle
    for _ in range(max_cycles):
        if nonce_signal.value.is_resolvable and int(nonce_signal.value) == value:
            return cycle
        await RisingEdge(dut.clk)
        cycle += 1
    raise AssertionError(f"timed out waiting for nonce value {value}")


@cocotb.test()
async def top_echoes_job_payload(dut):
    await _start_clock(dut)

    job = build_job_from_header(GENESIS_HEADER, ALL_ONES_TARGET)
    payload = encode_job_payload(job)

    await _uart_write(dut, b"TNE" + payload)

    response = await _uart_read(dut, 77)
    assert response == b"E" + payload


@cocotb.test()
async def top_hashes_genesis_nonce_zero(dut):
    await _start_clock(dut)

    job = build_job_from_header(GENESIS_HEADER, ALL_ONES_TARGET)
    payload = encode_job_payload(job)

    await _uart_write(dut, b"TNJ" + payload)

    response = await _uart_read(dut, 5)
    assert response[:1] == b"F"
    assert response[1:5] == b"\x00\x00\x00\x00"
    assert bitcoin_hash(job, 0) == GENESIS_EXPECTED_HASH_NONCE_ZERO
    assert meets_target(GENESIS_EXPECTED_HASH_NONCE_ZERO, ALL_ONES_TARGET)


@cocotb.test()
async def top_hashes_genesis_nonce_three(dut):
    await _start_clock(dut)

    all_ones_job = build_job_from_header(GENESIS_HEADER, ALL_ONES_TARGET)
    expected_hash = bitcoin_hash(all_ones_job, 3)
    job = build_job_from_header(GENESIS_HEADER, QUICK3_TARGET)
    payload = encode_job_payload(job)

    await _uart_write(dut, b"TNJ" + payload)

    response = await _uart_read(dut, 5)
    assert response[:1] == b"F"
    assert response[1:5] == b"\x00\x00\x00\x03"
    assert meets_target(expected_hash, QUICK3_TARGET)
    assert share_difficulty(expected_hash) >= target_difficulty(QUICK3_TARGET)


@cocotb.test()
async def top_reports_cycle_accurate_hashrate(dut):
    await _start_clock(dut)

    nonce_signal = _resolve_signal(
        dut,
        "current_nonce",
        "coreArea_currentNonce",
        "coreArea_cores_0_io_currentNonce",
        "coreArea_core_io_currentNonce",
    )
    job = build_job_from_header(GENESIS_HEADER, b"\x00" * 32)
    payload = encode_job_payload(job)

    await _uart_write(dut, b"TNJ" + payload)

    cycle = 0
    cycle = await _wait_for_nonce_value(dut, nonce_signal, 0, cycle)
    lane_nonce_cycles = {}
    for nonce in range(LANE_COUNT, LANE_COUNT * 5, LANE_COUNT):
        cycle = await _wait_for_nonce(dut, nonce_signal, nonce, cycle)
        lane_nonce_cycles[nonce] = cycle

    lane_deltas = [
        lane_nonce_cycles[nonce] - lane_nonce_cycles[nonce - LANE_COUNT]
        for nonce in range(LANE_COUNT * 2, LANE_COUNT * 5, LANE_COUNT)
    ]
    assert min(lane_deltas) == max(lane_deltas), f"non-steady lane cycle deltas: {lane_deltas}"

    lane_period_cycles = lane_deltas[0]
    cycles_per_nonce = lane_period_cycles / LANE_COUNT
    hashes_per_second = HARDWARE_CLOCK_HZ * LANE_COUNT / lane_period_cycles
    dut._log.info(
        "hashrate source=rtl_cycles "
        f"lane_count={LANE_COUNT} "
        f"lane_period_cycles={lane_period_cycles} "
        f"cycles_per_nonce={cycles_per_nonce:.3f} "
        f"clock_hz={HARDWARE_CLOCK_HZ} "
        f"rate={format_rate(hashes_per_second)} "
        f"rate_hps={hashes_per_second:.2f}"
    )

    await _uart_write(dut, b"TNS")


@cocotb.test()
async def top_runs_hardcoded_job(dut):
    await _start_clock(dut)

    await _uart_write(dut, b"TNH")

    response = await _uart_read(dut, 5)
    assert response[:1] == b"F"
    assert response[1:5] == b"\x00\x00\x00\x00"
    assert bitcoin_hash(build_job_from_header(GENESIS_HEADER, ALL_ONES_TARGET), 0) == GENESIS_EXPECTED_HASH_NONCE_ZERO
