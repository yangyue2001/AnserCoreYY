package yanqihu

import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}

object Gen extends App {
  val stageArgs = Array(
    "--target", "systemverilog",
    "--target-dir", "build/generated",
    "--split-verilog",
    "--dump-fir"
  )

  val firtoolOpts = Seq(
    FirtoolOption("-O=release"),
    FirtoolOption("--disable-annotation-unknown"),
    FirtoolOption("--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none")
  )

/*
  (new ChiselStage).execute(
    stageArgs,
    ChiselGeneratorAnnotation(() => new MyTop ) +: firtoolOpts
  )
*/
}
