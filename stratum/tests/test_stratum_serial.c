#include "stratum_serial.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static int failures = 0;

static void check(int condition, const char* message) {
    if (!condition) {
        fprintf(stderr, "FAIL: %s\n", message);
        ++failures;
    }
}

static void make_serial_pair(tangminer_serial_t* serial, int* peer_fd) {
    int fds[2];
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, fds) != 0) {
        perror("socketpair");
        exit(2);
    }
    memset(serial, 0, sizeof(*serial));
    serial->fd = fds[0];
    *peer_fd = fds[1];
}

static void close_serial_pair(tangminer_serial_t* serial, int peer_fd) {
    tangminer_serial_close(serial);
    close(peer_fd);
}

static void read_exact(int fd, uint8_t* bytes, size_t size) {
    size_t got = 0;
    while (got < size) {
        const ssize_t rc = read(fd, bytes + got, size - got);
        if (rc <= 0) {
            perror("read");
            exit(2);
        }
        got += (size_t)rc;
    }
}

static void test_commands(void) {
    tangminer_serial_t serial;
    int peer_fd = -1;
    make_serial_pair(&serial, &peer_fd);

    uint8_t packet[79];
    for (size_t i = 0; i < sizeof(packet); ++i) {
        packet[i] = (uint8_t)i;
    }
    packet[0] = 'T';
    packet[1] = 'N';
    packet[2] = 'J';
    check(tangminer_serial_send_job(&serial, packet) == 0, "send job");
    uint8_t received[79];
    read_exact(peer_fd, received, sizeof(received));
    check(memcmp(packet, received, sizeof(packet)) == 0, "job bytes preserved");

    check(tangminer_serial_send_tagged_job(&serial, 0xabcdU, packet) == 0,
          "send tagged job");
    uint8_t tagged[81];
    read_exact(peer_fd, tagged, sizeof(tagged));
    check(memcmp(tagged, "TNQ", 3) == 0, "tagged job command bytes");
    check(tagged[3] == 0xab && tagged[4] == 0xcd, "tagged job big-endian tag");
    check(memcmp(tagged + 5, packet + 3, 76) == 0, "tagged job payload bytes");

    check(tangminer_serial_send_stop(&serial) == 0, "send stop");
    uint8_t stop[3];
    read_exact(peer_fd, stop, sizeof(stop));
    check(memcmp(stop, "TNS", sizeof(stop)) == 0, "stop command bytes");

    close_serial_pair(&serial, peer_fd);
}

static void test_event_stream_and_partial_found(void) {
    tangminer_serial_t serial;
    int peer_fd = -1;
    make_serial_pair(&serial, &peer_fd);

    check(write(peer_fd, "F\x12", 2) == 2, "write partial found");
    uint32_t nonce = 0;
    check(tangminer_serial_read_event(&serial, &nonce, 1) == TANGMINER_SERIAL_EVENT_TIMEOUT,
          "partial found survives timeout");
    const uint8_t rest[] = {0x34, 0x56, 0x78, 'S', 'G'};
    check(write(peer_fd, rest, sizeof(rest)) == (ssize_t)sizeof(rest), "write remaining events");
    check(tangminer_serial_read_event(&serial, &nonce, 10) == TANGMINER_SERIAL_EVENT_FOUND,
          "found event");
    check(nonce == 0x12345678U, "found nonce byte order");
    check(tangminer_serial_read_event(&serial, &nonce, 10) == TANGMINER_SERIAL_EVENT_STOPPED,
          "stopped event");
    check(tangminer_serial_read_event(&serial, &nonce, 10) == TANGMINER_SERIAL_EVENT_CONTINUOUS_ACK,
          "continuous acknowledgement event");

    close_serial_pair(&serial, peer_fd);
}

static void test_partial_tagged_found(void) {
    tangminer_serial_t serial;
    int peer_fd = -1;
    make_serial_pair(&serial, &peer_fd);

    check(write(peer_fd, "R\xab", 2) == 2, "write partial tagged result");
    uint16_t tag = 0;
    uint32_t nonce = 0;
    check(tangminer_serial_read_tagged_event(&serial, &tag, &nonce, 5) ==
              TANGMINER_SERIAL_EVENT_TIMEOUT,
          "partial tagged result survives timeout");

    const uint8_t rest[] = {0xcd, 0x12, 0x34, 0x56, 0x78};
    check(write(peer_fd, rest, sizeof(rest)) == (ssize_t)sizeof(rest),
          "write remaining tagged result");
    check(tangminer_serial_read_tagged_event(&serial, &tag, &nonce, 10) ==
              TANGMINER_SERIAL_EVENT_TAGGED_FOUND,
          "tagged result event");
    check(tag == 0xabcdU, "tagged result tag byte order");
    check(nonce == 0x12345678U, "tagged result nonce byte order");

    const uint8_t legacy[] = {'F', 0xde, 0xad, 0xbe, 0xef};
    check(write(peer_fd, legacy, sizeof(legacy)) == (ssize_t)sizeof(legacy),
          "write legacy result during tagged read");
    check(tangminer_serial_read_tagged_event(&serial, &tag, &nonce, 10) ==
              TANGMINER_SERIAL_EVENT_FOUND,
          "legacy result is explicitly distinct from tagged result");

    close_serial_pair(&serial, peer_fd);
}

static void test_negotiate_continuous(void) {
    tangminer_serial_t serial;
    int peer_fd = -1;
    uint8_t command[3];

    make_serial_pair(&serial, &peer_fd);
    check(write(peer_fd, "G", 1) == 1, "write negotiation acknowledgement");
    check(tangminer_serial_negotiate_continuous(&serial, 10) == 1,
          "continuous negotiation accepted");
    read_exact(peer_fd, command, sizeof(command));
    check(memcmp(command, "TNG", sizeof(command)) == 0, "negotiation command bytes");
    close_serial_pair(&serial, peer_fd);

    make_serial_pair(&serial, &peer_fd);
    check(tangminer_serial_negotiate_continuous(&serial, 5) == 0,
          "continuous negotiation timeout falls back");
    check(!serial.receiving_found && serial.found_payload_len == 0,
          "timeout fallback leaves legacy parser reset");
    check(!serial.receiving_tagged_found && serial.tagged_found_payload_len == 0,
          "timeout fallback leaves tagged parser reset");
    read_exact(peer_fd, command, sizeof(command));
    check(memcmp(command, "TNG", sizeof(command)) == 0, "fallback negotiation command bytes");
    close_serial_pair(&serial, peer_fd);

    make_serial_pair(&serial, &peer_fd);
    check(write(peer_fd, "F\x12", 2) == 2, "write partial legacy negotiation reply");
    check(tangminer_serial_negotiate_continuous(&serial, 5) == 0,
          "partial legacy reply times out to fallback");
    check(!serial.receiving_found && serial.found_payload_len == 0,
          "fallback resets partial legacy parser");
    check(!serial.receiving_tagged_found && serial.tagged_found_payload_len == 0,
          "legacy fallback also resets tagged parser");
    read_exact(peer_fd, command, sizeof(command));
    close_serial_pair(&serial, peer_fd);

    make_serial_pair(&serial, &peer_fd);
    check(write(peer_fd, "R\xab", 2) == 2, "write partial tagged negotiation reply");
    check(tangminer_serial_negotiate_continuous(&serial, 5) == 0,
          "partial tagged reply times out to fallback");
    check(!serial.receiving_found && serial.found_payload_len == 0,
          "tagged fallback also resets legacy parser");
    check(!serial.receiving_tagged_found && serial.tagged_found_payload_len == 0,
          "fallback resets partial tagged parser");
    read_exact(peer_fd, command, sizeof(command));
    close_serial_pair(&serial, peer_fd);

    make_serial_pair(&serial, &peer_fd);
    check(write(peer_fd, "X", 1) == 1, "write invalid negotiation reply");
    check(tangminer_serial_negotiate_continuous(&serial, 10) == -1,
          "continuous negotiation serial error fails");
    read_exact(peer_fd, command, sizeof(command));
    close_serial_pair(&serial, peer_fd);
}

int main(void) {
    test_commands();
    test_event_stream_and_partial_found();
    test_partial_tagged_found();
    test_negotiate_continuous();
    if (failures != 0) {
        fprintf(stderr, "%d serial test(s) failed\n", failures);
        return 1;
    }
    puts("stratum serial tests passed");
    return 0;
}
