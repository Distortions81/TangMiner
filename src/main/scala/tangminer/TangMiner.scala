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
  odivSel: Int = 0,
  rpllSupported: Boolean = true,
  gw5IdivSel: Int = -1,
  gw5FbdivSel: Int = -1,
  gw5MdivSel: Int = -1,
  gw5OdivSel: Int = -1
) {
  def supportsGw5Pll: Boolean =
    gw5IdivSel >= 0 && gw5FbdivSel >= 0 && gw5MdivSel >= 0 && gw5OdivSel >= 0
}

case class TangMinerHardwareOptions(
  sharedRoundConstant: Boolean = false,
  enableEcho: Boolean = true,
  enableHardcodedJob: Boolean = true,
  fixedCandidateMode: Option[Int] = None,
  fullyUnrolled: Boolean = false,
  wideLaneBlock: Boolean = false,
  registerPassOutputs: Boolean = false,
  registerCompressorOutputs: Boolean = false,
  registerFirstPassFeedForward: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  twoRoundsPerCycle: Boolean = false,
  twoRoundPipeline: Boolean = false,
  twoPhaseRoundPipeline: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false,
  roundSkip: Boolean = false,
  csaRound: Boolean = false,
  csaSchedule: Boolean = false,
  balancedRoundAdder: Boolean = false,
  shareJobState: Boolean = false,
  hostRoundSkip: Boolean = false,
  externalRoundConstants: Boolean = false
) {
  fixedCandidateMode.foreach(mode =>
    require(mode >= 0 && mode <= 5, s"fixedCandidateMode must be 0..5, got $mode")
  )
  require(!(twoCycleRound && threeCycleRound), "twoCycleRound and threeCycleRound are mutually exclusive")
  require(!(twoRoundsPerCycle && (twoCycleRound || threeCycleRound)), "twoRoundsPerCycle cannot be combined with multi-cycle round modes")
  require(!(twoRoundPipeline && (twoCycleRound || threeCycleRound || twoRoundsPerCycle)), "twoRoundPipeline cannot be combined with other round-cycle modes")
  require(!(twoPhaseRoundPipeline && (twoCycleRound || threeCycleRound || twoRoundsPerCycle || twoRoundPipeline)), "twoPhaseRoundPipeline cannot be combined with other round-cycle modes")
  require(!(csaRound && (twoCycleRound || threeCycleRound)), "csaRound only applies to the one-cycle round datapath")
  require(!(csaRound && twoRoundsPerCycle), "csaRound cannot be combined with twoRoundsPerCycle")
  require(!(csaRound && twoRoundPipeline), "csaRound cannot be combined with twoRoundPipeline")
  require(!(csaRound && twoPhaseRoundPipeline), "csaRound cannot be combined with twoPhaseRoundPipeline")
  require(!csaSchedule || csaRound, "csaSchedule requires csaRound")
  require(!(balancedRoundAdder && (twoCycleRound || threeCycleRound || twoRoundsPerCycle)), "balancedRoundAdder only applies to the one-cycle round datapath")
  require(!(balancedRoundAdder && twoRoundPipeline), "balancedRoundAdder cannot be combined with twoRoundPipeline")
  require(!(balancedRoundAdder && twoPhaseRoundPipeline), "balancedRoundAdder cannot be combined with twoPhaseRoundPipeline")
  require(!(balancedRoundAdder && csaRound), "balancedRoundAdder and csaRound are alternative one-cycle adders")
  require(!(twoRoundsPerCycle && registerRoundConstant), "twoRoundsPerCycle uses two local K constants per cycle")
  require(!(twoRoundPipeline && registerRoundConstant), "twoRoundPipeline uses two local K constants per pipeline pair")
  require(!(twoPhaseRoundPipeline && registerRoundConstant), "twoPhaseRoundPipeline uses local K constants per phase")
  require(!(twoRoundsPerCycle && externalRoundConstants), "twoRoundsPerCycle does not support external single-K sharing")
  require(!(twoRoundPipeline && externalRoundConstants), "twoRoundPipeline does not support external single-K sharing")
  require(!(twoPhaseRoundPipeline && externalRoundConstants), "twoPhaseRoundPipeline does not support external single-K sharing")
  require(!(twoRoundPipeline && roundSkip), "twoRoundPipeline currently supports full SHA256d mode only")
  require(!(twoPhaseRoundPipeline && roundSkip), "twoPhaseRoundPipeline currently supports full SHA256d mode only")
  require(!(registerFirstPassFeedForward && registerCompressorOutputs), "registerFirstPassFeedForward is an alternative to registerCompressorOutputs")
  require(!registerFirstPassFeedForward || registerPassOutputs, "registerFirstPassFeedForward requires registerPassOutputs")
  require(!hostRoundSkip || roundSkip, "hostRoundSkip requires roundSkip")
  require(!(hostRoundSkip && fixedCandidateMode.isEmpty), "hostRoundSkip requires fixedCandidateMode")
  require(!fullyUnrolled || hostRoundSkip, "fullyUnrolled requires hostRoundSkip")
  require(!(fullyUnrolled && wideLaneBlock), "fullyUnrolled and wideLaneBlock are alternative mining topologies")
  require(!(fullyUnrolled && (twoCycleRound || threeCycleRound || twoRoundsPerCycle || twoRoundPipeline || twoPhaseRoundPipeline)),
    "fullyUnrolled cannot be combined with iterative round-cycle modes")
  require(!(fullyUnrolled && (csaRound || csaSchedule || balancedRoundAdder)),
    "fullyUnrolled uses its dedicated fixed-index round datapath")
}

object GowinClockProfiles {
  val Profiles = Map(
    "27m" -> GowinClockProfile("27m", 27.0, 234, usePll = false),
    "50m" -> GowinClockProfile("50m", 50.0, 434, usePll = true, rpllSupported = false,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 16, gw5OdivSel = 16),
    "54m" -> GowinClockProfile("54m", 54.0, 469, usePll = true, idivSel = 0, fbdivSel = 1, odivSel = 16),
    "57m" -> GowinClockProfile("57m", 57.0, 495, usePll = true, idivSel = 8, fbdivSel = 18, odivSel = 16),
    "58m5" -> GowinClockProfile("58m5", 58.5, 508, usePll = true, idivSel = 5, fbdivSel = 12, odivSel = 16),
    "60m75" -> GowinClockProfile("60m75", 60.75, 527, usePll = true, idivSel = 3, fbdivSel = 8, odivSel = 16),
    "67m5" -> GowinClockProfile("67m5", 67.5, 586, usePll = true, idivSel = 1, fbdivSel = 4, odivSel = 8),
    "81m" -> GowinClockProfile("81m", 81.0, 703, usePll = true, idivSel = 0, fbdivSel = 2, odivSel = 8),
    "84m" -> GowinClockProfile("84m", 84.0, 729, usePll = true, idivSel = 8, fbdivSel = 27, odivSel = 8),
    "85m5" -> GowinClockProfile("85m5", 85.5, 742, usePll = true, idivSel = 5, fbdivSel = 18, odivSel = 8),
    "75m" -> GowinClockProfile("75m", 75.0, 651, usePll = true, rpllSupported = false,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 24, gw5OdivSel = 16),
    "80m" -> GowinClockProfile("80m", 80.0, 694, usePll = true, rpllSupported = false,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 16, gw5OdivSel = 10),
    "90m" -> GowinClockProfile("90m", 90.0, 781, usePll = true, idivSel = 2, fbdivSel = 9, odivSel = 8,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 18, gw5OdivSel = 10),
    "100m" -> GowinClockProfile("100m", 100.0, 868, usePll = true, rpllSupported = false,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 16, gw5OdivSel = 8),
    "100m286" -> GowinClockProfile("100m286", 100.286, 871, usePll = true, idivSel = 6, fbdivSel = 25, odivSel = 8),
    "111m" -> GowinClockProfile("111m", 111.0, 964, usePll = true, idivSel = 8, fbdivSel = 36, odivSel = 8),
    "120m" -> GowinClockProfile("120m", 120.0, 1042, usePll = true, idivSel = 8, fbdivSel = 39, odivSel = 8),
    "123m" -> GowinClockProfile("123m", 123.0, 1068, usePll = true, idivSel = 8, fbdivSel = 40, odivSel = 8),
    "124m875" -> GowinClockProfile("124m875", 124.875, 1084, usePll = true, idivSel = 7, fbdivSel = 36, odivSel = 8),
    "125m" -> GowinClockProfile("125m", 125.0, 1085, usePll = true, rpllSupported = false,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 20, gw5OdivSel = 8),
    "126m" -> GowinClockProfile("126m", 126.0, 1094, usePll = true, idivSel = 2, fbdivSel = 13, odivSel = 8),
    "130m5" -> GowinClockProfile("130m5", 130.5, 1133, usePll = true, idivSel = 5, fbdivSel = 28, odivSel = 8),
    "135m" -> GowinClockProfile("135m", 135.0, 1172, usePll = true, idivSel = 1, fbdivSel = 9, odivSel = 8),
    "150m" -> GowinClockProfile("150m", 150.0, 1302, usePll = true, idivSel = 8, fbdivSel = 49, odivSel = 8,
      gw5IdivSel = 1, gw5FbdivSel = 1, gw5MdivSel = 30, gw5OdivSel = 10)
  )

  def byName(name: String): GowinClockProfile =
    Profiles.getOrElse(
      name,
      throw new IllegalArgumentException(s"unsupported clock profile '$name'; supported profiles: ${Profiles.keys.toSeq.sorted.mkString(", ")}")
    )
}

object NonceAttemptCounter {
  def apply(reset: Bool, restart: Bool, attempts: Seq[Bool]): UInt = {
    require(attempts.nonEmpty, "at least one nonce-attempt source is required")

    val incrementWidth = log2Up(attempts.length + 1)
    val increment = attempts.map(_.asUInt.resize(incrementWidth)).reduce(_ + _).resize(incrementWidth)
    val incrementReg = Reg(UInt(incrementWidth bits)) init 0
    val lowReg = Reg(UInt(32 bits)) init 0
    val highReg = Reg(UInt(32 bits)) init 0
    val nextLow = (lowReg + incrementReg.resize(32)).resize(32)
    val carry = nextLow < lowReg

    when(reset || restart) {
      incrementReg := 0
      lowReg := 0
      highReg := 0
    } otherwise {
      incrementReg := increment
      lowReg := nextLow
      when(carry) {
        highReg := highReg + 1
      }
    }

    (highReg.asBits ## lowReg.asBits).asUInt
  }
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

class Gowin5PllFrom50Mhz(profile: GowinClockProfile) extends BlackBox {
  require(profile.supportsGw5Pll, s"clock profile '${profile.name}' does not define GW5 PLL settings")

  setDefinitionName("PLL")
  noIoPrefix()

  addGeneric("FCLKIN", "50")
  addGeneric("IDIV_SEL", profile.gw5IdivSel)
  addGeneric("FBDIV_SEL", profile.gw5FbdivSel)
  addGeneric("MDIV_SEL", profile.gw5MdivSel)
  addGeneric("MDIV_FRAC_SEL", 0)
  addGeneric("ODIV0_SEL", profile.gw5OdivSel)
  addGeneric("ODIV0_FRAC_SEL", 0)
  addGeneric("CLKOUT0_EN", "TRUE")
  addGeneric("CLKOUT1_EN", "FALSE")
  addGeneric("CLKOUT2_EN", "FALSE")
  addGeneric("CLKOUT3_EN", "FALSE")
  addGeneric("CLKOUT4_EN", "FALSE")
  addGeneric("CLKOUT5_EN", "FALSE")
  addGeneric("CLKOUT6_EN", "FALSE")
  addGeneric("CLKFB_SEL", "INTERNAL")

  val io = new Bundle {
    val LOCK = out Bool()
    val CLKOUT0 = out Bool()
    val CLKOUT1 = out Bool()
    val CLKOUT2 = out Bool()
    val CLKOUT3 = out Bool()
    val CLKOUT4 = out Bool()
    val CLKOUT5 = out Bool()
    val CLKOUT6 = out Bool()
    val CLKFBOUT = out Bool()
    val CLKIN = in Bool()
    val CLKFB = in Bool()
    val RESET = in Bool()
    val PLLPWD = in Bool()
    val RESET_I = in Bool()
    val RESET_O = in Bool()
    val FBDSEL = in Bits(6 bits)
    val IDSEL = in Bits(6 bits)
    val MDSEL = in Bits(7 bits)
    val MDSEL_FRAC = in Bits(3 bits)
    val ODSEL0 = in Bits(7 bits)
    val ODSEL1 = in Bits(7 bits)
    val ODSEL2 = in Bits(7 bits)
    val ODSEL3 = in Bits(7 bits)
    val ODSEL4 = in Bits(7 bits)
    val ODSEL5 = in Bits(7 bits)
    val ODSEL6 = in Bits(7 bits)
    val ODSEL0_FRAC = in Bits(3 bits)
    val DT0 = in Bits(4 bits)
    val DT1 = in Bits(4 bits)
    val DT2 = in Bits(4 bits)
    val DT3 = in Bits(4 bits)
    val ICPSEL = in Bits(6 bits)
    val LPFRES = in Bits(3 bits)
    val LPFCAP = in Bits(2 bits)
    val PSSEL = in Bits(3 bits)
    val PSDIR = in Bool()
    val PSPULSE = in Bool()
    val ENCLK0 = in Bool()
    val ENCLK1 = in Bool()
    val ENCLK2 = in Bool()
    val ENCLK3 = in Bool()
    val ENCLK4 = in Bool()
    val ENCLK5 = in Bool()
    val ENCLK6 = in Bool()
    val SSCPOL = in Bool()
    val SSCON = in Bool()
    val SSCMDSEL = in Bits(7 bits)
    val SSCMDSEL_FRAC = in Bits(3 bits)
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

  def add32Csa(values: UInt*): UInt = {
    require(values.nonEmpty, "add32Csa requires at least one operand")

    def carrySave3(a: UInt, b: UInt, c: UInt): Seq[UInt] = {
      val sum = (a ^ b ^ c).resize(32)
      val carry = ((a & b) | (a & c) | (b & c)).resize(32)
      Seq(sum, (carry << 1).resize(32))
    }

    var operands = values.map(_.resize(32))
    while (operands.length > 2) {
      operands = operands.grouped(3).flatMap {
        case Seq(a, b, c) => carrySave3(a, b, c)
        case rest => rest
      }.toSeq
    }
    operands.reduce((left, right) => (left + right).resize(32))
  }

  def add32Balanced(values: UInt*): UInt = {
    require(values.nonEmpty, "add32Balanced requires at least one operand")

    def reduceLevel(operands: Seq[UInt]): Seq[UInt] =
      operands.grouped(2).map {
        case Seq(left, right) => (left.resize(32) + right.resize(32)).resize(32)
        case Seq(single) => single.resize(32)
      }.toSeq

    var operands = values.map(_.resize(32))
    while (operands.length > 1) {
      operands = reduceLevel(operands)
    }
    operands.head
  }

  def roundStepPrepare(state: Seq[UInt], schedule: Seq[UInt], kWord: UInt): (UInt, UInt, UInt) = {
    require(state.length == 8, s"roundStep state must have 8 words, got ${state.length}")
    require(schedule.length == 16, s"roundStep schedule must have 16 words, got ${schedule.length}")

    val ra = state(0)
    val rb = state(1)
    val rc = state(2)
    val re = state(4)
    val rf = state(5)
    val rg = state(6)
    val rh = state(7)
    val rwNext = (smallSigma1(schedule(14)) + schedule(9) + smallSigma0(schedule(1)) + schedule(0)).resize(32)
    val rt1 = (rh + bigSigma1(re) + ch(re, rf, rg) + kWord + schedule(0)).resize(32)
    val rt2 = (bigSigma0(ra) + maj(ra, rb, rc)).resize(32)
    (rt1, rt2, rwNext)
  }

  def roundStepComplete(state: Seq[UInt], schedule: Seq[UInt], t1: UInt, t2: UInt, wNext: UInt): (Seq[UInt], Seq[UInt]) = {
    require(state.length == 8, s"roundStep state must have 8 words, got ${state.length}")
    require(schedule.length == 16, s"roundStep schedule must have 16 words, got ${schedule.length}")

    val ra = state(0)
    val rb = state(1)
    val rc = state(2)
    val rd = state(3)
    val re = state(4)
    val rf = state(5)
    val rg = state(6)
    val raNext = (t1 + t2).resize(32)
    val reNext = (rd + t1).resize(32)
    val nextState = Seq(raNext, ra, rb, rc, reNext, re, rf, rg)
    val nextSchedule = (1 until 16).map(i => schedule(i)) :+ wNext
    (nextState, nextSchedule)
  }

  def roundStep(state: Seq[UInt], schedule: Seq[UInt], kWord: UInt): (Seq[UInt], Seq[UInt]) = {
    val (t1, t2, wNext) = roundStepPrepare(state, schedule, kWord)
    roundStepComplete(state, schedule, t1, t2, wNext)
  }

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
  twoRoundsPerCycle: Boolean = false,
  twoRoundPipeline: Boolean = false,
  twoPhaseRoundPipeline: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeResetFanout: Boolean = false,
  dynamicRoundWindow: Boolean = false,
  fixedStartRound: Int = 0,
  fixedStopRound: Int = 63,
  csaRound: Boolean = false,
  csaSchedule: Boolean = false,
  balancedRoundAdder: Boolean = false,
  lowWordOnlyOutput: Boolean = false,
  lowWordIndex: Int = 7
) extends Component {
  require(!(twoRoundsPerCycle && (twoCycleRound || threeCycleRound)), "twoRoundsPerCycle cannot be combined with multi-cycle round modes")
  require(!(twoRoundPipeline && (twoCycleRound || threeCycleRound || twoRoundsPerCycle)), "twoRoundPipeline cannot be combined with other round-cycle modes")
  require(!(twoPhaseRoundPipeline && (twoCycleRound || threeCycleRound || twoRoundsPerCycle || twoRoundPipeline)), "twoPhaseRoundPipeline cannot be combined with other round-cycle modes")
  require(!(csaRound && (twoCycleRound || threeCycleRound)), "csaRound only applies to the one-cycle round datapath")
  require(!(csaRound && twoRoundsPerCycle), "csaRound cannot be combined with twoRoundsPerCycle")
  require(!(csaRound && twoRoundPipeline), "csaRound cannot be combined with twoRoundPipeline")
  require(!(csaRound && twoPhaseRoundPipeline), "csaRound cannot be combined with twoPhaseRoundPipeline")
  require(!csaSchedule || csaRound, "csaSchedule requires csaRound")
  require(!(balancedRoundAdder && (twoCycleRound || threeCycleRound || twoRoundsPerCycle)), "balancedRoundAdder only applies to the one-cycle round datapath")
  require(!(balancedRoundAdder && twoRoundPipeline), "balancedRoundAdder cannot be combined with twoRoundPipeline")
  require(!(balancedRoundAdder && twoPhaseRoundPipeline), "balancedRoundAdder cannot be combined with twoPhaseRoundPipeline")
  require(!(balancedRoundAdder && csaRound), "balancedRoundAdder and csaRound are alternative one-cycle adders")
  require(!(twoRoundsPerCycle && registerRoundConstant), "twoRoundsPerCycle uses two local K constants per cycle")
  require(!(twoRoundPipeline && registerRoundConstant), "twoRoundPipeline uses two local K constants per pipeline pair")
  require(!(twoPhaseRoundPipeline && registerRoundConstant), "twoPhaseRoundPipeline uses local K constants per phase")
  require(!(twoRoundPipeline && dynamicRoundWindow), "twoRoundPipeline currently requires a fixed round window")
  require(!(twoPhaseRoundPipeline && dynamicRoundWindow), "twoPhaseRoundPipeline currently requires a fixed round window")
  require(fixedStartRound >= 0 && fixedStartRound < 64, s"fixedStartRound must be 0..63, got $fixedStartRound")
  require(fixedStopRound >= fixedStartRound && fixedStopRound < 64, s"fixedStopRound must be fixedStartRound..63, got $fixedStopRound")
  require(lowWordIndex >= 0 && lowWordIndex < 8, s"lowWordIndex must be 0..7, got $lowWordIndex")

  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val startRound = in UInt(6 bits)
    val stopRound = in UInt(6 bits)
    val kWord = in UInt(32 bits)
    val stateIn = in Bits(256 bits)
    val words = in Vec(UInt(32 bits), 16)
    val ready = out Bool()
    val done = out Bool()
    val roundOut = out UInt(6 bits)
    val workOut = out Bits(256 bits)
    val workLow32 = out UInt(32 bits)
  }

  val a, b, c, d, e, f, g, h = Reg(UInt(32 bits)) init 0
  val w = Vec(Reg(UInt(32 bits)) init 0, 16)
  val wRound = Reg(UInt(32 bits)) init 0
  val round = Reg(UInt(6 bits)) init 0
  val stopRoundReg = if (dynamicRoundWindow) Reg(UInt(6 bits)) init 63 else null
  val busyReg = Reg(Bool()) init False
  val kWords = Vec(Sha256.K.map(Sha256.word))
  val kWordReg = if (registerRoundConstant) {
    val reg = Reg(UInt(32 bits))
    if (!minimizeResetFanout) {
      reg.init(U(Sha256.K.head, 32 bits))
    }
    reg
  } else {
    null
  }
  val selectedKWord = if (registerRoundConstant) kWordReg else io.kWord
  val startRoundValue = if (dynamicRoundWindow) io.startRound else U(fixedStartRound, 6 bits)
  val stopRoundValue = if (dynamicRoundWindow) stopRoundReg else U(fixedStopRound, 6 bits)

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
      kWordReg := kWords(startRoundValue.resized)
    }

    round := startRoundValue
    if (dynamicRoundWindow) {
      stopRoundReg := io.stopRound
    }
    busyReg := True
  }

  def clearState(): Unit = {
    if (!minimizeResetFanout) {
      a := 0; b := 0; c := 0; d := 0; e := 0; f := 0; g := 0; h := 0
      for (i <- 0 until 16) w(i) := 0
      wRound := 0
    }
    round := 0
    if (dynamicRoundWindow) {
      stopRoundReg := 63
    }
    busyReg := False
  }

  val t1Terms = Seq(h, Sha256.bigSigma1(e), Sha256.ch(e, f, g), selectedKWord, wRound)
  val t2Terms = Seq(Sha256.bigSigma0(a), Sha256.maj(a, b, c))
  val wNext = if (csaSchedule) {
    Sha256.add32Csa(Sha256.smallSigma1(w(14)), w(9), Sha256.smallSigma0(w(1)), w(0))
  } else if (balancedRoundAdder) {
    Sha256.add32Balanced(Sha256.smallSigma1(w(14)), w(9), Sha256.smallSigma0(w(1)), w(0))
  } else {
    (Sha256.smallSigma1(w(14)) + w(9) + Sha256.smallSigma0(w(1)) + w(0)).resize(32)
  }
  val t1 = if (balancedRoundAdder) {
    Sha256.add32Balanced(h, Sha256.bigSigma1(e), Sha256.ch(e, f, g), selectedKWord, wRound)
  } else {
    (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g) + selectedKWord + wRound).resize(32)
  }
  val t2 = if (balancedRoundAdder) {
    Sha256.add32Balanced(Sha256.bigSigma0(a), Sha256.maj(a, b, c))
  } else {
    (Sha256.bigSigma0(a) + Sha256.maj(a, b, c)).resize(32)
  }
  val aNext = if (csaRound) {
    Sha256.add32Csa((t1Terms ++ t2Terms): _*)
  } else if (balancedRoundAdder) {
    Sha256.add32Balanced(t1, t2)
  } else {
    (t1 + t2).resize(32)
  }
  val eNext = if (csaRound) {
    Sha256.add32Csa((Seq(d) ++ t1Terms): _*)
  } else if (balancedRoundAdder) {
    Sha256.add32Balanced(d, t1)
  } else {
    (d + t1).resize(32)
  }
  val finalRound = busyReg && round === stopRoundValue
  val finalState = Seq(aNext, a, b, c, eNext, e, f, g)
  val finalWork = if (lowWordOnlyOutput) B(0, 256 bits) else Sha256.concatWords(finalState)
  val finalWorkLow32 = finalState(lowWordIndex)

  io.roundOut := round

  if (twoPhaseRoundPipeline) {
    val contextWordCount = 24
    val contextWidth = contextWordCount * 32
    val contextDepth = 16
    val contextSlotCount = 3
    val contextSlotBits = log2Up(contextSlotCount)
    val contextAddressBits = log2Up(contextDepth)

    def packContext(state: Seq[UInt], schedule: Seq[UInt]): Bits = {
      require(state.length == 8, s"context state must have 8 words, got ${state.length}")
      require(schedule.length == 16, s"context schedule must have 16 words, got ${schedule.length}")
      (state.map(_.asBits) ++ schedule.map(_.asBits)).reduce(_ ## _)
    }

    def unpackContextWord(context: Bits, index: Int): UInt = {
      require(index >= 0 && index < contextWordCount, s"context word index must be 0..${contextWordCount - 1}, got $index")
      context(contextWidth - 1 - index * 32 downto contextWidth - 32 - index * 32).asUInt
    }

    val slotActive = Vec(Reg(Bool()) init False, contextSlotCount)
    val slotRound = Vec(Reg(UInt(6 bits)) init 0, contextSlotCount)
    val slotStopRound = if (dynamicRoundWindow) Vec(Reg(UInt(6 bits)) init 63, contextSlotCount) else null
    val slotContext = Mem(Bits(contextWidth bits), wordCount = contextDepth)

    val readValid = Reg(Bool()) init False
    val readSlot = Reg(UInt(contextSlotBits bits)) init 0
    val readRound = Reg(UInt(6 bits)) init 0
    val readStopRound = if (dynamicRoundWindow) Reg(UInt(6 bits)) init 63 else null
    val readContext = Bits(contextWidth bits)
    val readAddress = UInt(contextAddressBits bits)
    val readFire = Bool()

    val stageValid = Reg(Bool()) init False
    val stageSlot = Reg(UInt(contextSlotBits bits)) init 0
    val stageRound = Reg(UInt(6 bits)) init 0
    val stageStopRound = if (dynamicRoundWindow) Reg(UInt(6 bits)) init 63 else null
    val stageState = Vec(Reg(UInt(32 bits)) init 0, 7)
    val stageSchedule = Vec(Reg(UInt(32 bits)) init 0, 16)
    val stageT1 = Reg(UInt(32 bits)) init 0
    val stageT2 = Reg(UInt(32 bits)) init 0
    val issuePrefer = Reg(UInt(contextSlotBits bits)) init 0
    val startGap = Reg(UInt(7 bits)) init 0

    val stageANext = (stageT1 + stageT2).resize(32)
    val stageENext = (stageState(3) + stageT1).resize(32)
    val stageStateAfterRound = Seq(
      stageANext,
      stageState(0),
      stageState(1),
      stageState(2),
      stageENext,
      stageState(4),
      stageState(5),
      stageState(6)
    )
    val stageScheduleAfterRound = (0 until 16).map(i => stageSchedule(i))
    val stageStopRoundValue = if (dynamicRoundWindow) stageStopRound else U(fixedStopRound, 6 bits)
    val stageComplete = stageValid && stageRound === stageStopRoundValue
    val stageWork = if (lowWordOnlyOutput) B(0, 256 bits) else Sha256.concatWords(stageStateAfterRound)
    val stageWorkLow32 = stageStateAfterRound(lowWordIndex)
    val stageRoundPlusOne = (stageRound + U(1, 6 bits)).resized
    val stageWriteBack = stageValid && !stageComplete

    val canIssue = Vec((0 until contextSlotCount).map { slot =>
      val slotId = U(slot, contextSlotBits bits)
      slotActive(slot) &&
        !(readValid && readSlot === slotId) &&
        !(stageValid && stageSlot === slotId)
    })
    val issueSlot = UInt(contextSlotBits bits)
    issueSlot := U(0, contextSlotBits bits)
    switch(issuePrefer) {
      is(U(0, contextSlotBits bits)) {
        when(canIssue(0)) {
          issueSlot := U(0, contextSlotBits bits)
        } elsewhen(canIssue(1)) {
          issueSlot := U(1, contextSlotBits bits)
        } elsewhen(canIssue(2)) {
          issueSlot := U(2, contextSlotBits bits)
        }
      }
      is(U(1, contextSlotBits bits)) {
        when(canIssue(1)) {
          issueSlot := U(1, contextSlotBits bits)
        } elsewhen(canIssue(2)) {
          issueSlot := U(2, contextSlotBits bits)
        } elsewhen(canIssue(0)) {
          issueSlot := U(0, contextSlotBits bits)
        }
      }
      default {
        when(canIssue(2)) {
          issueSlot := U(2, contextSlotBits bits)
        } elsewhen(canIssue(0)) {
          issueSlot := U(0, contextSlotBits bits)
        } elsewhen(canIssue(1)) {
          issueSlot := U(1, contextSlotBits bits)
        }
      }
    }
    val issueValid = canIssue.reduce(_ || _)
    val issueRound = slotRound(issueSlot)
    val issueStopRound = if (dynamicRoundWindow) slotStopRound(issueSlot) else U(fixedStopRound, 6 bits)
    val issueFinal = issueValid && issueRound === issueStopRound
    val issuePreferNext = UInt(contextSlotBits bits)
    issuePreferNext := issueSlot + U(1, contextSlotBits bits)
    when(issueSlot === U(contextSlotCount - 1, contextSlotBits bits)) {
      issuePreferNext := U(0, contextSlotBits bits)
    }

    readFire := issueValid
    readAddress := issueSlot.resize(contextAddressBits)
    readContext := slotContext.readSync(address = readAddress, enable = readFire)
    val readState = (0 until 8).map(i => unpackContextWord(readContext, i))
    val readSchedule = (0 until 16).map(i => unpackContextWord(readContext, 8 + i))
    val (readT1, readT2, readWNext) = Sha256.roundStepPrepare(readState, readSchedule, kWords(readRound))
    val readScheduleAfterRound = (1 until 16).map(i => readSchedule(i)) :+ readWNext

    val slotFree = Vec((0 until contextSlotCount).map(slot => !slotActive(slot)))
    val loadSlot = UInt(contextSlotBits bits)
    loadSlot := U(0, contextSlotBits bits)
    when(slotFree(0)) {
      loadSlot := U(0, contextSlotBits bits)
    } elsewhen(slotFree(1)) {
      loadSlot := U(1, contextSlotBits bits)
    } otherwise {
      loadSlot := U(2, contextSlotBits bits)
    }

    val roundCount = (stopRoundValue.resize(7) - startRoundValue.resize(7) + U(1, 7 bits)).resized
    val startGapLoad = roundCount - U(1, 7 bits)
    val pipelineReady = slotFree.reduce(_ || _) && startGap === 0 && !stageWriteBack
    val startAccepted = io.start && pipelineReady
    val startState = (0 until 8).map(i => io.stateIn(255 - i * 32 downto 224 - i * 32).asUInt)
    val startSchedule = (0 until 16).map(i => io.words(i))
    val startContext = packContext(startState, startSchedule)
    val writeContext = Mux(stageWriteBack, packContext(stageStateAfterRound, stageScheduleAfterRound), startContext)
    val writeSlot = Mux(stageWriteBack, stageSlot, loadSlot).resize(contextAddressBits)
    slotContext.write(address = writeSlot, data = writeContext, enable = stageWriteBack || startAccepted)

    io.ready := pipelineReady

    if (registerOutput) {
      val doneReg = Reg(Bool()) init False
      val workOutReg = if (lowWordOnlyOutput) null else Reg(Bits(256 bits)) init 0
      val workLow32Reg = Reg(UInt(32 bits)) init 0

      io.done := doneReg
      io.workOut := (if (lowWordOnlyOutput) B(0, 256 bits) else workOutReg)
      io.workLow32 := workLow32Reg

      when(io.reset) {
        doneReg := False
        workLow32Reg := 0
        if (!minimizeResetFanout && !lowWordOnlyOutput) {
          workOutReg := 0
        }
      } otherwise {
        doneReg := stageComplete
        when(stageComplete) {
          if (!lowWordOnlyOutput) {
            workOutReg := stageWork
          }
          workLow32Reg := stageWorkLow32
        }
      }
    } else {
      io.done := stageComplete
      io.workOut := stageWork
      io.workLow32 := stageWorkLow32
    }

    when(io.reset) {
      for (slot <- 0 until contextSlotCount) {
        slotActive(slot) := False
        slotRound(slot) := 0
        if (dynamicRoundWindow) {
          slotStopRound(slot) := 63
        }
      }
      readValid := False
      readSlot := 0
      readRound := 0
      if (dynamicRoundWindow) {
        readStopRound := 63
      }
      stageValid := False
      stageSlot := 0
      stageRound := 0
      if (dynamicRoundWindow) {
        stageStopRound := 63
      }
      issuePrefer := 0
      startGap := 0
      if (!minimizeResetFanout) {
        for (i <- 0 until 7) stageState(i) := 0
        for (i <- 0 until 16) stageSchedule(i) := 0
        stageT1 := 0
        stageT2 := 0
      }
    } otherwise {
      stageValid := False
      readValid := readFire

      when(startAccepted) {
        startGap := startGapLoad
      } elsewhen(startGap =/= 0) {
        startGap := startGap - 1
      }

      when(stageWriteBack) {
        when(stageSlot === U(0, contextSlotBits bits)) {
          slotRound(0) := stageRoundPlusOne
        } elsewhen(stageSlot === U(1, contextSlotBits bits)) {
          slotRound(1) := stageRoundPlusOne
        } otherwise {
          slotRound(2) := stageRoundPlusOne
        }
      }

      when(issueFinal) {
        when(issueSlot === U(0, contextSlotBits bits)) {
          slotActive(0) := False
        } elsewhen(issueSlot === U(1, contextSlotBits bits)) {
          slotActive(1) := False
        } otherwise {
          slotActive(2) := False
        }
      }

      when(startAccepted) {
        when(loadSlot === U(0, contextSlotBits bits)) {
          slotActive(0) := True
          slotRound(0) := startRoundValue
          if (dynamicRoundWindow) {
            slotStopRound(0) := stopRoundValue
          }
        } elsewhen(loadSlot === U(1, contextSlotBits bits)) {
          slotActive(1) := True
          slotRound(1) := startRoundValue
          if (dynamicRoundWindow) {
            slotStopRound(1) := stopRoundValue
          }
        } otherwise {
          slotActive(2) := True
          slotRound(2) := startRoundValue
          if (dynamicRoundWindow) {
            slotStopRound(2) := stopRoundValue
          }
        }
      }

      when(readFire) {
        readSlot := issueSlot
        readRound := issueRound
        if (dynamicRoundWindow) {
          readStopRound := issueStopRound
        }
        issuePrefer := issuePreferNext
      }

      when(readValid) {
        stageValid := True
        stageSlot := readSlot
        stageRound := readRound
        if (dynamicRoundWindow) {
          stageStopRound := readStopRound
        }
        for (i <- 0 until 7) {
          stageState(i) := readState(i)
        }
        for (i <- 0 until 16) {
          stageSchedule(i) := readScheduleAfterRound(i)
        }
        stageT1 := readT1
        stageT2 := readT2
      }
    }
  } else if (twoRoundPipeline) {
    val slotActive = Vec(Reg(Bool()) init False, 2)
    val slotRound = Vec(Reg(UInt(6 bits)) init 0, 2)
    val slotStopRound = Vec(Reg(UInt(6 bits)) init 63, 2)
    val slotState = Vec((0 until 2).map(_ => Vec(Reg(UInt(32 bits)) init 0, 8)))
    val slotSchedule = Vec((0 until 2).map(_ => Vec(Reg(UInt(32 bits)) init 0, 16)))

    val stageValid = Reg(Bool()) init False
    val stageSlot = Reg(UInt(1 bits)) init 0
    val stageRound = Reg(UInt(6 bits)) init 0
    val stageStopRound = Reg(UInt(6 bits)) init 63
    val stageState = Vec(Reg(UInt(32 bits)) init 0, 8)
    val stageSchedule = Vec(Reg(UInt(32 bits)) init 0, 16)
    val issuePrefer = Reg(UInt(1 bits)) init 0
    val startGap = Reg(UInt(6 bits)) init 0

    val stageRoundPlusOne = (stageRound + U(1, 6 bits)).resized
    val stageRoundPlusTwo = (stageRound + U(2, 6 bits)).resized
    val stageSecondValid = stageRound =/= stageStopRound
    val (stageStateAfterSecondRaw, stageScheduleAfterSecondRaw) =
      Sha256.roundStep((0 until 8).map(i => stageState(i)), (0 until 16).map(i => stageSchedule(i)), kWords(stageRoundPlusOne))
    val stageStateAfterPair = (0 until 8).map(i => Mux(stageSecondValid, stageStateAfterSecondRaw(i), stageState(i)))
    val stageScheduleAfterPair = (0 until 16).map(i => Mux(stageSecondValid, stageScheduleAfterSecondRaw(i), stageSchedule(i)))
    val stageComplete = stageValid && (stageRound === stageStopRound || stageRoundPlusOne === stageStopRound)
    val stageWork = if (lowWordOnlyOutput) B(0, 256 bits) else Sha256.concatWords(stageStateAfterPair)
    val stageWorkLow32 = stageStateAfterPair(lowWordIndex)

    val slotCompletes0 = stageComplete && stageSlot === U(0, 1 bits)
    val slotCompletes1 = stageComplete && stageSlot === U(1, 1 bits)
    val slotFree0 = !slotActive(0) || slotCompletes0
    val slotFree1 = !slotActive(1) || slotCompletes1
    val loadSlot = UInt(1 bits)
    loadSlot := U(0, 1 bits)
    when(!slotFree0 && slotFree1) {
      loadSlot := U(1, 1 bits)
    }
    val roundCount = (stopRoundValue.resize(7) - startRoundValue.resize(7) + U(1, 7 bits)).resized
    val pairCount = (roundCount(6 downto 1) + roundCount(0).asUInt.resize(6)).resize(6)
    val startGapLoad = pairCount
    val pipelineReady = (slotFree0 || slotFree1) && startGap === 0
    val startAccepted = io.start && pipelineReady

    val canIssue0 = slotActive(0) && !(stageValid && stageSlot === U(0, 1 bits))
    val canIssue1 = slotActive(1) && !(stageValid && stageSlot === U(1, 1 bits))
    val issueSlot = UInt(1 bits)
    issueSlot := issuePrefer
    when(issuePrefer === U(0, 1 bits)) {
      when(!canIssue0 && canIssue1) {
        issueSlot := U(1, 1 bits)
      }
    } otherwise {
      when(!canIssue1 && canIssue0) {
        issueSlot := U(0, 1 bits)
      }
    }
    val issueValid = (issueSlot === U(0, 1 bits) && canIssue0) || (issueSlot === U(1, 1 bits) && canIssue1)
    val issueRound = Mux(issueSlot === U(0, 1 bits), slotRound(0), slotRound(1))
    val issueStopRound = Mux(issueSlot === U(0, 1 bits), slotStopRound(0), slotStopRound(1))
    val issueState = (0 until 8).map(i => Mux(issueSlot === U(0, 1 bits), slotState(0)(i), slotState(1)(i)))
    val issueSchedule = (0 until 16).map(i => Mux(issueSlot === U(0, 1 bits), slotSchedule(0)(i), slotSchedule(1)(i)))
    val (issueStateAfterFirst, issueScheduleAfterFirst) = Sha256.roundStep(issueState, issueSchedule, kWords(issueRound))

    io.ready := pipelineReady

    if (registerOutput) {
      val doneReg = Reg(Bool()) init False
      val workOutReg = if (lowWordOnlyOutput) null else Reg(Bits(256 bits)) init 0
      val workLow32Reg = Reg(UInt(32 bits)) init 0

      io.done := doneReg
      io.workOut := (if (lowWordOnlyOutput) B(0, 256 bits) else workOutReg)
      io.workLow32 := workLow32Reg

      when(io.reset) {
        doneReg := False
        workLow32Reg := 0
        if (!minimizeResetFanout && !lowWordOnlyOutput) {
          workOutReg := 0
        }
      } otherwise {
        doneReg := stageComplete
        when(stageComplete) {
          if (!lowWordOnlyOutput) {
            workOutReg := stageWork
          }
          workLow32Reg := stageWorkLow32
        }
      }
    } else {
      io.done := stageComplete
      io.workOut := stageWork
      io.workLow32 := stageWorkLow32
    }

    when(io.reset) {
      for (slot <- 0 until 2) {
        slotActive(slot) := False
        slotRound(slot) := 0
        slotStopRound(slot) := 63
        if (!minimizeResetFanout) {
          for (i <- 0 until 8) slotState(slot)(i) := 0
          for (i <- 0 until 16) slotSchedule(slot)(i) := 0
        }
      }
      stageValid := False
      stageSlot := 0
      stageRound := 0
      stageStopRound := 63
      issuePrefer := 0
      startGap := 0
      if (!minimizeResetFanout) {
        for (i <- 0 until 8) stageState(i) := 0
        for (i <- 0 until 16) stageSchedule(i) := 0
      }
    } otherwise {
      stageValid := False

      when(startAccepted) {
        startGap := startGapLoad
      } elsewhen(startGap =/= 0) {
        startGap := startGap - 1
      }

      when(stageValid) {
        when(stageSlot === U(0, 1 bits)) {
          for (i <- 0 until 8) slotState(0)(i) := stageStateAfterPair(i)
          for (i <- 0 until 16) slotSchedule(0)(i) := stageScheduleAfterPair(i)
          when(stageComplete) {
            slotActive(0) := False
          } otherwise {
            slotRound(0) := stageRoundPlusTwo
          }
        } otherwise {
          for (i <- 0 until 8) slotState(1)(i) := stageStateAfterPair(i)
          for (i <- 0 until 16) slotSchedule(1)(i) := stageScheduleAfterPair(i)
          when(stageComplete) {
            slotActive(1) := False
          } otherwise {
            slotRound(1) := stageRoundPlusTwo
          }
        }
      }

      when(startAccepted) {
        when(loadSlot === U(0, 1 bits)) {
          slotActive(0) := True
          slotRound(0) := startRoundValue
          slotStopRound(0) := stopRoundValue
          for (i <- 0 until 8) {
            slotState(0)(i) := io.stateIn(255 - i * 32 downto 224 - i * 32).asUInt
          }
          for (i <- 0 until 16) {
            slotSchedule(0)(i) := io.words(i)
          }
        } otherwise {
          slotActive(1) := True
          slotRound(1) := startRoundValue
          slotStopRound(1) := stopRoundValue
          for (i <- 0 until 8) {
            slotState(1)(i) := io.stateIn(255 - i * 32 downto 224 - i * 32).asUInt
          }
          for (i <- 0 until 16) {
            slotSchedule(1)(i) := io.words(i)
          }
        }
      }

      when(issueValid) {
        stageValid := True
        stageSlot := issueSlot
        stageRound := issueRound
        stageStopRound := issueStopRound
        for (i <- 0 until 8) {
          stageState(i) := issueStateAfterFirst(i)
        }
        for (i <- 0 until 16) {
          stageSchedule(i) := issueScheduleAfterFirst(i)
        }
        issuePrefer := ~issueSlot
      }
    }
  } else if (twoRoundsPerCycle) {
    val state0 = Seq(a, b, c, d, e, f, g, h)
    val schedule0 = (0 until 16).map(i => w(i))
    val roundPlusOne = (round + U(1, 6 bits)).resized
    val roundPlusTwo = (round + U(2, 6 bits)).resized
    val (state1, schedule1) = Sha256.roundStep(state0, schedule0, kWords(round))
    val (state2, schedule2) = Sha256.roundStep(state1, schedule1, kWords(roundPlusOne))
    val secondRoundValid = round =/= stopRoundValue
    val finalRoundPair = busyReg && ((round === stopRoundValue) || (roundPlusOne === stopRoundValue))
    val selectedState = (0 until 8).map(i => Mux(secondRoundValid, state2(i), state1(i)))
    val selectedSchedule = (0 until 16).map(i => Mux(secondRoundValid, schedule2(i), schedule1(i)))
    val finalWorkPair = if (lowWordOnlyOutput) B(0, 256 bits) else Sha256.concatWords(selectedState)
    val finalWorkPairLow32 = selectedState(lowWordIndex)
    io.ready := !busyReg || finalRoundPair

    if (registerOutput) {
      val doneReg = Reg(Bool()) init False
      val workOutReg = if (lowWordOnlyOutput) null else Reg(Bits(256 bits)) init 0
      val workLow32Reg = Reg(UInt(32 bits)) init 0

      io.done := doneReg
      io.workOut := (if (lowWordOnlyOutput) B(0, 256 bits) else workOutReg)
      io.workLow32 := workLow32Reg

      when(io.reset) {
        doneReg := False
        workLow32Reg := 0
        if (!minimizeResetFanout) {
          if (!lowWordOnlyOutput) {
            workOutReg := 0
          }
        }
      } otherwise {
        doneReg := finalRoundPair
        when(finalRoundPair) {
          if (!lowWordOnlyOutput) {
            workOutReg := finalWorkPair
          }
          workLow32Reg := finalWorkPairLow32
        }
      }
    } else {
      io.done := finalRoundPair
      io.workOut := finalWorkPair
      io.workLow32 := finalWorkPairLow32
    }

    when(io.reset) {
      clearState()
    } otherwise {
      when(io.start && (!busyReg || finalRoundPair)) {
        loadState()
      } elsewhen(busyReg) {
        for (i <- 0 until 16) {
          w(i) := selectedSchedule(i)
        }
        wRound := selectedSchedule(0)

        a := selectedState(0)
        b := selectedState(1)
        c := selectedState(2)
        d := selectedState(3)
        e := selectedState(4)
        f := selectedState(5)
        g := selectedState(6)
        h := selectedState(7)

        when(finalRoundPair) {
          busyReg := False
        } otherwise {
          round := roundPlusTwo
        }
      }
    }
  } else if (threeCycleRound) {
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
    val finalSplitState = Seq(aSplitNext, a, b, c, eSplitNext, e, f, g)
    val finalSplitWork = if (lowWordOnlyOutput) B(0, 256 bits) else Sha256.concatWords(finalSplitState)
    val finalSplitWorkLow32 = finalSplitState(lowWordIndex)

    io.ready := !busyReg
    io.done := doneReg
    io.workOut := workOutReg
    val workLow32Reg = Reg(UInt(32 bits)) init 0
    io.workLow32 := workLow32Reg

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
      workLow32Reg := 0
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

            when(round === stopRoundValue) {
              busyReg := False
              round := 0
              phase := Phase.prepare
              doneReg := True
              workOutReg := finalSplitWork
              workLow32Reg := finalSplitWorkLow32
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
    val workLow32Reg = Reg(UInt(32 bits)) init 0
    val aSplitNext = (t1Reg + t2Reg).resize(32)
    val eSplitNext = (d + t1Reg).resize(32)
    val finalSplitState = Seq(aSplitNext, a, b, c, eSplitNext, e, f, g)
    val finalSplitWork = if (lowWordOnlyOutput) B(0, 256 bits) else Sha256.concatWords(finalSplitState)
    val finalSplitWorkLow32 = finalSplitState(lowWordIndex)

    io.ready := !busyReg
    io.done := doneReg
    io.workOut := workOutReg
    io.workLow32 := workLow32Reg

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
      workLow32Reg := 0
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

            when(round === stopRoundValue) {
              busyReg := False
              round := 0
              phase := Phase.compute
              doneReg := True
              workOutReg := finalSplitWork
              workLow32Reg := finalSplitWorkLow32
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
    io.ready := !busyReg || finalRound

    if (registerOutput) {
      val doneReg = Reg(Bool()) init False
      val workOutReg = if (lowWordOnlyOutput) null else Reg(Bits(256 bits)) init 0
      val workLow32Reg = Reg(UInt(32 bits)) init 0

      io.done := doneReg
      io.workOut := (if (lowWordOnlyOutput) B(0, 256 bits) else workOutReg)
      io.workLow32 := workLow32Reg

      when(io.reset) {
        doneReg := False
        workLow32Reg := 0
        if (!minimizeResetFanout) {
          if (!lowWordOnlyOutput) {
            workOutReg := 0
          }
        }
      } otherwise {
        doneReg := finalRound
        when(finalRound) {
          if (!lowWordOnlyOutput) {
            workOutReg := finalWork
          }
          workLow32Reg := finalWorkLow32
        }
      }
    } else {
      io.done := finalRound
      io.workOut := finalWork
      io.workLow32 := finalWorkLow32
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

        when(round === stopRoundValue) {
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
  registerCompressorOutputs: Boolean = false,
  registerFeedForward: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  twoRoundsPerCycle: Boolean = false,
  twoRoundPipeline: Boolean = false,
  twoPhaseRoundPipeline: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false,
  roundSkip: Boolean = false,
  csaRound: Boolean = false,
  csaSchedule: Boolean = false,
  balancedRoundAdder: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val prepare = in Bool()
    val start = in Bool()
    val kWord = in UInt(32 bits)
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val nonce = in UInt(32 bits)
    val ready = out Bool()
    val done = out Bool()
    val round = out UInt(6 bits)
    val digest = out Bits(256 bits)
  }

  val core = new Sha256CompressWords(
    registerOutput = registerCompressorOutputs,
    twoCycleRound = twoCycleRound,
    threeCycleRound = threeCycleRound,
    twoRoundsPerCycle = twoRoundsPerCycle,
    twoRoundPipeline = twoRoundPipeline,
    twoPhaseRoundPipeline = twoPhaseRoundPipeline,
    registerRoundConstant = registerRoundConstant,
    minimizeResetFanout = minimizeShaReset,
    dynamicRoundWindow = roundSkip,
    csaRound = csaRound,
    csaSchedule = csaSchedule,
    balancedRoundAdder = balancedRoundAdder
  )
  io.round := core.io.roundOut

  def driveOutputs(doneRaw: Bool, workRaw: Bits): Unit = {
    val digestRaw = Sha256Pass.addFeedForward(io.midstate, workRaw)

    if (registerOutputs) {
      val doneReg = Reg(Bool()) init False

      if (registerFeedForward) {
        val feedForwardPendingReg = Reg(Bool()) init False
        val workOutReg = Reg(Bits(256 bits))
        val digestPipelined = Sha256Pass.addFeedForward(io.midstate, workOutReg)

        io.done := doneReg
        io.digest := digestPipelined

        if (!minimizeShaReset) {
          workOutReg.init(0)
        }

        when(io.reset) {
          doneReg := False
          feedForwardPendingReg := False
        } otherwise {
          doneReg := feedForwardPendingReg
          feedForwardPendingReg := doneRaw
          when(doneRaw) {
            workOutReg := workRaw
          }
        }
      } else {
        val digestReg = Reg(Bits(256 bits)) init 0

        io.done := doneReg
        io.digest := digestReg

        when(io.reset) {
          doneReg := False
          if (!minimizeShaReset) {
            digestReg := 0
          }
        } otherwise {
          doneReg := doneRaw
          when(doneRaw) {
            digestReg := digestRaw
          }
        }
      }
    } else {
      io.done := doneRaw
      io.digest := digestRaw
    }
  }

  val tail0 = io.tail(95 downto 64).asUInt
  val tail1 = io.tail(63 downto 32).asUInt
  val tail2 = io.tail(31 downto 0).asUInt
  val padStart = U(BigInt("80000000", 16), 32 bits)
  val firstLength = U(BigInt("00000280", 16), 32 bits)
  val zero = U(0, 32 bits)

  if (roundSkip) {
    object State extends SpinalEnum {
      val idle, preparing, ready, hashing = newElement()
    }

    val state = Reg(State()) init State.idle
    val prefixState = Reg(Bits(256 bits)) init 0

    val w16 = (Sha256.smallSigma0(tail1) + tail0).resize(32)
    val w17 = (Sha256.smallSigma1(firstLength) + Sha256.smallSigma0(tail2) + tail1).resize(32)
    val w18 = (Sha256.smallSigma1(w16) + Sha256.smallSigma0(io.nonce) + tail2).resize(32)

    val prefixWords = Vec(Seq(
      tail0,
      tail1,
      tail2,
      zero,
      padStart,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      firstLength
    ))
    val hashWords = Vec(Seq(
      io.nonce,
      padStart,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      firstLength,
      w16,
      w17,
      w18
    ))

    val prepareStart = io.prepare && (state === State.idle || state === State.ready)
    val hashStart = io.start && (state === State.ready || (state === State.hashing && core.io.done))
    val coreWords = Vec((0 until 16).map(i => Mux(prepareStart, prefixWords(i), hashWords(i))))

    core.io.reset := io.reset
    core.io.start := prepareStart || hashStart
    core.io.startRound := Mux(prepareStart, U(0, 6 bits), U(3, 6 bits))
    core.io.stopRound := Mux(prepareStart, U(2, 6 bits), U(63, 6 bits))
    core.io.kWord := io.kWord
    core.io.stateIn := Mux(prepareStart, io.midstate, prefixState)
    core.io.words := coreWords

    when(io.reset) {
      state := State.idle
      prefixState := 0
    } otherwise {
      when(prepareStart) {
        state := State.preparing
      } elsewhen(hashStart) {
        state := State.hashing
      } elsewhen(state === State.preparing && core.io.done) {
        prefixState := core.io.workOut
        state := State.ready
      } elsewhen(state === State.hashing && core.io.done) {
        state := State.ready
      }
    }

    io.ready := state === State.ready || (state === State.hashing && core.io.done)
    driveOutputs(state === State.hashing && core.io.done, core.io.workOut)
  } else {
    val words = Vec(Seq(
      tail0,
      tail1,
      tail2,
      io.nonce,
      padStart,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      zero,
      firstLength
    ))

    core.io.reset := io.reset
    core.io.start := io.start
    core.io.startRound := U(0, 6 bits)
    core.io.stopRound := U(63, 6 bits)
    core.io.kWord := io.kWord
    core.io.stateIn := io.midstate
    core.io.words := words

    io.ready := core.io.ready
    driveOutputs(core.io.done, core.io.workOut)
  }
}

class Sha256BitcoinFirstPassPrefix(
  registerOutputs: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  twoRoundsPerCycle: Boolean = false,
  twoRoundPipeline: Boolean = false,
  twoPhaseRoundPipeline: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false,
  csaRound: Boolean = false,
  csaSchedule: Boolean = false,
  balancedRoundAdder: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val ready = out Bool()
    val prefixState = out Bits(256 bits)
    val tail2 = out UInt(32 bits)
    val w16 = out UInt(32 bits)
    val w17 = out UInt(32 bits)
  }

  val tail0 = io.tail(95 downto 64).asUInt
  val tail1 = io.tail(63 downto 32).asUInt
  val tail2 = io.tail(31 downto 0).asUInt
  val firstLength = U(BigInt("00000280", 16), 32 bits)
  val w16 = (Sha256.smallSigma0(tail1) + tail0).resize(32)
  val w17 = (Sha256.smallSigma1(firstLength) + Sha256.smallSigma0(tail2) + tail1).resize(32)

  val kVec = Vec(Sha256.K.map(Sha256.word))
  val a, b, c, d, e, f, g, h = Reg(UInt(32 bits)) init 0
  val round = Reg(UInt(2 bits)) init 0
  val busyReg = Reg(Bool()) init False
  val readyReg = Reg(Bool()) init False
  val tail0Reg = Reg(UInt(32 bits)) init 0
  val tail1Reg = Reg(UInt(32 bits)) init 0
  val tail2Reg = Reg(UInt(32 bits)) init 0
  val w16Reg = Reg(UInt(32 bits)) init 0
  val w17Reg = Reg(UInt(32 bits)) init 0

  val selectedW = UInt(32 bits)
  selectedW := tail0Reg
  switch(round) {
    is(U(1, 2 bits)) {
      selectedW := tail1Reg
    }
    is(U(2, 2 bits)) {
      selectedW := tail2Reg
    }
  }

  val selectedK = kVec(round.resized)
  val t1Terms = Seq(h, Sha256.bigSigma1(e), Sha256.ch(e, f, g), selectedK, selectedW)
  val t2Terms = Seq(Sha256.bigSigma0(a), Sha256.maj(a, b, c))
  val t1 = if (balancedRoundAdder) {
    Sha256.add32Balanced(h, Sha256.bigSigma1(e), Sha256.ch(e, f, g), selectedK, selectedW)
  } else {
    (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g) + selectedK + selectedW).resize(32)
  }
  val t2 = if (balancedRoundAdder) {
    Sha256.add32Balanced(Sha256.bigSigma0(a), Sha256.maj(a, b, c))
  } else {
    (Sha256.bigSigma0(a) + Sha256.maj(a, b, c)).resize(32)
  }
  val aNext = if (csaRound) {
    Sha256.add32Csa((t1Terms ++ t2Terms): _*)
  } else if (balancedRoundAdder) {
    Sha256.add32Balanced(t1, t2)
  } else {
    (t1 + t2).resize(32)
  }
  val eNext = if (csaRound) {
    Sha256.add32Csa((Seq(d) ++ t1Terms): _*)
  } else if (balancedRoundAdder) {
    Sha256.add32Balanced(d, t1)
  } else {
    (d + t1).resize(32)
  }
  val finalWork = Sha256.concatWords(Seq(aNext, a, b, c, eNext, e, f, g))

  when(io.reset) {
    a := 0; b := 0; c := 0; d := 0; e := 0; f := 0; g := 0; h := 0
    round := 0
    busyReg := False
    readyReg := False
    tail0Reg := 0
    tail1Reg := 0
    tail2Reg := 0
    w16Reg := 0
    w17Reg := 0
  } otherwise {
    when(io.start) {
      a := io.midstate(255 downto 224).asUInt
      b := io.midstate(223 downto 192).asUInt
      c := io.midstate(191 downto 160).asUInt
      d := io.midstate(159 downto 128).asUInt
      e := io.midstate(127 downto 96).asUInt
      f := io.midstate(95 downto 64).asUInt
      g := io.midstate(63 downto 32).asUInt
      h := io.midstate(31 downto 0).asUInt
      round := 0
      busyReg := True
      readyReg := False
      tail0Reg := tail0
      tail1Reg := tail1
      tail2Reg := tail2
      w16Reg := w16
      w17Reg := w17
    } elsewhen(busyReg) {
      h := g
      g := f
      f := e
      e := eNext
      d := c
      c := b
      b := a
      a := aNext

      when(round === U(2, 2 bits)) {
        busyReg := False
        readyReg := True
      } otherwise {
        round := round + 1
      }
    }
  }

  io.ready := readyReg
  io.prefixState := Sha256.concatWords(Seq(a, b, c, d, e, f, g, h))
  io.tail2 := tail2Reg
  io.w16 := w16Reg
  io.w17 := w17Reg
}

class Sha256BitcoinFirstPassPrepared(
  registerOutputs: Boolean = false,
  registerCompressorOutputs: Boolean = false,
  registerFeedForward: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  twoRoundsPerCycle: Boolean = false,
  twoRoundPipeline: Boolean = false,
  twoPhaseRoundPipeline: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false,
  csaRound: Boolean = false,
  csaSchedule: Boolean = false,
  balancedRoundAdder: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val kWord = in UInt(32 bits)
    val midstate = in Bits(256 bits)
    val prefixState = in Bits(256 bits)
    val tail2 = in UInt(32 bits)
    val w16 = in UInt(32 bits)
    val w17 = in UInt(32 bits)
    val nonce = in UInt(32 bits)
    val ready = out Bool()
    val done = out Bool()
    val round = out UInt(6 bits)
    val digest = out Bits(256 bits)
  }

  val core = new Sha256CompressWords(
    registerOutput = registerCompressorOutputs,
    twoCycleRound = twoCycleRound,
    threeCycleRound = threeCycleRound,
    twoRoundsPerCycle = twoRoundsPerCycle,
    twoRoundPipeline = twoRoundPipeline,
    twoPhaseRoundPipeline = twoPhaseRoundPipeline,
    registerRoundConstant = registerRoundConstant,
    minimizeResetFanout = minimizeShaReset,
    fixedStartRound = 3,
    fixedStopRound = 63,
    csaRound = csaRound,
    csaSchedule = csaSchedule,
    balancedRoundAdder = balancedRoundAdder
  )
  val padStart = U(BigInt("80000000", 16), 32 bits)
  val firstLength = U(BigInt("00000280", 16), 32 bits)
  val zero = U(0, 32 bits)
  val w18 = (Sha256.smallSigma1(io.w16) + Sha256.smallSigma0(io.nonce) + io.tail2).resize(32)
  val words = Vec(Seq(
    io.nonce,
    padStart,
    zero,
    zero,
    zero,
    zero,
    zero,
    zero,
    zero,
    zero,
    zero,
    zero,
    firstLength,
    io.w16,
    io.w17,
    w18
  ))

  core.io.reset := io.reset
  core.io.start := io.start
  core.io.startRound := U(3, 6 bits)
  core.io.stopRound := U(63, 6 bits)
  core.io.kWord := io.kWord
  core.io.stateIn := io.prefixState
  core.io.words := words

  io.round := core.io.roundOut
  io.ready := core.io.ready
  val digestRaw = Sha256Pass.addFeedForward(io.midstate, core.io.workOut)
  if (registerOutputs) {
    val doneReg = Reg(Bool()) init False

    if (registerFeedForward) {
      val feedForwardPendingReg = Reg(Bool()) init False
      val workOutReg = Reg(Bits(256 bits))
      val digestPipelined = Sha256Pass.addFeedForward(io.midstate, workOutReg)

      io.done := doneReg
      io.digest := digestPipelined

      if (!minimizeShaReset) {
        workOutReg.init(0)
      }

      when(io.reset) {
        doneReg := False
        feedForwardPendingReg := False
      } otherwise {
        doneReg := feedForwardPendingReg
        feedForwardPendingReg := core.io.done
        when(core.io.done) {
          workOutReg := core.io.workOut
        }
      }
    } else {
      val digestReg = Reg(Bits(256 bits)) init 0

      io.done := doneReg
      io.digest := digestReg

      when(io.reset) {
        doneReg := False
        if (!minimizeShaReset) {
          digestReg := 0
        }
      } otherwise {
        doneReg := core.io.done
        when(core.io.done) {
          digestReg := digestRaw
        }
      }
    }
  } else {
    io.done := core.io.done
    io.digest := digestRaw
  }
}

class Sha256BitcoinSecondPass(
  registerOutputs: Boolean = false,
  registerCompressorOutputs: Boolean = false,
  twoCycleRound: Boolean = false,
  threeCycleRound: Boolean = false,
  twoRoundsPerCycle: Boolean = false,
  twoRoundPipeline: Boolean = false,
  twoPhaseRoundPipeline: Boolean = false,
  registerRoundConstant: Boolean = false,
  minimizeShaReset: Boolean = false,
  roundSkip: Boolean = false,
  csaRound: Boolean = false,
  csaSchedule: Boolean = false,
  balancedRoundAdder: Boolean = false
) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val kWord = in UInt(32 bits)
    val firstDigest = in Bits(256 bits)
    val ready = out Bool()
    val done = out Bool()
    val round = out UInt(6 bits)
    val workLow32 = out UInt(32 bits)
  }

  val core = new Sha256CompressWords(
    registerOutput = registerCompressorOutputs,
    twoCycleRound = twoCycleRound,
    threeCycleRound = threeCycleRound,
    twoRoundsPerCycle = twoRoundsPerCycle,
    twoRoundPipeline = twoRoundPipeline,
    twoPhaseRoundPipeline = twoPhaseRoundPipeline,
    registerRoundConstant = registerRoundConstant,
    minimizeResetFanout = minimizeShaReset,
    fixedStopRound = if (roundSkip) 60 else 63,
    csaRound = csaRound,
    csaSchedule = csaSchedule,
    balancedRoundAdder = balancedRoundAdder,
    lowWordOnlyOutput = true,
    lowWordIndex = if (roundSkip) 4 else 7
  )
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
  core.io.startRound := U(0, 6 bits)
  core.io.stopRound := U(if (roundSkip) 60 else 63, 6 bits)
  core.io.kWord := io.kWord
  core.io.stateIn := shaIv
  core.io.words := words

  io.round := core.io.roundOut
  io.ready := core.io.ready
  val workLow32 = core.io.workLow32
  if (registerOutputs) {
    val doneReg = Reg(Bool()) init False
    val workLow32Reg = Reg(UInt(32 bits)) init 0

    io.done := doneReg
    io.workLow32 := workLow32Reg

    when(io.reset) {
      doneReg := False
      workLow32Reg := 0
    } otherwise {
      doneReg := core.io.done
      when(core.io.done) {
        workLow32Reg := workLow32
      }
    }
  } else {
    io.done := core.io.done
    io.workLow32 := workLow32
  }
}

object BitcoinCandidateFilter {
  private val Iv7 = Sha256.Iv(7)

  private def lowMask(width: Int): BigInt =
    (BigInt(1) << width) - 1

  private def addIv7Low(workLow32: UInt, width: Int): Bits = {
    require(width > 0 && width <= 32, s"width must be 1..32, got $width")
    (workLow32.asBits(width - 1 downto 0).asUInt + U(Iv7 & lowMask(width), width bits)).resize(width).asBits
  }

  private def quick3MeetsTarget(digestLowBits: Bits): Bool =
    digestLowBits(7 downto 5) === B(0, 3 bits)

  private def quick14MeetsTarget(digestLowBits: Bits): Bool =
    digestLowBits(7 downto 0) === B(0, 8 bits) &&
      digestLowBits(15 downto 10) === B(0, 6 bits)

  private def quick21MeetsTarget(digestLowBits: Bits): Bool =
    digestLowBits(7 downto 0) === B(0, 8 bits) &&
      digestLowBits(15 downto 8) === B(0, 8 bits) &&
      digestLowBits(23 downto 19) === B(0, 5 bits)

  private def quick23MeetsTarget(digestLowBits: Bits): Bool =
    digestLowBits(7 downto 0) === B(0, 8 bits) &&
      digestLowBits(15 downto 8) === B(0, 8 bits) &&
      digestLowBits(23 downto 17) === B(0, 7 bits)

  private def quick26MeetsTarget(digestLowBits: Bits): Bool =
    digestLowBits(7 downto 0) === B(0, 8 bits) &&
      digestLowBits(15 downto 8) === B(0, 8 bits) &&
      digestLowBits(23 downto 16) === B(0, 8 bits) &&
      digestLowBits(31 downto 30) === B(0, 2 bits)

  def meets(options: TangMinerHardwareOptions, mode: UInt, workLow32: UInt): Bool = {
    val candidateAlwaysSelected = mode === U(0, 3 bits)
    val quick3TargetSelected = mode === U(1, 3 bits)
    val quick21TargetSelected = mode === U(2, 3 bits)
    val quick23TargetSelected = mode === U(3, 3 bits)
    val quick26TargetSelected = mode === U(4, 3 bits)
    val quick14TargetSelected = mode === U(5, 3 bits)

    def fixedCandidateMeetsTarget(mode: Int): Bool = mode match {
      case 0 => True
      case 1 => quick3MeetsTarget(addIv7Low(workLow32, 8))
      case 2 => quick21MeetsTarget(addIv7Low(workLow32, 24))
      case 3 => quick23MeetsTarget(addIv7Low(workLow32, 24))
      case 4 => quick26MeetsTarget(addIv7Low(workLow32, 32))
      case 5 => quick14MeetsTarget(addIv7Low(workLow32, 16))
    }

    options.fixedCandidateMode match {
      case Some(fixedMode) => fixedCandidateMeetsTarget(fixedMode)
      case None =>
        val digestLow32 = addIv7Low(workLow32, 32)
        candidateAlwaysSelected ||
          (quick3TargetSelected && quick3MeetsTarget(digestLow32)) ||
          (quick14TargetSelected && quick14MeetsTarget(digestLow32)) ||
          (quick21TargetSelected && quick21MeetsTarget(digestLow32)) ||
          (quick23TargetSelected && quick23MeetsTarget(digestLow32)) ||
          (quick26TargetSelected && quick26MeetsTarget(digestLow32))
    }
  }
}

case class UnrolledPipelineStage(state: Seq[UInt], schedule: Seq[UInt], valid: Bool, nonce: UInt)

object UnrolledPipelineRound {
  def register(
    input: UnrolledPipelineStage,
    roundIndex: Int,
    scheduleRequired: Boolean,
    clearPipeline: Bool
  ): UnrolledPipelineStage = {
    val a = input.state(0)
    val b = input.state(1)
    val c = input.state(2)
    val d = input.state(3)
    val e = input.state(4)
    val f = input.state(5)
    val g = input.state(6)
    val h = input.state(7)
    val t1 = (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g) +
      Sha256.word(Sha256.K(roundIndex)) + input.schedule(0)).resize(32)
    val t2 = (Sha256.bigSigma0(a) + Sha256.maj(a, b, c)).resize(32)
    val nextState = Seq(
      (t1 + t2).resize(32),
      a,
      b,
      c,
      (d + t1).resize(32),
      e,
      f,
      g
    )
    val generatedWord = if (scheduleRequired) {
      (Sha256.smallSigma1(input.schedule(14)) + input.schedule(9) +
        Sha256.smallSigma0(input.schedule(1)) + input.schedule(0)).resize(32)
    } else {
      U(0, 32 bits)
    }
    val nextSchedule = input.schedule.drop(1) :+ generatedWord
    val stateRegs = nextState.map(RegNext(_))
    val scheduleRegs = nextSchedule.map(RegNext(_))
    val validReg = Reg(Bool()) init False
    val nonceReg = RegNext(input.nonce)

    validReg := input.valid
    when(clearPipeline) {
      validReg := False
    }

    UnrolledPipelineStage(stateRegs, scheduleRegs, validReg, nonceReg)
  }
}

class BitcoinHashUnrolledFirstPass extends Component {
  addAttribute("syn_srlstyle", "distributed_ram")
  val io = new Bundle {
    val clear = in Bool()
    val validIn = in Bool()
    val midstate = in Bits(256 bits)
    val roundSkipPrefixState = in Bits(256 bits)
    val roundSkipTail2 = in UInt(32 bits)
    val roundSkipW16 = in UInt(32 bits)
    val roundSkipW17 = in UInt(32 bits)
    val nonceIn = in UInt(32 bits)
    val validOut = out Bool()
    val digestOut = out Bits(256 bits)
    val nonceOut = out UInt(32 bits)
  }

  val firstPadStart = U(BigInt("80000000", 16), 32 bits)
  val firstLength = U(BigInt("00000280", 16), 32 bits)
  val zero = U(0, 32 bits)
  val w18 = (Sha256.smallSigma1(io.roundSkipW16) +
    Sha256.smallSigma0(io.nonceIn) + io.roundSkipTail2).resize(32)
  val firstInput = UnrolledPipelineStage(
    (0 until 8).map(i => Sha256.wordFromDigest(io.roundSkipPrefixState, i)),
    Seq(io.nonceIn, firstPadStart) ++ Seq.fill(10)(zero) ++
      Seq(firstLength, io.roundSkipW16, io.roundSkipW17, w18),
    io.validIn,
    io.nonceIn
  )
  val firstRounds = (3 to 63).foldLeft(firstInput) { (stage, roundIndex) =>
    UnrolledPipelineRound.register(
      stage,
      roundIndex,
      scheduleRequired = roundIndex <= 47,
      io.clear
    )
  }

  val digestNext = Sha256Pass.addFeedForward(
    io.midstate,
    Sha256.concatWords(firstRounds.state)
  )
  val digestReg = RegNext(digestNext)
  val digestValidReg = Reg(Bool()) init False
  val digestNonceReg = RegNext(firstRounds.nonce)
  digestValidReg := firstRounds.valid
  when(io.clear) {
    digestValidReg := False
  }

  io.validOut := digestValidReg
  io.digestOut := digestReg
  io.nonceOut := digestNonceReg
}

class BitcoinHashUnrolledSecondPass extends Component {
  addAttribute("syn_srlstyle", "distributed_ram")
  val io = new Bundle {
    val clear = in Bool()
    val validIn = in Bool()
    val digestIn = in Bits(256 bits)
    val nonceIn = in UInt(32 bits)
    val validOut = out Bool()
    val nonceOut = out UInt(32 bits)
    val candidateLow32 = out UInt(32 bits)
  }

  val zero = U(0, 32 bits)
  val secondInput = UnrolledPipelineStage(
    Sha256.Iv.map(Sha256.word),
    (0 until 8).map(i => Sha256.wordFromDigest(io.digestIn, i)) ++
      Seq(U(BigInt("80000000", 16), 32 bits)) ++ Seq.fill(6)(zero) ++
      Seq(U(BigInt("00000100", 16), 32 bits)),
    io.validIn,
    io.nonceIn
  )
  val secondRounds = (0 to 60).foldLeft(secondInput) { (stage, roundIndex) =>
    UnrolledPipelineRound.register(
      stage,
      roundIndex,
      scheduleRequired = roundIndex <= 44,
      io.clear
    )
  }

  val candidateValidReg = Reg(Bool()) init False
  val candidateNonceReg = RegNext(secondRounds.nonce)
  val candidateLow32Reg = RegNext(secondRounds.state(4))
  candidateValidReg := secondRounds.valid
  when(io.clear) {
    candidateValidReg := False
  }

  io.validOut := candidateValidReg
  io.nonceOut := candidateNonceReg
  io.candidateLow32 := candidateLow32Reg
}

class BitcoinHashUnrolledPipeline(options: TangMinerHardwareOptions) extends Component {
  require(options.fullyUnrolled, "BitcoinHashUnrolledPipeline requires fullyUnrolled")
  require(options.hostRoundSkip, "BitcoinHashUnrolledPipeline requires hostRoundSkip")
  require(options.roundSkip, "BitcoinHashUnrolledPipeline requires roundSkip")

  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stop = in Bool()
    val midstate = in Bits(256 bits)
    val candidateMode = in UInt(3 bits)
    val roundSkipPrefixState = in Bits(256 bits)
    val roundSkipTail2 = in UInt(32 bits)
    val roundSkipW16 = in UInt(32 bits)
    val roundSkipW17 = in UInt(32 bits)
    val startNonce = in UInt(32 bits)
    val nonceStride = in UInt(32 bits)
    val running = out Bool()
    val found = out Bool()
    val foundNonce = out UInt(32 bits)
    val currentNonce = out UInt(32 bits)
    val nonceAttempt = out Bool()
    val candidateValid = out Bool()
    val candidateNonce = out UInt(32 bits)
    val candidateLow32 = out UInt(32 bits)
  }

  val runningReg = Reg(Bool()) init False
  val foundReg = Reg(Bool()) init False
  val foundNonceReg = Reg(UInt(32 bits)) init 0
  val currentNonceReg = Reg(UInt(32 bits)) init 0
  val candidateMeetsTarget = Bool()
  val clearPipeline = io.reset || io.start || io.stop
  val injectValid = runningReg && !candidateMeetsTarget && !clearPipeline

  val firstPass = new BitcoinHashUnrolledFirstPass
  firstPass.io.clear := clearPipeline
  firstPass.io.validIn := injectValid
  firstPass.io.midstate := io.midstate
  firstPass.io.roundSkipPrefixState := io.roundSkipPrefixState
  firstPass.io.roundSkipTail2 := io.roundSkipTail2
  firstPass.io.roundSkipW16 := io.roundSkipW16
  firstPass.io.roundSkipW17 := io.roundSkipW17
  firstPass.io.nonceIn := currentNonceReg

  val secondPass = new BitcoinHashUnrolledSecondPass
  secondPass.io.clear := clearPipeline
  secondPass.io.validIn := firstPass.io.validOut
  secondPass.io.digestIn := firstPass.io.digestOut
  secondPass.io.nonceIn := firstPass.io.nonceOut

  candidateMeetsTarget := secondPass.io.validOut && runningReg &&
    BitcoinCandidateFilter.meets(
      options,
      io.candidateMode,
      secondPass.io.candidateLow32
    )

  when(io.reset) {
    runningReg := False
    foundReg := False
    foundNonceReg := 0
    currentNonceReg := 0
  } otherwise {
    when(io.stop) {
      runningReg := False
      foundReg := False
    } elsewhen(io.start) {
      runningReg := True
      foundReg := False
      foundNonceReg := 0
      currentNonceReg := io.startNonce
    } elsewhen(candidateMeetsTarget) {
      runningReg := False
      foundReg := True
      foundNonceReg := secondPass.io.nonceOut
    } elsewhen(injectValid) {
      currentNonceReg := currentNonceReg + io.nonceStride
    }
  }

  io.running := runningReg
  io.found := foundReg
  io.foundNonce := foundNonceReg
  io.currentNonce := currentNonceReg
  io.nonceAttempt := injectValid
  io.candidateValid := secondPass.io.validOut
  io.candidateNonce := secondPass.io.nonceOut
  io.candidateLow32 := secondPass.io.candidateLow32
}

class BitcoinHashCore(options: TangMinerHardwareOptions = TangMinerHardwareOptions()) extends Component {
  val io = new Bundle {
    val reset = in Bool()
    val start = in Bool()
    val stop = in Bool()
    val midstate = in Bits(256 bits)
    val tail = in Bits(96 bits)
    val candidateMode = in UInt(3 bits)
    val roundSkipPrefixState = in Bits(256 bits)
    val roundSkipTail2 = in UInt(32 bits)
    val roundSkipW16 = in UInt(32 bits)
    val roundSkipW17 = in UInt(32 bits)
    val externalFirstKWord = in UInt(32 bits)
    val externalSecondKWord = in UInt(32 bits)
    val startNonce = in UInt(32 bits)
    val nonceStride = in UInt(32 bits)
    val running = out Bool()
    val found = out Bool()
    val foundNonce = out UInt(32 bits)
    val currentNonce = out UInt(32 bits)
    val nonceAttempt = out Bool()
    val firstRound = out UInt(6 bits)
    val secondRound = out UInt(6 bits)
  }

  object State extends SpinalEnum {
    val idle, firstStart, run, report = newElement()
  }

  val state = Reg(State()) init State.idle
  val shaFirstPrepare = Bool()
  val shaFirstStart = Bool()
  val shaSecondStart = Bool()
  val jobMidstate = Bits(256 bits)
  val jobTail = Bits(96 bits)
  val jobCandidateMode = UInt(3 bits)
  val jobMidstateReg = if (options.shareJobState) null else Reg(Bits(256 bits)) init 0
  val jobTailReg = if (options.shareJobState) null else Reg(Bits(96 bits)) init 0
  val jobCandidateModeReg = if (options.shareJobState) null else Reg(UInt(3 bits)) init 3
  val firstNonceReg = Reg(UInt(32 bits)) init 0
  val secondNonceReg = Reg(UInt(32 bits)) init 0
  val checkValidReg = Reg(Bool()) init False
  val checkWorkLow32Reg = Reg(UInt(32 bits)) init 0
  val checkNonceReg = Reg(UInt(32 bits)) init 0
  val checkCandidateModeReg = Reg(UInt(3 bits)) init 3
  val foundNonceReg = Reg(UInt(32 bits)) init 0
  val currentNonceReg = Reg(UInt(32 bits)) init 0

  shaFirstPrepare := False
  shaFirstStart := False
  shaSecondStart := False
  io.nonceAttempt := False

  if (options.shareJobState) {
    jobMidstate := io.midstate
    jobTail := io.tail
    jobCandidateMode := io.candidateMode
  } else {
    jobMidstate := jobMidstateReg
    jobTail := jobTailReg
    jobCandidateMode := jobCandidateModeReg
  }

  val flushPipeline = io.stop || (io.start && state =/= State.idle)

  val shaFirstKWord = UInt(32 bits)
  val shaFirstReady = Bool()
  val shaFirstDone = Bool()
  val shaFirstRound = UInt(6 bits)
  val shaFirstDigest = Bits(256 bits)

  if (options.roundSkip) {
    val shaFirst = new Sha256BitcoinFirstPassPrepared(
      registerOutputs = options.registerPassOutputs,
      registerCompressorOutputs = options.registerCompressorOutputs,
      registerFeedForward = options.registerFirstPassFeedForward,
      twoCycleRound = options.twoCycleRound,
      threeCycleRound = options.threeCycleRound,
      twoRoundsPerCycle = options.twoRoundsPerCycle,
      twoRoundPipeline = options.twoRoundPipeline,
      twoPhaseRoundPipeline = options.twoPhaseRoundPipeline,
      registerRoundConstant = options.registerRoundConstant,
      minimizeShaReset = options.minimizeShaReset,
      csaRound = options.csaRound,
      csaSchedule = options.csaSchedule,
      balancedRoundAdder = options.balancedRoundAdder
    )
    shaFirst.io.reset := io.reset || flushPipeline
    shaFirst.io.start := shaFirstStart
    shaFirst.io.kWord := shaFirstKWord
    shaFirst.io.midstate := jobMidstate
    shaFirst.io.prefixState := io.roundSkipPrefixState
    shaFirst.io.tail2 := io.roundSkipTail2
    shaFirst.io.w16 := io.roundSkipW16
    shaFirst.io.w17 := io.roundSkipW17
    shaFirst.io.nonce := currentNonceReg

    shaFirstReady := shaFirst.io.ready
    shaFirstDone := shaFirst.io.done
    shaFirstRound := shaFirst.io.round
    shaFirstDigest := shaFirst.io.digest
  } else {
    val shaFirst = new Sha256BitcoinFirstPass(
      registerOutputs = options.registerPassOutputs,
      registerCompressorOutputs = options.registerCompressorOutputs,
      registerFeedForward = options.registerFirstPassFeedForward,
      twoCycleRound = options.twoCycleRound,
      threeCycleRound = options.threeCycleRound,
      twoRoundsPerCycle = options.twoRoundsPerCycle,
      twoRoundPipeline = options.twoRoundPipeline,
      twoPhaseRoundPipeline = options.twoPhaseRoundPipeline,
      registerRoundConstant = options.registerRoundConstant,
      minimizeShaReset = options.minimizeShaReset,
      roundSkip = false,
      csaRound = options.csaRound,
      csaSchedule = options.csaSchedule,
      balancedRoundAdder = options.balancedRoundAdder
    )
    shaFirst.io.reset := io.reset || flushPipeline
    shaFirst.io.prepare := shaFirstPrepare
    shaFirst.io.start := shaFirstStart
    shaFirst.io.kWord := shaFirstKWord
    shaFirst.io.midstate := jobMidstate
    shaFirst.io.tail := jobTail
    shaFirst.io.nonce := currentNonceReg

    shaFirstReady := shaFirst.io.ready
    shaFirstDone := shaFirst.io.done
    shaFirstRound := shaFirst.io.round
    shaFirstDigest := shaFirst.io.digest
  }

  val shaSecond = new Sha256BitcoinSecondPass(
    registerOutputs = options.registerPassOutputs,
    registerCompressorOutputs = options.registerCompressorOutputs,
    twoCycleRound = options.twoCycleRound,
    threeCycleRound = options.threeCycleRound,
    twoRoundsPerCycle = options.twoRoundsPerCycle,
    twoRoundPipeline = options.twoRoundPipeline,
    twoPhaseRoundPipeline = options.twoPhaseRoundPipeline,
    registerRoundConstant = options.registerRoundConstant,
    minimizeShaReset = options.minimizeShaReset,
    roundSkip = options.roundSkip,
    csaRound = options.csaRound,
    csaSchedule = options.csaSchedule,
    balancedRoundAdder = options.balancedRoundAdder
  )
  shaSecond.io.reset := io.reset || flushPipeline
  shaSecond.io.start := shaSecondStart
  val shaSecondInputDigest = Bits(256 bits)
  shaSecond.io.firstDigest := shaSecondInputDigest
  io.firstRound := shaFirstRound
  io.secondRound := shaSecond.io.round
  val shaSecondReady = shaSecond.io.ready

  if (options.registerRoundConstant || options.twoRoundsPerCycle || options.twoRoundPipeline || options.twoPhaseRoundPipeline) {
    shaFirstKWord := 0
    shaSecond.io.kWord := 0
  } else if (options.externalRoundConstants) {
    shaFirstKWord := io.externalFirstKWord
    shaSecond.io.kWord := io.externalSecondKWord
  } else {
    val kVec = Vec(Sha256.K.map(Sha256.word))
    val firstKWord = kVec(shaFirstRound)
    val secondKWord = if (options.sharedRoundConstant && !options.roundSkip) firstKWord else kVec(shaSecond.io.round)
    shaFirstKWord := firstKWord
    shaSecond.io.kWord := secondKWord
  }

  val checkDigestMeetsTarget = BitcoinCandidateFilter.meets(options, checkCandidateModeReg, checkWorkLow32Reg)

  io.running := state =/= State.idle && state =/= State.report
  io.found := state === State.report
  io.foundNonce := foundNonceReg
  io.currentNonce := currentNonceReg

  val pipelineFirstStart = Bool()
  val pipelineFirstCanStart = Bool()
  val pipelineSecondStart = Bool()
  val pipelineSecondDone = Bool()
  val pipelineSecondDoneNonce = UInt(32 bits)
  pipelineFirstStart := False

  val usesStartPipeline = options.twoRoundPipeline || options.twoPhaseRoundPipeline

  if (usesStartPipeline) {
    val pipelineFifoDepth = if (options.twoPhaseRoundPipeline) 4 else 8
    val firstNonceFifo = StreamFifo(UInt(32 bits), pipelineFifoDepth)
    val firstResultFifo = StreamFifo(Bits(288 bits), pipelineFifoDepth)
    val secondNonceFifo = StreamFifo(UInt(32 bits), pipelineFifoDepth)
    val firstResultNonce = firstResultFifo.io.pop.payload(31 downto 0).asUInt
    val firstResultDigest = firstResultFifo.io.pop.payload(287 downto 32)
    val pipelineActive = state === State.run
    val fifoSecondStart = pipelineActive && firstResultFifo.io.pop.valid && shaSecondReady && secondNonceFifo.io.push.ready
    val fifoSecondDone = pipelineActive && shaSecond.io.done && secondNonceFifo.io.pop.valid

    firstNonceFifo.io.flush := io.reset || io.start || io.stop
    firstNonceFifo.io.push.valid := pipelineFirstStart
    firstNonceFifo.io.push.payload := currentNonceReg
    firstNonceFifo.io.pop.ready := pipelineActive && shaFirstDone && firstResultFifo.io.push.ready
    pipelineFirstCanStart := shaFirstReady && firstNonceFifo.io.push.ready

    firstResultFifo.io.flush := io.reset || io.start || io.stop
    firstResultFifo.io.push.valid := pipelineActive && shaFirstDone && firstNonceFifo.io.pop.valid
    firstResultFifo.io.push.payload := shaFirstDigest ## firstNonceFifo.io.pop.payload.asBits
    firstResultFifo.io.pop.ready := fifoSecondStart

    secondNonceFifo.io.flush := io.reset || io.start || io.stop
    secondNonceFifo.io.push.valid := fifoSecondStart
    secondNonceFifo.io.push.payload := firstResultNonce
    secondNonceFifo.io.pop.ready := fifoSecondDone

    shaSecondInputDigest := firstResultDigest
    pipelineSecondStart := fifoSecondStart
    pipelineSecondDone := fifoSecondDone
    pipelineSecondDoneNonce := secondNonceFifo.io.pop.payload
  } else {
    shaSecondInputDigest := shaFirstDigest
    pipelineFirstCanStart := shaFirstReady
    pipelineSecondStart := False
    pipelineSecondDone := False
    pipelineSecondDoneNonce := 0
  }

  when(io.reset) {
    state := State.idle
    if (!options.shareJobState) {
      jobMidstateReg := 0
      jobTailReg := 0
      jobCandidateModeReg := 3
    }
    firstNonceReg := 0
    secondNonceReg := 0
    checkValidReg := False
    checkWorkLow32Reg := 0
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
      if (!options.shareJobState) {
        jobMidstateReg := io.midstate
        jobTailReg := io.tail
        jobCandidateModeReg := io.candidateMode
      }
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
          when(if (usesStartPipeline) pipelineFirstCanStart else shaFirstReady) {
            shaFirstStart := True
            if (usesStartPipeline) {
              pipelineFirstStart := True
            }
            io.nonceAttempt := True
            firstNonceReg := currentNonceReg
            currentNonceReg := currentNonceReg + io.nonceStride
            state := State.run
          } otherwise {
            shaFirstPrepare := True
          }
        }
        is(State.run) {
          when(checkValidReg && checkDigestMeetsTarget) {
            foundNonceReg := checkNonceReg
            state := State.report
          } otherwise {
            if (usesStartPipeline) {
              when(pipelineFirstCanStart) {
                shaFirstStart := True
                pipelineFirstStart := True
                io.nonceAttempt := True
                firstNonceReg := currentNonceReg
                currentNonceReg := currentNonceReg + io.nonceStride
              }

              when(pipelineSecondStart) {
                shaSecondStart := True
              }

              when(pipelineSecondDone) {
                checkValidReg := True
                checkWorkLow32Reg := shaSecond.io.workLow32
                checkNonceReg := pipelineSecondDoneNonce
                checkCandidateModeReg := jobCandidateMode
              }
            } else {
              when(shaFirstDone) {
                shaSecondStart := True
                secondNonceReg := firstNonceReg

                shaFirstStart := True
                io.nonceAttempt := True
                firstNonceReg := currentNonceReg
                currentNonceReg := currentNonceReg + io.nonceStride
              }

              when(shaSecond.io.done) {
                checkValidReg := True
                checkWorkLow32Reg := shaSecond.io.workLow32
                checkNonceReg := secondNonceReg
                checkCandidateModeReg := jobCandidateMode
              }
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
    val hostRoundSkipPrefixState = in Bits(256 bits)
    val hostRoundSkipTail2 = in UInt(32 bits)
    val hostRoundSkipW16 = in UInt(32 bits)
    val hostRoundSkipW17 = in UInt(32 bits)
    val runningAny = out Bool()
    val foundAny = out Bool()
    val foundNonce = out UInt(32 bits)
    val currentNonce = out UInt(32 bits)
    val nonceAttempts = out UInt(64 bits)
  }

  val jobMidstateReg = if (options.hostRoundSkip) null else Reg(Bits(256 bits)) init 0
  val jobTailReg = if (options.hostRoundSkip) null else Reg(Bits(96 bits)) init 0
  val jobCandidateModeReg = if (options.hostRoundSkip) null else Reg(UInt(3 bits)) init 3
  val startCoresReg = Reg(Bool()) init False

  val coreOptions = options.copy(
    wideLaneBlock = false,
    externalRoundConstants = options.sharedRoundConstant && options.roundSkip && !options.registerRoundConstant && !options.twoRoundsPerCycle && !options.twoRoundPipeline && !options.twoPhaseRoundPipeline
  )
  val cores = (0 until laneCount).map(_ => new BitcoinHashCore(coreOptions))
  val startCores = Bool()
  val stopCores = Bool()
  val roundSkipPrefixState = Bits(256 bits)
  val roundSkipTail2 = UInt(32 bits)
  val roundSkipW16 = UInt(32 bits)
  val roundSkipW17 = UInt(32 bits)

  if (options.roundSkip && options.hostRoundSkip) {
    startCores := startCoresReg
    stopCores := io.stop
    roundSkipPrefixState := io.hostRoundSkipPrefixState
    roundSkipTail2 := io.hostRoundSkipTail2
    roundSkipW16 := io.hostRoundSkipW16
    roundSkipW17 := io.hostRoundSkipW17
  } else if (options.roundSkip) {
    val prefix = new Sha256BitcoinFirstPassPrefix(
      registerOutputs = options.registerPassOutputs,
      twoCycleRound = options.twoCycleRound,
      threeCycleRound = options.threeCycleRound,
      twoRoundsPerCycle = options.twoRoundsPerCycle,
      twoRoundPipeline = options.twoRoundPipeline,
      twoPhaseRoundPipeline = options.twoPhaseRoundPipeline,
      registerRoundConstant = options.registerRoundConstant,
      minimizeShaReset = options.minimizeShaReset,
      csaRound = options.csaRound,
      balancedRoundAdder = options.balancedRoundAdder
    )
    prefix.io.reset := io.reset || io.stop
    prefix.io.start := io.start
    prefix.io.midstate := io.midstate
    prefix.io.tail := io.tail

    val prefixReadyLast = RegNext(prefix.io.ready) init False
    startCores := prefix.io.ready && !prefixReadyLast
    stopCores := io.stop || io.start
    roundSkipPrefixState := prefix.io.prefixState
    roundSkipTail2 := prefix.io.tail2
    roundSkipW16 := prefix.io.w16
    roundSkipW17 := prefix.io.w17
  } else {
    startCores := startCoresReg
    stopCores := io.stop
    roundSkipPrefixState := 0
    roundSkipTail2 := 0
    roundSkipW16 := 0
    roundSkipW17 := 0
  }

  when(io.reset) {
    if (!options.hostRoundSkip) {
      jobMidstateReg := 0
      jobTailReg := 0
      jobCandidateModeReg := 3
    }
    startCoresReg := False
  } otherwise {
    startCoresReg := False
    when(io.start) {
      if (!options.hostRoundSkip) {
        jobMidstateReg := io.midstate
        jobTailReg := io.tail
        jobCandidateModeReg := io.candidateMode
      }
      startCoresReg := True
    }
  }

  for ((core, lane) <- cores.zipWithIndex) {
    core.io.reset := io.reset
    core.io.start := startCores
    core.io.stop := stopCores
    core.io.midstate := (if (options.hostRoundSkip) io.midstate else jobMidstateReg)
    core.io.tail := (if (options.hostRoundSkip) io.tail else jobTailReg)
    core.io.candidateMode := (if (options.hostRoundSkip) io.candidateMode else jobCandidateModeReg)
    core.io.roundSkipPrefixState := roundSkipPrefixState
    core.io.roundSkipTail2 := roundSkipTail2
    core.io.roundSkipW16 := roundSkipW16
    core.io.roundSkipW17 := roundSkipW17
    core.io.startNonce := U(lane, 32 bits)
    core.io.nonceStride := U(laneCount, 32 bits)
  }

  if (coreOptions.externalRoundConstants) {
    val kVec = Vec(Sha256.K.map(Sha256.word))
    val firstKWord = kVec(cores(0).io.firstRound)
    val secondKWord = kVec(cores(0).io.secondRound)
    cores.foreach { core =>
      core.io.externalFirstKWord := firstKWord
      core.io.externalSecondKWord := secondKWord
    }
  } else {
    cores.foreach { core =>
      core.io.externalFirstKWord := 0
      core.io.externalSecondKWord := 0
    }
  }

  io.runningAny := cores.map(_.io.running).reduce(_ || _)
  io.foundAny := cores.map(_.io.found).reduce(_ || _)
  io.currentNonce := cores(0).io.currentNonce
  io.nonceAttempts := NonceAttemptCounter(io.reset, io.start, cores.map(_.io.nonceAttempt))

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
    val hostRoundSkipPrefixState = in Bits(256 bits)
    val hostRoundSkipTail2 = in UInt(32 bits)
    val hostRoundSkipW16 = in UInt(32 bits)
    val hostRoundSkipW17 = in UInt(32 bits)
    val runningAny = out Bool()
    val foundAny = out Bool()
    val currentNonce = out UInt(32 bits)
    val foundNonce = out UInt(32 bits)
    val nonceAttempts = out UInt(64 bits)
  }

  if (options.fullyUnrolled) {
    require(laneStartStagger == 0, "fullyUnrolled pipelines start together and do not support laneStartStagger")
    val pipelines = (0 until laneCount).map(_ => new BitcoinHashUnrolledPipeline(options))

    for ((pipeline, lane) <- pipelines.zipWithIndex) {
      pipeline.io.reset := io.reset
      pipeline.io.start := io.start
      pipeline.io.stop := io.stop
      pipeline.io.midstate := io.midstate
      pipeline.io.candidateMode := io.candidateMode
      pipeline.io.roundSkipPrefixState := io.hostRoundSkipPrefixState
      pipeline.io.roundSkipTail2 := io.hostRoundSkipTail2
      pipeline.io.roundSkipW16 := io.hostRoundSkipW16
      pipeline.io.roundSkipW17 := io.hostRoundSkipW17
      pipeline.io.startNonce := U(lane, 32 bits)
      pipeline.io.nonceStride := U(laneCount, 32 bits)
    }

    io.runningAny := pipelines.map(_.io.running).reduce(_ || _)
    io.foundAny := pipelines.map(_.io.found).reduce(_ || _)
    io.currentNonce := pipelines(0).io.currentNonce
    io.nonceAttempts := NonceAttemptCounter(io.reset, io.start, pipelines.map(_.io.nonceAttempt))

    io.foundNonce := pipelines(laneCount - 1).io.foundNonce
    for (lane <- (0 until laneCount - 1).reverse) {
      when(pipelines(lane).io.found) {
        io.foundNonce := pipelines(lane).io.foundNonce
      }
    }
  } else if (options.wideLaneBlock) {
    val lanes = new BitcoinHashWideLaneBlock(laneCount, options)
    lanes.io.reset := io.reset
    lanes.io.start := io.start
    lanes.io.stop := io.stop
    lanes.io.midstate := io.midstate
    lanes.io.tail := io.tail
    lanes.io.candidateMode := io.candidateMode
    lanes.io.hostRoundSkipPrefixState := io.hostRoundSkipPrefixState
    lanes.io.hostRoundSkipTail2 := io.hostRoundSkipTail2
    lanes.io.hostRoundSkipW16 := io.hostRoundSkipW16
    lanes.io.hostRoundSkipW17 := io.hostRoundSkipW17
    io.runningAny := lanes.io.runningAny
    io.foundAny := lanes.io.foundAny
    io.currentNonce := lanes.io.currentNonce
    io.foundNonce := lanes.io.foundNonce
    io.nonceAttempts := lanes.io.nonceAttempts
  } else {
    val coreOptions = options.copy(
      externalRoundConstants = options.sharedRoundConstant && options.roundSkip && laneStartStagger == 0 && !options.registerRoundConstant && !options.twoRoundsPerCycle && !options.twoRoundPipeline && !options.twoPhaseRoundPipeline
    )
    val cores = (0 until laneCount).map(_ => new BitcoinHashCore(coreOptions))
    cores.foreach(_.io.reset := io.reset)
    val coreStartByLane = Vec(Bool(), laneCount)
    val miningStart = Bool()
    val coreStop = Bool()
    val coreMidstate = Bits(256 bits)
    val coreTail = Bits(96 bits)
    val coreCandidateMode = UInt(3 bits)
    val roundSkipPrefixState = Bits(256 bits)
    val roundSkipTail2 = UInt(32 bits)
    val roundSkipW16 = UInt(32 bits)
    val roundSkipW17 = UInt(32 bits)

    if (options.roundSkip) {
      val jobMidstateReg = if (options.hostRoundSkip) null else Reg(Bits(256 bits)) init 0
      val jobTailReg = if (options.hostRoundSkip) null else Reg(Bits(96 bits)) init 0
      val jobCandidateModeReg = if (options.hostRoundSkip) null else Reg(UInt(3 bits)) init 3

      coreMidstate := (if (options.hostRoundSkip) io.midstate else jobMidstateReg)
      coreTail := (if (options.hostRoundSkip) io.tail else jobTailReg)
      coreCandidateMode := (if (options.hostRoundSkip) io.candidateMode else jobCandidateModeReg)

      if (options.hostRoundSkip) {
        miningStart := io.start
        coreStop := io.stop
        roundSkipPrefixState := io.hostRoundSkipPrefixState
        roundSkipTail2 := io.hostRoundSkipTail2
        roundSkipW16 := io.hostRoundSkipW16
        roundSkipW17 := io.hostRoundSkipW17
      } else {
        val prefix = new Sha256BitcoinFirstPassPrefix(
          registerOutputs = options.registerPassOutputs,
          twoCycleRound = options.twoCycleRound,
          threeCycleRound = options.threeCycleRound,
          twoRoundsPerCycle = options.twoRoundsPerCycle,
          twoRoundPipeline = options.twoRoundPipeline,
          twoPhaseRoundPipeline = options.twoPhaseRoundPipeline,
          registerRoundConstant = options.registerRoundConstant,
          minimizeShaReset = options.minimizeShaReset,
          csaRound = options.csaRound,
          balancedRoundAdder = options.balancedRoundAdder
        )

        prefix.io.reset := io.reset || io.stop
        prefix.io.start := io.start
        prefix.io.midstate := io.midstate
        prefix.io.tail := io.tail

        val prefixReadyLast = RegNext(prefix.io.ready) init False
        miningStart := prefix.io.ready && !prefixReadyLast
        coreStop := io.stop || io.start
        roundSkipPrefixState := prefix.io.prefixState
        roundSkipTail2 := prefix.io.tail2
        roundSkipW16 := prefix.io.w16
        roundSkipW17 := prefix.io.w17
      }

      when(io.reset) {
        if (!options.hostRoundSkip) {
          jobMidstateReg := 0
          jobTailReg := 0
          jobCandidateModeReg := 3
        }
      } otherwise {
        when(io.start) {
          if (!options.hostRoundSkip) {
            jobMidstateReg := io.midstate
            jobTailReg := io.tail
            jobCandidateModeReg := io.candidateMode
          }
        }
      }
    } else {
      miningStart := io.start
      coreStop := io.stop
      if (options.shareJobState) {
        val jobMidstateReg = Reg(Bits(256 bits)) init 0
        val jobTailReg = Reg(Bits(96 bits)) init 0
        val jobCandidateModeReg = Reg(UInt(3 bits)) init 3

        coreMidstate := jobMidstateReg
        coreTail := jobTailReg
        coreCandidateMode := jobCandidateModeReg

        when(io.reset) {
          jobMidstateReg := 0
          jobTailReg := 0
          jobCandidateModeReg := 3
        } otherwise {
          when(io.start) {
            jobMidstateReg := io.midstate
            jobTailReg := io.tail
            jobCandidateModeReg := io.candidateMode
          }
        }
      } else {
        coreMidstate := io.midstate
        coreTail := io.tail
        coreCandidateMode := io.candidateMode
      }
      roundSkipPrefixState := 0
      roundSkipTail2 := 0
      roundSkipW16 := 0
      roundSkipW17 := 0
    }

    if (laneStartStagger == 0) {
      for (lane <- 0 until laneCount) {
        coreStartByLane(lane) := miningStart
      }
    } else {
      val maxStartDelay = (laneCount - 1) * laneStartStagger
      val delayBits = log2Up(scala.math.max(2, maxStartDelay + 1))
      val laneStartPending = Reg(Bits(laneCount bits)) init 0
      val laneStartDelay = Vec(Reg(UInt(delayBits bits)) init 0, laneCount)

      for (lane <- 0 until laneCount) {
        coreStartByLane(lane) := False
      }

      when(io.reset || coreStop) {
        laneStartPending := 0
        for (lane <- 0 until laneCount) {
          laneStartDelay(lane) := 0
        }
      } otherwise {
        when(miningStart) {
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
      core.io.stop := coreStop
      core.io.midstate := coreMidstate
      core.io.tail := coreTail
      core.io.candidateMode := coreCandidateMode
      core.io.roundSkipPrefixState := roundSkipPrefixState
      core.io.roundSkipTail2 := roundSkipTail2
      core.io.roundSkipW16 := roundSkipW16
      core.io.roundSkipW17 := roundSkipW17
      core.io.startNonce := U(lane, 32 bits)
      core.io.nonceStride := U(laneCount, 32 bits)
    }

    if (coreOptions.externalRoundConstants) {
      val kVec = Vec(Sha256.K.map(Sha256.word))
      val firstKWord = kVec(cores(0).io.firstRound)
      val secondKWord = kVec(cores(0).io.secondRound)
      cores.foreach { core =>
        core.io.externalFirstKWord := firstKWord
        core.io.externalSecondKWord := secondKWord
      }
    } else {
      cores.foreach { core =>
        core.io.externalFirstKWord := 0
        core.io.externalSecondKWord := 0
      }
    }

    io.runningAny := cores.map(_.io.running).reduce(_ || _)
    io.foundAny := cores.map(_.io.found).reduce(_ || _)
    io.currentNonce := cores(0).io.currentNonce
    io.nonceAttempts := NonceAttemptCounter(io.reset, io.start, cores.map(_.io.nonceAttempt))

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
  pllKind: String = "rpll",
  inputClockMhz: Double = 27.0,
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
  val normalizedPllKind = pllKind.trim.toLowerCase
  require(clksPerBit > 1, "clksPerBit must leave room for UART start-bit centering")
  require(inputClockMhz > 0.0, "inputClockMhz must be positive")
  require(resetCounterBits > 0, "resetCounterBits must be positive")
  require(laneCount > 0, "laneCount must be positive")
  require(laneStartStagger >= 0, "laneStartStagger must be non-negative")
  require(!usePll || clockProfile.usePll, s"clock profile '${clockProfile.name}' does not define PLL settings")
  require(Set("rpll", "gw5").contains(normalizedPllKind), s"unsupported pllKind '$pllKind'; use rpll or gw5")
  require(!usePll || normalizedPllKind != "rpll" || clockProfile.rpllSupported,
    s"clock profile '${clockProfile.name}' does not define rPLL settings")
  require(!usePll || normalizedPllKind != "gw5" || clockProfile.supportsGw5Pll,
    s"clock profile '${clockProfile.name}' does not define GW5 PLL settings")
  require(!usePll || normalizedPllKind != "gw5" || scala.math.abs(inputClockMhz - 50.0) < 0.001,
    "GW5 PLL profiles require a 50 MHz input clock")
  require(!splitShaClock || usePll, "splitShaClock requires a PLL-backed SHA clock")
  require(!hardwareOptions.hostRoundSkip || !splitShaClock, "hostRoundSkip does not support splitShaClock")

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

  if (usePll && normalizedPllKind == "gw5") {
    val gowin5PllFrom50Mhz = new Gowin5PllFrom50Mhz(clockProfile)
    gowin5PllFrom50Mhz.io.CLKIN := io.clk
    gowin5PllFrom50Mhz.io.CLKFB := False
    gowin5PllFrom50Mhz.io.RESET := False
    gowin5PllFrom50Mhz.io.PLLPWD := False
    gowin5PllFrom50Mhz.io.RESET_I := False
    gowin5PllFrom50Mhz.io.RESET_O := False
    gowin5PllFrom50Mhz.io.FBDSEL := 0
    gowin5PllFrom50Mhz.io.IDSEL := 0
    gowin5PllFrom50Mhz.io.MDSEL := 0
    gowin5PllFrom50Mhz.io.MDSEL_FRAC := 0
    gowin5PllFrom50Mhz.io.ODSEL0 := 0
    gowin5PllFrom50Mhz.io.ODSEL1 := 0
    gowin5PllFrom50Mhz.io.ODSEL2 := 0
    gowin5PllFrom50Mhz.io.ODSEL3 := 0
    gowin5PllFrom50Mhz.io.ODSEL4 := 0
    gowin5PllFrom50Mhz.io.ODSEL5 := 0
    gowin5PllFrom50Mhz.io.ODSEL6 := 0
    gowin5PllFrom50Mhz.io.ODSEL0_FRAC := 0
    gowin5PllFrom50Mhz.io.DT0 := 0
    gowin5PllFrom50Mhz.io.DT1 := 0
    gowin5PllFrom50Mhz.io.DT2 := 0
    gowin5PllFrom50Mhz.io.DT3 := 0
    gowin5PllFrom50Mhz.io.ICPSEL := 0
    gowin5PllFrom50Mhz.io.LPFRES := 0
    gowin5PllFrom50Mhz.io.LPFCAP := 0
    gowin5PllFrom50Mhz.io.PSSEL := 0
    gowin5PllFrom50Mhz.io.PSDIR := False
    gowin5PllFrom50Mhz.io.PSPULSE := False
    gowin5PllFrom50Mhz.io.ENCLK0 := True
    gowin5PllFrom50Mhz.io.ENCLK1 := True
    gowin5PllFrom50Mhz.io.ENCLK2 := True
    gowin5PllFrom50Mhz.io.ENCLK3 := True
    gowin5PllFrom50Mhz.io.ENCLK4 := True
    gowin5PllFrom50Mhz.io.ENCLK5 := True
    gowin5PllFrom50Mhz.io.ENCLK6 := True
    gowin5PllFrom50Mhz.io.SSCPOL := False
    gowin5PllFrom50Mhz.io.SSCON := False
    gowin5PllFrom50Mhz.io.SSCMDSEL := 0
    gowin5PllFrom50Mhz.io.SSCMDSEL_FRAC := 0
    systemClock := gowin5PllFrom50Mhz.io.CLKOUT0
    systemClockLocked := gowin5PllFrom50Mhz.io.LOCK
  } else if (usePll) {
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
  val inputClockClksPerBit = scala.math.round(inputClockMhz * 1000000.0 / 115200.0).toInt
  val controlClksPerBit = if (splitShaClock) inputClockClksPerBit else clksPerBit
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
      lanes.io.hostRoundSkipPrefixState := 0
      lanes.io.hostRoundSkipTail2 := 0
      lanes.io.hostRoundSkipW16 := 0
      lanes.io.hostRoundSkipW17 := 0

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
    val CounterRespBytes = 9
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
    val hostRoundSkipPrefixState = if (hardwareOptions.hostRoundSkip) Reg(Bits(256 bits)) init 0 else B(0, 256 bits)
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
    val counterPending = Reg(Bool()) init False
    val counterSnapshot = Reg(UInt(64 bits)) init 0
    val echoToggle = if (hardwareOptions.enableEcho) Reg(Bool()) init False else False

    val runningAny = Bool()
    val foundAny = Bool()
    val currentNonce = UInt(32 bits)
    val selectedFoundNonce = UInt(32 bits)
    val nonceAttempts = UInt(64 bits)

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
      nonceAttempts := U(0, 64 bits)
    } else {
      val lanes = new MiningLanes(LaneCount, LaneStartStagger, hardwareOptions)
      lanes.io.reset := reset
      lanes.io.start := coreStart
      lanes.io.stop := coreStop
      lanes.io.midstate := midstate
      lanes.io.tail := tail
      lanes.io.candidateMode := candidateMode
      lanes.io.hostRoundSkipPrefixState := hostRoundSkipPrefixState
      lanes.io.hostRoundSkipTail2 := tail(95 downto 64).asUInt
      lanes.io.hostRoundSkipW16 := tail(63 downto 32).asUInt
      lanes.io.hostRoundSkipW17 := tail(31 downto 0).asUInt
      runningAny := lanes.io.runningAny
      foundAny := lanes.io.foundAny
      currentNonce := lanes.io.currentNonce
      selectedFoundNonce := lanes.io.foundNonce
      nonceAttempts := lanes.io.nonceAttempts
    }

    when(reset) {
      rxState := RxState.sync0
      payloadCount := 0
      command := 0
      coreStart := False
      coreStop := False
      coreStartPending := False
      counterPending := False
      counterSnapshot := 0
      if (splitShaClock) {
        splitStopPending := False
      }
      midstate := 0
      tail := 0
      if (hardwareOptions.hostRoundSkip) {
        hostRoundSkipPrefixState := 0
      }
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
            } elsewhen(rx.io.data === B"8'h43") {
              counterSnapshot := nonceAttempts
              counterPending := True
              rxState := RxState.sync0
            } elsewhen((if (hardwareOptions.enableHardcodedJob && !hardwareOptions.hostRoundSkip) rx.io.data === B"8'h48" else False)) {
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
            if (hardwareOptions.hostRoundSkip) {
              when(payloadCount < 32) {
                midstate := midstate(247 downto 0) ## rx.io.data
              } elsewhen(payloadCount < 44) {
                tail := tail(87 downto 0) ## rx.io.data
              } elsewhen(payloadCount < 76) {
                hostRoundSkipPrefixState := hostRoundSkipPrefixState(247 downto 0) ## rx.io.data
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
            } else if (useTargetAliases) {
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

    def counterResponseByte(index: UInt, count: UInt): Bits = {
      val countBytes = (0 until 8).map(i => count.asBits(63 - i * 8 downto 56 - i * 8))
      val bytes = Vec(Seq(B"8'h43") ++ countBytes)
      bytes(index.resized)
    }

    val txState = Reg(TxState()) init TxState.idle
    val txIndex = Reg(UInt(7 bits)) init 0
    val txStart = Reg(Bool()) init False
    val txData = Reg(Bits(8 bits)) init B"8'hff"
    val foundSeen = Reg(Bool()) init False
    val echoSeenToggle = if (hardwareOptions.enableEcho) Reg(Bool()) init False else False
    val txEcho = if (hardwareOptions.enableEcho) Reg(Bool()) init False else False
    val txCounter = Reg(Bool()) init False
    val txFoundNonce = Reg(UInt(32 bits)) init 0
    val txCounterValue = Reg(UInt(64 bits)) init 0

    tx.io.start := txStart
    tx.io.data := txData
    io.uart_tx_pin := tx.io.tx

    when(reset) {
      txState := TxState.idle
      txIndex := 0
      txStart := False
      txData := B"8'hff"
      foundSeen := False
      txCounter := False
      if (hardwareOptions.enableEcho) {
        echoSeenToggle := False
        txEcho := False
      }
      txFoundNonce := 0
      txCounterValue := 0
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
              txCounter := False
              txState := TxState.send
              echoSeenToggle := echoToggle
            } elsewhen(counterPending) {
              txIndex := 0
              txEcho := False
              txCounter := True
              txCounterValue := counterSnapshot
              counterPending := False
              txState := TxState.send
            } elsewhen(foundAny && !foundSeen) {
              txIndex := 0
              txEcho := False
              txCounter := False
              txFoundNonce := selectedFoundNonce
              txState := TxState.send
              foundSeen := True
              if (splitShaClock) {
                splitFoundFifo.io.pop.ready := True
              }
            }
          } else {
            when(counterPending) {
              txIndex := 0
              txCounter := True
              txCounterValue := counterSnapshot
              counterPending := False
              txState := TxState.send
            } elsewhen(foundAny && !foundSeen) {
              txIndex := 0
              txCounter := False
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
              val echoPayloadTail = if (hardwareOptions.hostRoundSkip) hostRoundSkipPrefixState else target
              val targetBytes = (0 until 32).map(i => echoPayloadTail(255 - i * 8 downto 248 - i * 8))
              val echoBytes = Vec(Seq(B"8'h45") ++ midstateBytes ++ tailBytes ++ targetBytes)
              txData := Mux(
                txCounter,
                counterResponseByte(txIndex, txCounterValue),
                Mux(txEcho, echoBytes(txIndex.resized), foundResponseByte(txIndex, txFoundNonce))
              )
            } else {
              txData := Mux(txCounter, counterResponseByte(txIndex, txCounterValue), foundResponseByte(txIndex, txFoundNonce))
            }
            txStart := True
            txState := TxState.waitBusy
          }
        }
        is(TxState.waitBusy) {
          when(tx.io.busy) {
            val responseDone = if (hardwareOptions.enableEcho) {
              (txCounter && txIndex === CounterRespBytes - 1) ||
                (!txCounter && ((!txEcho && txIndex === FoundRespBytes - 1) || (txEcho && txIndex === EchoRespBytes - 1)))
            } else {
              (txCounter && txIndex === CounterRespBytes - 1) ||
                (!txCounter && txIndex === FoundRespBytes - 1)
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

  def envDouble(names: Seq[String], default: Double): Double =
    names.collectFirst(Function.unlift(sys.env.get)).map(_.toDouble).getOrElse(default)

  def envOptionalInt(names: Seq[String], default: Option[Int] = None): Option[Int] =
    names.collectFirst(Function.unlift(sys.env.get)) match {
      case Some(value) if value.nonEmpty => Some(value.toInt)
      case Some(_) => None
      case None => default
    }

  val targetDirectory = sys.env.getOrElse("TANGMINER_VERILOG_DIR", "build/spinal")
  val usePll = envBoolean("TANGMINER_USE_PLL", default = true)
  val pllKind = envString(Seq("TANGMINER_PLL_KIND", "SPINAL_PLL_KIND"), "rpll")
  val inputClockMhz = envDouble(Seq("TANGMINER_INPUT_CLOCK_MHZ", "SPINAL_INPUT_CLOCK_MHZ"), 27.0)
  val clockProfile = GowinClockProfiles.byName(envString(Seq("TANGMINER_CLOCK_PROFILE", "SPINAL_CLOCK_PROFILE"), "100m286"))
  val clksPerBit = envInt(Seq("TANGMINER_CLKS_PER_BIT", "SPINAL_CLKS_PER_BIT"), clockProfile.clksPerBit)
  val laneCount = envInt(Seq("TANGMINER_LANES", "SPINAL_LANES"), 5)
  val laneStartStagger = envInt(Seq("TANGMINER_LANE_START_STAGGER", "SPINAL_LANE_START_STAGGER"), 0)
  val splitShaClock = envBoolean("TANGMINER_SPLIT_SHA_CLOCK", default = false) ||
    envBoolean("SPINAL_SPLIT_SHA_CLOCK", default = false)
  val hardwareOptions = TangMinerHardwareOptions(
    sharedRoundConstant = envBoolean("TANGMINER_SHARED_K", default = false),
    enableEcho = envBoolean("TANGMINER_ENABLE_ECHO", default = false),
    enableHardcodedJob = envBoolean("TANGMINER_ENABLE_HARDCODED", default = false),
    fixedCandidateMode = envOptionalInt(Seq("TANGMINER_FIXED_CANDIDATE", "SPINAL_FIXED_CANDIDATE"), Some(2)),
    fullyUnrolled = envBoolean("TANGMINER_FULLY_UNROLLED", default = false) ||
      envBoolean("SPINAL_FULLY_UNROLLED", default = false),
    wideLaneBlock = envBoolean("TANGMINER_WIDE_LANES", default = false) || envBoolean("SPINAL_WIDE_LANES", default = false),
    registerPassOutputs = envBoolean("TANGMINER_REGISTER_PASS_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_PASS_OUTPUTS", default = false),
    registerCompressorOutputs = envBoolean("TANGMINER_REGISTER_COMPRESSOR_OUTPUTS", default = false) ||
      envBoolean("TANGMINER_REGISTER_COMPRESS_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_COMPRESSOR_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_COMPRESS_OUTPUTS", default = false),
    registerFirstPassFeedForward = envBoolean("TANGMINER_REGISTER_FIRST_PASS_FEEDFORWARD", default = false) ||
      envBoolean("SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD", default = false),
    twoCycleRound = envBoolean("TANGMINER_TWO_CYCLE_ROUND", default = false) ||
      envBoolean("SPINAL_TWO_CYCLE_ROUND", default = false),
    threeCycleRound = envBoolean("TANGMINER_THREE_CYCLE_ROUND", default = false) ||
      envBoolean("SPINAL_THREE_CYCLE_ROUND", default = false),
    twoRoundsPerCycle = envBoolean("TANGMINER_TWO_ROUNDS_PER_CYCLE", default = false) ||
      envBoolean("SPINAL_TWO_ROUNDS_PER_CYCLE", default = false),
    twoRoundPipeline = envBoolean("TANGMINER_TWO_ROUND_PIPELINE", default = false) ||
      envBoolean("SPINAL_TWO_ROUND_PIPELINE", default = false),
    twoPhaseRoundPipeline = envBoolean("TANGMINER_TWO_PHASE_ROUND_PIPELINE", default = false) ||
      envBoolean("SPINAL_TWO_PHASE_ROUND_PIPELINE", default = false),
    registerRoundConstant = envBoolean("TANGMINER_REGISTER_ROUND_CONSTANT", default = false) ||
      envBoolean("SPINAL_REGISTER_ROUND_CONSTANT", default = false),
    minimizeShaReset = envBoolean("TANGMINER_MINIMIZE_SHA_RESET", default = false) ||
      envBoolean("SPINAL_MINIMIZE_SHA_RESET", default = false),
    roundSkip = envBoolean("TANGMINER_ROUND_SKIP", default = false) ||
      envBoolean("SPINAL_ROUND_SKIP", default = false),
    csaRound = envBoolean("TANGMINER_CSA_ROUND", default = false) ||
      envBoolean("SPINAL_CSA_ROUND", default = false),
    csaSchedule = envBoolean("TANGMINER_CSA_SCHEDULE", default = false) ||
      envBoolean("SPINAL_CSA_SCHEDULE", default = false),
    balancedRoundAdder = envBoolean("TANGMINER_BALANCED_ROUND_ADDER", default = false) ||
      envBoolean("SPINAL_BALANCED_ROUND_ADDER", default = false),
    shareJobState = envBoolean("TANGMINER_SHARE_JOB_STATE", default = false) ||
      envBoolean("SPINAL_SHARE_JOB_STATE", default = false),
    hostRoundSkip = envBoolean("TANGMINER_HOST_ROUND_SKIP", default = false) ||
      envBoolean("SPINAL_HOST_ROUND_SKIP", default = false)
  )

  SpinalConfig(
    targetDirectory = targetDirectory,
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new Top(
    clksPerBit = clksPerBit,
    usePll = usePll,
    pllKind = pllKind,
    inputClockMhz = inputClockMhz,
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

  def envString(names: Seq[String], default: String): String =
    names.collectFirst(Function.unlift(sys.env.get)).getOrElse(default)

  def envDouble(names: Seq[String], default: Double): Double =
    names.collectFirst(Function.unlift(sys.env.get)).map(_.toDouble).getOrElse(default)

  def envOptionalInt(names: Seq[String]): Option[Int] =
    names.collectFirst(Function.unlift(sys.env.get)).filter(_.nonEmpty).map(_.toInt)

  val targetDirectory = sys.env.getOrElse("TANGMINER_VERILOG_DIR", "build/spinal-sim")
  val laneCount = envInt(Seq("TANGMINER_LANES", "SPINAL_LANES"), 5)
  val laneStartStagger = envInt(Seq("TANGMINER_LANE_START_STAGGER", "SPINAL_LANE_START_STAGGER"), 0)
  val clksPerBit = envInt(Seq("TANGMINER_CLKS_PER_BIT", "SPINAL_CLKS_PER_BIT"), 8)
  val pllKind = envString(Seq("TANGMINER_PLL_KIND", "SPINAL_PLL_KIND"), "rpll")
  val inputClockMhz = envDouble(Seq("TANGMINER_INPUT_CLOCK_MHZ", "SPINAL_INPUT_CLOCK_MHZ"), 27.0)
  val hardwareOptions = TangMinerHardwareOptions(
    sharedRoundConstant = envBoolean("TANGMINER_SHARED_K", default = false),
    enableEcho = envBoolean("TANGMINER_ENABLE_ECHO", default = true),
    enableHardcodedJob = envBoolean("TANGMINER_ENABLE_HARDCODED", default = true),
    fixedCandidateMode = envOptionalInt(Seq("TANGMINER_FIXED_CANDIDATE", "SPINAL_FIXED_CANDIDATE")),
    fullyUnrolled = envBoolean("TANGMINER_FULLY_UNROLLED", default = false) ||
      envBoolean("SPINAL_FULLY_UNROLLED", default = false),
    wideLaneBlock = envBoolean("TANGMINER_WIDE_LANES", default = false) || envBoolean("SPINAL_WIDE_LANES", default = false),
    registerPassOutputs = envBoolean("TANGMINER_REGISTER_PASS_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_PASS_OUTPUTS", default = false),
    registerCompressorOutputs = envBoolean("TANGMINER_REGISTER_COMPRESSOR_OUTPUTS", default = false) ||
      envBoolean("TANGMINER_REGISTER_COMPRESS_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_COMPRESSOR_OUTPUTS", default = false) ||
      envBoolean("SPINAL_REGISTER_COMPRESS_OUTPUTS", default = false),
    registerFirstPassFeedForward = envBoolean("TANGMINER_REGISTER_FIRST_PASS_FEEDFORWARD", default = false) ||
      envBoolean("SPINAL_REGISTER_FIRST_PASS_FEEDFORWARD", default = false),
    twoCycleRound = envBoolean("TANGMINER_TWO_CYCLE_ROUND", default = false) ||
      envBoolean("SPINAL_TWO_CYCLE_ROUND", default = false),
    threeCycleRound = envBoolean("TANGMINER_THREE_CYCLE_ROUND", default = false) ||
      envBoolean("SPINAL_THREE_CYCLE_ROUND", default = false),
    twoRoundsPerCycle = envBoolean("TANGMINER_TWO_ROUNDS_PER_CYCLE", default = false) ||
      envBoolean("SPINAL_TWO_ROUNDS_PER_CYCLE", default = false),
    twoRoundPipeline = envBoolean("TANGMINER_TWO_ROUND_PIPELINE", default = false) ||
      envBoolean("SPINAL_TWO_ROUND_PIPELINE", default = false),
    twoPhaseRoundPipeline = envBoolean("TANGMINER_TWO_PHASE_ROUND_PIPELINE", default = false) ||
      envBoolean("SPINAL_TWO_PHASE_ROUND_PIPELINE", default = false),
    registerRoundConstant = envBoolean("TANGMINER_REGISTER_ROUND_CONSTANT", default = false) ||
      envBoolean("SPINAL_REGISTER_ROUND_CONSTANT", default = false),
    minimizeShaReset = envBoolean("TANGMINER_MINIMIZE_SHA_RESET", default = false) ||
      envBoolean("SPINAL_MINIMIZE_SHA_RESET", default = false),
    roundSkip = envBoolean("TANGMINER_ROUND_SKIP", default = false) ||
      envBoolean("SPINAL_ROUND_SKIP", default = false),
    csaRound = envBoolean("TANGMINER_CSA_ROUND", default = false) ||
      envBoolean("SPINAL_CSA_ROUND", default = false),
    csaSchedule = envBoolean("TANGMINER_CSA_SCHEDULE", default = false) ||
      envBoolean("SPINAL_CSA_SCHEDULE", default = false),
    balancedRoundAdder = envBoolean("TANGMINER_BALANCED_ROUND_ADDER", default = false) ||
      envBoolean("SPINAL_BALANCED_ROUND_ADDER", default = false),
    shareJobState = envBoolean("TANGMINER_SHARE_JOB_STATE", default = false) ||
      envBoolean("SPINAL_SHARE_JOB_STATE", default = false),
    hostRoundSkip = envBoolean("TANGMINER_HOST_ROUND_SKIP", default = false) ||
      envBoolean("SPINAL_HOST_ROUND_SKIP", default = false)
  )

  SpinalConfig(
    targetDirectory = targetDirectory,
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new Top(
    clksPerBit = clksPerBit,
    resetCounterBits = 4,
    usePll = false,
    pllKind = pllKind,
    inputClockMhz = inputClockMhz,
    laneCount = laneCount,
    laneStartStagger = laneStartStagger,
    clockProfile = GowinClockProfiles.byName("27m"),
    splitShaClock = false,
    hardwareOptions = hardwareOptions
  ))
}
