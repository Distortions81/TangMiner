import os

import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge

from tangminer_emulator import (
    ALL_ONES_TARGET,
    GENESIS_EXPECTED_HASH_NONCE_ZERO,
    GENESIS_HEADER,
    build_job_from_header,
    encode_job_payload,
)


CLKS_PER_BIT = int(os.environ.get("CLKS_PER_BIT", "8"))


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

    response = await _uart_read(dut, 37)
    assert response[:1] == b"F"
    assert response[1:5] == b"\x00\x00\x00\x00"
    assert response[5:] == GENESIS_EXPECTED_HASH_NONCE_ZERO


@cocotb.test()
async def top_runs_hardcoded_job(dut):
    await _start_clock(dut)

    await _uart_write(dut, b"TNH")

    response = await _uart_read(dut, 37)
    assert response[:1] == b"F"
    assert response[1:5] == b"\x00\x00\x00\x00"
    assert response[5:] == GENESIS_EXPECTED_HASH_NONCE_ZERO
