#include <cerrno>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <fcntl.h>
#include <string>
#include <termios.h>
#include <unistd.h>

#include "Vtop.h"
#include "verilated.h"

#ifndef CLKS_PER_BIT
#define CLKS_PER_BIT 8
#endif

namespace {

volatile std::sig_atomic_t keep_running = 1;

void handle_signal(int) {
    keep_running = 0;
}

void set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        std::perror("fcntl(F_GETFL)");
        std::exit(1);
    }
    if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        std::perror("fcntl(F_SETFL)");
        std::exit(1);
    }
}

std::string open_pty(int &master_fd) {
    master_fd = posix_openpt(O_RDWR | O_NOCTTY);
    if (master_fd < 0) {
        std::perror("posix_openpt");
        std::exit(1);
    }
    if (grantpt(master_fd) < 0 || unlockpt(master_fd) < 0) {
        std::perror("grantpt/unlockpt");
        std::exit(1);
    }

    char *name = ptsname(master_fd);
    if (name == nullptr) {
        std::perror("ptsname");
        std::exit(1);
    }

    int slave_fd = open(name, O_RDWR | O_NOCTTY);
    if (slave_fd >= 0) {
        termios tio {};
        if (tcgetattr(slave_fd, &tio) == 0) {
            cfmakeraw(&tio);
            tcsetattr(slave_fd, TCSANOW, &tio);
        }
        close(slave_fd);
    }

    set_nonblocking(master_fd);
    return std::string(name);
}

void tick(Vtop &top, uint64_t &cycles) {
    top.clk = 0;
    top.eval();
    top.clk = 1;
    top.eval();
    cycles++;
}

struct UartRxDriver {
    std::deque<uint8_t> pending;
    bool active = false;
    uint16_t frame = 0x03ff;
    int bit_index = 0;
    int cycles_left = 0;

    void enqueue_from_fd(int fd) {
        uint8_t buf[512];
        while (true) {
            ssize_t n = read(fd, buf, sizeof(buf));
            if (n > 0) {
                for (ssize_t i = 0; i < n; i++) {
                    pending.push_back(buf[i]);
                }
                continue;
            }
            if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EIO)) {
                return;
            }
            if (n == 0) {
                return;
            }
            if (errno == EINTR) {
                continue;
            }
            std::perror("read pty");
            keep_running = 0;
            return;
        }
    }

    void drive(Vtop &top) {
        if (!active && !pending.empty()) {
            uint8_t byte = pending.front();
            pending.pop_front();
            frame = static_cast<uint16_t>((1u << 9) | (static_cast<uint16_t>(byte) << 1));
            active = true;
            bit_index = 0;
            cycles_left = CLKS_PER_BIT;
        }

        if (!active) {
            top.uart_rx_pin = 1;
            return;
        }

        top.uart_rx_pin = (frame >> bit_index) & 1u;
        cycles_left--;
        if (cycles_left <= 0) {
            bit_index++;
            cycles_left = CLKS_PER_BIT;
            if (bit_index >= 10) {
                active = false;
                top.uart_rx_pin = 1;
            }
        }
    }
};

struct UartTxMonitor {
    enum class State {
        Idle,
        Data,
        Stop
    };

    State state = State::Idle;
    int cycles_left = 0;
    int bit_index = 0;
    uint8_t byte = 0;

    void sample(Vtop &top, int fd) {
        int tx = top.uart_tx_pin ? 1 : 0;
        switch (state) {
        case State::Idle:
            if (tx == 0) {
                state = State::Data;
                cycles_left = CLKS_PER_BIT + (CLKS_PER_BIT / 2);
                bit_index = 0;
                byte = 0;
            }
            break;
        case State::Data:
            cycles_left--;
            if (cycles_left <= 0) {
                if (tx) {
                    byte |= static_cast<uint8_t>(1u << bit_index);
                }
                bit_index++;
                cycles_left = CLKS_PER_BIT;
                if (bit_index >= 8) {
                    state = State::Stop;
                }
            }
            break;
        case State::Stop:
            cycles_left--;
            if (cycles_left <= 0) {
                ssize_t n = write(fd, &byte, 1);
                if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR && errno != EIO) {
                    std::perror("write pty");
                    keep_running = 0;
                }
                state = State::Idle;
            }
            break;
        }
    }
};

} // namespace

int main(int argc, char **argv) {
    Verilated::commandArgs(argc, argv);
    std::signal(SIGINT, handle_signal);
    std::signal(SIGTERM, handle_signal);

    int master_fd = -1;
    std::string slave_name = open_pty(master_fd);
    std::printf("rtl_fpga_pty=%s\n", slave_name.c_str());
    std::fflush(stdout);

    Vtop top;
    top.clk = 0;
    top.uart_rx_pin = 1;
    top.eval();

    uint64_t cycles = 0;
    UartRxDriver rx_driver;
    UartTxMonitor tx_monitor;

    for (int i = 0; i < 32 && keep_running; i++) {
        tick(top, cycles);
    }

    while (keep_running && !Verilated::gotFinish()) {
        rx_driver.enqueue_from_fd(master_fd);
        rx_driver.drive(top);
        tick(top, cycles);
        tx_monitor.sample(top, master_fd);
    }

    top.final();
    close(master_fd);
    return 0;
}
