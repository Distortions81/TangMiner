TARGET ?= tangnano20k

ifeq ($(TARGET),tangnano9k)
BOARD := tangnano9k
FAMILY := GW1N-9C
DEVICE := GW1NR-LV9QN88PC6/I5
CST := constr/tangnano9k.cst
SPINAL_USE_PLL ?= 0
SPINAL_CLOCK_PROFILE ?= 27m
else ifeq ($(TARGET),tangnano20k)
BOARD := tangnano20k
FAMILY := GW2A-18C
DEVICE := GW2AR-LV18QN88C8/I7
CST := constr/tangnano20k.cst
SPINAL_USE_PLL ?= 1
SPINAL_CLOCK_PROFILE ?= 100m286
else
$(error Unsupported TARGET '$(TARGET)'. Use tangnano20k or tangnano9k)
endif

ifeq ($(SPINAL_CLOCK_PROFILE),27m)
SPINAL_CLOCK_MHZ ?= 27
SPINAL_CLKS_PER_BIT ?= 234
else ifeq ($(SPINAL_CLOCK_PROFILE),90m)
SPINAL_CLOCK_MHZ ?= 90.000
SPINAL_CLKS_PER_BIT ?= 781
else ifeq ($(SPINAL_CLOCK_PROFILE),81m)
SPINAL_CLOCK_MHZ ?= 81.000
SPINAL_CLKS_PER_BIT ?= 703
else ifeq ($(SPINAL_CLOCK_PROFILE),84m)
SPINAL_CLOCK_MHZ ?= 84.000
SPINAL_CLKS_PER_BIT ?= 729
else ifeq ($(SPINAL_CLOCK_PROFILE),85m5)
SPINAL_CLOCK_MHZ ?= 85.500
SPINAL_CLKS_PER_BIT ?= 742
else ifeq ($(SPINAL_CLOCK_PROFILE),67m5)
SPINAL_CLOCK_MHZ ?= 67.500
SPINAL_CLKS_PER_BIT ?= 586
else ifeq ($(SPINAL_CLOCK_PROFILE),54m)
SPINAL_CLOCK_MHZ ?= 54.000
SPINAL_CLKS_PER_BIT ?= 469
else ifeq ($(SPINAL_CLOCK_PROFILE),100m286)
SPINAL_CLOCK_MHZ ?= 100.286
SPINAL_CLKS_PER_BIT ?= 871
else ifeq ($(SPINAL_CLOCK_PROFILE),111m)
SPINAL_CLOCK_MHZ ?= 111.000
SPINAL_CLKS_PER_BIT ?= 964
else ifeq ($(SPINAL_CLOCK_PROFILE),120m)
SPINAL_CLOCK_MHZ ?= 120.000
SPINAL_CLKS_PER_BIT ?= 1042
else ifeq ($(SPINAL_CLOCK_PROFILE),123m)
SPINAL_CLOCK_MHZ ?= 123.000
SPINAL_CLKS_PER_BIT ?= 1068
else ifeq ($(SPINAL_CLOCK_PROFILE),124m875)
SPINAL_CLOCK_MHZ ?= 124.875
SPINAL_CLKS_PER_BIT ?= 1084
else ifeq ($(SPINAL_CLOCK_PROFILE),126m)
SPINAL_CLOCK_MHZ ?= 126.000
SPINAL_CLKS_PER_BIT ?= 1094
else ifeq ($(SPINAL_CLOCK_PROFILE),130m5)
SPINAL_CLOCK_MHZ ?= 130.500
SPINAL_CLKS_PER_BIT ?= 1133
else ifeq ($(SPINAL_CLOCK_PROFILE),135m)
SPINAL_CLOCK_MHZ ?= 135.000
SPINAL_CLKS_PER_BIT ?= 1172
else ifeq ($(SPINAL_CLOCK_PROFILE),150m)
SPINAL_CLOCK_MHZ ?= 150.000
SPINAL_CLKS_PER_BIT ?= 1302
else
$(error Unsupported SPINAL_CLOCK_PROFILE '$(SPINAL_CLOCK_PROFILE)'. Use 27m, 54m, 67m5, 81m, 84m, 85m5, 90m, 100m286, 111m, 120m, 123m, 124m875, 126m, 130m5, 135m, or 150m)
endif

ifeq ($(TARGET),tangnano9k)
ifneq ($(SPINAL_CLOCK_PROFILE),27m)
$(error TARGET=tangnano9k only supports SPINAL_CLOCK_PROFILE=27m)
endif
endif

ifeq ($(TARGET),tangnano20k)
SPINAL_LANES ?= 5
SPINAL_ENABLE_ECHO ?= 0
SPINAL_ENABLE_HARDCODED ?= 0
SPINAL_FIXED_CANDIDATE ?= 2
NEXTPNR_SEED ?= 13
else
SPINAL_LANES ?= 4
SPINAL_ENABLE_ECHO ?= 1
SPINAL_ENABLE_HARDCODED ?= 1
SPINAL_FIXED_CANDIDATE ?=
NEXTPNR_SEED ?=
endif
SPINAL_SHARED_K ?= 1
SPINAL_WIDE_LANES ?= 0
SPINAL_LANE_START_STAGGER ?= 0
SPINAL_REGISTER_PASS_OUTPUTS ?= 0
SPINAL_TWO_CYCLE_ROUND ?= 0
SPINAL_THREE_CYCLE_ROUND ?= 0
SPINAL_REGISTER_ROUND_CONSTANT ?= 0
SPINAL_MINIMIZE_SHA_RESET ?= 0
SPINAL_SPLIT_SHA_CLOCK ?= 0
ifeq ($(SPINAL_SPLIT_SHA_CLOCK),1)
YOSYS_PRE_SYNTH_CMDS ?= setattr -unset keep_hierarchy */*; flatten;
else
YOSYS_PRE_SYNTH_CMDS ?=
endif
YOSYS_SYNTH_ARGS ?=
NEXTPNR_SEED_ARG := $(if $(NEXTPNR_SEED),--seed $(NEXTPNR_SEED),)
TOP := top
BUILD := build
SPINAL_DIR := $(BUILD)/spinal/$(TARGET)
SPINAL_SRC := $(SPINAL_DIR)/top.v
SPINAL_SIM_SRC := $(BUILD)/spinal-sim/top.v
SPINAL_CONFIG := $(SPINAL_DIR)/.config
SPINAL_SIM_CONFIG := $(dir $(SPINAL_SIM_SRC)).config
SPINAL_PREFIX := $(BUILD)/tangminer_spinal_$(TARGET)
VERILATOR_PTY_DIR := $(BUILD)/verilator-pty
VERILATOR_PTY_BIN := $(VERILATOR_PTY_DIR)/Vtop
OSS_CAD_SUITE ?= $(if $(wildcard local/oss-cad-suite/bin),$(abspath local/oss-cad-suite),$(HOME)/oss-cad-suite)
TOOLBIN := $(OSS_CAD_SUITE)/bin
YOSYS := $(TOOLBIN)/yosys
NEXTPNR := $(TOOLBIN)/nextpnr-himbaechel
GOWIN_PACK := $(TOOLBIN)/gowin_pack
OPENFPGALOADER := $(TOOLBIN)/openFPGALoader
SBT ?= $(if $(wildcard local/sbt/bin/sbt),$(abspath local/sbt/bin/sbt),sbt)
BOOTSTRAP_PYTHON ?= python3
PYTHON ?= $(if $(wildcard .venv/bin/python3),.venv/bin/python3,$(if $(wildcard .venv/bin/python),.venv/bin/python,python3))
SIM ?= verilator
EMU_TARGET ?= $(TARGET)
EMU_ARGS ?=
SWEEP_ARGS ?=
SPINAL_CLOCK_HZ ?= $(shell $(PYTHON) -c 'print(int(round(float("$(SPINAL_CLOCK_MHZ)") * 1000000)))')
SPINAL_CYCLES_PER_NONCE ?= $(shell $(PYTHON) -c 'print(64.0 / float("$(SPINAL_LANES)"))')
SPINAL_SIM_ENABLE_ECHO ?= 1
SPINAL_SIM_ENABLE_HARDCODED ?= 1
SPINAL_SIM_FIXED_CANDIDATE ?=

.PHONY: all build build-spinal spinal-verilog spinal-sim-verilog sweep-spinal load flash flash-and-mine load-and-mine clean sim setup-emulation install-ubuntu launch emu-smoke emu-pty software-mine hardware-mine mine mine-software mine-rtl mine-hardware stratum-client stratum-test stratum-mine-software stratum-mine-rtl stratum-mine-hardware stratum-smoke-rtl check-cocotb sim-cocotb verilator-pty FORCE

all: build

build: $(SPINAL_PREFIX).fs

build-spinal: build

spinal-verilog: $(SPINAL_SRC)

spinal-sim-verilog: $(SPINAL_SIM_SRC)

FORCE:

$(BUILD)/.dir:
	mkdir -p $(BUILD)
	touch $@

$(SPINAL_CONFIG): FORCE | $(BUILD)/.dir
	mkdir -p $(SPINAL_DIR)
	@tmp="$@.tmp"; \
	{ \
	  echo "target=$(TARGET)"; \
	  echo "lanes=$(SPINAL_LANES)"; \
	  echo "clock_profile=$(SPINAL_CLOCK_PROFILE)"; \
	  echo "clock_mhz=$(SPINAL_CLOCK_MHZ)"; \
	  echo "use_pll=$(SPINAL_USE_PLL)"; \
	  echo "clks_per_bit=$(SPINAL_CLKS_PER_BIT)"; \
	  echo "shared_k=$(SPINAL_SHARED_K)"; \
	  echo "enable_echo=$(SPINAL_ENABLE_ECHO)"; \
	  echo "enable_hardcoded=$(SPINAL_ENABLE_HARDCODED)"; \
	  echo "fixed_candidate=$(SPINAL_FIXED_CANDIDATE)"; \
	  echo "wide_lanes=$(SPINAL_WIDE_LANES)"; \
	  echo "lane_start_stagger=$(SPINAL_LANE_START_STAGGER)"; \
	  echo "register_pass_outputs=$(SPINAL_REGISTER_PASS_OUTPUTS)"; \
	  echo "two_cycle_round=$(SPINAL_TWO_CYCLE_ROUND)"; \
	  echo "three_cycle_round=$(SPINAL_THREE_CYCLE_ROUND)"; \
	  echo "register_round_constant=$(SPINAL_REGISTER_ROUND_CONSTANT)"; \
	  echo "minimize_sha_reset=$(SPINAL_MINIMIZE_SHA_RESET)"; \
	  echo "split_sha_clock=$(SPINAL_SPLIT_SHA_CLOCK)"; \
	  echo "yosys_pre_synth_cmds=$(YOSYS_PRE_SYNTH_CMDS)"; \
	  echo "yosys_synth_args=$(YOSYS_SYNTH_ARGS)"; \
	  echo "nextpnr_seed=$(NEXTPNR_SEED)"; \
	} > "$$tmp"; \
	if ! cmp -s "$$tmp" "$@"; then mv "$$tmp" "$@"; else rm "$$tmp"; fi

$(SPINAL_SIM_CONFIG): FORCE | $(BUILD)/.dir
	mkdir -p $(dir $(SPINAL_SIM_SRC))
	@tmp="$@.tmp"; \
	{ \
	  echo "lanes=$(SPINAL_LANES)"; \
	  echo "clks_per_bit=8"; \
	  echo "shared_k=$(SPINAL_SHARED_K)"; \
	  echo "enable_echo=$(SPINAL_SIM_ENABLE_ECHO)"; \
	  echo "enable_hardcoded=$(SPINAL_SIM_ENABLE_HARDCODED)"; \
	  echo "fixed_candidate=$(SPINAL_SIM_FIXED_CANDIDATE)"; \
	  echo "wide_lanes=$(SPINAL_WIDE_LANES)"; \
	  echo "lane_start_stagger=$(SPINAL_LANE_START_STAGGER)"; \
	  echo "register_pass_outputs=$(SPINAL_REGISTER_PASS_OUTPUTS)"; \
	  echo "two_cycle_round=$(SPINAL_TWO_CYCLE_ROUND)"; \
	  echo "three_cycle_round=$(SPINAL_THREE_CYCLE_ROUND)"; \
	  echo "register_round_constant=$(SPINAL_REGISTER_ROUND_CONSTANT)"; \
	  echo "minimize_sha_reset=$(SPINAL_MINIMIZE_SHA_RESET)"; \
	  echo "split_sha_clock=$(SPINAL_SPLIT_SHA_CLOCK)"; \
	} > "$$tmp"; \
	if ! cmp -s "$$tmp" "$@"; then mv "$$tmp" "$@"; else rm "$$tmp"; fi

$(SPINAL_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties $(SPINAL_CONFIG) | $(BUILD)/.dir
	mkdir -p $(SPINAL_DIR)
	@command -v java >/dev/null 2>&1 || { echo "java is not on PATH. Install OpenJDK or run: scripts/setup.sh"; exit 127; }
	@command -v "$(SBT)" >/dev/null 2>&1 || { echo "sbt is not on PATH. Install sbt or run: scripts/setup.sh"; exit 127; }
	TANGMINER_VERILOG_DIR=$(SPINAL_DIR) TANGMINER_USE_PLL=$(SPINAL_USE_PLL) TANGMINER_CLOCK_PROFILE=$(SPINAL_CLOCK_PROFILE) TANGMINER_CLKS_PER_BIT=$(SPINAL_CLKS_PER_BIT) TANGMINER_LANES=$(SPINAL_LANES) TANGMINER_SHARED_K=$(SPINAL_SHARED_K) TANGMINER_ENABLE_ECHO=$(SPINAL_ENABLE_ECHO) TANGMINER_ENABLE_HARDCODED=$(SPINAL_ENABLE_HARDCODED) TANGMINER_FIXED_CANDIDATE=$(SPINAL_FIXED_CANDIDATE) TANGMINER_WIDE_LANES=$(SPINAL_WIDE_LANES) TANGMINER_LANE_START_STAGGER=$(SPINAL_LANE_START_STAGGER) TANGMINER_REGISTER_PASS_OUTPUTS=$(SPINAL_REGISTER_PASS_OUTPUTS) TANGMINER_TWO_CYCLE_ROUND=$(SPINAL_TWO_CYCLE_ROUND) TANGMINER_THREE_CYCLE_ROUND=$(SPINAL_THREE_CYCLE_ROUND) TANGMINER_REGISTER_ROUND_CONSTANT=$(SPINAL_REGISTER_ROUND_CONSTANT) TANGMINER_MINIMIZE_SHA_RESET=$(SPINAL_MINIMIZE_SHA_RESET) TANGMINER_SPLIT_SHA_CLOCK=$(SPINAL_SPLIT_SHA_CLOCK) $(SBT) "runMain tangminer.GenerateVerilog"

$(SPINAL_SIM_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties $(SPINAL_SIM_CONFIG) | $(BUILD)/.dir
	@command -v java >/dev/null 2>&1 || { echo "java is not on PATH. Install OpenJDK or run: scripts/setup.sh"; exit 127; }
	@command -v "$(SBT)" >/dev/null 2>&1 || { echo "sbt is not on PATH. Install sbt or run: scripts/setup.sh"; exit 127; }
	TANGMINER_LANES=$(SPINAL_LANES) TANGMINER_CLKS_PER_BIT=8 TANGMINER_SHARED_K=$(SPINAL_SHARED_K) TANGMINER_ENABLE_ECHO=$(SPINAL_SIM_ENABLE_ECHO) TANGMINER_ENABLE_HARDCODED=$(SPINAL_SIM_ENABLE_HARDCODED) TANGMINER_FIXED_CANDIDATE=$(SPINAL_SIM_FIXED_CANDIDATE) TANGMINER_WIDE_LANES=$(SPINAL_WIDE_LANES) TANGMINER_LANE_START_STAGGER=$(SPINAL_LANE_START_STAGGER) TANGMINER_REGISTER_PASS_OUTPUTS=$(SPINAL_REGISTER_PASS_OUTPUTS) TANGMINER_TWO_CYCLE_ROUND=$(SPINAL_TWO_CYCLE_ROUND) TANGMINER_THREE_CYCLE_ROUND=$(SPINAL_THREE_CYCLE_ROUND) TANGMINER_REGISTER_ROUND_CONSTANT=$(SPINAL_REGISTER_ROUND_CONSTANT) TANGMINER_MINIMIZE_SHA_RESET=$(SPINAL_MINIMIZE_SHA_RESET) TANGMINER_SPLIT_SHA_CLOCK=$(SPINAL_SPLIT_SHA_CLOCK) $(SBT) "runMain tangminer.GenerateSimVerilog"

$(SPINAL_PREFIX).json: $(SPINAL_SRC) | $(BUILD)/.dir
	$(YOSYS) -p "read_verilog $(SPINAL_SRC); $(YOSYS_PRE_SYNTH_CMDS) synth_gowin $(YOSYS_SYNTH_ARGS) -top $(TOP) -json $@"

$(SPINAL_PREFIX)_pnr.json: $(SPINAL_PREFIX).json $(CST)
	$(NEXTPNR) --json $< --write $@ --freq $(SPINAL_CLOCK_MHZ) --device $(DEVICE) -o family=$(FAMILY) -o cst=$(CST) $(NEXTPNR_SEED_ARG)

$(SPINAL_PREFIX).fs: $(SPINAL_PREFIX)_pnr.json
	$(GOWIN_PACK) -d $(FAMILY) -o $@ $<

load: $(SPINAL_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) $<

flash: $(SPINAL_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) -f $<

flash-and-mine:
	scripts/flash-and-mine.sh "$(SERIAL_PORT)"

load-and-mine:
	scripts/flash-and-mine.sh --load "$(SERIAL_PORT)"

sim: sim-cocotb

setup-emulation:
	$(BOOTSTRAP_PYTHON) -m venv .venv
	. .venv/bin/activate && pip install -r requirements-emulation.txt

install-ubuntu:
	scripts/setup.sh

launch:
	scripts/sim.sh

emu-smoke:
	$(PYTHON) scripts/tools/emulator_smoke.py

emu-pty:
	$(PYTHON) scripts/tools/tangminer_emulator.py --board $(EMU_TARGET) --pty $(EMU_ARGS)

software-mine: mine-software

hardware-mine: mine-hardware

mine: mine-software

mine-software:
	scripts/mine-software.sh

mine-rtl:
	scripts/mine-rtl.sh

mine-hardware:
	scripts/mine-hardware.sh "$(SERIAL_PORT)"

sweep-spinal:
	$(PYTHON) scripts/tools/sweep_spinal_variants.py $(SWEEP_ARGS)

stratum-client:
	$(MAKE) -C stratum

stratum-test:
	$(MAKE) -C stratum test

stratum-smoke-rtl: stratum-client verilator-pty
	$(PYTHON) stratum/tools/smoke_fake_stack.py --backend rtl --timeout 15

stratum-mine-software: stratum-client
	scripts/helpers/stratum_mine.sh software

stratum-mine-rtl: stratum-client verilator-pty
	scripts/helpers/stratum_mine.sh rtl

stratum-mine-hardware: stratum-client
	scripts/helpers/stratum_mine.sh hardware "$(SERIAL_PORT)"

verilator-pty: $(VERILATOR_PTY_BIN)

$(VERILATOR_PTY_BIN): $(SPINAL_SIM_SRC) sim/verilator_uart_pty.cpp
	mkdir -p $(VERILATOR_PTY_DIR)
	PATH="$(TOOLBIN):$$PATH" verilator --cc --exe --build \
	  --Mdir $(VERILATOR_PTY_DIR) \
	  -top-module top \
	  -CFLAGS "-DCLKS_PER_BIT=8" \
	  $(SPINAL_SIM_SRC) sim/verilator_uart_pty.cpp

check-cocotb:
	@$(PYTHON) -c "import cocotb" >/dev/null 2>&1 || { echo "cocotb is not installed. Run: scripts/setup.sh"; exit 1; }
	@if [ "$(SIM)" = "verilator" ]; then PATH="$(TOOLBIN):$$PATH"; if ! command -v verilator >/dev/null 2>&1; then echo "verilator is not on PATH. Install OSS CAD Suite or your distro's verilator package."; exit 127; fi; fi
	@if [ "$(SIM)" = "icarus" ]; then PATH="$(TOOLBIN):$$PATH"; if ! command -v iverilog >/dev/null 2>&1 || ! command -v vvp >/dev/null 2>&1; then echo "iverilog/vvp are not on PATH. Install OSS CAD Suite or Icarus Verilog."; exit 127; fi; fi

sim-cocotb: $(SPINAL_SIM_SRC) check-cocotb
	PATH="$(TOOLBIN):$$PATH" $(MAKE) -C sim/cocotb SIM=$(SIM) PYTHON_BIN="$(abspath $(PYTHON))" RTL_SOURCES="$(abspath $(SPINAL_SIM_SRC))" EXTRA_COMPILE_ARGS= CLKS_PER_BIT=8 LANE_COUNT=$(SPINAL_LANES) HARDWARE_CLOCK_HZ=$(SPINAL_CLOCK_HZ)

clean:
	rm -rf $(BUILD)
	$(MAKE) -C stratum clean
