# Hardware Overview

The active TangMiner bitstream is a small UART-controlled hash engine for the
Tang Nano 20K. The host does the pool-facing work and sends compact jobs to the
FPGA. The FPGA then loops over nonces locally and only talks back when a nonce
meets the configured candidate filter.

![TangMiner hardware flow](hardware-flow.svg)

At a high level:

- `UartRx` samples incoming serial bits and emits bytes.
- The top-level parser finds the `TN` sync bytes, decodes the command, and loads
  the job registers.
- Six `BitcoinHashCore` lanes split the nonce space by residue: lane starts
  `0..5`, and each lane increments by `6`.
- Each lane has two iterative 64-round `Sha256Compress` engines: one for the
  header final-block pass and one for the second SHA-256 pass.
- Experimental round-skip mode precomputes first-pass rounds 0..2 once per job
  and starts nonce-dependent work at round 3.
- Each lane adds the SHA-256 feed-forward state outside the compressors: job
  midstate for pass one, SHA-256 IV for pass two.
- The final digest is checked in Bitcoin's byte-reversed proof-of-work ordering
  with a small prefix filter instead of a full 256-bit target comparator.
- On a hit, a priority selector latches one found nonce and sends `F || nonce`.
- The host reconstructs the block header, recomputes the hash, and performs the
  full share target comparison.

On the default 20K build, the onboard `27 MHz` clock feeds an internal Gowin
`rPLL` that drives the hash fabric at `67.500 MHz`. In steady state each lane
launches a new nonce every `65` FPGA clocks: a 64-cycle SHA cadence plus the
one-cycle pass-output fence. With six lanes, the aggregate chip cadence is one
tested nonce every `10.833` clocks, or about `6.231 MH/s`. The selected 20K
build uses Official Gowin EDA with `SPINAL_REGISTER_PASS_OUTPUTS=1` and
`SPINAL_MINIMIZE_SHA_RESET=1`, and has passed strict host nonce validation on
real hardware.

With `SPINAL_ROUND_SKIP=1`, each lane launches a nonce every `61` clocks by
skipping the nonce-independent first-pass rounds and deriving the low candidate
word after second-pass round 60. That mode is experimental until strict host
nonce validation passes on real hardware.

With `SPINAL_REGISTER_PASS_OUTPUTS=1`, each lane adds one cycle between
first-pass completion and the next first/second pass launch. That fence cuts the
direct first-pass digest to second-pass message-word timing path, so the full
64-round cadence becomes 65 clocks per lane. `SPINAL_REGISTER_COMPRESSOR_OUTPUTS`
is a separate optional compressor-output fence and adds another cycle only for
the one-cycle round datapath.

These boxes are logical hardware blocks. After synthesis, they become Gowin
FPGA LUTs, flip-flops, carry chains, IO buffers, and routing rather than
software tasks.
