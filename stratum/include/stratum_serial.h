#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
    TANGMINER_SERIAL_EVENT_ERROR = -1,
    TANGMINER_SERIAL_EVENT_TIMEOUT = 0,
    TANGMINER_SERIAL_EVENT_FOUND = 1,
    TANGMINER_SERIAL_EVENT_CONTINUOUS_ACK = 2,
    TANGMINER_SERIAL_EVENT_STOPPED = 3,
    TANGMINER_SERIAL_EVENT_TAGGED_FOUND = 4,
} tangminer_serial_event_t;

typedef struct {
    int fd;
    bool receiving_found;
    uint8_t found_payload[4];
    size_t found_payload_len;
    bool receiving_tagged_found;
    uint8_t tagged_found_payload[6];
    size_t tagged_found_payload_len;
} tangminer_serial_t;

int tangminer_serial_open(tangminer_serial_t* serial, const char* path, int baud);
int tangminer_serial_negotiate_continuous(tangminer_serial_t* serial, int timeout_ms);
int tangminer_serial_send_job(tangminer_serial_t* serial, const uint8_t packet[79]);
int tangminer_serial_send_tagged_job(
    tangminer_serial_t* serial, uint16_t tag, const uint8_t packet[79]);
int tangminer_serial_send_stop(tangminer_serial_t* serial);
tangminer_serial_event_t tangminer_serial_read_event(
    tangminer_serial_t* serial, uint32_t* nonce, int timeout_ms);
tangminer_serial_event_t tangminer_serial_read_tagged_event(
    tangminer_serial_t* serial, uint16_t* tag, uint32_t* nonce, int timeout_ms);
void tangminer_serial_close(tangminer_serial_t* serial);
