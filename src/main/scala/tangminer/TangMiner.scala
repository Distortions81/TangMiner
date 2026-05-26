package tangminer

import spinal.core._
import spinal.lib._

case class GowinClockProfile(
  name: String,
  clockMhz: Double,
  clksPerBit: Int,
  usePll: Boolean,
  idivSel: Int = 0,
  fbdivSel: Int = 0,
  odivSel: Int = 0
)

case class TangMinerHardwareOptions(
  sharedRoundConstant: Boolean = true,
  enableEcho: Boolean = true,
  enableHardcodedJob: Boolean = true,
  fixedCandidateMode: Option[Int] = None,
  wideLaneBlock: Boolean = false,
  registerPassOutputs: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false
) {
  fixedCandidateMode.foreach(mode =>
    require(mode >= 0 && mode <= 5, s"fixedCandidateMode must be 0..5, got $mode")
  )
  require(!(twoCycleRound && threeCycleRound), "twoCycleRound and threeCycleRound are mutually exclusive")
}

object GowinClockProfiles {
  val Profiles = Map(
    "27m" -> GowinClockProfile("27m", 27.0, 234, usePll = false),
    "54m" -> GowinClockProfile("54m", 54.0, 469, usePll = true, idivSel = 0, fbdivSel = 1, odivSel = 8),
    "67m5" -> GowinClockProfile("67m5", 67.5, 586, usePll = true, idivSel = 1, fbdivSel = 4, odivSel = 8),
    "81m" -> GowinClockProfile("81m", 81.0, 703, usePll = true, idivSel = 0, fbdivSel = 2, odivSel = 8),
    "84m" -> GowinClockProfile("84m", 84.0, 729, usePll = true, idivSel = 8, fbdivSel = 27, odivSel = 8),
    "85m5" -> GowinClockProfile("85m5", 85.5, 742, usePll = true, idivSel = 5, fbdivSel = 18, odivSel = 8),
    "90m" -> GowinClockProfile("90m", 90.0, 781, usePll = true, idivSel = 2, fbdivSel = 9, odivSel = 8),
    "100m286" -> GowinClockProfile("100m286", 100.286, 871, usePll = true, idivSel = 6, fbdivSel = 25, odivSel = 8),
    "111m" -> GowinClockProfile("111m", 111.0, 964, usePll = true, idivSel = 8, fbdivSel = 36, odivSel = 8),
    "120m" -> GowinClockProfile("120m", 120.0, 1042, usePll = true, idivSel = 8, fbdivSel = 39, odivSel = 8),
    "123m" -> GowinClockProfile("123m", 123.0, 1068, usePll = true, idivSel = 8, fbdivSel = 40, odivSel = 8),
    "124m875" -> GowinClockProfile("124m875", 124.875, 1084, usePll = true, idivSel = 7, fbdivSel = 36, odivSel = 8),
    "126m" -> GowinClockProfile("126m", 126.0, 1094, usePll = true, idivSel = 2, fbdivSel = 13, odivSel = 8),
    "130m5" -> GowinClockProfile("130m5", 130.5, 1133, usePll = true, idivSel = 5, fbdivSel = 28, odivSel = 8),
    "135m" -> GowinClockProfile("135m", 135.0, 1172, usePll = true, idivSel = 1, fbdivSel = 9, odivSel = 8),
    "150m" -> GowinClockProfile("150m", 150.0, 1302, usePll = true, idivSel = 8, fbdivSel = 49, odivSel = 8)
  )

  def byName(name: String): GowinClockProfile =
    Profiles.getOrElse(
      name,
      throw new IllegalArgumentException(s"unsupported clock profile '$name'; supported profiles: ${Profiles.keys.toSeq.sorted.mkString(", ")}")
    )
}

class GowinRpllFrom27Mhz(profile: GowinClockProfile) extends BlackBox {
  setDefinitionName("rPLL")
  noIoPrefix()

  addGeneric("FCLKIN", "27.0")
  addGeneric("IDIV_SEL", profile.idivSel)
  addGeneric("FBDIV_SEL", profile.fbdivSel)
  addGeneric("ODIV_SEL", profile.odivSel)

  val io = new Bundle {
    val CLKOUT = out Bool()
    val CLKOUTP = out Bool()
    val CLKOUTD = out Bool()
    val CLKOUTD3 = out Bool()
    val LOCK = out Bool()
    val CLKIN = in Bool()
    val CLKFB = in Bool()
    val FBDSEL = in Bits(6 bits)
    val IDSEL = in Bits(6 bits)
    val ODSEL = in Bits(6 bits)
    val DUTYDA = in Bits(4 bits)
    val PSDA = in Bits(4 bits)
    val FDLY = in Bits(4 bits)
    val RESET = in Bool()
    val RESET_P = in Bool()
  }
}

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

class Sha256CompressWords(
  registerOutput: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeResetFanout: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val kWord = in UInt(32 bits)
    val stateIn = in Bits(256 bits)
    val words = in Vec(UInt(32 bits), 16)
    val done = out Bool()
    val roundOut = out UInt(6 bits)
    val workOut = out Bits(256 bits)
  }

  val a, b, c, d, e, f, g, h = Reg(UInt(32 bits)) init 0
  val w = Vec(Reg(UInt(32 bits)) init 0, 16)
  val wRound = Reg(UInt(32 bits)) init 0
  val round = Reg(UInt(6 bits)) init 0
  val busyReg = Reg(Bool()) init False
  val kWords = Vec(Sha256.K.map(Sha256.word))
  val kWordReg = Reg(UInt(32 bits)) init U(Sha256.K.head, 32 bits)
  val selectedKWord = if (registerRoundConstant) kWordReg else io.kWord

  def loadState(): Unit = {
    a := io.stateIn(255 downto 224).asUInt
    b := io.stateIn(223 downto 192).asUInt
    c := io.stateIn(191 downto 160).asUInt
    d := io.stateIn(159 downto 128).asUInt
    e := io.stateIn(127 downto 96).asUInt
    f := io.stateIn(95 downto 64).asUInt
    g := io.stateIn(63 downto 32).asUInt
    h := io.stateIn(31 downto 0).asUInt

    for (i <- 0 until 16) {
      w(i) := io.words(i)
    }
    wRound := io.words(0)
    if (registerRoundConstant) {
      kWordReg := kWords(0)
    }

    round := 0
    busyReg := True
  }

  def clearState(): Unit = {
    if (!minimizeResetFanout) {
      a := 0; b := 0; c := 0; d := 0; e := 0; f := 0; g := 0; h := 0
      for (i <- 0 until 16) w(i) := 0
      wRound := 0
    }
    round := 0
    busyReg := False
  }

  val wNext = (Sha256.smallSigma1(w(14)) + w(9) + Sha256.smallSigma0(w(1)) + w(0)).resize(32)
  val t1 = (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g) + selectedKWord + wRound).resize(32)
  val t2 = (Sha256.bigSigma0(a) + Sha256.maj(a, b, c)).resize(32)
  val aNext = (t1 + t2).resize(32)
  val eNext = (d + t1).resize(32)
  val finalRound = busyReg && round === 63
  val finalWork = Sha256.concatWords(Seq(aNext, a, b, c, eNext, e, f, g))

  io.roundOut := round

  if (threeCycleRound) {
    object Phase extends SpinalEnum {
      val prepare, sum, update = newElement()
    }

    val phase = Reg(Phase()) init Phase.prepare
    val t1LeftReg = Reg(UInt(32 bits)) init 0
    val t1RightReg = Reg(UInt(32 bits)) init 0
    val t1Reg = Reg(UInt(32 bits)) init 0
    val t2Reg = Reg(UInt(32 bits)) init 0
    val wNextLeftReg = Reg(UInt(32 bits)) init 0
    val wNextRightReg = Reg(UInt(32 bits)) init 0
    val wNextReg = Reg(UInt(32 bits)) init 0
    val dReg = Reg(UInt(32 bits)) init 0
    val doneReg = Reg(Bool()) init False
    val workOutReg = Reg(Bits(256 bits)) init 0
    val t1Left = (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g)).resize(32)
    val t1Right = (selectedKWord + wRound).resize(32)
    val t1Combined = (t1LeftReg + t1RightReg).resize(32)
    val wNextLeft = (Sha256.smallSigma1(w(14)) + w(9)).resize(32)
    val wNextRight = (Sha256.smallSigma0(w(1)) + w(0)).resize(32)
    val wNextCombined = (wNextLeftReg + wNextRightReg).resize(32)
    val aSplitNext = (t1Reg + t2Reg).resize(32)
    val eSplitNext = (dReg + t1Reg).resize(32)
    val finalSplitWork = Sha256.concatWords(Seq(aSplitNext, a, b, c, eSplitNext, e, f, g))

    io.done := doneReg
    io.workOut := workOutReg

    when(io.reset) {
      clearState()
      phase := Phase.prepare
      if (!minimizeResetFanout) {
        t1LeftReg := 0
        t1RightReg := 0
        t1Reg := 0
        t2Reg := 0
        wNextLeftReg := 0
        wNextRightReg := 0
        wNextReg := 0
        dReg := 0
        workOutReg := 0
      }
      doneReg := False
    } otherwise {
      doneReg := False
      when(io.start && !busyReg) {
        loadState()
        phase := Phase.prepare
      } elsewhen(busyReg) {
        switch(phase) {
          is(Phase.prepare) {
            t1LeftReg := t1Left
            t1RightReg := t1Right
            t2Reg := t2
            wNextLeftReg := wNextLeft
            wNextRightReg := wNextRight
            dReg := d
            phase := Phase.sum
          }
          is(Phase.sum) {
            t1Reg := t1Combined
            wNextReg := wNextCombined
            phase := Phase.update
          }
          is(Phase.update) {
            for (i <- 0 until 15) {
              w(i) := w(i + 1)
            }
            w(15) := wNextReg
            wRound := w(1)

            h := g
            g := f
            f := e
            e := eSplitNext
            d := c
            c := b
            b := a
            a := aSplitNext

            when(round === 63) {
              busyReg := False
              round := 0
              phase := Phase.prepare
              doneReg := True
              workOutReg := finalSplitWork
            } otherwise {
              if (registerRoundConstant) {
                kWordReg := kWords((round + 1).resized)
              }
              round := round + 1
              phase := Phase.prepare
            }
          }
        }
      }
    }
  } else if (twoCycleRound) {
    object Phase extends SpinalEnum {
      val compute, update = newElement()
    }

    val phase = Reg(Phase()) init Phase.compute
    val t1Reg = Reg(UInt(32 bits)) init 0
    val t2Reg = Reg(UInt(32 bits)) init 0
    val wNextReg = Reg(UInt(32 bits)) init 0
    val doneReg = Reg(Bool()) init False
    val workOutReg = Reg(Bits(256 bits)) init 0
    val aSplitNext = (t1Reg + t2Reg).resize(32)
    val eSplitNext = (d + t1Reg).resize(32)
    val finalSplitWork = Sha256.concatWords(Seq(aSplitNext, a, b, c, eSplitNext, e, f, g))

    io.done := doneReg
    io.workOut := workOutReg

    when(io.reset) {
      clearState()
      phase := Phase.compute
      if (!minimizeResetFanout) {
        t1Reg := 0
        t2Reg := 0
        wNextReg := 0
        workOutReg := 0
      }
      doneReg := False
    } otherwise {
      doneReg := False
      when(io.start && !busyReg) {
        loadState()
        phase := Phase.compute
      } elsewhen(busyReg) {
        switch(phase) {
          is(Phase.compute) {
            t1Reg := t1
            t2Reg := t2
            wNextReg := wNext
            phase := Phase.update
          }
          is(Phase.update) {
            for (i <- 0 until 15) {
              w(i) := w(i + 1)
            }
            w(15) := wNextReg
            wRound := w(1)

            h := g
            g := f
            f := e
            e := eSplitNext
            d := c
            c := b
            b := a
            a := aSplitNext

            when(round === 63) {
              busyReg := False
              round := 0
              phase := Phase.compute
              doneReg := True
              workOutReg := finalSplitWork
            } otherwise {
              if (registerRoundConstant) {
                kWordReg := kWords((round + 1).resized)
              }
              round := round + 1
              phase := Phase.compute
            }
          }
        }
      }
    }
  } else {
    if (registerOutput) {
      val doneReg = Reg(Bool()) init False
      val workOutReg = Reg(Bits(256 bits)) init 0

      io.done := doneReg
      io.workOut := workOutReg

      when(io.reset) {
        doneReg := False
        if (!minimizeResetFanout) {
          workOutReg := 0
        }
      } otherwise {
        doneReg := finalRound
        when(finalRound) {
          workOutReg := finalWork
        }
      }
    } else {
      io.done := finalRound
      io.workOut := finalWork
    }

    when(io.reset) {
      clearState()
    } otherwise {
      when(io.start && (!busyReg || finalRound)) {
        loadState()
      } elsewhen(busyReg) {
        for (i <- 0 until 15) {
          w(i) := w(i + 1)
        }
        w(15) := wNext
        wRound := w(1)

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
          if (registerRoundConstant) {
            kWordReg := kWords((round + 1).resized)
          }
          round := round + 1
        }
      }
    }
  }
}

object Sha256Pass {
  def addFeedForward(base: Bits, work: Bits): Bits =
    Sha256.concatWords((0 until 8).map(i =>
      (Sha256.wordFromDigest(base, i) + Sha256.wordFromDigest(work, i)).resize(32)
    ))

  def ivBits: Bits = B(Sha256.Iv.map(v => B(v, 32 bits)).reduce(_ ## _))
}

class Sha256BitcoinFirstPass(
  registerOutputs: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val kWord = in UInt(32 bits)
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val nonce = in UInt(32 bits)
    val done = out Bool()
    val round = out UInt(6 bits)
    val digest = out Bits(256 bits)
  }

  val core = new Sha256CompressWords(registerOutputs, twoCycleRound, threeCycleRound, registerRoundConstant, minimizeShaReset)
  val words = Vec(Seq(
    io.tail(95 downto 64).asUInt,
    io.tail(63 downto 32).asUInt,
    io.tail(31 downto 0).asUInt,
    io.nonce,
    U(BigInt("80000000", 16), 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(BigInt("00000280", 16), 32 bits)
  ))

  core.io.reset := io.reset
  core.io.start := io.start
  core.io.kWord := io.kWord
  core.io.stateIn := io.midstate
  core.io.words := words

  io.round := core.io.roundOut
  val digest = Sha256Pass.addFeedForward(io.midstate, core.io.workOut)
  if (registerOutputs) {
    val doneReg = Reg(Bool()) init False
    val digestReg = Reg(Bits(256 bits)) init 0

    io.done := doneReg
    io.digest := digestReg

    when(io.reset) {
      doneReg := False
      digestReg := 0
    } otherwise {
      doneReg := core.io.done
      when(core.io.done) {
        digestReg := digest
      }
    }
  } else {
    io.done := core.io.done
    io.digest := digest
  }
}

class Sha256BitcoinSecondPass(
  registerOutputs: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val kWord = in UInt(32 bits)
    val firstDigest = in Bits(256 bits)
    val done = out Bool()
    val round = out UInt(6 bits)
    val digestLow32 = out Bits(32 bits)
  }

  val core = new Sha256CompressWords(registerOutputs, twoCycleRound, threeCycleRound, registerRoundConstant, minimizeShaReset)
  val shaIv = Sha256Pass.ivBits
  val words = Vec(Seq(
    Sha256.wordFromDigest(io.firstDigest, 0),
    Sha256.wordFromDigest(io.firstDigest, 1),
    Sha256.wordFromDigest(io.firstDigest, 2),
    Sha256.wordFromDigest(io.firstDigest, 3),
    Sha256.wordFromDigest(io.firstDigest, 4),
    Sha256.wordFromDigest(io.firstDigest, 5),
    Sha256.wordFromDigest(io.firstDigest, 6),
    Sha256.wordFromDigest(io.firstDigest, 7),
    U(BigInt("80000000", 16), 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(0, 32 bits),
    U(BigInt("00000100", 16), 32 bits)
  ))

  core.io.reset := io.reset
  core.io.start := io.start
  core.io.kWord := io.kWord
  core.io.stateIn := shaIv
  core.io.words := words

  io.round := core.io.roundOut
  val digestLow32 = (Sha256.word(Sha256.Iv(7)) + Sha256.wordFromDigest(core.io.workOut, 7)).resize(32).asBits
  if (registerOutputs) {
    val doneReg = Reg(Bool()) init False
    val digestLow32Reg = Reg(Bits(32 bits)) init 0

    io.done := doneReg
    io.digestLow32 := digestLow32Reg

    when(io.reset) {
      doneReg := False
      digestLow32Reg := 0
    } otherwise {
      doneReg := core.io.done
      when(core.io.done) {
        digestLow32Reg := digestLow32
      }
    }
  } else {
    io.done := core.io.done
    io.digestLow32 := digestLow32
  }
}

class BitcoinHashCore(options: TangMinerHardwareOptions = TangMinerHardwareOptions()) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stop = in Bool()
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val candidateMode = in UInt(3 bits)
    val startNonce = in UInt(32 bits)
    val nonceStride = in UInt(32 bits)
    val running = out Bool()
    val found = out Bool()
    val foundNonce = out UInt(32 bits)
    val currentNonce = out UInt(32 bits)
  }

  object State extends SpinalEnum {
    val idle, firstStart, run, report = newElement()
  }

  val state = Reg(State()) init State.idle
  val shaFirstStart = Bool()
  val shaSecondStart = Bool()
  val jobMidstateReg = Reg(Bits(256 bits)) init 0
  val jobTailReg = Reg(Bits(96 bits)) init 0
  val jobCandidateModeReg = Reg(UInt(3 bits)) init 3
  val firstNonceReg = Reg(UInt(32 bits)) init 0
  val secondNonceReg = Reg(UInt(32 bits)) init 0
  val checkValidReg = Reg(Bool()) init False
  val checkDigestLow32Reg = Reg(Bits(32 bits)) init 0
  val checkNonceReg = Reg(UInt(32 bits)) init 0
  val checkCandidateModeReg = Reg(UInt(3 bits)) init 3
  val foundNonceReg = Reg(UInt(32 bits)) init 0
  val currentNonceReg = Reg(UInt(32 bits)) init 0

  shaFirstStart := False
  shaSecondStart := False

  val flushPipeline = io.stop || (io.start && state =/= State.idle)

  val shaFirst = new Sha256BitcoinFirstPass(
    options.registerPassOutputs,
    options.twoCycleRound,
    options.threeCycleRound,
    options.registerRoundConstant,
    options.minimizeShaReset
  )
  shaFirst.io.reset := io.reset || flushPipeline
  shaFirst.io.start := shaFirstStart
  shaFirst.io.midstate := jobMidstateReg
  shaFirst.io.tail := jobTailReg
  shaFirst.io.nonce := currentNonceReg

  val shaSecond = new Sha256BitcoinSecondPass(
    options.registerPassOutputs,
    options.twoCycleRound,
    options.threeCycleRound,
    options.registerRoundConstant,
    options.minimizeShaReset
  )
  shaSecond.io.reset := io.reset || flushPipeline
  shaSecond.io.start := shaSecondStart
  shaSecond.io.firstDigest := shaFirst.io.digest

  if (options.registerRoundConstant) {
    shaFirst.io.kWord := 0
    shaSecond.io.kWord := 0
  } else {
    val kVec = Vec(Sha256.K.map(Sha256.word))
    val firstKWord = kVec(shaFirst.io.round)
    val secondKWord = if (options.sharedRoundConstant) firstKWord else kVec(shaSecond.io.round)
    shaFirst.io.kWord := firstKWord
    shaSecond.io.kWord := secondKWord
  }

  val candidateAlwaysSelected = checkCandidateModeReg === U(0, 3 bits)
  val quick3TargetSelected = checkCandidateModeReg === U(1, 3 bits)
  val quick21TargetSelected = checkCandidateModeReg === U(2, 3 bits)
  val quick23TargetSelected = checkCandidateModeReg === U(3, 3 bits)
  val quick26TargetSelected = checkCandidateModeReg === U(4, 3 bits)
  val quick14TargetSelected = checkCandidateModeReg === U(5, 3 bits)
  val quick3MeetsTarget = checkDigestLow32Reg(7 downto 5) === B(0, 3 bits)
  val quick14MeetsTarget =
    checkDigestLow32Reg(7 downto 0) === B(0, 8 bits) &&
      checkDigestLow32Reg(15 downto 10) === B(0, 6 bits)
  val quick21MeetsTarget =
    checkDigestLow32Reg(7 downto 0) === B(0, 8 bits) &&
      checkDigestLow32Reg(15 downto 8) === B(0, 8 bits) &&
      checkDigestLow32Reg(23 downto 19) === B(0, 5 bits)
  val quick23MeetsTarget =
    checkDigestLow32Reg(7 downto 0) === B(0, 8 bits) &&
      checkDigestLow32Reg(15 downto 8) === B(0, 8 bits) &&
      checkDigestLow32Reg(23 downto 17) === B(0, 7 bits)
  val quick26MeetsTarget =
    checkDigestLow32Reg(7 downto 0) === B(0, 8 bits) &&
      checkDigestLow32Reg(15 downto 8) === B(0, 8 bits) &&
      checkDigestLow32Reg(23 downto 16) === B(0, 8 bits) &&
      checkDigestLow32Reg(31 downto 30) === B(0, 2 bits)
  def fixedCandidateMeetsTarget(mode: Int): Bool = mode match {
    case 0 => True
    case 1 => quick3MeetsTarget
    case 2 => quick21MeetsTarget
    case 3 => quick23MeetsTarget
    case 4 => quick26MeetsTarget
    case 5 => quick14MeetsTarget
  }
  val checkDigestMeetsTarget = options.fixedCandidateMode match {
    case Some(mode) => fixedCandidateMeetsTarget(mode)
    case None =>
      candidateAlwaysSelected ||
        (quick3TargetSelected && quick3MeetsTarget) ||
        (quick14TargetSelected && quick14MeetsTarget) ||
        (quick21TargetSelected && quick21MeetsTarget) ||
        (quick23TargetSelected && quick23MeetsTarget) ||
        (quick26TargetSelected && quick26MeetsTarget)
  }

  io.running := state =/= State.idle && state =/= State.report
  io.found := state === State.report
  io.foundNonce := foundNonceReg
  io.currentNonce := currentNonceReg

  when(io.reset) {
    state := State.idle
    jobMidstateReg := 0
    jobTailReg := 0
    jobCandidateModeReg := 3
    firstNonceReg := 0
    secondNonceReg := 0
    checkValidReg := False
    checkDigestLow32Reg := 0
    checkNonceReg := 0
    checkCandidateModeReg := 3
    foundNonceReg := 0
    currentNonceReg := 0
  } otherwise {
    when(io.stop) {
      state := State.idle
      checkValidReg := False
    } elsewhen(io.start) {
      state := State.firstStart
      jobMidstateReg := io.midstate
      jobTailReg := io.tail
      jobCandidateModeReg := io.candidateMode
      currentNonceReg := io.startNonce
      firstNonceReg := io.startNonce
      secondNonceReg := io.startNonce
      checkValidReg := False
    } otherwise {
      checkValidReg := False
      switch(state) {
        is(State.idle) {
        }
        is(State.firstStart) {
          shaFirstStart := True
          firstNonceReg := currentNonceReg
          currentNonceReg := currentNonceReg + io.nonceStride
          state := State.run
        }
        is(State.run) {
          when(checkValidReg && checkDigestMeetsTarget) {
            foundNonceReg := checkNonceReg
            state := State.report
          } otherwise {
            when(shaFirst.io.done) {
              shaSecondStart := True
              secondNonceReg := firstNonceReg

              shaFirstStart := True
              firstNonceReg := currentNonceReg
              currentNonceReg := currentNonceReg + io.nonceStride
            }

            when(shaSecond.io.done) {
              checkValidReg := True
              checkDigestLow32Reg := shaSecond.io.digestLow32
              checkNonceReg := secondNonceReg
              checkCandidateModeReg := jobCandidateModeReg
            }
          }
        }
        is(State.report) {
        }
      }
    }
  }
}

class BitcoinHashWideLaneBlock(
  laneCount: Int,
  options: TangMinerHardwareOptions = TangMinerHardwareOptions()
) extends Component {
  require(laneCount > 0, "laneCount must be positive")

  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stop = in Bool()
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val candidateMode = in UInt(3 bits)
    val runningAny = out Bool()
    val foundAny = out Bool()
    val foundNonce = out UInt(32 bits)
    val currentNonce = out UInt(32 bits)
  }

  val jobMidstateReg = Reg(Bits(256 bits)) init 0
  val jobTailReg = Reg(Bits(96 bits)) init 0
  val jobCandidateModeReg = Reg(UInt(3 bits)) init 3
  val startCoresReg = Reg(Bool()) init False

  val cores = (0 until laneCount).map(_ => new BitcoinHashCore(options.copy(wideLaneBlock = false)))

  when(io.reset) {
    jobMidstateReg := 0
    jobTailReg := 0
    jobCandidateModeReg := 3
    startCoresReg := False
  } otherwise {
    startCoresReg := False
    when(io.start) {
      jobMidstateReg := io.midstate
      jobTailReg := io.tail
      jobCandidateModeReg := io.candidateMode
      startCoresReg := True
    }
  }

  for ((core, lane) <- cores.zipWithIndex) {
    core.io.reset := io.reset
    core.io.start := startCoresReg
    core.io.stop := io.stop
    core.io.midstate := jobMidstateReg
    core.io.tail := jobTailReg
    core.io.candidateMode := jobCandidateModeReg
    core.io.startNonce := U(lane, 32 bits)
    core.io.nonceStride := U(laneCount, 32 bits)
  }

  io.runningAny := cores.map(_.io.running).reduce(_ || _)
  io.foundAny := cores.map(_.io.found).reduce(_ || _)
  io.currentNonce := cores(0).io.currentNonce

  io.foundNonce := cores(laneCount - 1).io.foundNonce
  for (lane <- (0 until laneCount - 1).reverse) {
    when(cores(lane).io.found) {
      io.foundNonce := cores(lane).io.foundNonce
    }
  }
}

class MiningLanes(
  laneCount: Int,
  laneStartStagger: Int,
  options: TangMinerHardwareOptions = TangMinerHardwareOptions()
) extends Component {
  require(laneCount > 0, "laneCount must be positive")
  require(laneStartStagger >= 0, "laneStartStagger must be non-negative")

  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stop = in Bool()
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val candidateMode = in UInt(3 bits)
    val runningAny = out Bool()
    val foundAny = out Bool()
    val currentNonce = out UInt(32 bits)
    val foundNonce = out UInt(32 bits)
  }

  if (options.wideLaneBlock) {
    val lanes = new BitcoinHashWideLaneBlock(laneCount, options)
    lanes.io.reset := io.reset
    lanes.io.start := io.start
    lanes.io.stop := io.stop
    lanes.io.midstate := io.midstate
    lanes.io.tail := io.tail
    lanes.io.candidateMode := io.candidateMode
    io.runningAny := lanes.io.runningAny
    io.foundAny := lanes.io.foundAny
    io.currentNonce := lanes.io.currentNonce
    io.foundNonce := lanes.io.foundNonce
  } else {
    val cores = (0 until laneCount).map(_ => new BitcoinHashCore(options))
    cores.foreach(_.io.reset := io.reset)
    val coreStartByLane = Vec(Bool(), laneCount)

    if (laneStartStagger == 0) {
      for (lane <- 0 until laneCount) {
        coreStartByLane(lane) := io.start
      }
    } else {
      val maxStartDelay = (laneCount - 1) * laneStartStagger
      val delayBits = log2Up(scala.math.max(2, maxStartDelay + 1))
      val laneStartPending = Reg(Bits(laneCount bits)) init 0
      val laneStartDelay = Vec(Reg(UInt(delayBits bits)) init 0, laneCount)

      for (lane <- 0 until laneCount) {
        coreStartByLane(lane) := False
      }

      when(io.reset || io.stop) {
        laneStartPending := 0
        for (lane <- 0 until laneCount) {
          laneStartDelay(lane) := 0
        }
      } otherwise {
        when(io.start) {
          laneStartPending := B((BigInt(1) << laneCount) - 1, laneCount bits)
          for (lane <- 0 until laneCount) {
            laneStartDelay(lane) := U(lane * laneStartStagger, delayBits bits)
          }
        }

        for (lane <- 0 until laneCount) {
          when(laneStartPending(lane)) {
            when(laneStartDelay(lane) === 0) {
              coreStartByLane(lane) := True
              laneStartPending(lane) := False
            } otherwise {
              laneStartDelay(lane) := laneStartDelay(lane) - 1
            }
          }
        }
      }
    }

    for ((core, lane) <- cores.zipWithIndex) {
      core.io.start := coreStartByLane(lane)
      core.io.stop := io.stop
      core.io.midstate := io.midstate
      core.io.tail := io.tail
      core.io.candidateMode := io.candidateMode
      core.io.startNonce := U(lane, 32 bits)
      core.io.nonceStride := U(laneCount, 32 bits)
    }

    io.runningAny := cores.map(_.io.running).reduce(_ || _)
    io.foundAny := cores.map(_.io.found).reduce(_ || _)
    io.currentNonce := cores(0).io.currentNonce

    io.foundNonce := cores(laneCount - 1).io.foundNonce
    for (lane <- (0 until laneCount - 1).reverse) {
      when(cores(lane).io.found) {
        io.foundNonce := cores(lane).io.foundNonce
      }
    }
  }
}

class Top(
  clksPerBit: Int = 871,
  resetCounterBits: Int = 24,
  usePll: Boolean = true,
  laneCount: Int = 5,
  laneStartStagger: Int = 0,
  clockProfile: GowinClockProfile = GowinClockProfiles.byName("100m286"),
  splitShaClock: Boolean = false,
  hardwareOptions: TangMinerHardwareOptions = TangMinerHardwareOptions(
    enableEcho = false,
    enableHardcodedJob = false,
    fixedCandidateMode = Some(2)
  )
) extends Component {
  require(clksPerBit > 1, "clksPerBit must leave room for UART start-bit centering")
  require(resetCounterBits > 0, "resetCounterBits must be positive")
  require(laneCount > 0, "laneCount must be positive")
  require(laneStartStagger >= 0, "laneStartStagger must be non-negative")
  require(!usePll || clockProfile.usePll, s"clock profile '${clockProfile.name}' does not define PLL settings")
  require(!splitShaClock || usePll, "splitShaClock requires a PLL-backed SHA clock")

  setDefinitionName("top")
  noIoPrefix()

  val io = new Bundle {
    val clk = in Bool()
    val uart_rx_pin = in Bool()
    val uart_tx_pin = out Bool()
    val led = out Bits(6 bits)
  }

  val systemClock = Bool()
  val systemClockLocked = Bool()

  if (usePll) {
    val pll = new GowinRpllFrom27Mhz(clockProfile)
    pll.io.CLKIN := io.clk
    pll.io.CLKFB := False
    pll.io.FBDSEL := 0
    pll.io.IDSEL := 0
    pll.io.ODSEL := 0
    pll.io.DUTYDA := 0
    pll.io.PSDA := 0
    pll.io.FDLY := 0
    pll.io.RESET := False
    pll.io.RESET_P := False
    systemClock := pll.io.CLKOUT
    systemClockLocked := pll.io.LOCK
  } else {
    systemClock := io.clk
    systemClockLocked := True
  }

  val controlClock = if (splitShaClock) io.clk else systemClock
  val controlClockLocked = if (splitShaClock) True else systemClockLocked
  val controlClksPerBit = if (splitShaClock) GowinClockProfiles.byName("27m").clksPerBit else clksPerBit
  val controlDomain = ClockDomain(controlClock, config = ClockDomainConfig(resetKind = BOOT))
  val shaDomain = ClockDomain(systemClock, config = ClockDomainConfig(resetKind = BOOT))
  val SplitJobPayloadBits = 256 + 96 + 3

  val splitJobFifo: StreamFifoCC[Bits] =
    if (splitShaClock) StreamFifoCC(Bits(SplitJobPayloadBits bits), 2, controlDomain, shaDomain) else null
  val splitStopFifo: StreamFifoCC[Bits] =
    if (splitShaClock) StreamFifoCC(Bits(1 bits), 2, controlDomain, shaDomain) else null
  val splitFoundFifo: StreamFifoCC[Bits] =
    if (splitShaClock) StreamFifoCC(Bits(32 bits), 2, shaDomain, controlDomain) else null
  var splitShaReady: Bool = null
  var splitShaRunningAny: Bool = null

  if (splitShaClock) {
    val shaArea = new ClockingArea(shaDomain) {
      val resetCounter = Reg(UInt(resetCounterBits bits)) init 0
      val reset = !systemClockLocked || !resetCounter.msb
      when(!systemClockLocked) {
        resetCounter := 0
      } elsewhen(!resetCounter.msb) {
        resetCounter := resetCounter + 1
      }

      val lanes = new MiningLanes(laneCount, laneStartStagger, hardwareOptions)
      val jobPayload = splitJobFifo.io.pop.payload
      val jobStart = splitJobFifo.io.pop.valid && !reset
      val stopPulse = splitStopFifo.io.pop.valid && !reset

      splitJobFifo.io.pop.ready := jobStart
      splitStopFifo.io.pop.ready := True

      lanes.io.reset := reset
      lanes.io.start := jobStart
      lanes.io.stop := stopPulse
      lanes.io.midstate := jobPayload(SplitJobPayloadBits - 1 downto 99)
      lanes.io.tail := jobPayload(98 downto 3)
      lanes.io.candidateMode := jobPayload(2 downto 0).asUInt

      val foundSent = Reg(Bool()) init False
      when(reset || jobStart || stopPulse) {
        foundSent := False
      } elsewhen(splitFoundFifo.io.push.valid && splitFoundFifo.io.push.ready) {
        foundSent := True
      }

      splitFoundFifo.io.push.valid := lanes.io.foundAny && !foundSent && !reset
      splitFoundFifo.io.push.payload := lanes.io.foundNonce.asBits

      val ready = !reset
      val runningAny = lanes.io.runningAny
    }
    splitShaReady = shaArea.ready
    splitShaRunningAny = shaArea.runningAny
  }

  val coreArea = new ClockingArea(controlDomain) {
    val ClksPerBit = controlClksPerBit
    val JobBytes = 76
    val FoundRespBytes = 5
    val EchoRespBytes = 77
    val LaneCount = laneCount
    val LaneStartStagger = laneStartStagger
    val CandidateAlways = U(0, 3 bits)
    val CandidateQuick3 = U(1, 3 bits)
    val CandidateQuick21 = U(2, 3 bits)
    val CandidateQuick23 = U(3, 3 bits)
    val CandidateQuick26 = U(4, 3 bits)
    val CandidateQuick14 = U(5, 3 bits)
    val DefaultCandidate = U(hardwareOptions.fixedCandidateMode.getOrElse(3), 3 bits)
    val Quick3Target = B"256'h1fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val Quick14Target = B"256'h0003ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val Quick21Target = B"256'h000007ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val Quick23Target = B"256'h000001ffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val Quick26Target = B"256'h0000003fffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    val useTargetAliases = hardwareOptions.fixedCandidateMode.isEmpty

    val resetCounter = Reg(UInt(resetCounterBits bits)) init 0
    val reset = !controlClockLocked || !resetCounter.msb
    when(!controlClockLocked) {
      resetCounter := 0
    } elsewhen(!resetCounter.msb) {
      resetCounter := resetCounter + 1
    }

    val rx = new UartRx(ClksPerBit)
    rx.io.reset := reset
    rx.io.rx := io.uart_rx_pin

    val tx = new UartTx(ClksPerBit)
    tx.io.reset := reset

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
    val target = if (hardwareOptions.enableEcho) Reg(Bits(256 bits)) init 0 else B(0, 256 bits)
    val targetIsAllOnes = if (useTargetAliases) Reg(Bool()) init True else False
    val targetIsQuick3 = if (useTargetAliases) Reg(Bool()) init False else False
    val targetIsQuick14 = if (useTargetAliases) Reg(Bool()) init False else False
    val targetIsQuick21 = if (useTargetAliases) Reg(Bool()) init False else False
    val targetIsQuick23 = if (useTargetAliases) Reg(Bool()) init False else False
    val targetIsQuick26 = if (useTargetAliases) Reg(Bool()) init False else False
    val candidateMode = Reg(UInt(3 bits)) init DefaultCandidate
    val coreStart = Reg(Bool()) init False
    val coreStop = Reg(Bool()) init False
    val coreStartPending = Reg(Bool()) init False
    val echoToggle = if (hardwareOptions.enableEcho) Reg(Bool()) init False else False

    val runningAny = Bool()
    val foundAny = Bool()
    val currentNonce = UInt(32 bits)
    val selectedFoundNonce = UInt(32 bits)

    val splitStopPending = if (splitShaClock) Reg(Bool()) init False else False
    if (splitShaClock) {
      val shaReady = BufferCC(splitShaReady, 2)
      splitJobFifo.io.push.valid := coreStartPending && shaReady
      splitJobFifo.io.push.payload := midstate ## tail ## candidateMode.asBits
      splitStopFifo.io.push.valid := splitStopPending
      splitStopFifo.io.push.payload := B"1'b1"
      splitFoundFifo.io.pop.ready := False

      runningAny := BufferCC(splitShaRunningAny, 2)
      foundAny := splitFoundFifo.io.pop.valid
      currentNonce := U(0, 32 bits)
      selectedFoundNonce := splitFoundFifo.io.pop.payload.asUInt
    } else {
      val lanes = new MiningLanes(LaneCount, LaneStartStagger, hardwareOptions)
      lanes.io.reset := reset
      lanes.io.start := coreStart
      lanes.io.stop := coreStop
      lanes.io.midstate := midstate
      lanes.io.tail := tail
      lanes.io.candidateMode := candidateMode
      runningAny := lanes.io.runningAny
      foundAny := lanes.io.foundAny
      currentNonce := lanes.io.currentNonce
      selectedFoundNonce := lanes.io.foundNonce
    }

    when(reset) {
      rxState := RxState.sync0
      payloadCount := 0
      command := 0
      coreStart := False
      coreStop := False
      coreStartPending := False
      if (splitShaClock) {
        splitStopPending := False
      }
      midstate := 0
      tail := 0
      if (hardwareOptions.enableEcho) {
        target := 0
        echoToggle := False
      }
      if (useTargetAliases) {
        targetIsAllOnes := True
        targetIsQuick3 := False
        targetIsQuick14 := False
        targetIsQuick21 := False
        targetIsQuick23 := False
        targetIsQuick26 := False
      }
      candidateMode := DefaultCandidate
    } otherwise {
      coreStart := False
      coreStop := False

      if (splitShaClock) {
        when(coreStartPending && splitJobFifo.io.push.valid && splitJobFifo.io.push.ready) {
          coreStartPending := False
        }
        when(splitStopPending && splitStopFifo.io.push.ready) {
          splitStopPending := False
        }
      } else {
        when(coreStartPending) {
          coreStart := True
          coreStartPending := False
        }
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
            val acceptsJobPayload = rx.io.data === B"8'h4a" || (if (hardwareOptions.enableEcho) rx.io.data === B"8'h45" else False)
            when(rx.io.data === B"8'h53") {
              coreStop := True
              if (splitShaClock) {
                splitStopPending := True
              }
              rxState := RxState.sync0
            } elsewhen((if (hardwareOptions.enableHardcodedJob) rx.io.data === B"8'h48" else False)) {
              midstate := B"256'hbc909a336358bff090ccac7d1e59caa8c3c8d8e94f0103c896b187364719f91b"
              tail := B"96'h4b1e5e4a29ab5f49ffff001d"
              if (hardwareOptions.enableEcho) {
                target := B"256'hffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
              }
              candidateMode := CandidateAlways
              coreStartPending := True
              rxState := RxState.sync0
            } elsewhen(acceptsJobPayload) {
              if (useTargetAliases) {
                targetIsAllOnes := True
                targetIsQuick3 := True
                targetIsQuick14 := True
                targetIsQuick21 := True
                targetIsQuick23 := True
                targetIsQuick26 := True
              }
              rxState := RxState.payload
            } otherwise {
              rxState := RxState.sync0
            }
          }
          is(RxState.payload) {
            if (useTargetAliases) {
              val targetByteIndex = (payloadCount - U(44, 7 bits)).resize(5)
              val targetMatchesAllOnes = rx.io.data === B"8'hff"
              val targetMatchesQuick3 = rx.io.data === Sha256.byteFromMsb(Quick3Target, 32, targetByteIndex)
              val targetMatchesQuick14 = rx.io.data === Sha256.byteFromMsb(Quick14Target, 32, targetByteIndex)
              val targetMatchesQuick21 = rx.io.data === Sha256.byteFromMsb(Quick21Target, 32, targetByteIndex)
              val targetMatchesQuick23 = rx.io.data === Sha256.byteFromMsb(Quick23Target, 32, targetByteIndex)
              val targetMatchesQuick26 = rx.io.data === Sha256.byteFromMsb(Quick26Target, 32, targetByteIndex)
              val nextTargetIsAllOnes = targetIsAllOnes && targetMatchesAllOnes
              val nextTargetIsQuick3 = targetIsQuick3 && targetMatchesQuick3
              val nextTargetIsQuick14 = targetIsQuick14 && targetMatchesQuick14
              val nextTargetIsQuick21 = targetIsQuick21 && targetMatchesQuick21
              val nextTargetIsQuick23 = targetIsQuick23 && targetMatchesQuick23
              val nextTargetIsQuick26 = targetIsQuick26 && targetMatchesQuick26

              when(payloadCount < 32) {
                midstate := midstate(247 downto 0) ## rx.io.data
              } elsewhen(payloadCount < 44) {
                tail := tail(87 downto 0) ## rx.io.data
              } elsewhen(payloadCount < 76) {
                if (hardwareOptions.enableEcho) {
                  target := target(247 downto 0) ## rx.io.data
                }
                targetIsAllOnes := nextTargetIsAllOnes
                targetIsQuick3 := nextTargetIsQuick3
                targetIsQuick14 := nextTargetIsQuick14
                targetIsQuick21 := nextTargetIsQuick21
                targetIsQuick23 := nextTargetIsQuick23
                targetIsQuick26 := nextTargetIsQuick26
              }

              when(payloadCount === JobBytes - 1) {
                when(command === B"8'h4a") {
                  candidateMode := CandidateQuick23
                  when(nextTargetIsAllOnes) {
                    candidateMode := CandidateAlways
                  } elsewhen(nextTargetIsQuick3) {
                    candidateMode := CandidateQuick3
                  } elsewhen(nextTargetIsQuick14) {
                    candidateMode := CandidateQuick14
                  } elsewhen(nextTargetIsQuick21) {
                    candidateMode := CandidateQuick21
                  } elsewhen(nextTargetIsQuick26) {
                    candidateMode := CandidateQuick26
                  }
                  coreStartPending := True
                }
                if (hardwareOptions.enableEcho) {
                  when(command === B"8'h45") {
                    echoToggle := !echoToggle
                  }
                }
                rxState := RxState.sync0
              } otherwise {
                payloadCount := payloadCount + 1
              }
            } else {
              when(payloadCount < 32) {
                midstate := midstate(247 downto 0) ## rx.io.data
              } elsewhen(payloadCount < 44) {
                tail := tail(87 downto 0) ## rx.io.data
              } elsewhen(payloadCount < 76) {
                if (hardwareOptions.enableEcho) {
                  target := target(247 downto 0) ## rx.io.data
                }
              }

              when(payloadCount === JobBytes - 1) {
                when(command === B"8'h4a") {
                  candidateMode := DefaultCandidate
                  coreStartPending := True
                }
                if (hardwareOptions.enableEcho) {
                  when(command === B"8'h45") {
                    echoToggle := !echoToggle
                  }
                }
                rxState := RxState.sync0
              } otherwise {
                payloadCount := payloadCount + 1
              }
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

    val txState = Reg(TxState()) init TxState.idle
    val txIndex = Reg(UInt(7 bits)) init 0
    val txStart = Reg(Bool()) init False
    val txData = Reg(Bits(8 bits)) init B"8'hff"
    val foundSeen = Reg(Bool()) init False
    val echoSeenToggle = if (hardwareOptions.enableEcho) Reg(Bool()) init False else False
    val txEcho = if (hardwareOptions.enableEcho) Reg(Bool()) init False else False
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
      if (hardwareOptions.enableEcho) {
        echoSeenToggle := False
        txEcho := False
      }
      txFoundNonce := 0
    } otherwise {
      txStart := False

      when(!foundAny) {
        foundSeen := False
      }

      switch(txState) {
        is(TxState.idle) {
          if (hardwareOptions.enableEcho) {
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
              if (splitShaClock) {
                splitFoundFifo.io.pop.ready := True
              }
            }
          } else {
            when(foundAny && !foundSeen) {
              txIndex := 0
              txFoundNonce := selectedFoundNonce
              txState := TxState.send
              foundSeen := True
              if (splitShaClock) {
                splitFoundFifo.io.pop.ready := True
              }
            }
          }
        }
        is(TxState.send) {
          when(!tx.io.busy) {
            if (hardwareOptions.enableEcho) {
              val midstateBytes = (0 until 32).map(i => midstate(255 - i * 8 downto 248 - i * 8))
              val tailBytes = (0 until 12).map(i => tail(95 - i * 8 downto 88 - i * 8))
              val targetBytes = (0 until 32).map(i => target(255 - i * 8 downto 248 - i * 8))
              val echoBytes = Vec(Seq(B"8'h45") ++ midstateBytes ++ tailBytes ++ targetBytes)
              txData := Mux(txEcho, echoBytes(txIndex.resized), foundResponseByte(txIndex, txFoundNonce))
            } else {
              txData := foundResponseByte(txIndex, txFoundNonce)
            }
            txStart := True
            txState := TxState.waitBusy
          }
        }
        is(TxState.waitBusy) {
          when(tx.io.busy) {
            val responseDone = if (hardwareOptions.enableEcho) {
              (!txEcho && txIndex === FoundRespBytes - 1) || (txEcho && txIndex === EchoRespBytes - 1)
            } else {
              txIndex === FoundRespBytes - 1
            }
            when(responseDone) {
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
  def envBoolean(name: String, default: Boolean): Boolean =
    sys.env.get(name).map(value => value == "1" || value.equalsIgnoreCase("true")).getOrElse(default)

  def envInt(name: String, default: Int): Int =
    sys.env.get(name).map(_.toInt).getOrElse(default)

  def envString(names: Seq[String], default: String): String =
    names.collectFirst(Function.unlift(sys.env.get)).getOrElse(default)

  def envInt(names: Seq[String], default: Int): Int =
    names.collectFirst(Function.unlift(sys.env.get)).map(_.toInt).getOrElse(default)

  def envOptionalInt(names: Seq[String], default: Option[Int] = None): Option[Int] =
    names.collectFirst(Function.unlift(sys.env.get)) match {
      case Some(value) if value.nonEmpty => Some(value.toInt)
      case Some(_) => None
      case None => default
    }

  val targetDirectory = sys.env.getOrElse("TANGMINER_VERILOG_DIR", "build/spinal")
  val usePll = envBoolean("TANGMINER_USE_PLL", default = true)
  val clockProfile = GowinClockProfiles.byName(envString(Seq("TANGMINER_CLOCK_PROFILE", "SPINAL_CLOCK_PROFILE"), "100m286"))
  val clksPerBit = envInt(Seq("TANGMINER_CLKS_PER_BIT", "SPINAL_CLKS_PER_BIT"), clockProfile.clksPerBit)
  val laneCount = envInt(Seq("TANGMINER_LANES", "SPINAL_LANES"), 5)
  val laneStartStagger = envInt(Seq("TANGMINER_LANE_START_STAGGER", "SPINAL_LANE_START_STAGGER"), 0)
  val splitShaClock = envBoolean("TANGMINER_SPLIT_SHA_CLOCK", default = false) ||
    envBoolean("SPINAL_SPLIT_SHA_CLOCK", default = false)
  val hardwareOptions = TangMinerHardwareOptions(
    sharedRoundConstant = envBoolean("TANGMINER_SHARED_K", default = true),
    enableEcho = envBoolean("TANGMINER_ENABLE_ECHO", default = false),
    enableHardcodedJob = envBoolean("TANGMINER_ENABLE_HARDCODED", default = false),
    fixedCandidateMode = envOptionalInt(Seq("TANGMINER_FIXED_CANDIDATE", "SPINAL_FIXED_CANDIDATE"), Some(2)),
    wideLaneBlock = envBoolean("TANGMINER_WIDE_LANES", default = false) || envBoolean("SPINAL_WIDE_LANES", default = false),
    registerPassOutputs = envBoolean("TANGMINER_REGISTER_PASS_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_PASS_OUTPUTS", default = false),
    twoCycleRound = envBoolean("TANGMINER_TWO_CYCLE_ROUND", default = false) ||
      envBoolean("SPINAL_TWO_CYCLE_ROUND", default = false),
	    threeCycleRound = envBoolean("TANGMINER_THREE_CYCLE_ROUND", default = false) ||
	      envBoolean("SPINAL_THREE_CYCLE_ROUND", default = false),
	    registerRoundConstant = envBoolean("TANGMINER_REGISTER_ROUND_CONSTANT", default = false) ||
	      envBoolean("SPINAL_REGISTER_ROUND_CONSTANT", default = false),
	    minimizeShaReset = envBoolean("TANGMINER_MINIMIZE_SHA_RESET", default = false) ||
	      envBoolean("SPINAL_MINIMIZE_SHA_RESET", default = false)
	  )

  SpinalConfig(
    targetDirectory = targetDirectory,
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new Top(
    clksPerBit = clksPerBit,
    usePll = usePll,
    laneCount = laneCount,
    laneStartStagger = laneStartStagger,
    clockProfile = clockProfile,
    splitShaClock = splitShaClock,
    hardwareOptions = hardwareOptions
  ))
}

object GenerateSimVerilog extends App {
  def envBoolean(name: String, default: Boolean): Boolean =
    sys.env.get(name).map(value => value == "1" || value.equalsIgnoreCase("true")).getOrElse(default)

  def envInt(names: Seq[String], default: Int): Int =
    names.collectFirst(Function.unlift(sys.env.get)).map(_.toInt).getOrElse(default)

  def envOptionalInt(names: Seq[String]): Option[Int] =
    names.collectFirst(Function.unlift(sys.env.get)).filter(_.nonEmpty).map(_.toInt)

  val laneCount = envInt(Seq("TANGMINER_LANES", "SPINAL_LANES"), 5)
  val laneStartStagger = envInt(Seq("TANGMINER_LANE_START_STAGGER", "SPINAL_LANE_START_STAGGER"), 0)
  val clksPerBit = envInt(Seq("TANGMINER_CLKS_PER_BIT", "SPINAL_CLKS_PER_BIT"), 8)
  val hardwareOptions = TangMinerHardwareOptions(
    sharedRoundConstant = envBoolean("TANGMINER_SHARED_K", default = true),
    enableEcho = envBoolean("TANGMINER_ENABLE_ECHO", default = true),
    enableHardcodedJob = envBoolean("TANGMINER_ENABLE_HARDCODED", default = true),
    fixedCandidateMode = envOptionalInt(Seq("TANGMINER_FIXED_CANDIDATE", "SPINAL_FIXED_CANDIDATE")),
    wideLaneBlock = envBoolean("TANGMINER_WIDE_LANES", default = false) || envBoolean("SPINAL_WIDE_LANES", default = false),
    registerPassOutputs = envBoolean("TANGMINER_REGISTER_PASS_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_PASS_OUTPUTS", default = false),
    twoCycleRound = envBoolean("TANGMINER_TWO_CYCLE_ROUND", default = false) ||
      envBoolean("SPINAL_TWO_CYCLE_ROUND", default = false),
	    threeCycleRound = envBoolean("TANGMINER_THREE_CYCLE_ROUND", default = false) ||
	      envBoolean("SPINAL_THREE_CYCLE_ROUND", default = false),
	    registerRoundConstant = envBoolean("TANGMINER_REGISTER_ROUND_CONSTANT", default = false) ||
	      envBoolean("SPINAL_REGISTER_ROUND_CONSTANT", default = false),
	    minimizeShaReset = envBoolean("TANGMINER_MINIMIZE_SHA_RESET", default = false) ||
	      envBoolean("SPINAL_MINIMIZE_SHA_RESET", default = false)
	  )

  SpinalConfig(
    targetDirectory = "build/spinal-sim",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new Top(
    clksPerBit = clksPerBit,
    resetCounterBits = 4,
    usePll = false,
    laneCount = laneCount,
    laneStartStagger = laneStartStagger,
    clockProfile = GowinClockProfiles.byName("27m"),
    splitShaClock = false,
    hardwareOptions = hardwareOptions
  ))
}
