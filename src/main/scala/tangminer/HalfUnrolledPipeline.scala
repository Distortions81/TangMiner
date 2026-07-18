package tangminer

import spinal.core._

case class HalfUnrolledPipelineStage(state: Seq[UInt], schedule: Seq[UInt], valid: Bool, nonce: UInt)

object HalfUnrolledPipelineRound {
  def register(
    input: HalfUnrolledPipelineStage,
    feedback: Bool,
    clear: Bool,
    evenRoundIndex: Int,
    oddRoundIndex: Option[Int],
    scheduleLastRound: Int
  ): HalfUnrolledPipelineStage = {
    val stateRegs = Seq.fill(8)(Reg(UInt(32 bits)))
    val scheduleRegs = Seq.fill(16)(Reg(UInt(32 bits)))
    val validReg = Reg(Bool()) init False
    val nonceReg = Reg(UInt(32 bits)) init 0

    val sourceState = input.state.zip(stateRegs).map { case (external, local) => Mux(feedback, local, external) }
    val sourceSchedule = input.schedule.zip(scheduleRegs).map { case (external, local) => Mux(feedback, local, external) }
    val sourceValid = Mux(feedback, validReg, input.valid)
    val sourceNonce = Mux(feedback, nonceReg, input.nonce)
    val roundConstant = oddRoundIndex match {
      case Some(odd) => Mux(feedback, Sha256.word(Sha256.K(odd)), Sha256.word(Sha256.K(evenRoundIndex)))
      case None => Sha256.word(Sha256.K(evenRoundIndex))
    }

    val a = sourceState(0)
    val b = sourceState(1)
    val c = sourceState(2)
    val d = sourceState(3)
    val e = sourceState(4)
    val f = sourceState(5)
    val g = sourceState(6)
    val h = sourceState(7)
    val t1 = (h + Sha256.bigSigma1(e) + Sha256.ch(e, f, g) + roundConstant + sourceSchedule(0)).resize(32)
    val t2 = (Sha256.bigSigma0(a) + Sha256.maj(a, b, c)).resize(32)
    val nextState = Seq((t1 + t2).resize(32), a, b, c, (d + t1).resize(32), e, f, g)
    val generatedWord = (Sha256.smallSigma1(sourceSchedule(14)) + sourceSchedule(9) +
      Sha256.smallSigma0(sourceSchedule(1)) + sourceSchedule(0)).resize(32)
    val evenScheduleRequired = evenRoundIndex <= scheduleLastRound
    val oddScheduleRequired = oddRoundIndex.exists(_ <= scheduleLastRound)
    val scheduleRequired = oddRoundIndex match {
      case Some(_) => Mux(feedback, Bool(oddScheduleRequired), Bool(evenScheduleRequired))
      case None => Bool(evenScheduleRequired)
    }
    val nextSchedule = sourceSchedule.drop(1) :+ Mux(scheduleRequired, generatedWord, U(0, 32 bits))
    val doRound = oddRoundIndex match {
      case Some(_) => True
      case None => !feedback
    }

    when(clear) {
      validReg := False
    } elsewhen(doRound) {
      validReg := sourceValid
      nonceReg := sourceNonce
      stateRegs.zip(nextState).foreach { case (register, value) => register := value }
      scheduleRegs.zip(nextSchedule).foreach { case (register, value) => register := value }
    }

    HalfUnrolledPipelineStage(stateRegs, scheduleRegs, validReg, nonceReg)
  }
}

class BitcoinHashHalfUnrolledFirstPass extends Component {
  val io = new Bundle {
    val clear = in Bool()
    val feedback = in Bool()
    val injectValid = in Bool()
    val midstate = in Bits(256 bits)
    val roundSkipPrefixState = in Bits(256 bits)
    val roundSkipTail2 = in UInt(32 bits)
    val roundSkipW16 = in UInt(32 bits)
    val roundSkipW17 = in UInt(32 bits)
    val nonceIn = in UInt(32 bits)
    val digestOut = out Bits(256 bits)
    val validOut = out Bool()
    val nonceOut = out UInt(32 bits)
  }

  val zero = U(0, 32 bits)
  val w18 = (Sha256.smallSigma1(io.roundSkipW16) + Sha256.smallSigma0(io.nonceIn) + io.roundSkipTail2).resize(32)
  val firstInput = HalfUnrolledPipelineStage(
    (0 until 8).map(i => Sha256.wordFromDigest(io.roundSkipPrefixState, i)),
    Seq(io.nonceIn, U(BigInt("80000000", 16), 32 bits)) ++ Seq.fill(10)(zero) ++
      Seq(U(BigInt("00000280", 16), 32 bits), io.roundSkipW16, io.roundSkipW17, w18),
    io.injectValid,
    io.nonceIn
  )
  val firstRounds = (0 until 31).foldLeft(firstInput) { (stage, physicalIndex) =>
    val evenRound = 3 + physicalIndex * 2
    val oddRound = if (evenRound + 1 <= 63) Some(evenRound + 1) else None
    HalfUnrolledPipelineRound.register(stage, io.feedback, io.clear, evenRound, oddRound, scheduleLastRound = 47)
  }

  io.digestOut := Sha256Pass.addFeedForward(io.midstate, Sha256.concatWords(firstRounds.state))
  io.validOut := firstRounds.valid
  io.nonceOut := firstRounds.nonce
}

class BitcoinHashHalfUnrolledSecondPass extends Component {
  val io = new Bundle {
    val clear = in Bool()
    val feedback = in Bool()
    val inputValid = in Bool()
    val digestIn = in Bits(256 bits)
    val nonceIn = in UInt(32 bits)
    val candidateLow32 = out UInt(32 bits)
    val validOut = out Bool()
    val nonceOut = out UInt(32 bits)
  }

  val zero = U(0, 32 bits)
  val secondInput = HalfUnrolledPipelineStage(
    Sha256.Iv.map(Sha256.word),
    (0 until 8).map(i => Sha256.wordFromDigest(io.digestIn, i)) ++
      Seq(U(BigInt("80000000", 16), 32 bits)) ++ Seq.fill(6)(zero) ++
      Seq(U(BigInt("00000100", 16), 32 bits)),
    io.inputValid,
    io.nonceIn
  )
  val secondRounds = (0 until 31).foldLeft(secondInput) { (stage, physicalIndex) =>
    val evenRound = physicalIndex * 2
    val oddRound = if (evenRound + 1 <= 60) Some(evenRound + 1) else None
    HalfUnrolledPipelineRound.register(stage, io.feedback, io.clear, evenRound, oddRound, scheduleLastRound = 44)
  }

  io.candidateLow32 := secondRounds.state(4)
  io.validOut := secondRounds.valid
  io.nonceOut := secondRounds.nonce
}

class BitcoinHashHalfUnrolledPipeline(options: TangMinerHardwareOptions) extends Component {
  require(options.halfUnrolled, "BitcoinHashHalfUnrolledPipeline requires halfUnrolled")
  require(options.hostRoundSkip, "BitcoinHashHalfUnrolledPipeline requires hostRoundSkip")
  require(options.roundSkip, "BitcoinHashHalfUnrolledPipeline requires roundSkip")

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
  val feedbackPhaseReg = Reg(Bool()) init False
  val pendingDigestReg = Reg(Bits(256 bits))
  val pendingNonceReg = Reg(UInt(32 bits)) init 0
  val pendingValidReg = Reg(Bool()) init False

  val candidateValid = Bool()
  val candidateMeetsTarget = Bool()
  val clearPipeline = io.reset || io.start || io.stop || candidateMeetsTarget
  val injectValid = runningReg && !feedbackPhaseReg && !candidateMeetsTarget && !clearPipeline

  val firstPass = new BitcoinHashHalfUnrolledFirstPass
  firstPass.io.clear := clearPipeline
  firstPass.io.feedback := feedbackPhaseReg
  firstPass.io.injectValid := injectValid
  firstPass.io.midstate := io.midstate
  firstPass.io.roundSkipPrefixState := io.roundSkipPrefixState
  firstPass.io.roundSkipTail2 := io.roundSkipTail2
  firstPass.io.roundSkipW16 := io.roundSkipW16
  firstPass.io.roundSkipW17 := io.roundSkipW17
  firstPass.io.nonceIn := currentNonceReg

  when(clearPipeline) {
    pendingValidReg := False
  } otherwise {
    when(!feedbackPhaseReg) {
      pendingValidReg := False
    }
    when(feedbackPhaseReg && firstPass.io.validOut) {
      pendingDigestReg := firstPass.io.digestOut
      pendingNonceReg := firstPass.io.nonceOut
      pendingValidReg := True
    }
  }

  val secondPass = new BitcoinHashHalfUnrolledSecondPass
  secondPass.io.clear := clearPipeline
  secondPass.io.feedback := feedbackPhaseReg
  secondPass.io.inputValid := pendingValidReg
  secondPass.io.digestIn := pendingDigestReg
  secondPass.io.nonceIn := pendingNonceReg

  candidateValid := runningReg && feedbackPhaseReg && secondPass.io.validOut
  candidateMeetsTarget := candidateValid && BitcoinCandidateFilter.meets(
    options,
    io.candidateMode,
    secondPass.io.candidateLow32
  )

  when(io.reset) {
    runningReg := False
    foundReg := False
    foundNonceReg := 0
    currentNonceReg := 0
    feedbackPhaseReg := False
  } otherwise {
    when(io.stop) {
      runningReg := False
      foundReg := False
      feedbackPhaseReg := False
    } elsewhen(io.start) {
      runningReg := True
      foundReg := False
      foundNonceReg := 0
      currentNonceReg := io.startNonce
      feedbackPhaseReg := False
    } elsewhen(candidateMeetsTarget) {
      runningReg := False
      foundReg := True
      foundNonceReg := secondPass.io.nonceOut
      feedbackPhaseReg := False
    } elsewhen(runningReg) {
      feedbackPhaseReg := !feedbackPhaseReg
      when(injectValid) {
        currentNonceReg := currentNonceReg + io.nonceStride
      }
    }
  }

  io.running := runningReg
  io.found := foundReg
  io.foundNonce := foundNonceReg
  io.currentNonce := currentNonceReg
  io.nonceAttempt := injectValid
  io.candidateValid := candidateValid
  io.candidateNonce := secondPass.io.nonceOut
  io.candidateLow32 := secondPass.io.candidateLow32
}
