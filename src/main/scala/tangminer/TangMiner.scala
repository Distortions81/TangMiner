package tangminer

import spinal.core._
import spinal.lib._

object Sha256 {
  val Iv = List(
    BigInt("6a09e667", 16), BigInt("bb67ae85", 16),
    BigInt("3c6ef372", 16), BigInt("a54ff53a", 16),
    BigInt("510e527f", 16), BigInt("9b05688c", 16),
    BigInt("1f83d9ab", 16), BigInt("5be0cd19", 16)
  )

  val K = List(
    "428a2f98", "71374491", "b5c0fbcf", "e9b5dba5",
    "3956c25b", "59f111f1", "923f82a4", "ab1c5ed5",
    "d807aa98", "12835b01", "243185be", "550c7dc3",
    "72be5d74", "80deb1fe", "9bdc06a7", "c19bf174",
    "e49b69c1", "efbe4786", "0fc19dc6", "240ca1cc",
    "2de92c6f", "4a7484aa", "5cb0a9dc", "76f988da",
    "983e5152", "a831c66d", "b00327c8", "bf597fc7",
    "c6e00bf3", "d5a79147", "06ca6351", "14292967",
    "27b70a85", "2e1b2138", "4d2c6dfc", "53380d13",
    "650a7354", "766a0abb", "81c2c92e", "92722c85",
    "a2bfe8a1", "a81a664b", "c24b8b70", "c76c51a3",
    "d192e819", "d6990624", "f40e3585", "106aa070",
    "19a4c116", "1e376c08", "2748774c", "34b0bcb5",
    "391c0cb3", "4ed8aa4a", "5b9cca4f", "682e6ff3",
    "748f82ee", "78a5636f", "84c87814", "8cc70208",
    "90befffa", "a4506ceb", "bef9a3f7", "c67178f2"
  ).map(BigInt(_, 16))

  def word(value: BigInt): UInt = U(value, 32 bits)
  def wordFromBits(value: Bits, index: Int): UInt =
    value(511 - index * 32 downto 480 - index * 32).asUInt
  def wordFromDigest(value: Bits, index: Int): UInt =
    value(255 - index * 32 downto 224 - index * 32).asUInt

  def rotr(x: UInt, n: Int): UInt = (x.rotateRight(n)).resize(32)
  def shr(x: UInt, n: Int): UInt = (B(0, n bits) ## x.asBits(31 downto n)).asUInt
  def ch(x: UInt, y: UInt, z: UInt): UInt = ((x & y) ^ (~x & z)).resize(32)
  def maj(x: UInt, y: UInt, z: UInt): UInt = ((x & y) ^ (x & z) ^ (y & z)).resize(32)
  def bigSigma0(x: UInt): UInt = (rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22)).resize(32)
  def bigSigma1(x: UInt): UInt = (rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25)).resize(32)
  def smallSigma0(x: UInt): UInt = (rotr(x, 7) ^ rotr(x, 18) ^ shr(x, 3)).resize(32)
  def smallSigma1(x: UInt): UInt = (rotr(x, 17) ^ rotr(x, 19) ^ shr(x, 10)).resize(32)

  def concatWords(words: Seq[UInt]): Bits = words.map(_.asBits).reduce(_ ## _)

  def reverseBytes256(value: Bits): Bits =
    (0 until 32).map(i => value(i * 8 + 7 downto i * 8)).reduce(_ ## _)

  def byteFromMsb(value: Bits, byteCount: Int, index: UInt): Bits = {
    val bytes = Vec((0 until byteCount).map(i => value(byteCount * 8 - 1 - i * 8 downto byteCount * 8 - 8 - i * 8)))
    bytes(index.resized)
  }
}

class UartRx(clksPerBit: Int) extends Component {
  val io = new Bundle {
    val rx = in Bool()
    val data = out Bits(8 bits)
    val valid = out Bool()
    val reset = in Bool()
  }

  object State extends SpinalEnum {
    val idle, start, data, stop = newElement()
  }

  val state = Reg(State()) init State.idle
  val clkCount = Reg(UInt(16 bits)) init 0
  val bitIndex = Reg(UInt(3 bits)) init 0
  val rxShift = Reg(Bits(8 bits)) init 0
  val rxMeta = Reg(Bool()) init True
  val rxSync = Reg(Bool()) init True
  val dataReg = Reg(Bits(8 bits)) init 0
  val validReg = Reg(Bool()) init False

  io.data := dataReg
  io.valid := validReg

  rxMeta := io.rx
  rxSync := rxMeta
  validReg := False

  when(io.reset) {
    state := State.idle
    clkCount := 0
    bitIndex := 0
    rxShift := 0
    rxMeta := True
    rxSync := True
    dataReg := 0
    validReg := False
  } otherwise {
    switch(state) {
      is(State.idle) {
        clkCount := 0
        bitIndex := 0
        when(!rxSync) {
          state := State.start
        }
      }
      is(State.start) {
        when(clkCount === U(clksPerBit / 2, 16 bits)) {
          clkCount := 0
          state := Mux(rxSync, State.idle, State.data)
        } otherwise {
          clkCount := clkCount + 1
        }
      }
      is(State.data) {
        when(clkCount === U(clksPerBit - 1, 16 bits)) {
          clkCount := 0
          rxShift(bitIndex) := rxSync
          when(bitIndex === 7) {
            bitIndex := 0
            state := State.stop
          } otherwise {
            bitIndex := bitIndex + 1
          }
        } otherwise {
          clkCount := clkCount + 1
        }
      }
      is(State.stop) {
        when(clkCount === U(clksPerBit - 1, 16 bits)) {
          dataReg := rxShift
          validReg := rxSync
          clkCount := 0
          state := State.idle
        } otherwise {
          clkCount := clkCount + 1
        }
      }
    }
  }
}

class UartTx(clksPerBit: Int) extends Component {
  val io = new Bundle {
    val start = in Bool()
    val data = in Bits(8 bits)
    val tx = out Bool()
    val busy = out Bool()
    val reset = in Bool()
  }

  object State extends SpinalEnum {
    val idle, start, data, stop = newElement()
  }

  val state = Reg(State()) init State.idle
  val clkCount = Reg(UInt(16 bits)) init 0
  val bitIndex = Reg(UInt(3 bits)) init 0
  val txShift = Reg(Bits(8 bits)) init 0
  val txReg = Reg(Bool()) init True
  val busyReg = Reg(Bool()) init False

  io.tx := txReg
  io.busy := busyReg

  when(io.reset) {
    state := State.idle
    clkCount := 0
    bitIndex := 0
    txShift := 0
    txReg := True
    busyReg := False
  } otherwise {
    switch(state) {
      is(State.idle) {
        txReg := True
        busyReg := False
        clkCount := 0
        bitIndex := 0
        when(io.start) {
          txShift := io.data
          busyReg := True
          state := State.start
        }
      }
      is(State.start) {
        txReg := False
        when(clkCount === U(clksPerBit - 1, 16 bits)) {
          clkCount := 0
          state := State.data
        } otherwise {
          clkCount := clkCount + 1
        }
      }
      is(State.data) {
        txReg := txShift(bitIndex)
        when(clkCount === U(clksPerBit - 1, 16 bits)) {
          clkCount := 0
          when(bitIndex === 7) {
            bitIndex := 0
            state := State.stop
          } otherwise {
            bitIndex := bitIndex + 1
          }
        } otherwise {
          clkCount := clkCount + 1
        }
      }
      is(State.stop) {
        txReg := True
        when(clkCount === U(clksPerBit - 1, 16 bits)) {
          clkCount := 0
          state := State.idle
        } otherwise {
          clkCount := clkCount + 1
        }
      }
    }
  }
}

class Sha256Compress extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stateIn = in Bits(256 bits)
    val block = in Bits(512 bits)
    val done = out Bool()
    val workOut = out Bits(256 bits)
  }

  val a, b, c, d, e, f, g, h = Reg(UInt(32 bits)) init 0
  val w = Vec(Reg(UInt(32 bits)) init 0, 16)
  val round = Reg(UInt(6 bits)) init 0
  val busyReg = Reg(Bool()) init False

  val kVec = Vec(Sha256.K.map(Sha256.word))
  val wNext = (Sha256.smallSigma1(w(14)) + w(9) + Sha256.smallSigma0(w(1)) + w(0)).resize(32)
  val t1 = (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g) + kVec(round) + w(0)).resize(32)
  val t2 = (Sha256.bigSigma0(a) + Sha256.maj(a, b, c)).resize(32)
  val aNext = (t1 + t2).resize(32)
  val eNext = (d + t1).resize(32)
  val finalRound = busyReg && round === 63
  val finalWork = Sha256.concatWords(Seq(
    aNext,
    a,
    b,
    c,
    eNext,
    e,
    f,
    g
  ))

  io.done := finalRound
  io.workOut := finalWork

  when(io.reset) {
    a := 0; b := 0; c := 0; d := 0; e := 0; f := 0; g := 0; h := 0
    for (i <- 0 until 16) w(i) := 0
    round := 0
    busyReg := False
  } otherwise {
    when(io.start && (!busyReg || finalRound)) {
      a := io.stateIn(255 downto 224).asUInt
      b := io.stateIn(223 downto 192).asUInt
      c := io.stateIn(191 downto 160).asUInt
      d := io.stateIn(159 downto 128).asUInt
      e := io.stateIn(127 downto 96).asUInt
      f := io.stateIn(95 downto 64).asUInt
      g := io.stateIn(63 downto 32).asUInt
      h := io.stateIn(31 downto 0).asUInt

      for (i <- 0 until 16) {
        w(i) := Sha256.wordFromBits(io.block, i)
      }

      round := 0
      busyReg := True
    } elsewhen(busyReg) {
      for (i <- 0 until 15) {
        w(i) := w(i + 1)
      }
      w(15) := wNext

      h := g
      g := f
      f := e
      e := eNext
      d := c
      c := b
      b := a
      a := aNext

      when(round === 63) {
        busyReg := False
      } otherwise {
        round := round + 1
      }
    }
  }
}

class BitcoinHashCore extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stop = in Bool()
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val candidateMode = in UInt(2 bits)
    val startNonce = in UInt(32 bits)
    val nonceStride = in UInt(32 bits)
    val running = out Bool()
    val found = out Bool()
    val foundNonce = out UInt(32 bits)
    val currentNonce = out UInt(32 bits)
  }

  object State extends SpinalEnum {
    val idle, firstStart, firstWait, secondWait, report = newElement()
  }

  val state = Reg(State()) init State.idle
  val shaStart = Bool()
  val shaStateIn = Bits(256 bits)
  val shaBlock = Bits(512 bits)
  val jobMidstateReg = Reg(Bits(256 bits)) init 0
  val jobTailReg = Reg(Bits(96 bits)) init 0
  val jobCandidateModeReg = Reg(UInt(2 bits)) init 3
  val foundNonceReg = Reg(UInt(32 bits)) init 0
  val currentNonceReg = Reg(UInt(32 bits)) init 0

  shaStart := False
  shaStateIn := 0
  shaBlock := 0

  val flushPipeline = io.stop || (io.start && state =/= State.idle)

  val sha = new Sha256Compress
  sha.io.reset := io.reset || flushPipeline
  sha.io.start := shaStart
  sha.io.stateIn := shaStateIn
  sha.io.block := shaBlock

  val shaIv = B(Sha256.Iv.map(v => B(v, 32 bits)).reduce(_ ## _))
  def addFeedForward(base: Bits, work: Bits): Bits =
    Sha256.concatWords((0 until 8).map(i =>
      (Sha256.wordFromDigest(base, i) + Sha256.wordFromDigest(work, i)).resize(32)
    ))

  val firstDigest = addFeedForward(jobMidstateReg, sha.io.workOut)
  val finalDigest = addFeedForward(shaIv, sha.io.workOut)
  val reversedDigest = Sha256.reverseBytes256(finalDigest)
  val candidateAlwaysSelected = jobCandidateModeReg === U(0, 2 bits)
  val quick3TargetSelected = jobCandidateModeReg === U(1, 2 bits)
  val quick21TargetSelected = jobCandidateModeReg === U(2, 2 bits)
  val quick23TargetSelected = jobCandidateModeReg === U(3, 2 bits)
  val quick3MeetsTarget = reversedDigest(255 downto 253) === B(0, 3 bits)
  val quick21MeetsTarget = reversedDigest(255 downto 235) === B(0, 21 bits)
  val quick23MeetsTarget = reversedDigest(255 downto 233) === B(0, 23 bits)
  val digestMeetsTarget =
    candidateAlwaysSelected ||
      (quick3TargetSelected && quick3MeetsTarget) ||
      (quick21TargetSelected && quick21MeetsTarget) ||
      (quick23TargetSelected && quick23MeetsTarget)

  def firstBlockForNonce(nonce: UInt): Bits =
    jobTailReg(95 downto 64) ## jobTailReg(63 downto 32) ## jobTailReg(31 downto 0) ## nonce.asBits ##
      B"32'h80000000" ## B"32'h00000000" ## B"32'h00000000" ## B"32'h00000000" ##
      B"32'h00000000" ## B"32'h00000000" ## B"32'h00000000" ## B"32'h00000000" ##
      B"32'h00000000" ## B"32'h00000000" ## B"32'h00000000" ## B"32'h00000280"
  val firstBlock = firstBlockForNonce(currentNonceReg)
  def secondBlockForDigest(digest: Bits): Bits =
    digest ##
      B"32'h80000000" ## B"32'h00000000" ## B"32'h00000000" ## B"32'h00000000" ##
      B"32'h00000000" ## B"32'h00000000" ## B"32'h00000000" ## B"32'h00000100"

  io.running := state =/= State.idle && state =/= State.report
  io.found := state === State.report
  io.foundNonce := foundNonceReg
  io.currentNonce := currentNonceReg

  when(io.reset) {
    state := State.idle
    jobMidstateReg := 0
    jobTailReg := 0
    jobCandidateModeReg := 3
    foundNonceReg := 0
    currentNonceReg := 0
  } otherwise {
    when(io.stop) {
      state := State.idle
    } elsewhen(io.start) {
      state := State.firstStart
      jobMidstateReg := io.midstate
      jobTailReg := io.tail
      jobCandidateModeReg := io.candidateMode
      currentNonceReg := io.startNonce
    } otherwise {
      switch(state) {
        is(State.idle) {
        }
        is(State.firstStart) {
          shaStateIn := jobMidstateReg
          shaBlock := firstBlock
          shaStart := True
          state := State.firstWait
        }
        is(State.firstWait) {
          when(sha.io.done) {
            shaStateIn := shaIv
            shaBlock := secondBlockForDigest(firstDigest)
            shaStart := True
            state := State.secondWait
          }
        }
        is(State.secondWait) {
          when(sha.io.done) {
            val nextNonce = currentNonceReg + io.nonceStride
            when(digestMeetsTarget) {
              foundNonceReg := currentNonceReg
              state := State.report
            } otherwise {
              shaStateIn := jobMidstateReg
              shaBlock := firstBlockForNonce(nextNonce)
              shaStart := True
              currentNonceReg := nextNonce
              state := State.firstWait
            }
          }
        }
        is(State.report) {
        }
      }
    }
  }
}

class Top(clksPerBit: Int = 234, resetCounterBits: Int = 24) extends Component {
  require(clksPerBit > 1, "clksPerBit must leave room for UART start-bit centering")
  require(resetCounterBits > 0, "resetCounterBits must be positive")

  setDefinitionName("top")
  noIoPrefix()

  val io = new Bundle {
    val clk = in Bool()
    val uart_rx_pin = in Bool()
    val uart_tx_pin = out Bool()
    val led = out Bits(6 bits)
  }

  val coreArea = new ClockingArea(ClockDomain(io.clk, config = ClockDomainConfig(resetKind = BOOT))) {
    val ClksPerBit = clksPerBit
    val JobBytes = 76
    val FoundRespBytes = 5
    val EchoRespBytes = 77
    val LaneCount = 4
    val CandidateAlways = U(0, 2 bits)
    val CandidateQuick3 = U(1, 2 bits)
    val CandidateQuick21 = U(2, 2 bits)
    val CandidateQuick23 = U(3, 2 bits)
    val Quick3Target = B"256'h1fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val Quick21Target = B"256'h000007ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val Quick23Target = B"256'h000001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

    val resetCounter = Reg(UInt(resetCounterBits bits)) init 0
    val reset = !resetCounter.msb
    when(!resetCounter.msb) {
      resetCounter := resetCounter + 1
    }

    val rx = new UartRx(ClksPerBit)
    rx.io.reset := reset
    rx.io.rx := io.uart_rx_pin

    val tx = new UartTx(ClksPerBit)
    tx.io.reset := reset

    val cores = (0 until LaneCount).map(_ => new BitcoinHashCore)
    cores.foreach(_.io.reset := reset)

    object RxState extends SpinalEnum {
      val sync0, sync1, cmd, payload = newElement()
    }

    object TxState extends SpinalEnum {
      val idle, send, waitBusy = newElement()
    }

    val rxState = Reg(RxState()) init RxState.sync0
    val payloadCount = Reg(UInt(7 bits)) init 0
    val command = Reg(Bits(8 bits)) init 0
    val midstate = Reg(Bits(256 bits)) init 0
    val tail = Reg(Bits(96 bits)) init 0
    val target = Reg(Bits(256 bits)) init 0
    val targetIsAllOnes = Reg(Bool()) init True
    val targetIsQuick3 = Reg(Bool()) init False
    val targetIsQuick21 = Reg(Bool()) init False
    val targetIsQuick23 = Reg(Bool()) init False
    val candidateMode = Reg(UInt(2 bits)) init CandidateQuick23
    val coreStart = Reg(Bool()) init False
    val coreStop = Reg(Bool()) init False
    val coreStartPending = Reg(Bool()) init False
    val echoToggle = Reg(Bool()) init False

    for ((core, lane) <- cores.zipWithIndex) {
      core.io.start := coreStart
      core.io.stop := coreStop
      core.io.midstate := midstate
      core.io.tail := tail
      core.io.candidateMode := candidateMode
      core.io.startNonce := U(lane, 32 bits)
      core.io.nonceStride := U(LaneCount, 32 bits)
    }

    val runningAny = cores.map(_.io.running).reduce(_ || _)
    val foundAny = cores.map(_.io.found).reduce(_ || _)
    val currentNonce = UInt(32 bits)
    currentNonce := cores(0).io.currentNonce

    val selectedFoundNonce = UInt(32 bits)
    selectedFoundNonce := cores(LaneCount - 1).io.foundNonce
    for (lane <- (0 until LaneCount - 1).reverse) {
      when(cores(lane).io.found) {
        selectedFoundNonce := cores(lane).io.foundNonce
      }
    }

    when(reset) {
      rxState := RxState.sync0
      payloadCount := 0
      command := 0
      coreStart := False
      coreStop := False
      coreStartPending := False
      midstate := 0
      tail := 0
      target := 0
      targetIsAllOnes := True
      targetIsQuick3 := False
      targetIsQuick21 := False
      targetIsQuick23 := False
      candidateMode := CandidateQuick23
      echoToggle := False
    } otherwise {
      coreStart := False
      coreStop := False

      when(coreStartPending) {
        coreStart := True
        coreStartPending := False
      }

      when(rx.io.valid) {
        switch(rxState) {
          is(RxState.sync0) {
            rxState := Mux(rx.io.data === B"8'h54", RxState.sync1, RxState.sync0)
          }
          is(RxState.sync1) {
            rxState := Mux(rx.io.data === B"8'h4e", RxState.cmd, RxState.sync0)
          }
          is(RxState.cmd) {
            command := rx.io.data
            payloadCount := 0
            when(rx.io.data === B"8'h53") {
              coreStop := True
              rxState := RxState.sync0
            } elsewhen(rx.io.data === B"8'h48") {
              midstate := B"256'hbc909a336358bff090ccac7d1e59caa8c3c8d8e94f0103c896b187364719f91b"
              tail := B"96'h4b1e5e4a29ab5f49ffff001d"
              target := B"256'hffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
              candidateMode := CandidateAlways
              coreStartPending := True
              rxState := RxState.sync0
            } elsewhen(rx.io.data === B"8'h4a" || rx.io.data === B"8'h45") {
              targetIsAllOnes := True
              targetIsQuick3 := True
              targetIsQuick21 := True
              targetIsQuick23 := True
              rxState := RxState.payload
            } otherwise {
              rxState := RxState.sync0
            }
          }
          is(RxState.payload) {
            val targetByteIndex = (payloadCount - U(44, 7 bits)).resize(5)
            val targetMatchesAllOnes = rx.io.data === B"8'hff"
            val targetMatchesQuick3 = rx.io.data === Sha256.byteFromMsb(Quick3Target, 32, targetByteIndex)
            val targetMatchesQuick21 = rx.io.data === Sha256.byteFromMsb(Quick21Target, 32, targetByteIndex)
            val targetMatchesQuick23 = rx.io.data === Sha256.byteFromMsb(Quick23Target, 32, targetByteIndex)
            val nextTargetIsAllOnes = targetIsAllOnes && targetMatchesAllOnes
            val nextTargetIsQuick3 = targetIsQuick3 && targetMatchesQuick3
            val nextTargetIsQuick21 = targetIsQuick21 && targetMatchesQuick21
            val nextTargetIsQuick23 = targetIsQuick23 && targetMatchesQuick23

            when(payloadCount < 32) {
              midstate := midstate(247 downto 0) ## rx.io.data
            } elsewhen(payloadCount < 44) {
              tail := tail(87 downto 0) ## rx.io.data
            } elsewhen(payloadCount < 76) {
              target := target(247 downto 0) ## rx.io.data
              targetIsAllOnes := nextTargetIsAllOnes
              targetIsQuick3 := nextTargetIsQuick3
              targetIsQuick21 := nextTargetIsQuick21
              targetIsQuick23 := nextTargetIsQuick23
            }

            when(payloadCount === JobBytes - 1) {
              when(command === B"8'h4a") {
                candidateMode := CandidateQuick23
                when(nextTargetIsAllOnes) {
                  candidateMode := CandidateAlways
                } elsewhen(nextTargetIsQuick3) {
                  candidateMode := CandidateQuick3
                } elsewhen(nextTargetIsQuick21) {
                  candidateMode := CandidateQuick21
                }
                coreStartPending := True
              } elsewhen(command === B"8'h45") {
                echoToggle := !echoToggle
              }
              rxState := RxState.sync0
            } otherwise {
              payloadCount := payloadCount + 1
            }
          }
        }
      }
    }

    def foundResponseByte(index: UInt, nonce: UInt): Bits = {
      val nonceBytes = (0 until 4).map(i => nonce.asBits(31 - i * 8 downto 24 - i * 8))
      val bytes = Vec(Seq(B"8'h46") ++ nonceBytes)
      bytes(index.resized)
    }

    def echoResponseByte(index: UInt): Bits = {
      val midstateBytes = (0 until 32).map(i => midstate(255 - i * 8 downto 248 - i * 8))
      val tailBytes = (0 until 12).map(i => tail(95 - i * 8 downto 88 - i * 8))
      val targetBytes = (0 until 32).map(i => target(255 - i * 8 downto 248 - i * 8))
      val bytes = Vec(Seq(B"8'h45") ++ midstateBytes ++ tailBytes ++ targetBytes)
      bytes(index.resized)
    }

    val txState = Reg(TxState()) init TxState.idle
    val txIndex = Reg(UInt(7 bits)) init 0
    val txStart = Reg(Bool()) init False
    val txData = Reg(Bits(8 bits)) init B"8'hff"
    val foundSeen = Reg(Bool()) init False
    val echoSeenToggle = Reg(Bool()) init False
    val txEcho = Reg(Bool()) init False
    val txFoundNonce = Reg(UInt(32 bits)) init 0

    tx.io.start := txStart
    tx.io.data := txData
    io.uart_tx_pin := tx.io.tx

    when(reset) {
      txState := TxState.idle
      txIndex := 0
      txStart := False
      txData := B"8'hff"
      foundSeen := False
      echoSeenToggle := False
      txEcho := False
      txFoundNonce := 0
    } otherwise {
      txStart := False

      when(!foundAny) {
        foundSeen := False
      }

      switch(txState) {
        is(TxState.idle) {
          when(echoSeenToggle =/= echoToggle) {
            txIndex := 0
            txEcho := True
            txState := TxState.send
            echoSeenToggle := echoToggle
          } elsewhen(foundAny && !foundSeen) {
            txIndex := 0
            txEcho := False
            txFoundNonce := selectedFoundNonce
            txState := TxState.send
            foundSeen := True
          }
        }
        is(TxState.send) {
          when(!tx.io.busy) {
            txData := Mux(txEcho, echoResponseByte(txIndex), foundResponseByte(txIndex, txFoundNonce))
            txStart := True
            txState := TxState.waitBusy
          }
        }
        is(TxState.waitBusy) {
          when(tx.io.busy) {
            when((!txEcho && txIndex === FoundRespBytes - 1) || (txEcho && txIndex === EchoRespBytes - 1)) {
              txState := TxState.idle
            } otherwise {
              txIndex := txIndex + 1
              txState := TxState.send
            }
          }
        }
      }
    }

    io.led(0) := !runningAny
    io.led(1) := !foundAny
    io.led(2) := !currentNonce(20)
    io.led(3) := !currentNonce(21)
    io.led(4) := !currentNonce(22)
    io.led(5) := !currentNonce(23)
  }
}

object GenerateVerilog extends App {
  SpinalConfig(
    targetDirectory = "build/spinal",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new Top)
}

object GenerateSimVerilog extends App {
  SpinalConfig(
    targetDirectory = "build/spinal-sim",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new Top(clksPerBit = 8, resetCounterBits = 4))
}
