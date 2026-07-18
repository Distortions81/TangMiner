CC ?= cc
CFLAGS ?= -std=c99 -Wall -Wextra -Werror -O2
CPPFLAGS ?= -D_DEFAULT_SOURCE -D_POSIX_C_SOURCE=200112L -Iinclude
LDFLAGS ?= -pthread

BUILD := build
SRC := \
	src/stratum_continuous.c \
	src/stratum_client.c \
	src/stratum_miner.c \
	src/stratum_json.c \
	src/stratum_messages.c \
	src/stratum_serial_posix.c \
	src/stratum_sha256.c \
	src/stratum_transport_posix.c \
	src/stratum_work_state.c
CLI_SRC := src/stratum_cli.c
JSON_TEST_SRC := tests/test_stratum_json.c
MINER_TEST_SRC := tests/test_stratum_miner.c
CONTINUOUS_TEST_SRC := tests/test_stratum_continuous.c
SERIAL_TEST_SRC := tests/test_stratum_serial.c
WORK_STATE_TEST_SRC := tests/test_stratum_work_state.c

OBJ := $(SRC:src/%.c=$(BUILD)/%.o)
CLI_OBJ := $(CLI_SRC:src/%.c=$(BUILD)/%.o)
JSON_TEST_OBJ := $(JSON_TEST_SRC:tests/%.c=$(BUILD)/%.o)
MINER_TEST_OBJ := $(MINER_TEST_SRC:tests/%.c=$(BUILD)/%.o)
CONTINUOUS_TEST_OBJ := $(CONTINUOUS_TEST_SRC:tests/%.c=$(BUILD)/%.o)
SERIAL_TEST_OBJ := $(SERIAL_TEST_SRC:tests/%.c=$(BUILD)/%.o)
WORK_STATE_TEST_OBJ := $(WORK_STATE_TEST_SRC:tests/%.c=$(BUILD)/%.o)

.PHONY: all test smoke-fakes fake-pool fake-fpga clean

all: $(BUILD)/stratum-client

$(BUILD):
	mkdir -p $@

$(BUILD)/%.o: src/%.c | $(BUILD)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

$(BUILD)/%.o: tests/%.c | $(BUILD)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

$(BUILD)/stratum-client: $(OBJ) $(CLI_OBJ)
	$(CC) $(LDFLAGS) $^ -o $@

$(BUILD)/test-stratum-json: $(BUILD)/stratum_json.o $(BUILD)/stratum_messages.o $(JSON_TEST_OBJ)
	$(CC) $(LDFLAGS) $^ -o $@

$(BUILD)/test-stratum-miner: $(BUILD)/stratum_miner.o $(BUILD)/stratum_sha256.o $(MINER_TEST_OBJ)
	$(CC) $(LDFLAGS) $^ -o $@

$(BUILD)/test-stratum-continuous: $(BUILD)/stratum_continuous.o $(CONTINUOUS_TEST_OBJ)
	$(CC) $(LDFLAGS) $^ -o $@

$(BUILD)/test-stratum-serial: $(BUILD)/stratum_serial_posix.o $(BUILD)/stratum_continuous.o $(SERIAL_TEST_OBJ)
	$(CC) $(LDFLAGS) $^ -o $@

$(BUILD)/test-stratum-work-state: $(BUILD)/stratum_work_state.o $(WORK_STATE_TEST_OBJ)
	$(CC) $(LDFLAGS) $^ -o $@

test: $(BUILD)/test-stratum-json $(BUILD)/test-stratum-miner $(BUILD)/test-stratum-continuous $(BUILD)/test-stratum-serial $(BUILD)/test-stratum-work-state
	$(BUILD)/test-stratum-json
	$(BUILD)/test-stratum-miner
	$(BUILD)/test-stratum-continuous
	$(BUILD)/test-stratum-serial
	$(BUILD)/test-stratum-work-state

smoke-fakes: $(BUILD)/stratum-client
	python3 tools/smoke_fake_stack.py

fake-pool:
	python3 tools/fake_pool.py

fake-fpga:
	python3 tools/fake_fpga.py

clean:
	rm -rf $(BUILD)
