TARGET ?= tangnano20k

ifeq ($(TARGET),tangnano9k)
BOARD := tangnano9k
FAMILY := GW1N-9C
DEVICE := GW1NR-LV9QN88PC6/I5
GOWIN_DEVICE_VERSION ?=
CST := constr/tangnano9k.cst
DEFAULT_FLOW ?= oss
SPINAL_USE_PLL ?= 0
SPINAL_CLOCK_PROFILE ?= 27m
else ifeq ($(TARGET),tangnano20k)
BOARD := tangnano20k
FAMILY := GW2A-18C
DEVICE := GW2AR-LV18QN88C8/I7
GOWIN_DEVICE_VERSION ?= C
CST := constr/tangnano20k.cst
DEFAULT_FLOW ?= gowin
SPINAL_USE_PLL ?= 1
ifeq ($(DEFAULT_FLOW),oss)
SPINAL_CLOCK_PROFILE ?= 54m
else
SPINAL_CLOCK_PROFILE ?= 67m5
endif
YOSYS_SYNTH_ARGS ?= -nowidelut
else ifeq ($(TARGET),tangmega138k)
BOARD := tangmega138k
FAMILY := GW5AST-138C
DEVICE := GW5AST-LV138PG484AC1/I0
GOWIN_DEVICE_VERSION ?= C
CST := constr/tangmega138k.cst
DEFAULT_FLOW ?= gowin
SPINAL_USE_PLL ?= 1
SPINAL_PLL_KIND ?= gw5
SPINAL_INPUT_CLOCK_MHZ ?= 50
SPINAL_CLOCK_PROFILE ?= 100m
GOWIN_CPU_PIN_REUSE ?= 1
GOWIN_SSPI_PIN_REUSE ?= 1
else
$(error Unsupported TARGET '$(TARGET)'. Use tangmega138k, tangnano20k, or tangnano9k)
endif

SPINAL_PLL_KIND ?= rpll
SPINAL_INPUT_CLOCK_MHZ ?= 27
GOWIN_CPU_PIN_REUSE ?= 0
GOWIN_SSPI_PIN_REUSE ?= 0

ifeq ($(TARGET),tangmega138k)
ifneq ($(DEFAULT_FLOW),gowin)
$(error TARGET=tangmega138k requires DEFAULT_FLOW=gowin)
endif
endif

ifeq ($(SPINAL_CLOCK_PROFILE),27m)
SPINAL_CLOCK_MHZ ?= 27
SPINAL_CLKS_PER_BIT ?= 234
else ifeq ($(SPINAL_CLOCK_PROFILE),50m)
SPINAL_CLOCK_MHZ ?= 50.000
SPINAL_CLKS_PER_BIT ?= 434
else ifeq ($(SPINAL_CLOCK_PROFILE),75m)
SPINAL_CLOCK_MHZ ?= 75.000
SPINAL_CLKS_PER_BIT ?= 651
else ifeq ($(SPINAL_CLOCK_PROFILE),80m)
SPINAL_CLOCK_MHZ ?= 80.000
SPINAL_CLKS_PER_BIT ?= 694
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
else ifeq ($(SPINAL_CLOCK_PROFILE),60m75)
SPINAL_CLOCK_MHZ ?= 60.750
SPINAL_CLKS_PER_BIT ?= 527
else ifeq ($(SPINAL_CLOCK_PROFILE),58m5)
SPINAL_CLOCK_MHZ ?= 58.500
SPINAL_CLKS_PER_BIT ?= 508
else ifeq ($(SPINAL_CLOCK_PROFILE),57m)
SPINAL_CLOCK_MHZ ?= 57.000
SPINAL_CLKS_PER_BIT ?= 495
else ifeq ($(SPINAL_CLOCK_PROFILE),54m)
SPINAL_CLOCK_MHZ ?= 54.000
SPINAL_CLKS_PER_BIT ?= 469
else ifeq ($(SPINAL_CLOCK_PROFILE),100m286)
SPINAL_CLOCK_MHZ ?= 100.286
SPINAL_CLKS_PER_BIT ?= 871
else ifeq ($(SPINAL_CLOCK_PROFILE),100m)
SPINAL_CLOCK_MHZ ?= 100.000
SPINAL_CLKS_PER_BIT ?= 868
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
else ifeq ($(SPINAL_CLOCK_PROFILE),125m)
SPINAL_CLOCK_MHZ ?= 125.000
SPINAL_CLKS_PER_BIT ?= 1085
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
$(error Unsupported SPINAL_CLOCK_PROFILE '$(SPINAL_CLOCK_PROFILE)'. Use 27m, 50m, 54m, 57m, 58m5, 60m75, 67m5, 75m, 80m, 81m, 84m, 85m5, 90m, 100m, 100m286, 111m, 120m, 123m, 124m875, 125m, 126m, 130m5, 135m, or 150m)
endif

ifeq ($(TARGET),tangnano9k)
ifneq ($(SPINAL_CLOCK_PROFILE),27m)
$(error TARGET=tangnano9k only supports SPINAL_CLOCK_PROFILE=27m)
endif
endif

ifeq ($(TARGET),tangnano20k)
ifeq ($(filter $(SPINAL_CLOCK_PROFILE),27m 54m 57m 58m5 60m75 67m5 81m 84m 85m5 90m 100m286 111m 120m 123m 124m875 126m 130m5 135m 150m),)
$(error TARGET=tangnano20k does not support GW5-only SPINAL_CLOCK_PROFILE=$(SPINAL_CLOCK_PROFILE))
endif
ifneq ($(SPINAL_PLL_KIND),rpll)
$(error TARGET=tangnano20k requires SPINAL_PLL_KIND=rpll)
endif
endif

ifeq ($(TARGET),tangmega138k)
ifeq ($(filter $(SPINAL_CLOCK_PROFILE),50m 75m 80m 90m 100m 125m 150m),)
$(error TARGET=tangmega138k supports SPINAL_CLOCK_PROFILE=50m, 75m, 80m, 90m, 100m, 125m, or 150m)
endif
ifneq ($(SPINAL_PLL_KIND),gw5)
$(error TARGET=tangmega138k requires SPINAL_PLL_KIND=gw5)
endif
ifeq ($(filter $(SPINAL_INPUT_CLOCK_MHZ),50 50.0 50.00 50.000),)
$(error TARGET=tangmega138k requires SPINAL_INPUT_CLOCK_MHZ=50)
endif
endif

ifeq ($(TARGET),tangnano20k)
ifeq ($(DEFAULT_FLOW),oss)
SPINAL_LANES ?= 5
SPINAL_REGISTER_PASS_OUTPUTS ?= 0
SPINAL_MINIMIZE_SHA_RESET ?= 0
else
SPINAL_LANES ?= 6
SPINAL_REGISTER_PASS_OUTPUTS ?= 1
SPINAL_MINIMIZE_SHA_RESET ?= 1
endif
SPINAL_ENABLE_ECHO ?= 0
SPINAL_ENABLE_HARDCODED ?= 0
SPINAL_FIXED_CANDIDATE ?= 2
NEXTPNR_SEED ?= 13
else ifeq ($(TARGET),tangmega138k)
SPINAL_LANES ?= 1
SPINAL_FULLY_UNROLLED ?= 1
SPINAL_ENABLE_ECHO ?= 0
SPINAL_ENABLE_HARDCODED ?= 0
SPINAL_FIXED_CANDIDATE ?= 2
SPINAL_ROUND_SKIP ?= 1
SPINAL_HOST_ROUND_SKIP ?= 1
SPINAL_SIM_FIXED_CANDIDATE ?= 2
NEXTPNR_SEED ?=
else
SPINAL_LANES ?= 4
SPINAL_ENABLE_ECHO ?= 1
SPINAL_ENABLE_HARDCODED ?= 1
SPINAL_FIXED_CANDIDATE ?=
NEXTPNR_SEED ?=
endif
SPINAL_SHARED_K ?= 0
SPINAL_FULLY_UNROLLED ?= 0
SPINAL_WIDE_LANES ?= 0
SPINAL_SHARE_JOB_STATE ?= 0
SPINAL_LANE_START_STAGGER ?= 0
SPINAL_REGISTER_PASS_OUTPUTS ?= 0
SPINAL_REGISTER_COMPRESS_OUTPUTS ?= 0
SPINAL_REGISTER_COMPRESSOR_OUTPUTS ?= $(SPINAL_REGISTER_COMPRESS_OUTPUTS)
SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD ?= 0
SPINAL_TWO_CYCLE_ROUND ?= 0
SPINAL_THREE_CYCLE_ROUND ?= 0
SPINAL_TWO_ROUNDS_PER_CYCLE ?= 0
SPINAL_TWO_ROUND_PIPELINE ?= 0
SPINAL_TWO_PHASE_ROUND_PIPELINE ?= 0
SPINAL_REGISTER_ROUND_CONSTANT ?= 0
SPINAL_MINIMIZE_SHA_RESET ?= 0
SPINAL_SPLIT_SHA_CLOCK ?= 0
SPINAL_ROUND_SKIP ?= 0
SPINAL_CSA_ROUND ?= 0
SPINAL_CSA_SCHEDULE ?= 0
SPINAL_BALANCED_ROUND_ADDER ?= 0
SPINAL_HOST_ROUND_SKIP ?= 0
SPINAL_UNROLLED_DELAY_STYLE ?= auto
ifneq ($(words $(SPINAL_UNROLLED_DELAY_STYLE)),1)
$(error SPINAL_UNROLLED_DELAY_STYLE must be exactly one value)
endif
ifeq ($(filter auto registers distributed_ram block_ram,$(SPINAL_UNROLLED_DELAY_STYLE)),)
$(error SPINAL_UNROLLED_DELAY_STYLE must be auto, registers, distributed_ram, or block_ram)
endif

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
GOWIN_EDA ?= $(if $(wildcard local/gowin-eda/IDE/bin/gw_sh),local/gowin-eda,$(if $(wildcard ../MIPS-FPGA/local/gowin-eda/IDE/bin/gw_sh),../MIPS-FPGA/local/gowin-eda,$(if $(wildcard ../TMS9900-FPGA/local/gowin-eda/IDE/bin/gw_sh),../TMS9900-FPGA/local/gowin-eda,$(if $(wildcard ../FocusTerm/local/gowin-eda/IDE/bin/gw_sh),../FocusTerm/local/gowin-eda,))))
GOWIN_SH ?= $(if $(strip $(GOWIN_EDA)),$(GOWIN_EDA)/IDE/bin/gw_sh,gw_sh)
GOWIN_SH_RUNNER ?= scripts/gowin-sh-env.sh
GOWIN_PLACE_OPTION ?= 2
GOWIN_ROUTE_OPTION ?= 1
GOWIN_ROUTE_MAXFAN ?= 23
GOWIN_CLOCK_ROUTE_ORDER ?= 1
GOWIN_CORRECT_HOLD ?= 0
GOWIN_REPLICATE_RESOURCES ?= 1
GOWIN_SYNTH_ONLY ?= 0
GOWIN_KEEP_FAILED ?= 0
GOWIN_CONVERT_SDP32_36_TO_SDP16_18 ?= 1
truthy = $(filter 1 true TRUE yes YES on ON,$(strip $(1)))
GOWIN_OPTION_SUFFIX := $(if $(call truthy,$(SPINAL_FULLY_UNROLLED)),_unrolled1,)$(if $(call truthy,$(SPINAL_ROUND_SKIP)),_skip1,)$(if $(call truthy,$(SPINAL_HOST_ROUND_SKIP)),_hostskip1,)$(if $(call truthy,$(SPINAL_TWO_ROUNDS_PER_CYCLE)),_2round1,)$(if $(call truthy,$(SPINAL_TWO_ROUND_PIPELINE)),_2rpipe1,)$(if $(call truthy,$(SPINAL_TWO_PHASE_ROUND_PIPELINE)),_2phasepipe1,)$(if $(call truthy,$(SPINAL_REGISTER_PASS_OUTPUTS)),_regpass1,)$(if $(call truthy,$(SPINAL_REGISTER_COMPRESSOR_OUTPUTS)),_regcomp1,)$(if $(call truthy,$(SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD)),_regff1,)$(if $(call truthy,$(SPINAL_REGISTER_ROUND_CONSTANT)),_regk1,)$(if $(call truthy,$(SPINAL_MINIMIZE_SHA_RESET)),_minreset1,)$(if $(call truthy,$(SPINAL_CSA_ROUND)),_csa1,)$(if $(call truthy,$(SPINAL_CSA_SCHEDULE)),_csasch1,)$(if $(call truthy,$(SPINAL_BALANCED_ROUND_ADDER)),_baladd1,)$(if $(call truthy,$(SPINAL_SHARE_JOB_STATE)),_sharejob1,)$(if $(filter-out auto,$(SPINAL_UNROLLED_DELAY_STYLE)),_delay$(SPINAL_UNROLLED_DELAY_STYLE),)$(if $(filter 0 false FALSE no NO off OFF,$(strip $(SPINAL_SHARED_K))),_localK,)
GOWIN_PROJECT_NAME ?= tangminer_gowin_$(TARGET)_lanes$(SPINAL_LANES)_$(SPINAL_CLOCK_PROFILE)$(GOWIN_OPTION_SUFFIX)
GOWIN_PROJECT_DIR := $(BUILD)/gowin/$(GOWIN_PROJECT_NAME)
GOWIN_SDC := $(BUILD)/gowin/constraints/$(GOWIN_PROJECT_NAME).sdc
GOWIN_FS := $(GOWIN_PROJECT_DIR)/impl/pnr/$(GOWIN_PROJECT_NAME).fs
GOWIN_PLL_OUTPUT_NET := $(if $(filter gw5,$(SPINAL_PLL_KIND)),gowin5PllFrom50Mhz_CLKOUT0,gowinRpllFrom27Mhz_CLKOUT)
SBT ?= $(if $(wildcard local/sbt/bin/sbt),$(abspath local/sbt/bin/sbt),sbt)
BOOTSTRAP_PYTHON ?= python3
PYTHON ?= $(if $(wildcard .venv/bin/python3),.venv/bin/python3,$(if $(wildcard .venv/bin/python),.venv/bin/python,python3))
SIM ?= verilator
EMU_TARGET ?= $(TARGET)
EMU_ARGS ?=
SWEEP_ARGS ?=
SPINAL_CLOCK_HZ ?= $(shell $(PYTHON) -c 'print(int(round(float("$(SPINAL_CLOCK_MHZ)") * 1000000)))')
SPINAL_BASE_ROUNDS := $(if $(call truthy,$(SPINAL_ROUND_SKIP)),61,64)
SPINAL_BASE_PERIOD_CYCLES := $(if $(call truthy,$(SPINAL_TWO_PHASE_ROUND_PIPELINE)),$(SPINAL_BASE_ROUNDS),$(if $(call truthy,$(SPINAL_TWO_ROUND_PIPELINE)),$(shell expr \( $(SPINAL_BASE_ROUNDS) + 1 \) / 2 + 1),$(if $(call truthy,$(SPINAL_TWO_ROUNDS_PER_CYCLE)),$(shell expr \( $(SPINAL_BASE_ROUNDS) + 1 \) / 2),$(SPINAL_BASE_ROUNDS))))
SPINAL_ROUND_CYCLE_MULTIPLIER := $(if $(call truthy,$(SPINAL_THREE_CYCLE_ROUND)),3,$(if $(call truthy,$(SPINAL_TWO_CYCLE_ROUND)),2,1))
SPINAL_PASS_OUTPUT_CYCLES := $(if $(call truthy,$(SPINAL_TWO_PHASE_ROUND_PIPELINE)),0,$(if $(call truthy,$(SPINAL_TWO_ROUND_PIPELINE)),0,$(if $(call truthy,$(SPINAL_REGISTER_PASS_OUTPUTS)),$(if $(filter 1,$(SPINAL_ROUND_CYCLE_MULTIPLIER)),1,2),0)))
SPINAL_COMPRESSOR_OUTPUT_CYCLES := $(if $(and $(call truthy,$(SPINAL_REGISTER_COMPRESSOR_OUTPUTS)),$(filter 1,$(SPINAL_ROUND_CYCLE_MULTIPLIER))),1,0)
SPINAL_FIRST_PASS_FEEDFORWARD_CYCLES := $(if $(and $(call truthy,$(SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD)),$(filter 1,$(SPINAL_ROUND_CYCLE_MULTIPLIER))),1,0)
SPINAL_LANE_PERIOD_CYCLES ?= $(if $(call truthy,$(SPINAL_FULLY_UNROLLED)),1,$(shell expr $(SPINAL_BASE_PERIOD_CYCLES) \* $(SPINAL_ROUND_CYCLE_MULTIPLIER) + $(SPINAL_PASS_OUTPUT_CYCLES) + $(SPINAL_COMPRESSOR_OUTPUT_CYCLES) + $(SPINAL_FIRST_PASS_FEEDFORWARD_CYCLES)))
SPINAL_CYCLES_PER_NONCE ?= $(shell $(PYTHON) -c 'print(float("$(SPINAL_LANE_PERIOD_CYCLES)") / float("$(SPINAL_LANES)"))')
SPINAL_SIM_ENABLE_ECHO ?= 1
SPINAL_SIM_ENABLE_HARDCODED ?= 1
SPINAL_SIM_FIXED_CANDIDATE ?=
SPINAL_SIM_STRICT_NONCE_CHECKS ?= 0

.PHONY: all build build-spinal spinal-verilog spinal-sim-verilog oss-build oss-load oss-flash gowin-build gowin-fmax check-gowin-tools gowin-load gowin-flash gowin-load-and-mine gowin-flash-and-mine sweep-spinal load flash flash-and-mine load-and-mine clean sim setup-emulation install-ubuntu launch emu-smoke emu-pty software-mine hardware-mine mine mine-software mine-rtl mine-hardware stratum-client stratum-test stratum-mine-software stratum-mine-rtl stratum-mine-hardware stratum-smoke-rtl check-cocotb sim-cocotb verilator-pty FORCE

all: build

build:
	$(MAKE) $(DEFAULT_FLOW)-build

build-spinal: spinal-verilog

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
	  echo "pll_kind=$(SPINAL_PLL_KIND)"; \
	  echo "input_clock_mhz=$(SPINAL_INPUT_CLOCK_MHZ)"; \
	  echo "clks_per_bit=$(SPINAL_CLKS_PER_BIT)"; \
	  echo "shared_k=$(SPINAL_SHARED_K)"; \
	  echo "fully_unrolled=$(SPINAL_FULLY_UNROLLED)"; \
	  echo "unrolled_delay_style=$(SPINAL_UNROLLED_DELAY_STYLE)"; \
	  echo "enable_echo=$(SPINAL_ENABLE_ECHO)"; \
	  echo "enable_hardcoded=$(SPINAL_ENABLE_HARDCODED)"; \
	  echo "fixed_candidate=$(SPINAL_FIXED_CANDIDATE)"; \
	  echo "wide_lanes=$(SPINAL_WIDE_LANES)"; \
	  echo "share_job_state=$(SPINAL_SHARE_JOB_STATE)"; \
	  echo "lane_start_stagger=$(SPINAL_LANE_START_STAGGER)"; \
	  echo "register_pass_outputs=$(SPINAL_REGISTER_PASS_OUTPUTS)"; \
	  echo "register_compressor_outputs=$(SPINAL_REGISTER_COMPRESSOR_OUTPUTS)"; \
	  echo "register_first_pass_feedforward=$(SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD)"; \
	  echo "two_cycle_round=$(SPINAL_TWO_CYCLE_ROUND)"; \
	  echo "three_cycle_round=$(SPINAL_THREE_CYCLE_ROUND)"; \
	  echo "two_rounds_per_cycle=$(SPINAL_TWO_ROUNDS_PER_CYCLE)"; \
	  echo "two_round_pipeline=$(SPINAL_TWO_ROUND_PIPELINE)"; \
	  echo "two_phase_round_pipeline=$(SPINAL_TWO_PHASE_ROUND_PIPELINE)"; \
	  echo "register_round_constant=$(SPINAL_REGISTER_ROUND_CONSTANT)"; \
	  echo "minimize_sha_reset=$(SPINAL_MINIMIZE_SHA_RESET)"; \
	  echo "split_sha_clock=$(SPINAL_SPLIT_SHA_CLOCK)"; \
	  echo "round_skip=$(SPINAL_ROUND_SKIP)"; \
	  echo "csa_round=$(SPINAL_CSA_ROUND)"; \
	  echo "csa_schedule=$(SPINAL_CSA_SCHEDULE)"; \
	  echo "balanced_round_adder=$(SPINAL_BALANCED_ROUND_ADDER)"; \
	  echo "host_round_skip=$(SPINAL_HOST_ROUND_SKIP)"; \
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
	  echo "pll_kind=$(SPINAL_PLL_KIND)"; \
	  echo "input_clock_mhz=$(SPINAL_INPUT_CLOCK_MHZ)"; \
	  echo "clks_per_bit=8"; \
	  echo "shared_k=$(SPINAL_SHARED_K)"; \
	  echo "fully_unrolled=$(SPINAL_FULLY_UNROLLED)"; \
	  echo "unrolled_delay_style=$(SPINAL_UNROLLED_DELAY_STYLE)"; \
	  echo "enable_echo=$(SPINAL_SIM_ENABLE_ECHO)"; \
	  echo "enable_hardcoded=$(SPINAL_SIM_ENABLE_HARDCODED)"; \
	  echo "fixed_candidate=$(SPINAL_SIM_FIXED_CANDIDATE)"; \
	  echo "wide_lanes=$(SPINAL_WIDE_LANES)"; \
	  echo "share_job_state=$(SPINAL_SHARE_JOB_STATE)"; \
	  echo "lane_start_stagger=$(SPINAL_LANE_START_STAGGER)"; \
	  echo "register_pass_outputs=$(SPINAL_REGISTER_PASS_OUTPUTS)"; \
	  echo "register_compressor_outputs=$(SPINAL_REGISTER_COMPRESSOR_OUTPUTS)"; \
	  echo "register_first_pass_feedforward=$(SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD)"; \
	  echo "two_cycle_round=$(SPINAL_TWO_CYCLE_ROUND)"; \
	  echo "three_cycle_round=$(SPINAL_THREE_CYCLE_ROUND)"; \
	  echo "two_rounds_per_cycle=$(SPINAL_TWO_ROUNDS_PER_CYCLE)"; \
	  echo "two_round_pipeline=$(SPINAL_TWO_ROUND_PIPELINE)"; \
	  echo "two_phase_round_pipeline=$(SPINAL_TWO_PHASE_ROUND_PIPELINE)"; \
	  echo "register_round_constant=$(SPINAL_REGISTER_ROUND_CONSTANT)"; \
	  echo "minimize_sha_reset=$(SPINAL_MINIMIZE_SHA_RESET)"; \
	  echo "split_sha_clock=$(SPINAL_SPLIT_SHA_CLOCK)"; \
	  echo "round_skip=$(SPINAL_ROUND_SKIP)"; \
	  echo "csa_round=$(SPINAL_CSA_ROUND)"; \
	  echo "csa_schedule=$(SPINAL_CSA_SCHEDULE)"; \
	  echo "balanced_round_adder=$(SPINAL_BALANCED_ROUND_ADDER)"; \
	  echo "host_round_skip=$(SPINAL_HOST_ROUND_SKIP)"; \
	} > "$$tmp"; \
	if ! cmp -s "$$tmp" "$@"; then mv "$$tmp" "$@"; else rm "$$tmp"; fi

$(SPINAL_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties $(SPINAL_CONFIG) | $(BUILD)/.dir
	mkdir -p $(SPINAL_DIR)
	@command -v java >/dev/null 2>&1 || { echo "java is not on PATH. Install OpenJDK or run: scripts/setup.sh"; exit 127; }
	@command -v "$(SBT)" >/dev/null 2>&1 || { echo "sbt is not on PATH. Install sbt or run: scripts/setup.sh"; exit 127; }
	TANGMINER_VERILOG_DIR=$(SPINAL_DIR) TANGMINER_USE_PLL=$(SPINAL_USE_PLL) TANGMINER_PLL_KIND=$(SPINAL_PLL_KIND) TANGMINER_INPUT_CLOCK_MHZ=$(SPINAL_INPUT_CLOCK_MHZ) TANGMINER_CLOCK_PROFILE=$(SPINAL_CLOCK_PROFILE) TANGMINER_CLKS_PER_BIT=$(SPINAL_CLKS_PER_BIT) TANGMINER_LANES=$(SPINAL_LANES) TANGMINER_SHARED_K=$(SPINAL_SHARED_K) TANGMINER_FULLY_UNROLLED=$(SPINAL_FULLY_UNROLLED) TANGMINER_UNROLLED_DELAY_STYLE=$(SPINAL_UNROLLED_DELAY_STYLE) TANGMINER_ENABLE_ECHO=$(SPINAL_ENABLE_ECHO) TANGMINER_ENABLE_HARDCODED=$(SPINAL_ENABLE_HARDCODED) TANGMINER_FIXED_CANDIDATE=$(SPINAL_FIXED_CANDIDATE) TANGMINER_WIDE_LANES=$(SPINAL_WIDE_LANES) TANGMINER_SHARE_JOB_STATE=$(SPINAL_SHARE_JOB_STATE) TANGMINER_LANE_START_STAGGER=$(SPINAL_LANE_START_STAGGER) TANGMINER_REGISTER_PASS_OUTPUTS=$(SPINAL_REGISTER_PASS_OUTPUTS) TANGMINER_REGISTER_COMPRESSOR_OUTPUTS=$(SPINAL_REGISTER_COMPRESSOR_OUTPUTS) TANGMINER_REGISTER_FIRST_PASS_FEEDFORWARD=$(SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD) TANGMINER_TWO_CYCLE_ROUND=$(SPINAL_TWO_CYCLE_ROUND) TANGMINER_THREE_CYCLE_ROUND=$(SPINAL_THREE_CYCLE_ROUND) TANGMINER_TWO_ROUNDS_PER_CYCLE=$(SPINAL_TWO_ROUNDS_PER_CYCLE) TANGMINER_TWO_ROUND_PIPELINE=$(SPINAL_TWO_ROUND_PIPELINE) TANGMINER_TWO_PHASE_ROUND_PIPELINE=$(SPINAL_TWO_PHASE_ROUND_PIPELINE) TANGMINER_REGISTER_ROUND_CONSTANT=$(SPINAL_REGISTER_ROUND_CONSTANT) TANGMINER_MINIMIZE_SHA_RESET=$(SPINAL_MINIMIZE_SHA_RESET) TANGMINER_SPLIT_SHA_CLOCK=$(SPINAL_SPLIT_SHA_CLOCK) TANGMINER_ROUND_SKIP=$(SPINAL_ROUND_SKIP) TANGMINER_CSA_ROUND=$(SPINAL_CSA_ROUND) TANGMINER_CSA_SCHEDULE=$(SPINAL_CSA_SCHEDULE) TANGMINER_BALANCED_ROUND_ADDER=$(SPINAL_BALANCED_ROUND_ADDER) TANGMINER_HOST_ROUND_SKIP=$(SPINAL_HOST_ROUND_SKIP) $(SBT) "runMain tangminer.GenerateVerilog"

$(SPINAL_SIM_SRC): src/main/scala/tangminer/TangMiner.scala build.sbt project/build.properties $(SPINAL_SIM_CONFIG) | $(BUILD)/.dir
	@command -v java >/dev/null 2>&1 || { echo "java is not on PATH. Install OpenJDK or run: scripts/setup.sh"; exit 127; }
	@command -v "$(SBT)" >/dev/null 2>&1 || { echo "sbt is not on PATH. Install sbt or run: scripts/setup.sh"; exit 127; }
	TANGMINER_VERILOG_DIR=$(dir $(SPINAL_SIM_SRC)) TANGMINER_PLL_KIND=$(SPINAL_PLL_KIND) TANGMINER_INPUT_CLOCK_MHZ=$(SPINAL_INPUT_CLOCK_MHZ) TANGMINER_LANES=$(SPINAL_LANES) TANGMINER_CLKS_PER_BIT=8 TANGMINER_SHARED_K=$(SPINAL_SHARED_K) TANGMINER_FULLY_UNROLLED=$(SPINAL_FULLY_UNROLLED) TANGMINER_UNROLLED_DELAY_STYLE=$(SPINAL_UNROLLED_DELAY_STYLE) TANGMINER_ENABLE_ECHO=$(SPINAL_SIM_ENABLE_ECHO) TANGMINER_ENABLE_HARDCODED=$(SPINAL_SIM_ENABLE_HARDCODED) TANGMINER_FIXED_CANDIDATE=$(SPINAL_SIM_FIXED_CANDIDATE) TANGMINER_WIDE_LANES=$(SPINAL_WIDE_LANES) TANGMINER_SHARE_JOB_STATE=$(SPINAL_SHARE_JOB_STATE) TANGMINER_LANE_START_STAGGER=$(SPINAL_LANE_START_STAGGER) TANGMINER_REGISTER_PASS_OUTPUTS=$(SPINAL_REGISTER_PASS_OUTPUTS) TANGMINER_REGISTER_COMPRESSOR_OUTPUTS=$(SPINAL_REGISTER_COMPRESSOR_OUTPUTS) TANGMINER_REGISTER_FIRST_PASS_FEEDFORWARD=$(SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD) TANGMINER_TWO_CYCLE_ROUND=$(SPINAL_TWO_CYCLE_ROUND) TANGMINER_THREE_CYCLE_ROUND=$(SPINAL_THREE_CYCLE_ROUND) TANGMINER_TWO_ROUNDS_PER_CYCLE=$(SPINAL_TWO_ROUNDS_PER_CYCLE) TANGMINER_TWO_ROUND_PIPELINE=$(SPINAL_TWO_ROUND_PIPELINE) TANGMINER_TWO_PHASE_ROUND_PIPELINE=$(SPINAL_TWO_PHASE_ROUND_PIPELINE) TANGMINER_REGISTER_ROUND_CONSTANT=$(SPINAL_REGISTER_ROUND_CONSTANT) TANGMINER_MINIMIZE_SHA_RESET=$(SPINAL_MINIMIZE_SHA_RESET) TANGMINER_SPLIT_SHA_CLOCK=$(SPINAL_SPLIT_SHA_CLOCK) TANGMINER_ROUND_SKIP=$(SPINAL_ROUND_SKIP) TANGMINER_CSA_ROUND=$(SPINAL_CSA_ROUND) TANGMINER_CSA_SCHEDULE=$(SPINAL_CSA_SCHEDULE) TANGMINER_BALANCED_ROUND_ADDER=$(SPINAL_BALANCED_ROUND_ADDER) TANGMINER_HOST_ROUND_SKIP=$(SPINAL_HOST_ROUND_SKIP) $(SBT) "runMain tangminer.GenerateSimVerilog"

$(SPINAL_PREFIX).json: $(SPINAL_SRC) | $(BUILD)/.dir
	$(YOSYS) -p "read_verilog $(SPINAL_SRC); $(YOSYS_PRE_SYNTH_CMDS) synth_gowin $(YOSYS_SYNTH_ARGS) -top $(TOP) -json $@"

$(SPINAL_PREFIX)_pnr.json: $(SPINAL_PREFIX).json $(CST)
	$(NEXTPNR) --json $< --write $@ --freq $(SPINAL_CLOCK_MHZ) --device $(DEVICE) -o family=$(FAMILY) -o cst=$(CST) $(NEXTPNR_SEED_ARG)

$(SPINAL_PREFIX).fs: $(SPINAL_PREFIX)_pnr.json
	$(GOWIN_PACK) -d $(FAMILY) -o $@ $<

oss-build: $(SPINAL_PREFIX).fs

oss-load: $(SPINAL_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) $<

oss-flash: $(SPINAL_PREFIX).fs
	$(OPENFPGALOADER) -b $(BOARD) -f $<

$(GOWIN_SDC): FORCE | $(BUILD)/.dir
	mkdir -p $(dir $@)
	@input_period="$$( $(PYTHON) -c 'print("%.3f" % (1000.0 / float("$(SPINAL_INPUT_CLOCK_MHZ)")))' )"; \
	input_half="$$( $(PYTHON) -c 'print("%.3f" % ((1000.0 / float("$(SPINAL_INPUT_CLOCK_MHZ)")) / 2.0))' )"; \
	period="$$( $(PYTHON) -c 'print("%.3f" % (1000.0 / float("$(SPINAL_CLOCK_MHZ)")))' )"; \
	half="$$( $(PYTHON) -c 'print("%.3f" % ((1000.0 / float("$(SPINAL_CLOCK_MHZ)")) / 2.0))' )"; \
	{ \
	  if [ -n "$(call truthy,$(SPINAL_USE_PLL))" ]; then \
	    echo "create_clock -name inputClock -period $$input_period -waveform {0 $$input_half} [get_ports {clk}]"; \
	    echo "create_clock -name systemClock -period $$period -waveform {0 $$half} [get_nets {$(GOWIN_PLL_OUTPUT_NET)}]"; \
	  else \
	    echo "create_clock -name systemClock -period $$period -waveform {0 $$half} [get_ports {clk}]"; \
	  fi; \
	} > $@

check-gowin-tools:
	@command -v "$(GOWIN_SH)" >/dev/null 2>&1 || { echo "Missing Official Gowin EDA gw_sh. Set GOWIN_SH=/path/to/gw_sh."; exit 1; }
	@"$(GOWIN_SH)" -v >/dev/null 2>&1 || "$(GOWIN_SH)" -h >/dev/null 2>&1 || true

gowin-build: $(SPINAL_SRC) $(CST) $(GOWIN_SDC) scripts/gowin-build.sh scripts/gowin-sh-env.sh
	GOWIN_SH="$(GOWIN_SH)" \
	GOWIN_SH_RUNNER="$(GOWIN_SH_RUNNER)" \
	GOWIN_PROJECT_DIR="$(GOWIN_PROJECT_DIR)" \
	GOWIN_PROJECT_NAME="$(GOWIN_PROJECT_NAME)" \
	DEVICE="$(DEVICE)" \
	GOWIN_DEVICE_VERSION="$(GOWIN_DEVICE_VERSION)" \
	TOP="$(TOP)" \
	CST="$(CST)" \
	SDC="$(GOWIN_SDC)" \
	RTL_SRC="$(SPINAL_SRC)" \
	GOWIN_PLACE_OPTION="$(GOWIN_PLACE_OPTION)" \
	GOWIN_ROUTE_OPTION="$(GOWIN_ROUTE_OPTION)" \
	GOWIN_ROUTE_MAXFAN="$(GOWIN_ROUTE_MAXFAN)" \
	GOWIN_CLOCK_ROUTE_ORDER="$(GOWIN_CLOCK_ROUTE_ORDER)" \
	GOWIN_CORRECT_HOLD="$(GOWIN_CORRECT_HOLD)" \
	GOWIN_REPLICATE_RESOURCES="$(GOWIN_REPLICATE_RESOURCES)" \
	GOWIN_CPU_PIN_REUSE="$(GOWIN_CPU_PIN_REUSE)" \
	GOWIN_SSPI_PIN_REUSE="$(GOWIN_SSPI_PIN_REUSE)" \
	GOWIN_SYNTH_ONLY="$(GOWIN_SYNTH_ONLY)" \
	GOWIN_KEEP_FAILED="$(GOWIN_KEEP_FAILED)" \
	GOWIN_CONVERT_SDP32_36_TO_SDP16_18="$(GOWIN_CONVERT_SDP32_36_TO_SDP16_18)" \
	bash scripts/gowin-build.sh

gowin-fmax: gowin-build
	@tr="$(GOWIN_PROJECT_DIR)/impl/pnr/$(GOWIN_PROJECT_NAME).tr"; \
	if [ ! -f "$$tr" ]; then echo "No Gowin timing report found: $$tr"; exit 1; fi; \
	$(PYTHON) scripts/tools/gowin_timing_summary.py "$$tr"

gowin-load: gowin-build
	$(OPENFPGALOADER) -b $(BOARD) $(GOWIN_FS)

gowin-flash: gowin-build
	$(OPENFPGALOADER) -b $(BOARD) -f $(GOWIN_FS)

gowin-load-and-mine: gowin-load
	HARDWARE_CLOCK_MHZ="$(SPINAL_CLOCK_MHZ)" HARDWARE_LANES="$(SPINAL_LANES)" HARDWARE_LANE_PERIOD_CYCLES="$(SPINAL_LANE_PERIOD_CYCLES)" scripts/mine-hardware.sh "$(SERIAL_PORT)"

gowin-flash-and-mine: gowin-flash
	HARDWARE_CLOCK_MHZ="$(SPINAL_CLOCK_MHZ)" HARDWARE_LANES="$(SPINAL_LANES)" HARDWARE_LANE_PERIOD_CYCLES="$(SPINAL_LANE_PERIOD_CYCLES)" scripts/mine-hardware.sh "$(SERIAL_PORT)"

load:
	$(MAKE) $(DEFAULT_FLOW)-load

flash:
	$(MAKE) $(DEFAULT_FLOW)-flash

flash-and-mine:
	TARGET="$(TARGET)" BITSTREAM_FLOW="$(DEFAULT_FLOW)" scripts/flash-and-mine.sh "$(SERIAL_PORT)"

load-and-mine:
	TARGET="$(TARGET)" BITSTREAM_FLOW="$(DEFAULT_FLOW)" scripts/flash-and-mine.sh --load "$(SERIAL_PORT)"

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
	HARDWARE_CLOCK_MHZ="$(SPINAL_CLOCK_MHZ)" HARDWARE_LANES="$(SPINAL_LANES)" HARDWARE_LANE_PERIOD_CYCLES="$(SPINAL_LANE_PERIOD_CYCLES)" scripts/mine-hardware.sh "$(SERIAL_PORT)"

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
	PATH="$(TOOLBIN):$$PATH" $(MAKE) -C sim/cocotb SIM=$(SIM) PYTHON_BIN="$(abspath $(PYTHON))" RTL_SOURCES="$(abspath $(SPINAL_SIM_SRC))" EXTRA_COMPILE_ARGS= CLKS_PER_BIT=8 LANE_COUNT=$(SPINAL_LANES) HARDWARE_CLOCK_HZ=$(SPINAL_CLOCK_HZ) EXPECTED_LANE_PERIOD_CYCLES=$(SPINAL_LANE_PERIOD_CYCLES) HOST_ROUND_SKIP_PAYLOAD=$(SPINAL_HOST_ROUND_SKIP) FIXED_CANDIDATE_MODE=$(SPINAL_SIM_FIXED_CANDIDATE) STRICT_NONCE_CHECKS=$(SPINAL_SIM_STRICT_NONCE_CHECKS)

clean:
	rm -rf $(BUILD)
	$(MAKE) -C stratum clean
