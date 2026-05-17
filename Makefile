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
SPINAL_CLOCK_PROFILE ?= 111m
else
$(error Unsupported TARGET '$(TARGET)'. Use tangnano20k or tangnano9k)
endif

ifeq ($(SPINAL_CLOCK_PROFILE),27m)
SPINAL_CLOCK_MHZ ?= 27
SPINAL_CLKS_PER_BIT ?= 234
else ifeq ($(SPINAL_CLOCK_PROFILE),90m)
SPINAL_CLOCK_MHZ ?= 90.000
SPINAL_CLKS_PER_BIT ?= 781
else ifeq ($(SPINAL_CLOCK_PROFILE),100m286)
SPINAL_CLOCK_MHZ ?= 100.286
SPINAL_CLKS_PER_BIT ?= 871
else ifeq ($(SPINAL_CLOCK_PROFILE),111m)
SPINAL_CLOCK_MHZ ?= 111.000
SPINAL_CLKS_PER_BIT ?= 964
else ifeq ($(SPINAL_CLOCK_PROFILE),120m)
SPINAL_CLOCK_MHZ ?= 120.000
SPINAL_CLKS_PER_BIT ?= 1042
else
$(error Unsupported SPINAL_CLOCK_PROFILE '$(SPINAL_CLOCK_PROFILE)'. Use 27m, 90m, 100m286, 111m, or 120m)
endif

ifeq ($(TARGET),tangnano9k)
ifneq ($(SPINAL_CLOCK_PROFILE),27m)
$(error TARGET=tangnano9k only supports SPINAL_CLOCK_PROFILE=27m)
endif
endif

SPINAL_LANES ?= 4
TOP := top
BUILD := build
SRC := src/top.v src/uart_rx.v src/uart_tx.v src/bitcoin_hash_core.v src/sha256_compress.v
SPINAL_DIR := $(BUILD)/spinal/$(TARGET)
SPINAL_SRC := $(SPINAL_DIR)/top.v
SPINAL_SIM_SRC := $(BUILD)/spinal-sim/top.v
SPINAL_CONFIG := $(SPINAL_DIR)/.config
SPINAL_SIM_CONFIG := $(dir $(SPINAL_SIM_SRC)).config
SPINAL_PREFIX := $(BUILD)/tangminer_spinal_$(TARGET)
VERILOG_PREFIX := $(BUILD)/tangminer_verilog_$(TARGET)
OSS_CAD_SUITE ?= $(HOME)/oss-cad-suite
TOOLBIN := $(OSS_CAD_SUITE)/bin
YOSYS := $(TOOLBIN)/yosys
NEXTPNR := $(TOOLBIN)/nextpnr-himbaechel
GOWIN_PACK := $(TOOLBIN)/gowin_pack
OPENFPGALOADER := $(TOOLBIN)/openFPGALoader
IVERILOG := $(TOOLBIN)/iverilog
VVP := $(TOOLBIN)/vvp
SBT ?= sbt
BOOTSTRAP_PYTHON ?= python3
PYTHON ?= $(if $(wildcard .venv/bin/python3),.venv/bin/python3,$(if $(wildcard .venv/bin/python),.venv/bin/python,python3))
SIM ?= verilator
EMU_TARGET ?= $(TARGET)
EMU_ARGS ?=
MINE_ARGS ?=
SWEEP_ARGS ?=
SPINAL_CLOCK_HZ ?= $(shell $(PYTHON) -c 'print(int(round(float("$(SPINAL_CLOCK_MHZ)") * 1000000)))')
SPINAL_CYCLES_PER_NONCE ?= $(shell $(PYTHON) -c 'print(64.0 / float("$(SPINAL_LANES)"))')

.PHONY: all build build-verilog spinal-verilog spinal-sim-verilog build-spinal sweep-spinal load load-verilog load-spinal flash flash-verilog flash-spinal clean sim sim-sha sim-bitcoin setup-emulation install-ubuntu launch emu-smoke emu-pty software-mine hardware-mine stratum-client stratum-test check-cocotb sim-cocotb sim-cocotb-spinal FORCE

all: build

build: build-spinal

build-verilog: $(VERILOG_PREFIX).fs

build-spinal: $(SPINAL_PREFIX).fs

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
	} > "$$tmp"; \
	if ! cmp -s "$$tmp" "$@"; then mv "$$tmp" "$@"; else rm "$$tmp"; fi

$(SPINAL_SIM_CONFIG): FORCE | $(BUILD)/.dir
	mkdir -p $(dir $(SPINAL_SIM_SRC))
	@tmp="$@.tmp"; \
	{ \
	  echo "lanes=$(SPINAL_LANES)"; \
	  echo "clks_per_bit=8"; \
	} > "$$tmp"; \
	if ! cmp -s "$$tmp" "$@"; then mv "$$tmp" "$@"; else rm "$$tmp"; fi

$(SPINAL_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties $(SPINAL_CONFIG) | $(BUILD)/.dir
	mkdir -p $(SPINAL_DIR)
	TANGMINER_VERILOG_DIR=$(SPINAL_DIR) TANGMINER_USE_PLL=$(SPINAL_USE_PLL) TANGMINER_CLOCK_PROFILE=$(SPINAL_CLOCK_PROFILE) TANGMINER_CLKS_PER_BIT=$(SPINAL_CLKS_PER_BIT) TANGMINER_LANES=$(SPINAL_LANES) $(SBT) "runMain tangminer.GenerateVerilog"

$(SPINAL_SIM_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties $(SPINAL_SIM_CONFIG) | $(BUILD)/.dir
	TANGMINER_LANES=$(SPINAL_LANES) TANGMINER_CLKS_PER_BIT=8 $(SBT) "runMain tangminer.GenerateSimVerilog"

$(VERILOG_PREFIX).json: $(SRC) | $(BUILD)/.dir
	$(YOSYS) -p "read_verilog $(SRC); synth_gowin -top $(TOP) -json $@"

$(VERILOG_PREFIX)_pnr.json: $(VERILOG_PREFIX).json $(CST)
	$(NEXTPNR) --json $< --write $@ --freq 27 --device $(DEVICE) -o family=$(FAMILY) -o cst=$(CST)

$(VERILOG_PREFIX).fs: $(VERILOG_PREFIX)_pnr.json
	$(GOWIN_PACK) -d $(FAMILY) -o $@ $<

$(SPINAL_PREFIX).json: $(SPINAL_SRC) | $(BUILD)/.dir
	$(YOSYS) -p "read_verilog $(SPINAL_SRC); synth_gowin -top $(TOP) -json $@"

$(SPINAL_PREFIX)_pnr.json: $(SPINAL_PREFIX).json $(CST)
	$(NEXTPNR) --json $< --write $@ --freq $(SPINAL_CLOCK_MHZ) --device $(DEVICE) -o family=$(FAMILY) -o cst=$(CST)

$(SPINAL_PREFIX).fs: $(SPINAL_PREFIX)_pnr.json
	$(GOWIN_PACK) -d $(FAMILY) -o $@ $<

load: load-spinal

load-verilog: $(VERILOG_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) $<

load-spinal: $(SPINAL_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) $<

flash: flash-spinal

flash-verilog: $(VERILOG_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) -f $<

flash-spinal: $(SPINAL_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) -f $<

sim: sim-sha sim-bitcoin

sim-sha: | $(BUILD)/.dir
	$(IVERILOG) -g2012 -o $(BUILD)/tb_sha256_compress sim/tb_sha256_compress.v src/sha256_compress.v
	$(VVP) $(BUILD)/tb_sha256_compress

sim-bitcoin: | $(BUILD)/.dir
	$(IVERILOG) -g2012 -o $(BUILD)/tb_bitcoin_hash_core sim/tb_bitcoin_hash_core.v src/bitcoin_hash_core.v src/sha256_compress.v
	$(VVP) $(BUILD)/tb_bitcoin_hash_core

setup-emulation:
	$(BOOTSTRAP_PYTHON) -m venv .venv
	. .venv/bin/activate && pip install -r requirements-emulation.txt

install-ubuntu:
	scripts/install_ubuntu_24_04.sh --target $(TARGET)

launch:
	scripts/launch_ubuntu_24_04.sh --target $(TARGET)

emu-smoke:
	$(PYTHON) scripts/emulator_smoke.py

emu-pty:
	$(PYTHON) scripts/tangminer_emulator.py --board $(EMU_TARGET) --pty $(EMU_ARGS)

software-mine:
	$(PYTHON) scripts/software_mine_test.py $(MINE_ARGS)

hardware-mine:
	$(PYTHON) scripts/hardware_mine.py $(MINE_ARGS)

sweep-spinal:
	$(PYTHON) scripts/sweep_spinal_variants.py $(SWEEP_ARGS)

stratum-client:
	$(MAKE) -C stratum

stratum-test:
	$(MAKE) -C stratum test

check-cocotb:
	@$(PYTHON) -c "import cocotb" >/dev/null 2>&1 || { echo "cocotb is not installed. Run: make setup-emulation && . .venv/bin/activate"; exit 1; }
	@if [ "$(SIM)" = "verilator" ]; then PATH="$(TOOLBIN):$$PATH"; if ! command -v verilator >/dev/null 2>&1; then echo "verilator is not on PATH. Install OSS CAD Suite or your distro's verilator package."; exit 127; fi; fi
	@if [ "$(SIM)" = "icarus" ]; then PATH="$(TOOLBIN):$$PATH"; if ! command -v iverilog >/dev/null 2>&1 || ! command -v vvp >/dev/null 2>&1; then echo "iverilog/vvp are not on PATH. Install OSS CAD Suite or Icarus Verilog."; exit 127; fi; fi

sim-cocotb: check-cocotb
	PATH="$(TOOLBIN):$$PATH" $(MAKE) -C sim/cocotb SIM=$(SIM) PYTHON_BIN="$(abspath $(PYTHON))"

sim-cocotb-spinal: $(SPINAL_SIM_SRC) check-cocotb
	PATH="$(TOOLBIN):$$PATH" $(MAKE) -C sim/cocotb SIM=$(SIM) PYTHON_BIN="$(abspath $(PYTHON))" RTL_SOURCES="$(abspath $(SPINAL_SIM_SRC))" EXTRA_COMPILE_ARGS= CLKS_PER_BIT=8 LANE_COUNT=$(SPINAL_LANES) HARDWARE_CLOCK_HZ=$(SPINAL_CLOCK_HZ)

clean:
	rm -rf $(BUILD)
	$(MAKE) -C stratum clean
