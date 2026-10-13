package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._

import yanqihu.frontend.bpu.phr.PhrAllFoldedHistories
import utils.SaturateCounter

class TageTableData(implicit info: TageTableInfo) extends TageBundle{
  val valid = Bool()
  val tag   = UInt(TagWidth.W)
}

class TageTableEntry(implicit info: TageTableInfo) extends TageBundle {
  val ctr   = new SaturateCounter(TakenCtrWidth)
  val data = new TageTableData
  val useful = new SaturateCounter(UsefulCtrWidth)
}

class TageTableReadReq(implicit info: TageTableInfo) extends TageBundle {
  val bankMask = UInt(NumBanks.W)
  val setIdx  = UInt(SetIdxWidth.W)
}


class BaseTableReadReq extends TageBundle {
  val bankMask = UInt(NumBanks.W)
  val setIdx  = UInt(BaseTableSetIdxWidth.W)
}


class BaseTableWriteReq extends TageBundle{
  val bankMask = UInt(NumBanks.W)
  val setIdx = UInt(BaseTableSetIdxWidth.W)
  val brMask = UInt(NumBr.W)
  val entry = Vec(NumBr, new SaturateCounter(BaseTakenCtrWidth))
}


class BaseTableResp extends TageBundle {
  val entries = Vec(NumBr, new SaturateCounter(BaseTakenCtrWidth))
}


class TageTableWriteReq(implicit info: TageTableInfo) extends TageBundle{
  val bankMask = UInt(NumBanks.W)
  val setIdx = UInt(SetIdxWidth.W)
  val wayMask = UInt(NumWays.W)
  val ctrEn = Vec(NumWays, Bool())
  val dataEn = Vec(NumWays, Bool())
  val usefulEn = Vec(NumWays, Bool())
  val entry = Vec(NumWays, new TageTableEntry)

}

class TageTableResp(implicit info: TageTableInfo) extends TageBundle {
  val entries = Vec(NumWays, new TageTableEntry)
}


class TageMeta extends TageBundle{
  val providerValid = Bool()
  val providerTableIdx = UInt(TableIdxWidth.W)
  val providerTableWayIdx = UInt(WayIdxWidth.W)
  val providerCtr = new SaturateCounter(TakenCtrWidth)
  val providerUseful = new SaturateCounter(UsefulCtrWidth)
  val altUsed = Bool()
  val altTableValid = Bool()
  val altCtr = new SaturateCounter(TakenCtrWidth)
  val altTableIdx = UInt((TableIdxWidth).W)
  val altTableWayIdx = UInt(WayIdxWidth.W)
}

class TagePredReq  extends TageBundle{
  val pc = UInt(pcWidth.W)
  val predPhr = new PhrAllFoldedHistories
}

class TageFoldedHist(implicit info: TageTableInfo) extends TageBundle {
  val forIdx: UInt      = UInt(SetIdxWidth.W)
  val forTag: Vec[UInt] = Vec(2, UInt(TagWidth.W))
}

class TageAllFoldedHistories extends TageBundle {
  val hist = MixedVec(TableInfos.map { implicit tableInfo =>
    new TageFoldedHist
  })
}

