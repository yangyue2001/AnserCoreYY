package yanqihu.frontend.ifu.predecode

import chisel3._
import chisel3.util._

import yanqihu.frontend.ifu.IfuBundle
import yanqihu.frontend.CfiAttribute

class PredecodeInfo extends IfuBundle{
  val redirectValid = Bool()
  val endOffset = UInt(FetchBlockOffsetWidth.W)
  val target = UInt(PcWidth.W)
  val taken = Bool()
  val cfiAttr = new CfiAttribute
}

class PreDecodeCfiAttribute extends IfuBundle{
  val branch = Bool()
  val jal = Bool()
  val jalr = Bool()
  val call = Bool()
  val ret = Bool()
}

class PreDecodeReq extends IfuBundle{
  val inst = Vec(FetchBlockNumSlots, UInt(16.W))
  val predTaken = Bool()
  val predEndOffset = UInt(FetchBlockOffsetWidth.W)
  val startPc = UInt(PcWidth.W)
  val rasTopAddr = UInt(PcWidth.W)
  val predTarget = UInt(PcWidth.W)
}