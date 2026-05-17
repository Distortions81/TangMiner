#pragma once

#include <stddef.h>
#include <stdint.h>

void stratum_sha256(const uint8_t* data, size_t len, uint8_t out[32]);
void stratum_sha256d(const uint8_t* data, size_t len, uint8_t out[32]);
void stratum_sha256_midstate(const uint8_t block[64], uint8_t out[32]);
