TARGET ?= tangnano20k

ifeq ($(TARGET),tangnano9k)
BOARD := tangnano9k
FAMILY := GW1N-9C
DEVICE := GW1NR-LV9QN88PC6/I5
CST := constr/tangnano9k.cst
SPINAL_USE_PLL ?= 0
SPINAL_CLOCK_MHZ ?= 27
SPINAL_CLKS_PER_BIT ?= 234
else ifeq ($(TARGET),tangnano20k)
BOARD := tangnano20k
FAMILY := GW2A-18C
DEVICE := GW2AR-LV18QN88C8/I7
CST := constr/tangnano20k.cst
SPINAL_USE_PLL ?= 1
SPINAL_CLOCK_MHZ ?= 81
SPINAL_CLKS_PER_BIT ?= 703
else
$(error Unsupported TARGET '$(TARGET)'. Use tangnano20k or tangnano9k)
endif

TOP := top
BUILD := build
SRC := src/top.v src/uart_rx.v src/uart_tx.v src/bitcoin_hash_core.v src/sha256_compress.v
SPINAL_DIR := $(BUILD)/spinal/$(TARGET)
SPINAL_SRC := $(SPINAL_DIR)/top.v
SPINAL_SIM_SRC := $(BUILD)/spinal-sim/top.v
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

.PHONY: all build build-verilog spinal-verilog spinal-sim-verilog build-spinal load load-verilog load-spinal flash flash-verilog flash-spinal clean sim sim-sha sim-bitcoin setup-emulation install-ubuntu launch emu-smoke emu-pty software-mine hardware-mine check-cocotb sim-cocotb sim-cocotb-spinal

all: build

build: build-spinal

build-verilog: $(VERILOG_PREFIX).fs

build-spinal: $(SPINAL_PREFIX).fs

spinal-verilog: $(SPINAL_SRC)

spinal-sim-verilog: $(SPINAL_SIM_SRC)

$(BUILD)/.dir:
	mkdir -p $(BUILD)
	touch $@

$(SPINAL_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties | $(BUILD)/.dir
	mkdir -p $(SPINAL_DIR)
	TANGMINER_VERILOG_DIR=$(SPINAL_DIR) TANGMINER_USE_PLL=$(SPINAL_USE_PLL) TANGMINER_CLKS_PER_BIT=$(SPINAL_CLKS_PER_BIT) $(SBT) "runMain tangminer.GenerateVerilog"

$(SPINAL_SIM_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties | $(BUILD)/.dir
	$(SBT) "runMain tangminer.GenerateSimVerilog"

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

check-cocotb:
	@$(PYTHON) -c "import cocotb" >/dev/null 2>&1 || { echo "cocotb is not installed. Run: make setup-emulation && . .venv/bin/activate"; exit 1; }
	@if [ "$(SIM)" = "verilator" ]; then PATH="$(TOOLBIN):$$PATH"; if ! command -v verilator >/dev/null 2>&1; then echo "verilator is not on PATH. Install OSS CAD Suite or your distro's verilator package."; exit 127; fi; fi
	@if [ "$(SIM)" = "icarus" ]; then PATH="$(TOOLBIN):$$PATH"; if ! command -v iverilog >/dev/null 2>&1 || ! command -v vvp >/dev/null 2>&1; then echo "iverilog/vvp are not on PATH. Install OSS CAD Suite or Icarus Verilog."; exit 127; fi; fi

sim-cocotb: check-cocotb
	PATH="$(TOOLBIN):$$PATH" $(MAKE) -C sim/cocotb SIM=$(SIM) PYTHON_BIN="$(abspath $(PYTHON))"

sim-cocotb-spinal: $(SPINAL_SIM_SRC) check-cocotb
	PATH="$(TOOLBIN):$$PATH" $(MAKE) -C sim/cocotb SIM=$(SIM) PYTHON_BIN="$(abspath $(PYTHON))" RTL_SOURCES="$(abspath $(SPINAL_SIM_SRC))" EXTRA_COMPILE_ARGS= CLKS_PER_BIT=8

clean:
	rm -rf $(BUILD)
