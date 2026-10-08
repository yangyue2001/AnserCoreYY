package yanqihu.frontend.bpu.ftb

import chisel3._
import chisel3.util._


trait FtbHelper {
  this: HasFtbParameters =>

  def getBankIndex(pc: UInt): UInt = {
    require(pc.getWidth == PcWidth)
    pc(BankIdxWidth-1 , 0)
  }

  def getSetIndex(pc: UInt) :UInt ={
    require(pc.getWidth == PcWidth)
    pc(BankIdxWidth+SetIdxWidth-1, BankIdxWidth)
  }

  def getTag(pc: UInt) :UInt ={
    require(pc.getWidth == PcWidth)
    pc(SetIdxWidth+BankIdxWidth+TagWidth-1, SetIdxWidth+BankIdxWidth)
  }

  def getTarget(pc: UInt, tarStat: UInt, lower: UInt): UInt = {
    require(pc.getWidth == PcWidth)
    val lowerWidth = lower.getWidth

    val pcHigh = pc(PcWidth-1, lowerWidth)
    val plusOne  = (pcHigh + 1.U)
    val minusOne = (pcHigh - 1.U)
    val targetHigh = MuxLookup(tarStat, pcHigh)(Seq(
      0.U -> pcHigh,
      1.U -> plusOne,
      2.U -> minusOne
    ))
    Cat(targetHigh, lower)
  }

}
