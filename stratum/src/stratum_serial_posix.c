#include "stratum_serial.h"

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>

static speed_t baud_to_speed(int baud) {
    switch (baud) {
        case 9600: return B9600;
        case 19200: return B19200;
        case 38400: return B38400;
        case 57600: return B57600;
        case 115200: return B115200;
#ifdef B230400
        case 230400: return B230400;
#endif
#ifdef B460800
        case 460800: return B460800;
#endif
#ifdef B921600
        case 921600: return B921600;
#endif
        default: return B115200;
    }
}

int tangminer_serial_open(tangminer_serial_t* serial, const char* path, int baud) {
    struct termios tio;
    memset(serial, 0, sizeof(*serial));
    serial->fd = -1;
    const int fd = open(path, O_RDWR | O_NOCTTY | O_SYNC);
    if (fd < 0) {
        return -1;
    }
    if (tcgetattr(fd, &tio) != 0) {
        close(fd);
        return -1;
    }
    cfmakeraw(&tio);
    tio.c_cflag |= CLOCAL | CREAD;
#ifdef CRTSCTS
    tio.c_cflag &= ~CRTSCTS;
#endif
    tio.c_cflag &= ~CSTOPB;
    tio.c_cflag &= ~PARENB;
    tio.c_cflag &= ~CSIZE;
    tio.c_cflag |= CS8;
    tio.c_cc[VMIN] = 0;
    tio.c_cc[VTIME] = 0;
    const speed_t speed = baud_to_speed(baud);
    cfsetispeed(&tio, speed);
    cfsetospeed(&tio, speed);
    if (tcsetattr(fd, TCSANOW, &tio) != 0) {
        close(fd);
        return -1;
    }
    tcflush(fd, TCIOFLUSH);
    serial->fd = fd;
    return 0;
}

int tangminer_serial_send_job(tangminer_serial_t* serial, const uint8_t packet[79]) {
    size_t written = 0;
    while (written < 79) {
        const ssize_t rc = write(serial->fd, packet + written, 79 - written);
        if (rc <= 0) {
            return -1;
        }
        written += (size_t)rc;
    }
    tcdrain(serial->fd);
    return 0;
}

int tangminer_serial_read_found(tangminer_serial_t* serial, uint32_t* nonce, int timeout_ms) {
    uint8_t response[5];
    size_t got = 0;
    while (got < sizeof(response)) {
        struct pollfd pfd = {.fd = serial->fd, .events = POLLIN};
        const int poll_rc = poll(&pfd, 1, timeout_ms);
        if (poll_rc <= 0) {
            return poll_rc;
        }
        const ssize_t rc = read(serial->fd, response + got, sizeof(response) - got);
        if (rc < 0 && errno == EINTR) {
            continue;
        }
        if (rc <= 0) {
            return -1;
        }
        got += (size_t)rc;
    }
    if (response[0] != 'F') {
        return -1;
    }
    *nonce = ((uint32_t)response[1] << 24) | ((uint32_t)response[2] << 16) |
             ((uint32_t)response[3] << 8) | response[4];
    return 1;
}

void tangminer_serial_stop(tangminer_serial_t* serial) {
    if (serial->fd >= 0) {
        const uint8_t stop[] = {'T', 'N', 'S'};
        ssize_t ignored = write(serial->fd, stop, sizeof(stop));
        (void)ignored;
        tcdrain(serial->fd);
    }
}

void tangminer_serial_close(tangminer_serial_t* serial) {
    if (serial->fd >= 0) {
        close(serial->fd);
        serial->fd = -1;
    }
}
