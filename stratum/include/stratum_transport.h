#pragma once

#include <stddef.h>
#include <stdint.h>

typedef struct stratum_transport stratum_transport_t;

int stratum_transport_connect(
    stratum_transport_t* transport, const char* host, uint16_t port, int timeout_ms);
int stratum_transport_send_all(stratum_transport_t* transport, const char* data, size_t len);
int stratum_transport_recv(stratum_transport_t* transport, char* data, size_t len);
void stratum_transport_close(stratum_transport_t* transport);

struct stratum_transport {
    int fd;
    int timeout_ms;
};
