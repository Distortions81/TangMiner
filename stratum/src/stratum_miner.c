#include "stratum_miner.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "stratum_sha256.h"

static int hex_value(char ch) {
    if (ch >= '0' && ch <= '9') {
        return ch - '0';
    }
    if (ch >= 'a' && ch <= 'f') {
        return ch - 'a' + 10;
    }
    if (ch >= 'A' && ch <= 'F') {
        return ch - 'A' + 10;
    }
    return -1;
}

static bool hex_to_bytes(const char* hex, uint8_t* out, size_t out_size, size_t* out_len) {
    const size_t len = strlen(hex);
    if ((len & 1U) != 0 || len / 2U > out_size) {
        return false;
    }
    for (size_t i = 0; i < len; i += 2) {
        const int hi = hex_value(hex[i]);
        const int lo = hex_value(hex[i + 1]);
        if (hi < 0 || lo < 0) {
            return false;
        }
        out[i / 2U] = (uint8_t)((hi << 4) | lo);
    }
    if (out_len != NULL) {
        *out_len = len / 2U;
    }
    return true;
}

static bool hex_to_reversed(const char* hex, uint8_t* out, size_t out_len) {
    const size_t len = strlen(hex);
    if (len != out_len * 2U) {
        return false;
    }
    for (size_t i = 0; i < out_len; ++i) {
        const size_t offset = (out_len - 1U - i) * 2U;
        const int hi = hex_value(hex[offset]);
        const int lo = hex_value(hex[offset + 1]);
        if (hi < 0 || lo < 0) {
            return false;
        }
        out[i] = (uint8_t)((hi << 4) | lo);
    }
    return true;
}

static bool hex_to_word_swapped32(const char* hex, uint8_t* out) {
    if (strlen(hex) != 64) {
        return false;
    }
    for (size_t word = 0; word < 8; ++word) {
        const size_t in = word * 8U;
        const size_t offset = word * 4U;
        for (size_t byte = 0; byte < 4; ++byte) {
            const size_t hex_offset = in + (3U - byte) * 2U;
            const int hi = hex_value(hex[hex_offset]);
            const int lo = hex_value(hex[hex_offset + 1]);
            if (hi < 0 || lo < 0) {
                return false;
            }
            out[offset + byte] = (uint8_t)((hi << 4) | lo);
        }
    }
    return true;
}

static bool target_mul_u64(uint8_t target[32], uint64_t factor) {
    uint64_t carry = 0;
    for (int i = 31; i >= 0; --i) {
        const uint64_t value = (uint64_t)target[i] * factor + carry;
        target[i] = (uint8_t)value;
        carry = value >> 8;
    }
    if (carry != 0) {
        memset(target, 0xff, 32);
        return false;
    }
    return true;
}

static void target_div_u64(uint8_t target[32], uint64_t divisor) {
    uint64_t remainder = 0;
    for (size_t i = 0; i < 32; ++i) {
        const uint64_t value = (remainder << 8) | target[i];
        target[i] = (uint8_t)(value / divisor);
        remainder = value % divisor;
    }
}

static void target_add(uint8_t target[32], const uint8_t addend[32]) {
    uint16_t carry = 0;
    for (int i = 31; i >= 0; --i) {
        const uint16_t value = (uint16_t)target[i] + addend[i] + carry;
        target[i] = (uint8_t)value;
        carry = value >> 8;
    }
    if (carry != 0) {
        memset(target, 0xff, 32);
    }
}

static void build_share_target(uint8_t target[32], double difficulty) {
    static const uint8_t diff1_target[32] = {
        0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    memcpy(target, diff1_target, sizeof(diff1_target));
    if (!(difficulty > 0.0)) {
        return;
    }
    const uint64_t scale = 100000000ULL;
    const uint64_t numerator = (uint64_t)(difficulty * (double)scale + 0.5);
    if (numerator == 0) {
        return;
    }
    if (scale >= numerator) {
        const uint64_t factor = scale / numerator;
        const uint64_t remainder = scale % numerator;
        uint8_t rem[32];
        memcpy(rem, target, sizeof(rem));
        if (!target_mul_u64(target, factor)) {
            return;
        }
        if (remainder != 0 && target_mul_u64(rem, remainder)) {
            target_div_u64(rem, numerator);
            target_add(target, rem);
        }
    } else {
        target_div_u64(target, numerator);
        target_mul_u64(target, scale);
    }
}

bool tangminer_builder_set_fpga_target(tangminer_builder_t* builder, const char* name) {
    if (strcmp(name, "all-ones") == 0 || strcmp(name, "easy") == 0) {
        memset(builder->fpga_target, 0xff, 32);
    } else if (strcmp(name, "quick3") == 0) {
        memset(builder->fpga_target, 0xff, 32);
        builder->fpga_target[0] = 0x1f;
    } else if (strcmp(name, "quick21") == 0) {
        memset(builder->fpga_target, 0xff, 32);
        builder->fpga_target[0] = 0x00;
        builder->fpga_target[1] = 0x00;
        builder->fpga_target[2] = 0x07;
    } else if (strcmp(name, "quick23") == 0) {
        memset(builder->fpga_target, 0xff, 32);
        builder->fpga_target[0] = 0x00;
        builder->fpga_target[1] = 0x00;
        builder->fpga_target[2] = 0x01;
    } else if (strcmp(name, "quick26") == 0) {
        memset(builder->fpga_target, 0xff, 32);
        builder->fpga_target[0] = 0x00;
        builder->fpga_target[1] = 0x00;
        builder->fpga_target[2] = 0x00;
        builder->fpga_target[3] = 0x3f;
    } else if (strcmp(name, "share") == 0) {
        memset(builder->fpga_target, 0, 32);
    } else {
        return false;
    }
    return true;
}

static bool build_block_target(const char* nbits, uint8_t target[32]) {
    uint8_t compact[4];
    size_t len = 0;
    memset(target, 0, 32);
    if (!hex_to_bytes(nbits, compact, sizeof(compact), &len) || len != 4) {
        return false;
    }
    const uint8_t exponent = compact[0];
    uint32_t mantissa = ((uint32_t)compact[1] << 16) | ((uint32_t)compact[2] << 8) | compact[3];
    if (mantissa == 0 || (compact[1] & 0x80U) != 0 || exponent > 32U) {
        return false;
    }
    if (exponent <= 3U) {
        mantissa >>= 8U * (3U - exponent);
        for (size_t i = 0; i < 3; ++i) {
            target[31U - i] = (uint8_t)(mantissa >> (8U * i));
        }
    } else {
        const size_t index = 32U - exponent;
        target[index] = compact[1];
        target[index + 1U] = compact[2];
        target[index + 2U] = compact[3];
    }
    return true;
}

static bool hash_meets_target(const uint8_t hash[32], const uint8_t target[32]) {
    for (size_t i = 0; i < 32; ++i) {
        const uint8_t hash_be = hash[31U - i];
        if (hash_be < target[i]) {
            return true;
        }
        if (hash_be > target[i]) {
            return false;
        }
    }
    return true;
}

static void bytes_to_hex(const uint8_t* data, size_t len, char* out, size_t out_size) {
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

static void extranonce2_increment(uint8_t* bytes, uint32_t len) {
    for (uint32_t i = 0; i < len && i < TANGMINER_MAX_EXTRANONCE2_LEN; ++i) {
        ++bytes[i];
        if (bytes[i] != 0) {
            break;
        }
    }
}

void tangminer_builder_init(tangminer_builder_t* builder) {
    memset(builder, 0, sizeof(*builder));
    builder->difficulty = 1.0;
    (void)tangminer_builder_set_fpga_target(builder, "quick23");
}

bool tangminer_builder_set_extranonce(
    tangminer_builder_t* builder, const char* extranonce1_hex, uint32_t extranonce2_size) {
    if (extranonce2_size == 0 || extranonce2_size > TANGMINER_MAX_EXTRANONCE2_LEN ||
        strlen(extranonce1_hex) >= sizeof(builder->extranonce1_hex)) {
        return false;
    }
    size_t len = 0;
    if (!hex_to_bytes(extranonce1_hex, builder->extranonce1, sizeof(builder->extranonce1), &len)) {
        return false;
    }
    strcpy(builder->extranonce1_hex, extranonce1_hex);
    builder->extranonce1_len = len;
    builder->extranonce2_size = extranonce2_size;
    memset(builder->extranonce2, 0, sizeof(builder->extranonce2));
    return true;
}

void tangminer_builder_set_difficulty(tangminer_builder_t* builder, double difficulty) {
    if (difficulty > 0.0) {
        builder->difficulty = difficulty;
    }
}

bool tangminer_build_work(
    tangminer_builder_t* builder, const stratum_notify_t* notify, tangminer_work_t* out) {
    uint8_t coinbase[2048];
    size_t offset = 0;
    uint8_t merkle[32];
    uint8_t midstate[32];
    memset(out, 0, sizeof(*out));
    if (builder->extranonce2_size == 0) {
        return false;
    }
    extranonce2_increment(builder->extranonce2, builder->extranonce2_size);
    if (!hex_to_bytes(notify->coinbase1, coinbase + offset, sizeof(coinbase) - offset, &offset)) {
        return false;
    }
    if (offset + builder->extranonce1_len + builder->extranonce2_size > sizeof(coinbase)) {
        return false;
    }
    memcpy(coinbase + offset, builder->extranonce1, builder->extranonce1_len);
    offset += builder->extranonce1_len;
    memcpy(coinbase + offset, builder->extranonce2, builder->extranonce2_size);
    offset += builder->extranonce2_size;
    size_t part_len = 0;
    if (!hex_to_bytes(notify->coinbase2, coinbase + offset, sizeof(coinbase) - offset, &part_len)) {
        return false;
    }
    offset += part_len;
    stratum_sha256d(coinbase, offset, merkle);
    for (size_t i = 0; i < notify->merkle_branch_count; ++i) {
        uint8_t combined[64];
        memcpy(combined, merkle, 32);
        if (!hex_to_bytes(notify->merkle_branches[i], combined + 32, 32, &part_len) || part_len != 32) {
            return false;
        }
        stratum_sha256d(combined, sizeof(combined), merkle);
    }
    if (!hex_to_reversed(notify->version, out->header, 4) ||
        !hex_to_word_swapped32(notify->prev_hash, out->header + 4) ||
        !hex_to_reversed(notify->ntime, out->header + 68, 4) ||
        !hex_to_reversed(notify->nbits, out->header + 72, 4) ||
        !build_block_target(notify->nbits, out->block_target)) {
        return false;
    }
    memcpy(out->header + 36, merkle, 32);
    build_share_target(out->share_target, builder->difficulty);
    stratum_sha256_midstate(out->header, midstate);
    out->packet[0] = 'T';
    out->packet[1] = 'N';
    out->packet[2] = 'J';
    memcpy(out->packet + 3, midstate, 32);
    memcpy(out->packet + 35, out->header + 64, 12);
    memcpy(out->packet + 47,
           builder->fpga_target[0] || builder->fpga_target[1] || builder->fpga_target[2] ||
                   builder->fpga_target[3]
               ? builder->fpga_target
               : out->share_target,
           32);
    snprintf(out->job_id, sizeof(out->job_id), "%s", notify->job_id);
    snprintf(out->ntime, sizeof(out->ntime), "%s", notify->ntime);
    bytes_to_hex(builder->extranonce2, builder->extranonce2_size, out->extranonce2_hex, sizeof(out->extranonce2_hex));
    ++builder->jobs_built;
    return true;
}

bool tangminer_validate_nonce(
    const tangminer_work_t* work, uint32_t nonce, uint8_t hash[32], bool* meets_share, bool* meets_block) {
    uint8_t header[80];
    memcpy(header, work->header, sizeof(header));
    header[76] = (uint8_t)(nonce >> 24);
    header[77] = (uint8_t)(nonce >> 16);
    header[78] = (uint8_t)(nonce >> 8);
    header[79] = (uint8_t)nonce;
    stratum_sha256d(header, sizeof(header), hash);
    *meets_share = hash_meets_target(hash, work->share_target);
    *meets_block = hash_meets_target(hash, work->block_target);
    return true;
}

void tangminer_nonce_to_hex(uint32_t nonce, char out[9]) {
    snprintf(out, 9, "%08x", nonce);
}
