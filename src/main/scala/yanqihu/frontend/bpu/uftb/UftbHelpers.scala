package yanqihu.frontend.bpu.uftb

import chisel3._
import chisel3.util._


trait UftbHelper extends HasUftbParameters{

  def getTag(pc: UInt) :UInt ={
    require(pc.getWidth == pcWidth)
    pc(TagWidth-1, 0)
  }

  def getTarget(pc: UInt, tarStat: UInt, lower: UInt): UInt = {
    require(pc.getWidth == pcWidth)
    val lowerWidth = lower.getWidth

    val pcHigh = pc(pcWidth-1, lowerWidth)
    val plusOne  = (pcHigh + 1.U)
    val minusOne = (pcHigh - 1.U)
    val targetHigh = MuxLookup(tarStat, pcHigh)(Seq(
      0.U -> pcHigh,
      1.U -> plusOne,
      2.U -> minusOne
    ))
    Cat(targetHigh, lower)
  }

  def getFallThroughTarget(pc: UInt, carry: Bool, lower: UInt): UInt = {
    require(pc.getWidth == pcWidth)
    val lowerWidth = lower.getWidth
    val pcHigh = pc(pcWidth-1, lowerWidth)
    val plusOne  = (pcHigh + 1.U)
    val targetHigh = Mux(carry, plusOne, pcHigh)
    Cat(targetHigh, lower)
  }

  def getFallThroughOffset(pc: UInt, carry: Bool, lower: UInt): UInt = {
    require(pc.getWidth == pcWidth)
    val lowerWidth = lower.getWidth
    val pcLower = pc(lowerWidth-1, 0)
    Cat(carry, pcLower) - Cat(0.U(1.W),lower)
  }
}
