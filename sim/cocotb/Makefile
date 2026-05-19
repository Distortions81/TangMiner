PROJECT_ROOT := $(abspath ../..)
COCOTB_CONFIG ?= $(if $(wildcard $(PROJECT_ROOT)/.venv/bin/cocotb-config),$(PROJECT_ROOT)/.venv/bin/cocotb-config,cocotb-config)
PYTHON_BIN ?= $(if $(wildcard $(PROJECT_ROOT)/.venv/bin/python3),$(PROJECT_ROOT)/.venv/bin/python3,$(if $(wildcard $(PROJECT_ROOT)/.venv/bin/python),$(PROJECT_ROOT)/.venv/bin/python,python3))

SIM ?= verilator
TOPLEVEL_LANG := verilog
TOPLEVEL := top
MODULE := test_top_uart

CLKS_PER_BIT ?= 8
RTL_SOURCES ?= \
	$(PROJECT_ROOT)/src/top.v \
	$(PROJECT_ROOT)/src/uart_rx.v \
	$(PROJECT_ROOT)/src/uart_tx.v \
	$(PROJECT_ROOT)/src/bitcoin_hash_core.v \
	$(PROJECT_ROOT)/src/sha256_compress.v
EXTRA_COMPILE_ARGS ?= -DSIM_FAST_RESET -DSIM_FAST_UART

VERILOG_SOURCES := $(RTL_SOURCES)
COMPILE_ARGS += $(EXTRA_COMPILE_ARGS)

export CLKS_PER_BIT
export PYTHONPATH := $(PROJECT_ROOT)/scripts/tools:$(PYTHONPATH)

include $(shell $(COCOTB_CONFIG) --makefiles)/Makefile.sim
