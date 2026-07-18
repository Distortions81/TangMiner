# Tang Mega 138K Bring-up Status

Status as of 2026-07-18: the selected reproducible 138K build is an iterative
28-lane design at 50 MHz. It is hardware-validated from SRAM with 100/100
strict `quick21` jobs and is now the target default.

## Selected 28-lane Iterative Default

```text
TARGET=tangmega138k
SPINAL_LANES=28
SPINAL_CLOCK_PROFILE=50m
SPINAL_FULLY_UNROLLED=0
SPINAL_SHARED_K=0
SPINAL_REGISTER_PASS_OUTPUTS=1
SPINAL_MINIMIZE_SHA_RESET=1
SPINAL_ROUND_SKIP=0
SPINAL_HOST_ROUND_SKIP=0
SPINAL_FIXED_CANDIDATE=2
GOWIN_PLACE_OPTION=3
GOWIN_ROUTE_OPTION=2
GOWIN_CLOCK_ROUTE_ORDER=0
GOWIN_CORRECT_HOLD=1
GOWIN_REPLICATE_RESOURCES=0
```

Each lane starts a nonce every 65 clocks, for a modeled aggregate rate of
`50,000,000 * 28 / 65 = 21.538 MH/s`. The routed image closes at 50.124 MHz
with no setup or hold violations and uses 76,221 logic cells, 50,881 registers,
47,521 CLS, and 448 RAM16 primitives.

The FPGA’s 115200-baud UART was strictly validated with 100/100 `quick21`
jobs. Every returned nonce met the host-recomputed target. Faster UART images
remain experimental until they respond and pass the same test reliably.

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
