#pragma once

#include <stddef.h>
#include <stdint.h>

typedef struct {
    int fd;
} tangminer_serial_t;

int tangminer_serial_open(tangminer_serial_t* serial, const char* path, int baud);
int tangminer_serial_send_job(tangminer_serial_t* serial, const uint8_t packet[79]);
int tangminer_serial_read_found(tangminer_serial_t* serial, uint32_t* nonce, int timeout_ms);
void tangminer_serial_stop(tangminer_serial_t* serial);
void tangminer_serial_close(tangminer_serial_t* serial);
