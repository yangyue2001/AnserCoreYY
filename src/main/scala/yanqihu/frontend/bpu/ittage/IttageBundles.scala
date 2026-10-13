package yanqihu.frontend.bpu.ittage

import chisel3._
import chisel3.util._
import utils.SaturateCounter

import yanqihu.frontend.bpu.phr.PhrAllFoldedHistories
import yanqihu.frontend.bpu.tage.TageTableInfo

class IttageOffset extends IttageBundle {
  val offset      = UInt(TargetOffsetWidth.W)
  val usePcRegion = Bool()
}


class IttageData(implicit info: TageTableInfo) extends IttageBundle {
  val valid = Bool()
  val tag   = UInt(TagWidth.W)
  val pointer = UInt(RegionIdxWidth.W)
}

class IttageEntry(implicit info: TageTableInfo) extends IttageBundle {
  val ctr    = new SaturateCounter(ConfidenceCtrWidth)
  val data   = new IttageData
  val useful = new SaturateCounter(UsefulCtrWidth)
  val offset = new IttageOffset
}

class RegionEntry extends IttageBundle {
  val valid  = Bool()
  val region = UInt(RegionWidth.W)
}

class IttageTableReadReq(implicit info: TageTableInfo) extends IttageBundle{
  val bankMask = UInt(NumBanks.W)
  val setIdx  = UInt(SetIdxWidth.W)
}

class IttageTableWriteReq(implicit info: TageTableInfo) extends IttageBundle{
  val bankMask = UInt(NumBanks.W)
  val setIdx = UInt(SetIdxWidth.W)
  val wayMask = UInt(NumWays.W)
  val ctrEn = Bool()
  val dataEn = Bool()
  val usefulEn = Bool()
  val offsetEn = Bool()
  val entry = new IttageEntry
}


class IttageTableResp(implicit info: TageTableInfo) extends IttageBundle {
  val entries = Vec(NumWays, new IttageEntry)
}

class IttagePredReq  extends IttageBundle{
  val pc = UInt(pcWidth.W)
  val predPhr = new PhrAllFoldedHistories
}


class IttagePrediction  extends IttageBundle {
  val taken = Bool()
  val target = UInt(pcWidth.W)
}

class IttageMeta extends IttageBundle {
  val providerValid    = Bool()
  val providerTableIdx = UInt(TableIdxWidth.W)
  val providerTarget   = UInt(pcWidth.W)
  val providerCtr      = new SaturateCounter(ConfidenceCtrWidth)
  val providerUseful   = new SaturateCounter(UsefulCtrWidth)
  val providerWayIdx   = UInt(WayIdxWidth.W)
  val altUsed          = Bool()
  val altTableValid    = Bool()
  val altTarget = UInt(pcWidth.W)
  val altTableIdx      = UInt(TableIdxWidth.W)
  val altCtr           = new SaturateCounter(ConfidenceCtrWidth)
  val altWayIdx = UInt(WayIdxWidth.W)
}

class IttageFoldedHist(implicit info: TageTableInfo) extends IttageBundle {
  val forIdx: UInt      = UInt(SetIdxWidth.W)
  val forTag: Vec[UInt] = Vec(2, UInt(TagWidth.W))
}


class IttageAllFoldedHistories extends IttageBundle {
  val hist = MixedVec(TableInfos.map { implicit tableInfo =>
    new IttageFoldedHist
  })
}