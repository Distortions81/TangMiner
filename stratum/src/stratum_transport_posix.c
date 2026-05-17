#include "stratum_transport.h"

#include <errno.h>
#include <netdb.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

static void set_socket_timeout(int fd, int timeout_ms) {
    struct timeval timeout = {
        .tv_sec = timeout_ms / 1000,
        .tv_usec = (timeout_ms % 1000) * 1000,
    };
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
}

int stratum_transport_connect(
    stratum_transport_t* transport, const char* host, uint16_t port, int timeout_ms) {
    char port_text[8];
    struct addrinfo hints;
    struct addrinfo* result = NULL;
    snprintf(port_text, sizeof(port_text), "%u", port);
    memset(transport, 0, sizeof(*transport));
    transport->fd = -1;
    transport->timeout_ms = timeout_ms;

    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(host, port_text, &hints, &result) != 0) {
        return -1;
    }

    for (struct addrinfo* entry = result; entry != NULL; entry = entry->ai_next) {
        const int fd = socket(entry->ai_family, entry->ai_socktype, entry->ai_protocol);
        if (fd < 0) {
            continue;
        }
        set_socket_timeout(fd, timeout_ms);
        if (connect(fd, entry->ai_addr, entry->ai_addrlen) == 0) {
            transport->fd = fd;
            freeaddrinfo(result);
            return 0;
        }
        close(fd);
    }

    freeaddrinfo(result);
    return -1;
}

int stratum_transport_send_all(stratum_transport_t* transport, const char* data, size_t len) {
    size_t sent = 0;
    while (sent < len) {
        const ssize_t rc = send(transport->fd, data + sent, len - sent, 0);
        if (rc <= 0) {
            return -1;
        }
        sent += (size_t)rc;
    }
    return 0;
}

int stratum_transport_recv(stratum_transport_t* transport, char* data, size_t len) {
    const ssize_t rc = recv(transport->fd, data, len, 0);
    if (rc < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
        return 0;
    }
    return rc > 0 ? (int)rc : -1;
}

void stratum_transport_close(stratum_transport_t* transport) {
    if (transport->fd >= 0) {
        close(transport->fd);
        transport->fd = -1;
    }
}
