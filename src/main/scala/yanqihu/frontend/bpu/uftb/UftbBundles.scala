package yanqihu.frontend.bpu.uftb

import chisel3._
import chisel3.util._

import yanqihu.frontend.bpu.ftb.BranchSlot
import yanqihu.frontend.bpu.ftb.TailSlot
import yanqihu.frontend.bpu.ftb.FtbEntry

class UftbEntry extends UftbBundle {
  val ftb = new FtbEntry
}

class UftbPrediction extends UftbBundle {
  val taken       = Bool()
  val hit         = Bool()
  val cfiOffset   = UInt(FetchBlockOffsetWidth.W)
  val target      = UInt(pcWidth.W)
  val isJalr      = Bool()
}


class UftbMeta extends UftbBundle {
  val isJalr = Bool()
  val fallThroughCarry = Bool()
  val fallThroughLower = UInt(FetchBlockOffsetWidth.W)
  val brSlot = new BranchSlot
  val tailSlot = new TailSlot

  def isEqual(that: UftbMeta): Bool = {
    (this.isJalr === that.isJalr) && (this.fallThroughCarry === that.fallThroughCarry) &&
    (this.fallThroughLower === that.fallThroughLower) && (this.brSlot.asUInt === that.brSlot.asUInt) &&
    (this.tailSlot.valid === that.tailSlot.valid) && (this.tailSlot.offset === that.tailSlot.offset) &&
    (this.tailSlot.tarStat === that.tailSlot.tarStat) &&(this.tailSlot.share === that.tailSlot.share) &&
    (this.isJalr || (this.tailSlot.lower === this.tailSlot.lower))
  }
}


class UftbTrainReq extends UftbBundle {
  val s3Override = Bool()
  val trainPc    = UInt(pcWidth.W)
  val finalTaken = Vec(NumBr, Bool())
  val finalTarget = UInt(pcWidth.W)
  val ftbMeta = new UftbMeta
  val uftbMeta       = new UftbMeta
}
