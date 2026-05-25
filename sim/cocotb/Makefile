PROJECT_ROOT := $(abspath ../..)
COCOTB_CONFIG ?= $(if $(wildcard $(PROJECT_ROOT)/.venv/bin/cocotb-config),$(PROJECT_ROOT)/.venv/bin/cocotb-config,cocotb-config)
PYTHON_BIN ?= $(if $(wildcard $(PROJECT_ROOT)/.venv/bin/python3),$(PROJECT_ROOT)/.venv/bin/python3,$(if $(wildcard $(PROJECT_ROOT)/.venv/bin/python),$(PROJECT_ROOT)/.venv/bin/python,python3))

SIM ?= verilator
TOPLEVEL_LANG := verilog
TOPLEVEL := top
MODULE := test_top_uart

CLKS_PER_BIT ?= 8
RTL_SOURCES ?= $(PROJECT_ROOT)/build/spinal-sim/top.v
EXTRA_COMPILE_ARGS ?=

VERILOG_SOURCES := $(RTL_SOURCES)
COMPILE_ARGS += $(EXTRA_COMPILE_ARGS)

export CLKS_PER_BIT
export PYTHONPATH := $(PROJECT_ROOT)/scripts/tools:$(PYTHONPATH)

include $(shell $(COCOTB_CONFIG) --makefiles)/Makefile.sim
