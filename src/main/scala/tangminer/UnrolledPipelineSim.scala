package tangminer

import spinal.core._

/** Direct, UART-free wrapper for the streaming SHA256d pipeline. */
class BitcoinHashUnrolledPipelineSimTop extends Component {
  noIoPrefix()

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

  def makePipeline(fixedCandidateMode: Int): BitcoinHashUnrolledPipeline =
    new BitcoinHashUnrolledPipeline(
      TangMinerHardwareOptions(
        fixedCandidateMode = Some(fixedCandidateMode),
        roundSkip = true,
        hostRoundSkip = true,
        fullyUnrolled = true
      )
    )

  val quick26Pipeline = makePipeline(4)
  val alwaysPipeline = makePipeline(0)
  Seq(quick26Pipeline, alwaysPipeline).foreach { pipeline =>
    pipeline.io.reset := io.reset
    pipeline.io.start := io.start
    pipeline.io.stop := io.stop
    pipeline.io.midstate := io.midstate
    pipeline.io.candidateMode := io.candidateMode
    pipeline.io.roundSkipPrefixState := io.roundSkipPrefixState
    pipeline.io.roundSkipTail2 := io.roundSkipTail2
    pipeline.io.roundSkipW16 := io.roundSkipW16
    pipeline.io.roundSkipW17 := io.roundSkipW17
    pipeline.io.startNonce := io.startNonce
    pipeline.io.nonceStride := io.nonceStride
  }

  val selectAlways = io.candidateMode === U(0, 3 bits)
  io.running := Mux(selectAlways, alwaysPipeline.io.running, quick26Pipeline.io.running)
  io.found := Mux(selectAlways, alwaysPipeline.io.found, quick26Pipeline.io.found)
  io.foundNonce := Mux(selectAlways, alwaysPipeline.io.foundNonce, quick26Pipeline.io.foundNonce)
  io.currentNonce := Mux(selectAlways, alwaysPipeline.io.currentNonce, quick26Pipeline.io.currentNonce)
  io.nonceAttempt := Mux(selectAlways, alwaysPipeline.io.nonceAttempt, quick26Pipeline.io.nonceAttempt)
  io.candidateValid := Mux(selectAlways, alwaysPipeline.io.candidateValid, quick26Pipeline.io.candidateValid)
  io.candidateNonce := Mux(selectAlways, alwaysPipeline.io.candidateNonce, quick26Pipeline.io.candidateNonce)
  io.candidateLow32 := Mux(selectAlways, alwaysPipeline.io.candidateLow32, quick26Pipeline.io.candidateLow32)
}

object GenerateUnrolledPipelineSimVerilog extends App {
  val targetDirectory = sys.env.getOrElse(
    "TANGMINER_UNROLLED_SIM_DIR",
    "build/unrolled-pipeline-sim"
  )
  SpinalConfig(
    targetDirectory = targetDirectory,
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT)
  ).generateVerilog(new BitcoinHashUnrolledPipelineSimTop)
}
