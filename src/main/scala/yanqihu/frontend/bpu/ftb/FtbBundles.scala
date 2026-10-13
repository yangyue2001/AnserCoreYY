package yanqihu.frontend.bpu.ftb

import chisel3._
import chisel3.util._


class JmpInfo extends FtbBundle{
  val call = Bool()
  val ret = Bool()
  val jalr = Bool()
}

class BranchSlot extends FtbBundle {
  val valid   = Bool()
  val offset  = UInt(FetchBlockOffsetWidth.W)
  val lower   = UInt(12.W)
  val tarStat = UInt(2.W)
}

class TailSlot extends FtbBundle {
  val valid   = Bool()
  val share   = Bool()
  val offset  = UInt(FetchBlockOffsetWidth.W)
  val lower   = UInt(20.W)
  val tarStat = UInt(2.W)
}

class FtbTagInfo extends FtbBundle{
  val tag         = UInt(TagWidth.W)
  val jmpInfo     = new JmpInfo
}

class FtbSlotInfo extends FtbBundle{
  val branch      = new BranchSlot
  val tail        = new TailSlot
}

class FtbEntry extends FtbBundle {
  val tag = new FtbTagInfo
  val slots = new FtbSlotInfo
}

class FtbPredReq extends FtbBundle {
  val pc = UInt(pcWidth.W)
}

class FtbPrediction extends FtbBundle {
  val branchHit         = Bool()
  val tailHit           = Bool()
  val fallThroughTarget = UInt(pcWidth.W)
  val branchTarget      = UInt(pcWidth.W)
  val tailTarget        = UInt(pcWidth.W)
  val fallThroughOffset = UInt(FetchBlockOffsetWidth.W)
  val branchOffset      = UInt(FetchBlockOffsetWidth.W)
  val tailOffset        = UInt(FetchBlockOffsetWidth.W)
}

class FtbMeta extends FtbBundle {
  val hitWay = UInt(WayIdxWidth.W)
  val hit = Bool()
  val brSlot = new BranchSlot
  val tailSlot = new TailSlot
}

class FtbReadReq extends FtbBundle {
  val bankMask = UInt(NumBanks.W)
  val setIdx = UInt(SetIdxWidth.W)
}

class FtbWriteReq extends FtbBundle {
  val bankMask = UInt(NumBanks.W)
  val setIdx = UInt(SetIdxWidth.W)
  val wayMask = UInt(NumWays.W)
  val tagEn = Bool()
  val slotEn = Bool()
  val entry = new FtbEntry
}

class FtbReadResp extends FtbBundle {
  val entries = Vec(NumWays, new FtbEntry)
}