# Compression Circuitry

TangMiner uses four compact iterative SHA-256 lanes on the Tang Nano 20K. Each
lane has one `Sha256Compress` block, does not unroll the 64 SHA-256 rounds, and
does not duplicate the compressor for the second hash pass. Instead, each
`BitcoinHashCore` lane drives its own `Sha256Compress` block twice per nonce.

The top level gives the lanes different nonce residue classes: starts `0..3`
and stride `4`. The diagrams below show the datapath inside one lane unless
they explicitly call out the four-lane wrapper.

## Per-Lane Nonce Flow

```mermaid
flowchart LR
    host["Host job packet<br/>midstate[32], tail[12], target[32]"]
    lanes["4 lane wrapper<br/>start nonce 0..3<br/>stride = 4"]
    regs["Lane job registers<br/>midstate, tail, candidate mode<br/>current_nonce"]
    first_block["First-pass final block<br/>tail || nonce || padding || 0x00000280"]
    comp1["Lane Sha256Compress<br/>64 rounds from host midstate"]
    digest1["First digest<br/>32 bytes"]
    second_block["Second-pass block<br/>digest1 || padding || 0x00000100"]
    comp2["Same lane Sha256Compress<br/>64 rounds from SHA-256 IV"]
    compare["Byte-reverse final digest<br/>cheap candidate prefix check"]
    found["F response<br/>selected nonce only"]
    next["current_nonce += 4<br/>start next first pass"]

    host --> lanes
    lanes --> regs
    regs --> first_block
    first_block --> comp1
    regs --> comp1
    comp1 --> digest1
    digest1 --> second_block
    second_block --> comp2
    comp2 --> compare
    regs --> compare
    compare -->|meets target| found
    compare -->|misses target| next
    next --> first_block
```

The first compressor pass continues the SHA-256 state that the host already
computed over bytes `0..63` of the Bitcoin header. The FPGA only builds the
final 64-byte first-pass block from `tail`, the lane's current nonce word, and
standard SHA-256 padding.

The second pass hashes the 32-byte first digest from the normal SHA-256 IV.
After that, the lane byte-reverses the final digest so the cheap prefix check
uses Bitcoin proof-of-work bit ordering. The FPGA does not contain the full
256-bit target comparator in the four-lane 20K path; the host validates the
returned candidate nonce by rebuilding the header and double-hashing it. If more
than one lane is reporting, the top level latches one selected result before
UART transmit.

For bring-up and roughly 10-second candidate testing at the default `27 MHz` clock,
the host tools accept the named target `quick23`:

```text
000001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
```

That target is equivalent to requiring the top 23 bits of the byte-reversed
digest to be zero. The bitstream also recognizes `all-ones` for immediate smoke
tests, `quick3` for short RTL tests, and `quick21` as an easier legacy candidate
filter. Arbitrary target packets currently use the same `quick23` hardware
filter and leave exact validation to the host.

## Compressor Datapath

```mermaid
flowchart TB
    start["start"]
    state_in["stateIn[255:0]"]
    block["block[511:0]"]

    feed["Feed-forward base state<br/>held by BitcoinHashCore"]
    work["Working state regs<br/>a b c d e f g h"]
    schedule["16-word schedule shift regs<br/>w0..w15"]
    round["round counter<br/>0..63"]
    k["K[round] constant"]

    wnext["wNext = smallSigma1(w14)<br/>+ w9 + smallSigma0(w1) + w0"]
    t1["t1 = h + bigSigma1(e)<br/>+ ch(e,f,g) + K[round] + w0"]
    t2["t2 = bigSigma0(a)<br/>+ maj(a,b,c)"]
    update["Round register update<br/>a'=t1+t2<br/>e'=d+t1<br/>b'=a, c'=b, d'=c<br/>f'=e, g'=f, h'=g"]
    final["Final addback<br/>digest = base state + final working state"]
    done["done"]

    start --> state_in
    start --> block
    state_in --> work
    state_in --> feed
    block --> schedule
    round --> k
    schedule --> t1
    schedule --> wnext
    k --> t1
    work --> t1
    work --> t2
    t1 --> update
    t2 --> update
    update --> work
    wnext --> schedule
    round --> final
    feed --> final
    work --> final
    final --> done
```

The schedule memory is a rolling 16-word window. During each busy cycle, the
core consumes `w0`, shifts `w1..w15` down by one slot, and writes the newly
expanded word into `w15`.

The working state update is the standard SHA-256 round transform. All additions
are 32-bit modular additions because each expression is resized back to 32 bits.

## One Compressor Round

```mermaid
flowchart LR
    subgraph inputs["Current registers"]
      a((a))
      b((b))
      c((c))
      d((d))
      e((e))
      f((f))
      g((g))
      h((h))
      w0((w0))
      kr(("K[round]"))
    end

    s1["bigSigma1(e)"]
    ch["ch(e,f,g)"]
    s0["bigSigma0(a)"]
    maj["maj(a,b,c)"]
    t1["t1"]
    t2["t2"]

    anew["a next = t1 + t2"]
    enew["e next = d + t1"]
    shifts["b,c,d,f,g,h shift<br/>from previous neighbors"]

    e --> s1
    e --> ch
    f --> ch
    g --> ch
    h --> t1
    s1 --> t1
    ch --> t1
    kr --> t1
    w0 --> t1

    a --> s0
    a --> maj
    b --> maj
    c --> maj
    s0 --> t2
    maj --> t2

    t1 --> anew
    t2 --> anew
    d --> enew
    t1 --> enew
    a --> shifts
    b --> shifts
    c --> shifts
    e --> shifts
    f --> shifts
    g --> shifts
```

The compressor performs exactly one SHA-256 round per clock while busy. Round
`63` produces the final working values. `BitcoinHashCore` then adds those words
back into the correct feed-forward base state: the host-provided midstate for
the first pass, or the fixed SHA-256 IV for the second pass. Keeping that
feed-forward state outside the compressor avoids duplicating the eight 32-bit
starting-state registers inside the round engine.

## Control Timing

```mermaid
sequenceDiagram
    participant Core as One BitcoinHashCore lane
    participant SHA as Lane Sha256Compress

    Core->>SHA: start, stateIn=midstate, block=tail||nonce||pad
    Note over SHA: 64 busy clocks, rounds 0..63
    SHA-->>Core: done, workOut=first pass final working state
    Core->>Core: add host midstate feed-forward to make first_digest
    Core->>SHA: start, stateIn=SHA256_IV, block=first_digest||pad
    Note over SHA: 64 busy clocks, rounds 0..63
    SHA-->>Core: done, workOut=second pass final working state
    Core->>Core: add SHA256_IV feed-forward to make final_digest
    Core->>Core: check reversed final digest prefix
    alt share found
        Core->>Core: latch found nonce
    else no share
        Core->>SHA: immediately start next lane nonce first pass
    end
```

In steady state the second-pass `done` cycle also launches the first pass for
the next nonce. That overlap at the control boundary is why the measured
per-lane throughput is `128` clocks per nonce rather than `130` or more: each
nonce still does two 64-round compression passes, but there are no extra idle
cycles between nonces once scanning is running. With four lanes in parallel,
the aggregate chip cadence is one tested nonce every `32` clocks.

## Source Pointers

- `Sha256Compress` is implemented in `src/main/scala/tangminer/TangMiner.scala`.
- `BitcoinHashCore` constructs the two SHA-256 blocks, sequences the compressor,
  increments the lane nonce by the configured stride, and performs the candidate
  prefix check.
- `src/sha256_compress.v` is the legacy hand-written Verilog compressor kept for
  comparison and simulation.
