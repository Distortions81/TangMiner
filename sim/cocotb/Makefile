PROJECT_ROOT := $(abspath ../..)
COCOTB_CONFIG ?= $(if $(wildcard $(PROJECT_ROOT)/.venv/bin/cocotb-config),$(PROJECT_ROOT)/.venv/bin/cocotb-config,cocotb-config)
PYTHON_BIN ?= $(if $(wildcard $(PROJECT_ROOT)/.venv/bin/python3),$(PROJECT_ROOT)/.venv/bin/python3,$(if $(wildcard $(PROJECT_ROOT)/.venv/bin/python),$(PROJECT_ROOT)/.venv/bin/python,python3))

SIM ?= verilator
TOPLEVEL_LANG := verilog
TOPLEVEL := top
MODULE := test_top_uart

CLKS_PER_BIT ?= 8
LANE_COUNT ?= 6
HARDWARE_CLOCK_HZ ?= 67500000
EXPECTED_LANE_PERIOD_CYCLES ?= 65
HOST_ROUND_SKIP_PAYLOAD ?= 0
FIXED_CANDIDATE_MODE ?=
STRICT_NONCE_CHECKS ?= 0
CONTINUOUS_RESULTS ?= 0
RTL_SOURCES ?= $(PROJECT_ROOT)/build/spinal-sim/top.v
EXTRA_COMPILE_ARGS ?=

VERILOG_SOURCES := $(RTL_SOURCES)
COMPILE_ARGS += $(EXTRA_COMPILE_ARGS)

export CLKS_PER_BIT
export LANE_COUNT
export HARDWARE_CLOCK_HZ
export EXPECTED_LANE_PERIOD_CYCLES
export HOST_ROUND_SKIP_PAYLOAD
export FIXED_CANDIDATE_MODE
export STRICT_NONCE_CHECKS
export CONTINUOUS_RESULTS
export PYTHONPATH := $(PROJECT_ROOT)/scripts/tools:$(PYTHONPATH)

include $(shell $(COCOTB_CONFIG) --makefiles)/Makefile.sim
