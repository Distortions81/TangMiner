# Optimization Matrix

Snapshot date: 2026-07-08.

This note summarizes the optimization branches and local build artifacts that
exist in this checkout. It keeps historical sweep data because that explains
the current defaults. The active branch graph is:

- `main` / `origin/main`: selected hardware-validated six-lane 20K design at
  `67.500 MHz`, built with Official Gowin EDA, local K constants,
  `SPINAL_REGISTER_PASS_OUTPUTS=1`, and `SPINAL_MINIMIZE_SHA_RESET=1`.
- The previous open-source `5x54` `synth_gowin -nowidelut` image remains a
  hardware-validated fallback, but it is no longer the selected default.
- `main` also has unvalidated `SPINAL_ROUND_SKIP=1`,
  `SPINAL_HOST_ROUND_SKIP=1`, `SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD=1`, and
  `SPINAL_CSA_ROUND=1` experimental knobs. `SPINAL_TWO_ROUNDS_PER_CYCLE=1` and
  `SPINAL_TWO_ROUND_PIPELINE=1` are the first partially unrolled/deeper pipeline
  experiments; `SPINAL_TWO_PHASE_ROUND_PIPELINE=1` is a follow-up per-round
  two-phase pipeline experiment that validates in RTL but exceeds 20K register
  resources at useful lane counts. `SPINAL_SHARE_JOB_STATE=1` and
  `SPINAL_BALANCED_ROUND_ADDER=1` are timing diagnostics for the current
  full-path core. None are selected defaults.
- `width-exp` / `origin/width-exp`: historical 61-cycle round skipping and
  wider local A/B compressor-pair lane experiments.
- `origin/sram-optimize`: experimental SHA message schedule storage using
  distributed LUT RAM.

No `AGENTS.md` or `agents.md` file exists inside this repository at the time of
this snapshot.

## 2026-07-08 Active Experimental Knobs

Round-skip has been integrated into the active SpinalHDL design behind
`SPINAL_ROUND_SKIP=1`. It prepares first-pass rounds 0..2 once per job, starts
nonce-dependent first-pass work at round 3, stops the second pass at round 60,
and derives the candidate low word from that round-60 working state. The modeled
default six-lane 67.5 MHz rate with the pass fence still enabled would be
`6 * 67.5 / 62 = 6.532 MH/s`, but this is not a validated hashrate until strict
hardware nonce validation passes. The historical unfenced `5x54` comparison
models as `5 * 54 / 61 = 4.426 MH/s`.

Latest local open-source 5-lane 54 MHz seed-13 reruns with `synth_gowin
-nowidelut` did not produce a new flashable round-skip image. The default
full-64 build packed at 72% LUT4 / 61% DFF / 20% ALU but failed legal
placement in `build/attempt-logs/roundskip-baseline-build.log`; the round-skip
candidate packed at 76% LUT4 / 65% DFF / 22% ALU and also failed legal
placement in `build/attempt-logs/roundskip-candidate-build.log`. Keep the
previously hardware-validated `build/hw-prod5-54m-nowidelut-seed13` image only
as the open-source fallback; the selected build is now the official-Gowin
six-lane 67.5 MHz pass-fenced/minimized-reset image.

`SPINAL_CSA_ROUND=1` is also available as a one-cycle datapath experiment. It
uses carry-save reduction for the SHA round state addition trees and is mutually
exclusive with the existing two-cycle and three-cycle round options.
`SPINAL_CSA_SCHEDULE=1` additionally maps the message-schedule adder to CSA; it
is off by default because the full-schedule CSA form exceeds area on dense
six-lane 20K builds. The local-K CSA-lite 6-lane 81 MHz build is still slightly
too large at 20954 logic cells versus 20736 available. The shared-K CSA-lite
variant fits and, with `GOWIN_ROUTE_MAXFAN=12`, closes static timing at
81.015 MHz with 0 setup/hold violations, but hardware rejects it: strict
`quick21` returned 43 false positives in 100 jobs on `/dev/ttyUSB1`. Do not
count CSA mode as a hashrate improvement until a strict hardware run passes.

`SPINAL_BALANCED_ROUND_ADDER=1` is an alternate one-cycle round-adder mapping
that recursively balances the SHA addition trees. It is correctness-valid in
strict RTL, but it is not a timing win: the 6-lane 84 MHz full-path candidate
with pass fence, first-pass feed-forward fence, minimized reset, and shared job
state routed at only 71.450 MHz Fmax versus 77.155 MHz without the balanced
adder.

`SPINAL_REGISTER_PASS_OUTPUTS=1` now means a one-cycle pass-output fence. It
registers the first-pass digest before the second-pass message load and registers
the second-pass low word before candidate checking. The older two-cycle
compressor-plus-pass fence is still reproducible by also setting
`SPINAL_REGISTER_COMPRESSOR_OUTPUTS=1`.

`SPINAL_SHARE_JOB_STATE=1` hoists the job midstate/tail/candidate-mode registers
out of individual lanes and shares one registered job state across all lanes.
It does not change lane cadence. The 6-lane 81 MHz full-path candidate with
local K, pass fence, minimized reset, and shared job state validates in strict
RTL at a 65-cycle cadence for a modeled 7.48 MH/s, but Gowin routes it at only
73.603 MHz Fmax, or 71.478 MHz with route option 0. At measured Fmax that is
about 6.79 MH/s, below the 20% target.

`SPINAL_HOST_ROUND_SKIP=1` accepts host-provided first-pass prefix state and
message-schedule tail words. It validates in RTL with strict nonce checks, but
the dense speedup candidates have not closed timing on the 20K. The best modeled
shape, 7 lanes at 67.5 MHz with a 62-cycle lane period, would be 7.62 MH/s; it
routes only with route option 0 and then fails setup at 57.929 MHz. The narrower
6-lane 81 MHz shape routes but fails setup at 69.220 MHz. Removing the
pass-output fence gives a 61-cycle modeled period but makes timing worse:
7 lanes at 67.5 MHz routes with option 0 and fails setup at 51.612 MHz.
Follow-up shared-K/CSA host-round-skip runs do not change that conclusion:
7 lanes at 67.5 MHz with shared K and CSA-lite exceeds resources at 23587
logic cells, shared K without CSA fails normal routing with 1617 unrouted nets
and route option 0 falls to 54.002 MHz Fmax, and 6 lanes at 81 MHz with shared K
plus CSA-lite fails normal routing with 749 unrouted nets while route option 0
only reaches 73.456 MHz Fmax.

`SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD=1` is a targeted timing experiment that
captures the final first-pass work state and delays the first-pass `done` by one
cycle before the second pass. In full-path mode, 6 lanes at 84 MHz with local K,
pass fence, minimized reset, and shared job state validates in strict RTL at a
66-cycle cadence for a modeled 7.64 MH/s, but Gowin routes it at only 77.155 MHz
Fmax, or 77.106 MHz with `GOWIN_ROUTE_MAXFAN=12`, about 7.01 MH/s at measured
Fmax. In host-round-skip mode, 7 lanes at 67.5 MHz validates in RTL at
63 cycles, normal routing fails with 10546 unrouted nets, and route option 0
fails setup at 55.354 MHz.

The hardware nonce-attempt counter now uses a small registered popcount and a
split 32/32 accumulator. That keeps `TNC` measurement unambiguous without making
the counter a SHA timing path in dense host-round-skip builds.

The second-pass compressor now exposes a separate 32-bit candidate low-word
output so the host-round-skip path does not need to route the full 256-bit final
work state into the candidate filter. This is correctness-neutral and the
default six-lane target-alias RTL suite still passes 7/7 tests, but it did not
improve the dense 7-lane host-round-skip timing point: route option 0 still
fails setup at 57.929 MHz.

`SPINAL_TWO_ROUND_PIPELINE=1` is the first deeper SHA compressor experiment. It
keeps two context slots inside each compressor and starts a new nonce every
`ceil(64 / 2) + 1 = 33` clocks in full SHA256d mode. The 4-lane 67.5 MHz shape
passes strict cocotb with 44 counted attempts and models at
`4 * 67.5 / 33 = 8.18 MH/s`, which is above the 20% target. It is not a usable
20K image: 4 lanes exceeds synthesis resources with 31126 logic, 3 lanes at
84 MHz also exceeds resources with 23476 logic, and 2 lanes at 126 MHz routes
but fails setup at 61.831 MHz Fmax, only about 3.75 MH/s at measured Fmax. The
top failing paths are still inside the SHA round-pair/output path, so the next
architecture needs smaller per-round pipeline stages or a fuller streaming
SHA256d pipeline.

`SPINAL_TWO_PHASE_ROUND_PIPELINE=1` splits one SHA round into a registered
prepare phase (`t1`, `t2`, message-schedule next word) and a completion phase.
The first register-only version interleaves two context slots so the compressor
can accept a new full SHA256d start every 64 clocks. That is correctness-valid
in RTL and keeps the hardware nonce-attempt counter unambiguous:

| Build | RTL result | Gowin result |
| --- | --- | --- |
| 1 lane, `67m5`, local K, pass fence, minimized reset | strict cocotb passes 7/7; hardware counter reports 64-cycle lane period and 1.05 MH/s | not run |
| 5 lanes, `100m286`, local K, pass fence, minimized reset | not run; modeled at 7.83 MH/s from the same 64-cycle cadence | synthesis resource failure, 25412 logic vs 20736 available |
| 6 lanes, `81m`, local K, pass fence, minimized reset | strict cocotb passes 7/7; hardware counter reports 64-cycle lane period, 42 counted attempts, and 7.593750 MH/s | synthesis resource failure, 33155 DFF vs 15750 available |

The first context-memory version moved the slot contexts into a tiny async-read
`Mem`. It validated in strict RTL, but blocked starts on context writeback cycles
and still mapped too much storage into DFFs:

| Build | RTL result | Gowin result |
| --- | --- | --- |
| 1 lane, `67m5`, async context-memory slots | strict cocotb passes 7/7; hardware counter reports 65-cycle lane period and 1.038 MH/s | not run |
| 4 lanes, `126m`, async context-memory slots | strict cocotb passes 7/7; hardware counter reports 65-cycle lane period, 28 counted attempts, and 7.753846 MH/s | synthesis resource failure, 21515 DFF vs 15750 available |

The current sync-read context-memory form recovers the 64-cycle cadence and is
the best RTL two-phase point so far, but it remains over the 20K DFF limit:

| Build | RTL result | Gowin result |
| --- | --- | --- |
| 1 lane, `67m5`, sync context-memory slots | strict cocotb passes 7/7; hardware counter reports 64-cycle lane period and 1.054688 MH/s | not run |
| 4 lanes, `126m`, sync context-memory slots, FIFO depth 4 | strict cocotb passes 7/7; hardware counter reports 64-cycle lane period, 28 counted attempts, and 7.875000 MH/s | synthesis resource failure, 16415 DFF vs 15750 available |
| 4 lanes, `126m`, same plus conditional K/fixed-stop register trims | strict cocotb passes 7/7; same 7.875000 MH/s modeled rate | synthesis resource failure, 16399 DFF vs 15750 available |
| 4 lanes, `126m`, FIFO depth 2 | rejected by strict cocotb: quick14/quick21 time out and the counter sees a 130-cycle gap | not run |
| 4 lanes, `126m`, direct first-pass to second-pass handoff | one-lane strict cocotb passes 7/7 | synthesis resource failure, 29063 DFF vs 15750 available |
| 4 lanes, `126m`, no pass-output fence or two-phase first-pass fence bypass | strict cocotb passes 7/7 | synthesis resource failure, about 23110-23111 logic vs 20736 available |

The two-phase result proves the scheduler/counter model, but it is not yet a
usable 20K speedup. Five register-only lanes already exceed logic resources, and
four sync-memory context lanes at the high clock needed for +20% still exceed
DFFs. Padding the context memory to 16 entries did not materially change Gowin's
DFF mapping, and removing output fences moves the failure to LUT/ALU logic
overuse. The next storage design needs a RAM-friendly schedule/context split or
a streaming SHA256d pipeline with fewer replicated context shells.

On 2026-07-08, the selected six-lane official Gowin build at 67.5 MHz uses
local K constants and passed hardware checks with:

```sh
make gowin-fmax TARGET=tangnano20k \
  SPINAL_LANES=6 \
  SPINAL_CLOCK_PROFILE=67m5 \
  SPINAL_SHARED_K=0 \
  SPINAL_REGISTER_PASS_OUTPUTS=1 \
  SPINAL_MINIMIZE_SHA_RESET=1 \
  GOWIN_PROJECT_NAME=tangminer_gowin_tangnano20k_lanes6_67m5_regpass1_minreset1_localK
```

This build runs one lane nonce every 65 clocks, modeled as
`6 * 67.5 / 65 = 6.231 MH/s`. Static timing closed at 67.644 MHz reported Fmax
against a 67.499 MHz constraint. Resource use dropped versus the unfenced
six-lane baseline because `SPINAL_MINIMIZE_SHA_RESET=1` let Gowin map state into
SSRAM: 82% logic, 75% registers, 92% CLS. Hardware validation passed 100/100
strict quick21 returned nonces on `/dev/ttyUSB1`, with the hardware
nonce-attempt counter queried through `TNC`.

Rejected six-lane 67.5 MHz follow-ups from the same pass:

- Pass fence only: improved Fmax from 60.149 MHz to 63.307 MHz, but still failed
  setup with 128 endpoints and TNS -48.259.
- Pass fence plus registered round constant: the invalid-K behavior was fixed
  and strict cocotb passes 7/7 at the normal 65-cycle cadence, but the corrected
  local-K build no longer closes setup at 67.5 MHz; latest Fmax is 64.099 MHz.
- Pass fence plus `SPINAL_CSA_ROUND=1`: failed Gowin synthesis resource limits
  with 23995 logic cells requested for a 20736-cell device.
- Shared K: no longer selected; local K is the current default so K fanout is
  local to each compressor.

Rejected six-lane 81 MHz follow-ups from the 20% push:

- Local K, pass fence, minimized reset, CSA-lite: strict RTL passes at a
  65-cycle cadence, but Gowin synthesis exceeds 20K resources with 20954 logic
  cells requested for a 20736-cell device.
- Shared K, pass fence, minimized reset, CSA-lite, `GOWIN_ROUTE_MAXFAN=12`:
  strict RTL passes 7/7 and Gowin closes at 81.015 MHz with 20299/20736 logic,
  11759/15750 registers, and 10283/10368 CLS. Hardware validation failed
  `quick21 --count 100 --require-target` with 43 false positives, so this is
  not a selected image despite meeting the modeled `6 * 81 / 65 = 7.477 MH/s`
  target.
- Host-round-skip plus shared K and CSA-lite: strict RTL reports the expected
  62-cycle cadence and modeled `6 * 81 / 62 = 7.839 MH/s`, but normal routing
  leaves 749 unrouted nets and route option 0 misses timing at 73.456 MHz Fmax.

Rejected seven-lane 67.5 MHz follow-ups from the 20% push:

- Host-round-skip plus shared K, pass fence, minimized reset, and
  `GOWIN_ROUTE_MAXFAN=12`: normal routing leaves 1617 unrouted nets; route
  option 0 completes but misses timing at 54.002 MHz Fmax.
- Host-round-skip plus shared K and CSA-lite: strict RTL reports the expected
  62-cycle cadence and modeled `7 * 67.5 / 62 = 7.621 MH/s`, but synthesis
  exceeds the 20K with 23587 logic cells requested.
- Full-path local K at 84 MHz with first-pass feed-forward fence and
  `GOWIN_ROUTE_MAXFAN=12`: routes at 77.106 MHz Fmax, essentially unchanged
  from the earlier 77.155 MHz run and still below the 84 MHz target.

Promotion gate for either option:

```sh
python scripts/tools/serial_smoke.py --target quick21 --count 100 --require-target <uart>
```

Additional spot-check targets must match the configured hardware candidate
filter. The selected default uses `SPINAL_FIXED_CANDIDATE=2`, so it is a quick21
filter image; a stricter `quick23` host target is only valid for a build whose
hardware filter is also configured for quick23 or stricter. Any false positive
under the intended filter invalidates the candidate regardless of static timing.

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
| 5 lanes, 54 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 123.92 MHz; placement reported 72.26 MHz before routing; utilization 72% LUT4 / 61% DFF / 20% ALU | 50/50 and 100/100 strict quick21 valid; historical open-source fallback | `build/hw-prod5-54m-nowidelut-seed13` |
| 5 lanes, 57 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 121.37 MHz; utilization 72% LUT4 / 61% DFF / 20% ALU | 41/50 strict quick21 valid; 9 false positives. Invalidated. | `build/hw-prod5-57m-nowidelut-seed13` |
| 5 lanes, 58.5 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 119.88 MHz; utilization 72% LUT4 / 61% DFF / 20% ALU | 37/50 strict quick21 valid; 13 false positives. Invalidated. | `build/hw-prod5-58m5-nowidelut-seed13` |
| 5 lanes, 60.75 MHz, seed 13, `synth_gowin -nowidelut` | Failed legal placement at 72% LUT4 / 61% DFF / 20% ALU; direct seed 4 was stopped after spending several minutes in legalisation with no progress. | Not flashed; no bitstream | `build/attempt-logs/prod5-60m75-nowidelut-seed13.log`, `build/seed-sweep-prod5-60m75-nowidelut/seed4` |
| 5 lanes, 67.5 MHz, seed 13, `synth_gowin -nowidelut` | Pass, 102.29 MHz; placement reported 67.40 MHz before routing; utilization 72% LUT4 / 61% DFF / 20% ALU | 0/50 strict quick21 valid; 50 false positives. Invalidated. | `build/hw-prod5-67m5-nowidelut-seed13` |
| 6 lanes, 67.5 MHz, official Gowin, local K, pass-output fence + minimized SHA reset fanout | Pass, 68.525 MHz in the 2026-07-08 rerun; utilization 82% logic / 75% register / 92% CLS in the earlier run | 100/100 strict quick21 valid, plus 20/20 quick14 valid; current default with `TNC` nonce-attempt counter | `build/sweep_localK_regpass_minreset/lanes6_67m5_regpass1_minreset1_localK` |
| 6 lanes, 54 MHz, seed 13, `synth_gowin -nowidelut` | Failed legal placement at 86% LUT4 / 72% DFF / 24% ALU | Not flashed; no bitstream | `build/attempt-logs/prod6-54m-nowidelut-seed13.log` |
| 2 lanes, 27 MHz, no PLL, seed 13 | Pass, 121.20 MHz | 5/5 valid | `build/hw-verify-prod2-27m-seed13` |

The `54m` PLL profile was also corrected during this pass. The previous profile
used `ODIV_SEL=8`, which made a 432 MHz VCO and failed `gowin_pack` on GW2AR-18.
Changing only `ODIV_SEL` to `16` keeps the output at 54.000 MHz and raises VCO
to 864 MHz, inside the 500-1250 MHz device range.

The 2026-05-26 production-trimmed open-source hardware boundary was no longer
`2x81`; the stricter 50-job check invalidated it. The best validated multi-lane
point measured in that pass was `5x54` with `synth_gowin -nowidelut`, modeled at
`5 * 54 / 64 = 4.219 MH/s`. `4x54` also passed and models at 3.375 MH/s, `3x54`
passed and models at 2.531 MH/s, and `2x67.5` passed and models at 2.109 MH/s.
More lanes do help when the clock is kept in a conservative timing region, but
the normal 5-lane 54 MHz seed-13 netlist was still placement-limited. Avoiding
wide LUT packing reduced packed LUT4 use from 77% to 72% and made the 5-lane
54 MHz image place, route, and pass hardware hash validation. Follow-up 5-lane
`-nowidelut` clock steps at 57 MHz, 58.5 MHz, and 67.5 MHz all routed with
strong static Fmax but returned false positives on hardware, while 60.75 MHz did
not legally place with the seeds tried. There is no clean `CLKOUT` PLL profile
between 54 MHz and 57 MHz that keeps the 27 MHz PFD at or above the 3.0 MHz
device limit. A sixth lane at 54 MHz reached 86% LUT4 / 72% DFF and failed legal
placement. The previous `5x100.286` and `5x111` static candidates must not be
treated as hardware-valid. As of 2026-07-07, the best validated multi-lane point
in this checkout is the official-Gowin six-lane 67.5 MHz pass-fenced/minimized
reset image, modeled at 6.231 MH/s.

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
The 5-lane 54 MHz `-nowidelut` image remains the best hardware-validated
open-source point, but it has been superseded by the six-lane official-Gowin
67.5 MHz pass-fenced/minimized-reset image. That newer image raises modeled
throughput to 6.231 MH/s by improving placement and reset mapping enough for six
lanes at 67.5 MHz. Pushing beyond this should still be treated as structural
work: reduce SHA round critical-path depth or area enough that higher clocks,
more lanes, or lower-cadence round splits validate in hardware instead of only
closing static timing.

## Current Selected Build

The current default build is:

```text
TARGET=tangnano20k
DEFAULT_FLOW=gowin
SPINAL_LANES=6
SPINAL_CLOCK_PROFILE=67m5
SPINAL_ENABLE_ECHO=0
SPINAL_ENABLE_HARDCODED=0
SPINAL_FIXED_CANDIDATE=2
SPINAL_SHARED_K=0
SPINAL_REGISTER_PASS_OUTPUTS=1
SPINAL_MINIMIZE_SHA_RESET=1
SPINAL_ROUND_SKIP=0
SPINAL_CSA_ROUND=0
```

It models at `6.231 MH/s`. The relevant hardware evidence is the
`build/sweep_localK_regpass_minreset/lanes6_67m5_regpass1_minreset1_localK`
image. A 2026-07-08 SRAM-loaded rerun closed at 68.525 MHz with 0 setup/hold
violations and passed 100/100 strict `quick21` host nonce validation on
`/dev/ttyUSB1`, with `TNC` nonce-attempt counter reads after each job. The older
`build/hw-prod5-54m-nowidelut-seed13` image remains the open-source fallback.
Higher-frequency 5-lane static candidates remain historical data only because
hardware validation invalidated them with false positives.

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
| 5 production lanes at 54 MHz with `synth_gowin -nowidelut` | Hardware-proven open-source point | `5x54` passes 50/50 and 100/100 strict quick21 host verification and models at 4.219 MH/s. |
| 6 official-Gowin lanes at 67.5 MHz with local K, pass fence + minimized SHA reset | Hardware-proven current best | Closes at 68.525 MHz in the 2026-07-08 rerun, passes 100/100 quick21 with `TNC` available, and models at 6.231 MH/s. |
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
| 61-cycle round skip | Active but unvalidated | Integrated as `SPINAL_ROUND_SKIP=1`; with the default pass fence it would model `6x67.5` at 6.532 MH/s, but the latest 5-lane 54 MHz `-nowidelut` seed-13 round-skip run failed legal placement at 76% LUT4 / 65% DFF / 22% ALU. It must place and pass strict quick21 hardware checks before use. |
| Carry-save one-cycle round | Active but unvalidated | Integrated as `SPINAL_CSA_ROUND=1`; intended for 57 MHz and higher sweeps after round-skip correctness is established. |
| Wider local pairs, 1x4 or 2x2 | Tried, not helpful so far | Worse Fmax and lower best passing rate than baseline. |
| Simple timing fences/synchronizers | Tried, not helpful | Top-level digest staging and registered compressor-output `done` both passed simulation but worsened hardware validity around the then-promising 2x81 point. |
| Local K constants, `SPINAL_SHARED_K=0` | Current default at 67.5 MHz; not sufficient by itself at high clock | Removes shared round-constant fanout and is used by the selected 6-lane image. Historical `2x90` local-K checks still returned 10/10 invalid candidates. |
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
  the validated official-Gowin `6x67.5` pass-fenced/minreset image in modeled
  rate. The tried 5-lane open-source clock steps above 54 MHz returned false
  positives, and `6x54 -nowidelut` failed legal placement.
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
3. If throughput is the priority, use the six-lane 67.5 MHz official-Gowin
   image as the baseline and avoid small flat 5-lane clock bumps: `5x57` is
   already invalid on hardware, and no legal PLL point exists between 54 and 57
   MHz with the current `CLKOUT` path. The next meaningful performance work is
   structural: reduce SHA round critical-path depth or area enough that higher
   clocks or additional validated lane-level optimizations survive hardware
   checks.
4. Build a reusable flash plus `serial_smoke.py --require-target` harness and
   emit a CSV/Markdown result row per image.
5. Treat 100+ MHz multi-lane static timing wins as untrusted until the host hash
   verifier passes.
