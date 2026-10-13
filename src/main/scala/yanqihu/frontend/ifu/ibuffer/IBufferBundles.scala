package yanqihu.frontend.ifu.ibuffer

import chisel3._
import chisel3.util._

import yanqihu.frontend.ftq.FtqPtr


class IBufferEntry extends IBufferBundle{
  val slot = UInt(16.W)
  val instEnd = Bool()
  val isLastInFetchBlock = Bool()
}

class IBufferExceptionEntry extends IBufferBundle{
  val valid = Bool()
  val pageFault = Bool()
  val accessFault = Bool()
}

class InstPacket extends IBufferBundle{
  val inst = UInt(32.W)
  val isLastInFetchBlock = Bool()
  val ftqPtr= new FtqPtr
  val endOffset = UInt(FetchBlockOffsetWidth.W)
  //val except
}

class IBufferReq extends IBufferBundle{
  val ftqPtr= new FtqPtr
  val slots = Vec(FetchBlockNumSlots, UInt(16.W))
  val slotInstEnd = Vec(FetchBlockNumSlots, Bool()) // 这个slot 是否是一个指令的end
  val lastSlotOffset = UInt(FetchBlockOffsetWidth.W) // fetch Block 内部最后一个slot
}