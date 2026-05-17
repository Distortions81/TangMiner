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
- Four `BitcoinHashCore` lanes split the nonce space by residue: lane starts
  `0..3`, and each lane increments by `4`.
- Each lane has one iterative 64-round `Sha256Compress` reused for both passes.
- Each lane adds the SHA-256 feed-forward state outside its compressor: job
  midstate for pass one, SHA-256 IV for pass two.
- The final digest is byte-reversed and checked with a small prefix filter
  instead of a full 256-bit target comparator.
- On a hit, a priority selector latches one found nonce and sends `F || nonce`.
- The host reconstructs the block header, recomputes the hash, and performs the
  full share target comparison.

In steady state each lane still costs `128` FPGA clocks per nonce: `64` clocks
for the first compressor pass and `64` clocks for the second. With four lanes,
the aggregate chip cadence is one tested nonce every `32` clocks. At the
default `27 MHz` board clock that is about `843.75 kH/s`.

These boxes are logical hardware blocks. After synthesis, they become Gowin
FPGA LUTs, flip-flops, carry chains, IO buffers, and routing rather than
software tasks.
