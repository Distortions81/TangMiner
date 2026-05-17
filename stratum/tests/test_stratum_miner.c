#include "stratum_miner.h"
#include "stratum_sha256.h"

#include <stdio.h>
#include <string.h>

static int failures;

static void check(int condition, const char* message) {
    if (!condition) {
        fprintf(stderr, "FAIL: %s\n", message);
        ++failures;
    }
}

static void to_hex(const uint8_t* data, size_t len, char* out, size_t out_size) {
    static const char digits[] = "0123456789abcdef";
    if (out_size < len * 2U + 1U) {
        return;
    }
    for (size_t i = 0; i < len; ++i) {
        out[i * 2U] = digits[data[i] >> 4];
        out[i * 2U + 1U] = digits[data[i] & 0x0f];
    }
    out[len * 2U] = '\0';
}

static void test_sha256d_genesis_nonce_zero(void) {
    const uint8_t header[80] = {
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x3b, 0xa3, 0xed, 0xfd, 0x7a, 0x7b, 0x12, 0xb2,
        0x7a, 0xc7, 0x2c, 0x3e, 0x67, 0x76, 0x8f, 0x61,
        0x7f, 0xc8, 0x1b, 0xc3, 0x88, 0x8a, 0x51, 0x32,
        0x3a, 0x9f, 0xb8, 0xaa, 0x4b, 0x1e, 0x5e, 0x4a,
        0x29, 0xab, 0x5f, 0x49,
        0xff, 0xff, 0x00, 0x1d,
        0x00, 0x00, 0x00, 0x00,
    };
    uint8_t hash[32];
    char hash_hex[65];
    stratum_sha256d(header, sizeof(header), hash);
    to_hex(hash, sizeof(hash), hash_hex, sizeof(hash_hex));
    check(strcmp(hash_hex, "bf483998a9b44cbf5a113973e34da96b5cf3c7757d75ac3bd7c6b30af5a7c12b") == 0,
          "genesis nonce-zero sha256d");
}

static void test_work_builder_packet_and_validation(void) {
    tangminer_builder_t builder;
    tangminer_work_t work;
    stratum_notify_t notify;
    uint8_t hash[32];
    char hash_hex[65];
    bool meets_share = false;
    bool meets_block = false;
    memset(&notify, 0, sizeof(notify));
    strcpy(notify.job_id, "job1");
    strcpy(notify.prev_hash, "0000000000000000000000000000000000000000000000000000000000000001");
    strcpy(notify.coinbase1, "0100000001");
    strcpy(notify.coinbase2, "ffffffff");
    strcpy(notify.version, "20000000");
    strcpy(notify.nbits, "207fffff");
    strcpy(notify.ntime, "65abcdef");
    notify.clean_jobs = true;

    tangminer_builder_init(&builder);
    check(tangminer_builder_set_extranonce(&builder, "abcd", 4), "set extranonce");
    tangminer_builder_set_difficulty(&builder, 0.00000001);
    check(tangminer_builder_set_fpga_target(&builder, "all-ones"), "set fpga target");
    check(tangminer_build_work(&builder, &notify, &work), "build work");
    check(work.packet[0] == 'T' && work.packet[1] == 'N' && work.packet[2] == 'J', "packet command");
    check(strcmp(work.extranonce2_hex, "01000000") == 0, "first extranonce2 value");
    check(work.packet[47] == 0xff && work.packet[78] == 0xff, "all-ones FPGA target");

    check(tangminer_validate_nonce(&work, 0, hash, &meets_share, &meets_block), "validate nonce");
    to_hex(hash, sizeof(hash), hash_hex, sizeof(hash_hex));
    check(strcmp(hash_hex, "96718e6a1d83d5669d30624c3d5dfc41a0445ab519d71a8d0d60218d3ba352e0") == 0,
          "candidate hash fixture");
    check(!meets_share, "candidate does not meet mock share target");
    check(!meets_block, "candidate does not meet mock block target");
}

int main(void) {
    test_sha256d_genesis_nonce_zero();
    test_work_builder_packet_and_validation();
    return failures == 0 ? 0 : 1;
}
