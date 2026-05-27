# Optimization Matrix

Snapshot date: 2026-05-26.

This note summarizes the optimization branches and local build artifacts that
exist in this checkout. It keeps historical sweep data because that explains
the current defaults. The active branch graph is:

- `main` / `origin/main`: selected hardware-validated 5-lane 20K design at
  `54.000 MHz`, built with `synth_gowin -nowidelut` and `NEXTPNR_SEED=13`.
- `width-exp` / `origin/width-exp`: experimental 61-cycle round skipping and
  wider local A/B compressor-pair lanes.
- `origin/sram-optimize`: experimental SHA message schedule storage using
  distributed LUT RAM.

No `AGENTS.md` or `agents.md` file exists inside this repository at the time of
this snapshot.

## 2026-05-26 Hardware Progress

USB/JTAG/UART are working again on the Tang Nano 20K. The host sees the Sipeed
FTDI debugger as JTAG on `/dev/ttyUSB0` and UART on `/dev/ttyUSB1`; SRAM loads
use:

```sh
local/oss-cad-suite/bin/openFPGALoader \
  --ftdi-channel 0 --freq 2000000 \
  -b tangnano20k \
  <bitstream.fs>
```

Hash validity is checked by sending `quick21` jobs and recomputing each returned
nonce on the host. `scripts/tools/serial_smoke.py --require-target` now exits
non-zero when any returned nonce fails the host target check, which makes these
hardware checks scriptable.

The important result is that static timing is not predictive above the validated
boundary. Several images that routed with comfortable reported Fmax returned bad
hashes on hardware.

| Variant | Static result | Hardware result | Evidence |
| --- | --- | --- | --- |
| 5 lanes, 111 MHz, seed 6 | Pass, 117.67 MHz | Invalid quick21; Stratum candidates did not meet share/block target | `build/seed-sweep-prod5-fast/lanes5_111m/seed6` |
| 4 lanes, 111 MHz | Pass in historical sweeps | 5/5 invalid | serial quick21 run |
| 1 lane, 111 MHz | Build/load OK | 5/5 invalid | serial quick21 run |
| 4 lanes, 100.286 MHz | Build/load OK | 5/5 invalid | serial quick21 run |
| 1 lane, 100.286 MHz | Build/load OK | 3/3 invalid | serial quick21 run |
| 4 lanes, 90 MHz, seed 13 | Pass, 107.90 MHz | 10/10 invalid | `build/hw-verify-prod4-90m-seed13` |
| 2 lanes, 90 MHz, seed 13 | Pass, 121.37 MHz | 10/10 invalid | `build/hw-verify-prod2-90m-seed13` |
| 2 lanes, 90 MHz, 16-cycle lane start stagger, seed 13 | Pass, 114.05 MHz | 10/10 invalid | `build/hw-prod2-90m-stagger16-seed13` |
| 2 lanes, 85.5 MHz, seed 13 | Pass, 118.85 MHz | 5/10 valid, 5/10 invalid | `build/hw-prod2-85m5-seed13` |
| 2 lanes, 84 MHz, seed 13 | Pass, 119.35 MHz | 8/10 valid, 2/10 invalid | `build/hw-prod2-84m-seed13` |
| 1 lane, 90 MHz | Build/load OK | Valid quick21 control | serial quick21 run |
| 1 lane, 120 MHz, two-cycle round + registered pass outputs, seed 13 | Pass, 131.60 MHz | 10/10 valid | `build/hw-prod1-120m-2cycle-regpass-seed13` |
| 1 lane, 123 MHz, two-cycle round + registered pass outputs, seed 13 | Pass, 153.66 MHz | 50/50 valid | `build/hw-prod1-123m-2cycle-regpass-seed13` |
| 1 lane, 124.875 MHz, two-cycle round + registered pass outputs, seed 13 | Pass, 134.19 MHz | 50/50 valid, then reloaded and rechecked 10/10 valid | `build/hw-prod1-124m875-2cycle-regpass-seed13` |
| 1 lane, 126 MHz, two-cycle round + registered pass outputs, seed 13 | Pass, 142.37 MHz | 9/10 valid; 1 false positive | `build/hw-prod1-126m-2cycle-regpass-seed13` |
| 1 lane, 126 MHz, two-cycle round + registered pass outputs + registered round constant, seed 13 | Pass, 153.07 MHz | 0/10 valid | `build/hw-prod1-126m-2cycle-regpass-regk-seed13` |
| 1 lane, 126 MHz, two-cycle round + registered pass outputs + minimized SHA reset fanout, seed 13 | Pass, 129.85 MHz | 49/50 valid; 1 false positive | `build/hw-prod1-126m-2cycle-regpass-minreset-seed13` |
| 1 lane, 135 MHz, two-cycle round + registered pass outputs, seed 13 | Pass, 148.68 MHz | 0/10 valid | `build/hw-prod1-135m-2cycle-regpass-seed13` |
| 1 lane, 135 MHz, three-cycle round + registered pass outputs, seed 13 | Pass, 141.14 MHz | 50/50 valid | `build/hw-prod1-135m-3cycle-regpass-seed13` |
| 1 lane, 150 MHz, three-cycle round + registered pass outputs, seed 13 | Pass, 154.08 MHz | 3/10 valid; 7 false positives | `build/hw-prod1-150m-3cycle-regpass-seed13` |
| 1 lane, 81 MHz split SHA/control clocks, two-cycle round + registered pass outputs, seed 13 | Pass, SHA Fmax 131.32 MHz | 10/10 valid | `build/hw-prod1-81m-splitsha-2cycle-regpass-seed13` |
| 1 lane, 124.875 MHz split SHA/control clocks, two-cycle round + registered pass outputs, seed 13 | Pass, SHA Fmax 131.32 MHz | 10/10 invalid | `build/hw-prod1-124m875-splitsha-2cycle-regpass-seed13` |
| 1 lane, 126 MHz split SHA/control clocks, two-cycle round + registered pass outputs, seed 13 | Pass, SHA Fmax 131.32 MHz | 10/10 invalid | `build/hw-prod1-126m-splitsha-2cycle-regpass-seed13` |
| 2 lanes, 81 MHz, seed 13 | Pass, 112.56 MHz | Earlier short runs passed, but strict 50-job retest returned 47/50 valid and 3 false positives. Invalidated. | `build/hw-verify-prod2-81m-seed13` |
| 2 lanes, 67.5 MHz, seed 13 | Pass, 103.78 MHz | 50/50 strict quick21 valid | `build/hw-prod2-67m5-seed13` |
| 3 lanes, 54 MHz, seed 13 | Pass, 106.86 MHz after fixing the `54m` PLL profile | 50/50 strict quick21 valid | `build/hw-prod3-54m-seed13` |
| 4 lanes, 54 MHz, seed 13 | Pass, 108.28 MHz; placement reported 61.59 MHz before routing | 50/50 strict quick21 valid | `build/hw-prod4-54m-seed13` |
| 5 lanes, 54 MHz, seed 13 | Synthesis reached 77% LUT4 / 61% DFF, but placement did not advance beyond the first reported iteration in a practical run and was stopped. Direct seeds 1/2/3/4/6/10, plus seed-4 `--no-tmdriv` and `--placer-heap-beta 1.0`, all failed legal placement. | Not flashed; no bitstream | `build/seed-sweep-prod5-54m` |
| 5 lanes, 54 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 123.92 MHz; placement reported 72.26 MHz before routing; utilization 72% LUT4 / 61% DFF / 20% ALU | 50/50 and 100/100 strict quick21 valid; board currently loaded with this image | `build/hw-prod5-54m-nowidelut-seed13` |
| 5 lanes, 57 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 121.37 MHz; utilization 72% LUT4 / 61% DFF / 20% ALU | 41/50 strict quick21 valid; 9 false positives. Invalidated. | `build/hw-prod5-57m-nowidelut-seed13` |
| 5 lanes, 58.5 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 119.88 MHz; utilization 72% LUT4 / 61% DFF / 20% ALU | 37/50 strict quick21 valid; 13 false positives. Invalidated. | `build/hw-prod5-58m5-nowidelut-seed13` |
| 5 lanes, 60.75 MHz, seed 13, `synth_gowin -nowidelut` | Failed legal placement at 72% LUT4 / 61% DFF / 20% ALU; direct seed 4 was stopped after spending several minutes in legalisation with no progress. | Not flashed; no bitstream | `build/attempt-logs/prod5-60m75-nowidelut-seed13.log`, `build/seed-sweep-prod5-60m75-nowidelut/seed4` |
| 5 lanes, 67.5 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 102.29 MHz; placement reported 67.40 MHz before routing; utilization 72% LUT4 / 61% DFF / 20% ALU | 0/50 strict quick21 valid; 50 false positives. Invalidated. | `build/hw-prod5-67m5-nowidelut-seed13` |
| 6 lanes, 54 MHz, seed 13, `synth_gowin -nowidelut` | Failed legal placement at 86% LUT4 / 72% DFF / 24% ALU | Not flashed; no bitstream | `build/attempt-logs/prod6-54m-nowidelut-seed13.log` |
| 2 lanes, 27 MHz, no PLL, seed 13 | Pass, 121.20 MHz | 5/5 valid | `build/hw-verify-prod2-27m-seed13` |

The `54m` PLL profile was also corrected during this pass. The previous profile
used `ODIV_SEL=8`, which made a 432 MHz VCO and failed `gowin_pack` on GW2AR-18.
Changing only `ODIV_SEL` to `16` keeps the output at 54.000 MHz and raises VCO
to 864 MHz, inside the 500-1250 MHz device range.

The current production-trimmed multi-lane hardware boundary is therefore no
longer `2x81`; the stricter 50-job check invalidated it. The best validated
multi-lane point measured in this pass is now `5x54` with `synth_gowin
-nowidelut`, modeled at `5 * 54 / 64 = 4.219 MH/s`. `4x54` also passed and
models at 3.375 MH/s, `3x54` passed and models at 2.531 MH/s, and `2x67.5`
passed and models at 2.109 MH/s. More lanes do help when the clock is kept in a
conservative timing region, but the normal 5-lane 54 MHz seed-13 netlist was
still placement-limited. Avoiding wide LUT packing reduced packed LUT4 use from
77% to 72% and made the 5-lane 54 MHz image place, route, and pass hardware
hash validation. Follow-up 5-lane `-nowidelut` clock steps at 57 MHz, 58.5 MHz,
and 67.5 MHz all routed with strong static Fmax but returned false positives on
hardware, while 60.75 MHz did not legally place with the seeds tried. There is
no clean `CLKOUT` PLL profile between 54 MHz and 57 MHz that keeps the 27 MHz
PFD at or above the 3.0 MHz device limit. A sixth lane at 54 MHz reached 86%
LUT4 / 72% DFF and failed legal placement. The previous `5x100.286` and `5x111`
static candidates must not be treated as hardware-valid.

The current single-lane structural probe boundary is
`1x124.875` valid for 50 strict quick21 jobs at the 130-cycle two-cycle cadence,
with `1x126` still showing false positives after both K-prefetch and reset-fanout
experiments. A three-cycle round reaches a valid `1x135`, but at 194 cycles/nonce
it is slower than `1x124.875` two-cycle in hashes per second. Splitting
UART/control back to the 27 MHz input clock does not raise that boundary; the
split-clock image works at 81 MHz but is invalid at 124.875 MHz and 126 MHz.

Synchronization/timing-fence experiments on 2026-05-26:

| Experiment | Result |
| --- | --- |
| Top-level one-cycle inter-pass digest staging | Cocotb passed, but `2x90` stayed invalid and `2x81` became intermittent. Reverted. |
| Registered compressor-output `done` fence | Cocotb passed, but `2x81` produced a false positive in a 10-job quick21 run. Reverted. |
| `SPINAL_SHARED_K=0` at `2x90` | Routed at 119.05 MHz, but hardware returned 10/10 invalid quick21 candidates. Not a fix. |
| `SPINAL_LANE_START_STAGGER=16` at `2x90` | Cocotb passed and route passed at 114.05 MHz, but hardware returned 10/10 invalid candidates. De-phasing lane start is not enough. |
| `synth_gowin -noalu` at `2x90` | Not viable: placement reported only 65.41 MHz before routing, so the build was stopped and not flashed. |

Single-lane structural timing experiments on 2026-05-26:

| Experiment | Result |
| --- | --- |
| Registered compressor pass-output fence only | Cocotb passed at 66 cycles/nonce, but `1x111` failed route timing at 104.68 MHz and `1x100.286` failed at 96.61 MHz. This fence alone is not a speed fix. |
| Two-cycle SHA round plus registered pass outputs | Cocotb passed at 130 cycles/nonce. `1x150` failed route timing at 138.70 MHz. `1x135` passed static timing but returned 10/10 invalid quick21 candidates. `1x126` passed static timing but returned 1 false positive in 10 jobs. `1x124.875` and `1x123` both passed 50/50 strict quick21 jobs. |
| `125m18` PLL candidate | Routed with a 152.79 MHz reported Fmax, but `gowin_pack` rejected it because `PFD = 27 MHz / (10 + 1) = 2.45 MHz`, below the 3.0 MHz device limit. Not a usable hardware profile. |
| Split SHA/control clocks | Implemented as an optional diagnostic using `StreamFifoCC` for job, stop, and found-nonce crossings. `1x81` passed 10/10 strict quick21 jobs, proving the split path can carry correct work. `1x124.875` and `1x126` both routed at 131.32 MHz reported SHA Fmax but returned 10/10 invalid candidates. Not a speed fix. |
| Registered round-constant prefetch | Moves K selection inside each compressor. Cocotb passed, and `1x126` routed at 153.07 MHz, but hardware returned 10/10 invalid quick21 candidates. Static timing improved while functional timing got worse, so this is not a fix. |
| Three-cycle SHA round | Splits `t1` and message-schedule arithmetic across prepare/sum/update phases. Cocotb passed at 194 cycles/nonce. `1x135` passed 50/50 strict quick21 jobs, while `1x150` still returned 7 false positives in 10 jobs. This is a useful diagnostic and a valid high-clock image, but it is not a throughput win: 135 MHz / 194 cycles is about 696 kH/s versus about 961 kH/s for 124.875 MHz / 130 cycles. |
| Minimized SHA reset fanout | Leaves datapath registers out of the explicit flush/reset tree; starts still load all datapath registers. Cocotb passed for two-cycle and three-cycle modes. `1x150` three-cycle failed static timing at 132.12 MHz because the pressure moved to clock-enable/control paths. `1x126` two-cycle routed at 129.85 MHz but still returned 1 false positive in 50 jobs. Not a verified speed fix. |

Conclusion: the problem does look timing/placement related, but not in a way
fixed by a simple cross-lane synchronizer, by de-sharing the round constant, or
by clocking control/UART separately from the SHA engine. Removing the SHA
datapath reset fanout also does not raise the verified throughput boundary.
The failing quick21 candidates are false positives: the FPGA reports nonces whose
host recomputed hashes do not meet the target. The critical path reports continue
to point into SHA round add/carry datapaths, and single-lane failures above the
validated boundary rule out cross-lane synchronization as the primary cause. The
likely flaw is intra-lane SHA datapath margin under real hardware conditions.
The new 5-lane 54 MHz `-nowidelut` image is the best hardware-validated
multi-lane point, but it does not change that diagnosis: it succeeds by reducing
packing and placement pressure at a conservative clock. Pushing the same 5-lane
shape to 57 MHz already produces false positives, and adding a sixth 54 MHz
lane does not legally place in the flat layout. Pushing throughput beyond this
should move toward a carry-save-style round datapath or another split that keeps
close to the 130-cycle cadence; the 194-cycle three-phase split fixes `135m`
correctness but gives away too much throughput.

## Current Selected Build

The current default build is:

```text
TARGET=tangnano20k
SPINAL_LANES=5
SPINAL_CLOCK_PROFILE=54m
SPINAL_ENABLE_ECHO=0
SPINAL_ENABLE_HARDCODED=0
SPINAL_FIXED_CANDIDATE=2
YOSYS_SYNTH_ARGS=-nowidelut
NEXTPNR_SEED=13
```

It models at `4.219 MH/s`. The relevant hardware evidence is the
`build/hw-prod5-54m-nowidelut-seed13` image, which passed both 50/50 and
100/100 strict `quick21` host nonce validation. Higher-frequency 5-lane static
candidates remain historical data only because hardware validation on
2026-05-26 invalidated them with false positives.

## Historical Main Baseline

The previous selected baseline was four top-level lanes, one A/B compressor
pair per lane, full 64-cycle nonce cadence, and the 111 MHz clock profile.

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 4 lanes, 90 MHz | Pass | 5.63 MH/s | 118.82 MHz | 32.0% | 65% | 52% | `build/sweep/lanes4_90m/build.log` |
| 4 lanes, 100.286 MHz | Pass | 6.27 MH/s | 116.90 MHz | 16.6% | 65% | 52% | `build/sweep/lanes4_100m286/build.log` |
| 4 lanes, 111 MHz | Pass | 6.94 MH/s | 119.13 MHz | 7.3% | 65% | 52% | `build/sweep/lanes4_111m/build.log` |
| 4 lanes, 120 MHz | Fail timing | 7.50 MH/s | 110.75 MHz | -7.7% | 65% | 52% | `build/sweep/lanes4_120m/build.log` |
| 5 lanes, 90/100.286/111/120 MHz | Fail placement | 7.03-9.38 MH/s | n/a | n/a | 80% | 63% | `build/sweep/lanes5_*` |
| 5 lanes, 100.286 MHz, `synth_gowin -nowidelut`, seed 13 | Fail placement | 7.84 MH/s | n/a | n/a | 85% | 63% | `build/attempt-logs/normal5-100m286-nowidelut-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -retime`, seed 13 | Stopped/no final result | 7.84 MH/s | n/a | n/a | 90% | 71% | `build/attempt-logs/normal5-100m286-retime-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noabc9`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 196% | 63% | `build/attempt-logs/normal5-100m286-noabc9-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -nodffe`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 118% | 63% | `build/attempt-logs/normal5-100m286-nodffe-seed13.log` |
| 5 lanes, 90 MHz, `synth_gowin -nowidelut`, seed 13 | Fail placement | 7.03 MH/s | n/a | n/a | 85% | 63% | `build/attempt-logs/normal5-90m-nowidelut-seed13.log` |

Historical takeaway: the 4-lane 111 MHz build was the best proven baseline in
the plain design. 120 MHz did not close timing, and a fifth plain lane did not
place even at lower clocks. The simple synthesis knobs that were worth trying on
production 5-lane builds did not rescue the normal untrimmed 5-lane build:
`-nowidelut` still failed placement even at 90 MHz, `-retime` increased area,
and disabling ABC9 or DFFE mapping badly overused LUT4s.

## Production Trimming

The `sweep-prod` and `sweep-prod5` artifacts appear to use production-oriented
trims such as no echo, no hardcoded smoke job, and fixed candidate filtering.

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 3 lanes, 111 MHz | Pass, below 5% preferred margin | 5.20 MH/s | 116.29 MHz | 4.8% | 44% | 39% | `build/sweep-prod/lanes3_111m/build.log` |
| 3 lanes, 120 MHz | Fail timing | 5.63 MH/s | 113.11 MHz | -5.7% | 43% | 39% | `build/sweep-prod/lanes3_120m/build.log` |
| 3 lanes, 135 MHz | Fail timing | 6.33 MH/s | 113.52 MHz | -15.9% | 44% | 39% | `build/sweep-prod/lanes3_135m/build.log` |
| 3 lanes, 150 MHz | Fail timing | 7.03 MH/s | 118.46 MHz | -21.0% | 44% | 39% | `build/sweep-prod/lanes3_150m/build.log` |
| 4 lanes, 111 MHz | Pass | 6.94 MH/s | 118.61 MHz | 6.9% | 58% | 50% | `build/sweep-prod/lanes4_111m/build.log` |
| 4 lanes, 120 MHz | Fail timing | 7.50 MH/s | 108.19 MHz | -9.8% | 58% | 50% | `build/sweep-prod/lanes4_120m/build.log` |
| 5 lanes, 90 MHz | Pass | 7.03 MH/s | 101.98 MHz | 13.3% | 73% | 61% | `build/sweep-prod5/lanes5_90m/build.log` |
| 5 lanes, 100.286 MHz | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/sweep-prod5/lanes5_100m286/build.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 4 | Pass | 7.84 MH/s | 104.98 MHz | 4.7% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed4.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 5 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed5.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 6 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed6.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 7 | Fail timing | 7.84 MH/s | 97.22 MHz | -3.1% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed7.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 8 | No final result | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed8.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 9 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed9.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 10 | Pass | 7.84 MH/s | 108.68 MHz | 8.4% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed10.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 11 | No final result | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed11.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 12 | Fail placement | 7.84 MH/s | n/a | n/a | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed12.log` |
| 5 lanes, 100.286 MHz, direct nextpnr seed 13 | Pass | 7.84 MH/s | 116.28 MHz | 15.9% | 72% | 61% | `build/attempt-logs/direct-prod5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, wide lane block, seeds 4/5/6/7/10/11/12/13 | Fail placement | 7.84 MH/s | n/a | n/a | 73% | 63% | `build/attempt-logs/wide5-100m286-seed*.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noflatten` | Fail pack | 7.84 MH/s | n/a | n/a | n/a | n/a | `build/attempt-logs/noflatten5-100m286-seed*.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noflatten -noiopads` | Fail pack | 7.84 MH/s | n/a | n/a | n/a | n/a | `build/attempt-logs/noflatten-noiopads5-100m286-seed*.log` |
| 5 lanes, 100.286 MHz, `keep_hierarchy` on `BitcoinHashCore` | Fail pack | 7.84 MH/s | n/a | n/a | n/a | n/a | `build/attempt-logs/keephier-core5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, staged top-only IO pads plus `keep_hierarchy` | Fail placement | 7.84 MH/s | n/a | n/a | 79% | 70% | `build/attempt-logs/staged-keephier-core5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -nowidelut`, seed 13 | Pass | 7.84 MH/s | 110.04 MHz | 9.7% | 72% | 61% | `build/attempt-logs/opt-nowidelut5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -retime`, seed 13 | Fail placement | 7.84 MH/s | n/a | n/a | 78% | 67% | `build/attempt-logs/opt-retime5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -noabc9`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 115% | 61% | `build/attempt-logs/opt-noabc9-5-100m286-seed13.log` |
| 5 lanes, 100.286 MHz, `synth_gowin -nodffe`, seed 13 | Fail placement/overuse | 7.84 MH/s | n/a | n/a | 111% | 61% | `build/attempt-logs/opt-nodffe5-100m286-seed13.log` |

Takeaway: production trimming reduces area enough for a 5-lane 90 MHz build,
which slightly beats 4 lanes at 111 MHz in modeled rate. A direct nextpnr seed
rerun of the already-synthesized 5-lane 100.286 MHz netlist found three passing
seeds, four placement failures, one timing failure, and two no-result runs that
were stopped after about 24 minutes without a final result. That makes 5x100.286
the highest modeled local result tried so far, and seed 13 had a strong 116.28
MHz Fmax, but placement is still not deterministic enough to treat the result as
a stable replacement without locking down the seed and validating functionally.
Seed 13 has a packaged bitstream at
`build/direct-prod5-100m286-seed13/tangminer_spinal_tangnano20k.fs`.

The wide-lane experiment adds `SPINAL_WIDE_LANES=1`, which wraps all lanes in a
single `BitcoinHashWideLaneBlock` with local job registers and local found
selection. It did not help placement for the 5-lane 100.286 MHz case: all tried
comparison seeds failed legal placement, and area increased to 73% LUT4 and 63%
DFF.

The `-noflatten` experiment did preserve five `BitcoinHashCore` submodules, but
the open Gowin flow did not pack the resulting JSON. Plain `-noflatten` inserted
I/O buffers on internal module boundaries, causing unconstrained internal I/O
errors. Adding `-noiopads` removed that issue but left top-level outputs driven
by unsupported non-pad ports, so nextpnr still failed before placement.

Selective `keep_hierarchy` on `BitcoinHashCore` has the same direct-flow problem:
Yosys preserves the five core modules, but `iopadmap` still inserts internal I/O
buffers on core ports. A staged Yosys flow can avoid that by running
`synth_gowin -noiopads`, applying `iopadmap` only to `top`, and then finishing
the Gowin flow. That does produce a nextpnr-readable hierarchical JSON, but the
packed design is much larger than the flat baseline and seed 13 fails placement
with 79% LUT4 and 70% DFF.

Of the simple synthesis knobs tried after the online Yosys/Apicula guidance,
only `-nowidelut` produced a useful passing result. It avoids wide LUT packing
and still places/routes at seed 13, but Fmax drops to 110.04 MHz versus the
already-known flat seed 13 result at 116.28 MHz. `-retime` raises area and fails
placement; `-noabc9` and `-nodffe` both overuse LUT4 after packing.

Additional production seed checks for 4 lanes at 120 MHz:

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 4 lanes, 120 MHz, seed 2/full make | Fail timing | 7.50 MH/s | 108.19 MHz | -9.8% | 58% | 50% | `build/attempt-logs/prod4-120m-seed2.log` |
| 4 lanes, 120 MHz, direct nextpnr seed 4 | Fail timing | 7.50 MH/s | 116.65 MHz | -2.8% | 58% | 50% | `build/attempt-logs/direct-prod4-120m-seed4.log` |
| 4 lanes, 120 MHz, direct nextpnr seed 5 | Fail timing | 7.50 MH/s | 107.41 MHz | -10.5% | 58% | 50% | `build/attempt-logs/direct-prod4-120m-seed5.log` |

Takeaway: 4x120 remains unclosed. Seed 4 came close enough that more seed
searching may find a timing pass, but it would have little margin.

## Width Experiment

Branch `width-exp` adds these knobs:

- `SPINAL_ROUND_SKIP=1`: prepare first-pass rounds 0..2 once per job, start
  nonce work at round 3, and stop the second pass at round 60 for the low32
  candidate word. This changes cadence from 64 to 61 clocks per A/B pair.
- `SPINAL_PAIRS_PER_LANE=2` or `4`: place multiple local A/B compressor pairs
  inside each top-level lane so they share job state, prefix-prep outputs,
  constant lookup wiring, and local result selection.
- `NEXTPNR_ARGS`: allows seed and placer experiments from make.

Local logs show these paired-lane attempts:

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 1 lane x 4 pairs, full 64, 111 MHz | Fail timing | 6.94 MH/s | 76.96 MHz | -30.7% | 58% | 47% | `build/paired1x4_full64/pnr.log` |
| 1 lane x 4 pairs, 61-cycle, 111 MHz | Fail timing | 7.28 MH/s | 85.06 MHz | -23.4% | 63% | 48% | `build/paired1x4/pnr.log` |
| 1 lane x 4 pairs, 61-cycle, 100.286 MHz | Fail timing | 6.58 MH/s | 92.85 MHz | -7.4% | 62% | 48% | `build/paired1x4_61_100m286/pnr.log` |
| 1 lane x 4 pairs, 61-cycle, 90 MHz | Pass | 5.90 MH/s | 94.62 MHz | 5.1% | 62% | 48% | `build/paired1x4_61_90m/pnr.log` |
| 2 lanes x 2 pairs, 111 MHz | Fail timing | 7.28 MH/s | 66.60 MHz | -40.0% | 70% | 52% | `build/paired2x2/place.log` |
| 4 lanes x 1 pair, 61-cycle, 90 MHz | Fail placement | 5.90 MH/s | n/a | n/a | 80% | 58% | `../TangMiner-width-exp/build/sweep-roundskip-single/lanes4_pairs1_skip1_90m/build.log` |
| 3 lanes x 1 pair, 61-cycle, 111 MHz | Fail timing | 5.46 MH/s | 56.38 MHz | -49.2% | 64% | 46% | terminal run, `../TangMiner-width-exp/build/roundskip-3x111` |
| 4 lanes x 1 pair, 61-cycle, 111 MHz, production trim | Fail timing | 7.28 MH/s | 91.64 MHz | -17.4% | n/a | n/a | terminal run, `../TangMiner-width-exp/build/roundskip-prod-4x111` |

Takeaway: round skipping helps modeled rate by about 4.9% at the same clock, but
the implementations tried so far have much worse Fmax or placement behavior
than the plain 4-lane layout. The only round-skip result that closes timing is
1x4 at 90 MHz, and its modeled 5.90 MH/s is worse than the previous 4-lane
111 MHz baseline. Single-pair round skip also failed in both untrimmed and
production-trimmed trials, so the current round-skip implementation is not
helpful without a structural timing fix.

## SRAM Schedule Experiment

Branch `origin/sram-optimize` changes `Sha256CompressWords` by replacing the
16-register shifting schedule window with three distributed LUT RAM taps
(`scheduleTap1`, `scheduleTap9`, and `scheduleTap14`) plus async reads. It also
registers active nonce/digest inputs for the first and second pass wrappers.

Tried result:

| Variant | Result | Modeled rate | Fmax | Margin | LUT4 | DFF | Extra RAM | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 4 lanes, 111 MHz | Pass | 6.94 MH/s | 122.13 MHz | 10.0% | 87% | 42% | 192 RAM16SDP4 | terminal run, `../TangMiner-sram-opt/build/sram-baseline-4x111` |

Observed impact:

- DFF drops materially versus the plain 4-lane baseline, from about 52% to 42%.
- LUT4 rises sharply, from about 65% to 87%, plus 192 distributed RAM cells.
- Timing still closes at 111 MHz and this run produced the best 4-lane 111 MHz
  Fmax observed locally. It is useful if register pressure is the limiting
  problem, but it consumes too much LUT headroom to be an obvious lane-count win
  by itself.

## What Helps

| Change | Status | Result |
| --- | --- | --- |
| 4 plain lanes at 111 MHz | Proven historical baseline | 6.94 MH/s modeled, 7.3% timing margin. |
| Production trim with fixed candidate/no echo/no hardcoded job | Proven useful | Reduces 4-lane LUT4 from about 65% to 58%, and enables a 5-lane 90 MHz build. |
| 2 production lanes at 67.5 MHz | Hardware-proven lower-clock control | `2x67.5` passes 50/50 strict quick21 host verification and models at 2.109 MH/s. |
| 3/4 production lanes at 54 MHz | Hardware-proven lane scaling | `3x54` and `4x54` both pass 50/50 strict quick21 host verification. `4x54` models at 3.375 MH/s. |
| 5 production lanes at 54 MHz with `synth_gowin -nowidelut` | Hardware-proven best multi-lane point | `5x54` passes 50/50 and 100/100 strict quick21 host verification and models at 4.219 MH/s. |
| 5 production lanes above 54 MHz with `synth_gowin -nowidelut` | Invalidated by hardware | `5x57` and `5x58.5` route cleanly but return false positives; `5x67.5` returns 50/50 false positives. `5x60.75` did not legally place with the tried seeds. |
| 6 production lanes at 54 MHz with `synth_gowin -nowidelut` | Does not place | Area reaches 86% LUT4 / 72% DFF / 24% ALU and nextpnr cannot find a legal placement at seed 13. |
| 2 production lanes at 81 MHz | Invalidated by stricter hardware check | Earlier short runs passed, but a 50-job strict retest found 3 false positives. Do not use as a verified point. |
| 5 production lanes at 90 MHz | Static-only, not hardware-proven | 7.03 MH/s modeled with 13.3% timing margin, but lower lane-count 90 MHz images already return invalid hardware hashes. |
| 5 production lanes at 100.286 MHz with seed search | Static-only, hardware invalid above boundary | Seeds 4, 10, and 13 pass; seed 13 reaches 116.28 MHz for 7.84 MH/s modeled. Hardware checks at 100.286 MHz returned invalid candidates. |
| 5 production lanes at 111 MHz with seed search | Static-only, hardware invalid | Seed 6 passes at 117.67 MHz for 8.67 MH/s modeled, but SRAM-loaded hardware candidates failed host validation. |
| `synth_gowin -nowidelut` on 5 production lanes | Useful at lower clock | At 100.286 MHz, seed 13 passes static timing but remains hardware-untrusted. At 54 MHz, it reduces packed LUT4 enough to produce a hardware-valid 5-lane image. |
| 5 production lanes as one wide block | Tried, not helpful | `SPINAL_WIDE_LANES=1` increased area and failed placement for every tried comparison seed. |
| Global `synth_gowin -noflatten` | Tried, not usable directly | Preserves hierarchy but produces JSON that nextpnr rejects during I/O packing in this flow. |
| Selective/staged hierarchy preservation | Tried, not helpful | A custom top-only IO-pad flow reaches nextpnr, but area rises to 79% LUT4/70% DFF and placement fails. |
| SRAM/distributed schedule taps | Useful for DFF pressure, costly for LUTs | 4x111 passes at 122.13 MHz and cuts DFF to 42%, but LUT4 rises to 87%. |
| 61-cycle round skip | Tried, not helpful yet | Theoretical 4.9% cadence gain, but both paired and single-pair implementations lose too much Fmax or fail placement. |
| Wider local pairs, 1x4 or 2x2 | Tried, not helpful so far | Worse Fmax and lower best passing rate than baseline. |
| Simple timing fences/synchronizers | Tried, not helpful | Top-level digest staging and registered compressor-output `done` both passed simulation but worsened hardware validity around the then-promising 2x81 point. |
| `SPINAL_SHARED_K=0` | Tried, not helpful at 90 MHz | Removes shared round-constant fanout, but `2x90` still returned 10/10 invalid candidates. |
| Lane start staggering | Tried, not helpful at 90 MHz | `SPINAL_LANE_START_STAGGER=16` keeps lanes out of exact start-cycle phase, but `2x90` still returned 10/10 invalid candidates. |
| `synth_gowin -noalu` | Tried, not viable at 90 MHz | Avoids Gowin ALU carry-chain mapping, but the two-lane 90 MHz build only placed to about 65 MHz. |
| Plain 5 lanes | Tried, not helpful | Fails placement at 90 MHz and above without production trimming; `-nowidelut`, `-retime`, `-noabc9`, and `-nodffe` did not recover it. |
| 120 MHz clock profile | Tried, not helpful yet | Fails timing for baseline and production-trimmed 4-lane builds. |

## Untested Or Incomplete Combinations

- Add a flash-and-verify harness that logs lane count, clock profile, seed,
  Fmax, SRAM-load status, and strict host hash validity.
- Add any remaining legal 20K clock profiles if needed, but current hardware
  checks show `2x67.5` is reliable while `2x81` can still false-positive under
  longer strict runs.
- Try `3x67.5` or `4x67.5` as comparison points if useful, but they do not beat
  `5x54` in modeled rate. The tried 5-lane clock steps above 54 MHz returned
  false positives, and `6x54 -nowidelut` failed legal placement.
- Add first-class seed sweeping to `scripts/tools/sweep_spinal_variants.py` so
  seed searches can reuse a synthesized netlist and emit a compact summary.
- `origin/sram-optimize` baseline sweep at 90/100.286/120 MHz.
- `origin/sram-optimize` plus production trim.
- Combined `width-exp` round-skip single-pair with `sram-optimize`. This needs
  a merge/test branch because the two branches touch the same compressor code.
- nextpnr seed sweeps for hardware-valid clock regions before spending more
  time on 100+ MHz static-only wins.

## Recommended Next Sweep Order

1. For the single-lane timing path, treat `1x124.875` two-cycle as the current
   fastest verified image and `1x126` as the first failing point.
2. If higher single-lane clock is worth the lower cadence, split the remaining
   `t1` and state-update arithmetic into another phase, then re-run cocotb and
   strict hardware hash checks. The split SHA/control-clock diagnostic did not
   improve the boundary.
3. If throughput is the priority, stop spending time on small flat 5-lane clock
   bumps: `5x57` is already invalid on hardware, and no legal PLL point exists
   between 54 and 57 MHz with the current `CLKOUT` path. The next meaningful
   performance work is structural: reduce SHA round critical-path depth or area
   enough that either 57+ MHz validates or 6 lanes at 54 MHz can place.
4. Build a reusable flash plus `serial_smoke.py --require-target` harness and
   emit a CSV/Markdown result row per image.
5. Treat 100+ MHz multi-lane static timing wins as untrusted until the host hash
   verifier passes.
