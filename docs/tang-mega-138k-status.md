# Tang Mega 138K Bring-up Status

Status as of 2026-07-18: a retained pre-commit 16-lane iterative artifact passes
strict hardware validation, but rebuilding the same nominal configuration from
current RTL fails hardware validation. There is not yet a reproducible selected
138K image from the current source tree.

## 16-lane Iterative Experiment

The experiment uses:

```text
TARGET=tangmega138k
SPINAL_LANES=16
SPINAL_CLOCK_PROFILE=50m
SPINAL_FULLY_UNROLLED=0
SPINAL_ROUND_SKIP=1
SPINAL_HOST_ROUND_SKIP=1
SPINAL_FIXED_CANDIDATE=2
```

Each lane starts a nonce every 61 clocks, giving a modeled aggregate rate of
`50,000,000 * 16 / 61 = 13.115 MH/s`.

### Retained pre-commit artifact

`build/gowin/tangminer_mega138k_iterative16_50m_p0r2` was generated immediately
before commit `12e2b41`. It closes at 53.763 MHz and passed 100/100 strict
`quick21` jobs after SRAM loading. Every returned nonce met the requested
target when recomputed by the host, with a valid `TNC` counter response for
every job.

This is useful hardware evidence, but it is not reproducible from the current
RTL and must not be treated as the default build.

### Current-RTL rebuild

The current source was rebuilt with placer 3, router 2, clock-route order 0,
hold correction enabled, and resource replication disabled. Gowin completed
placement, routing, timing analysis, and bitstream generation.

| Result | Value |
| --- | ---: |
| Actual Fmax | 57.601 MHz |
| Setup-violated endpoints | 0 |
| Hold-violated endpoints | 0 |
| Logic | 53,978 / 138,240 (40%) |
| Registers | 28,829 / 139,095 (21%) |
| CLS | 31,037 / 69,120 (45%) |

Despite clean static timing and passing strict RTL simulation, the SRAM-loaded
image returned 88 false-positive candidates in 88 observed `quick21` jobs. It
is hardware-rejected.

The rebuilt mapping also differs materially from the retained valid artifact:
the valid artifact used 43,552 logic cells and 256 RAM16 primitives, while the
current rebuild uses 53,978 logic cells and no RAM16 primitives. That mapping
change is a concrete lead for the hardware failure.

## Fully Unrolled Experiment

The fully unrolled SHA-256d path remains bit-exact in direct simulation. It has
a 124-cycle result latency and accepts one nonce per clock per pipeline.
One-pipeline Gowin experiments remain routing-congestion limited, so it is not a
usable hardware image.

## Next Steps

1. Compare the retained valid netlist and current rejected netlist around the
   iterative message-schedule storage and RAM16 inference.
2. Reproduce the old 256-RAM16 mapping explicitly, then rerun strict simulation,
   route, and a short hardware check.
3. Require zero timing violations and 100/100 strict `quick21` hardware jobs
   before changing the 138K defaults.
4. Keep the fully unrolled path experimental until it routes cleanly.

Static timing closure is not hardware validation. Do not flash a new 138K image
persistently until strict host nonce verification passes.
