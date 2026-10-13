package yanqihu.frontend.bpu.ittage

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.tage.TageTableInfo
import yanqihu.frontend.bpu.phr.PhrAllFoldedHistories

trait IttageHelper{
  this: HasIttageParameters =>
  def getBankIndex(pc: UInt): UInt ={
    require(pc.getWidth == pcWidth)
    pc(BankIdxWidth-1, 0)
  }

  def getSetIndex(pc: UInt, hist: IttageFoldedHist)(implicit info: TageTableInfo) :UInt ={
    val forIdx = hist.forIdx
    require(forIdx == SetIdxWidth)
    pc(SetIdxWidth+BankIdxWidth-1, BankIdxWidth) ^ forIdx
  }

  def getTag(pc: UInt, hist: IttageFoldedHist)(implicit info: TageTableInfo): UInt = {
    val hist0 = hist.forTag(0)
    val hist1 = hist.forTag(1)
    require(hist0.getWidth == TagWidth)
    require(hist1.getWidth == TagWidth)
    pc(SetIdxWidth+BankIdxWidth+TagWidth-1, SetIdxWidth+BankIdxWidth) ^ hist0 ^ hist1
  }

  def getOffset(target: UInt): UInt = {
    require(target.getWidth == pcWidth)
    target(TargetOffsetWidth-1, 0)
  }

  def getRegion(pc: UInt): UInt = {
    pc(pcWidth-1, TargetOffsetWidth)
  }

  def getMaxTagWidth: Int = {
    TableInfos.map(_.tagBits).max
  }

  def getMaxSetIndexTableIdx: Int = {
    TableInfos.zipWithIndex.maxBy {case (tableInfo, idx) => tableInfo.numSets}._2
  }

  def getFoldedHist(allFoldedPathHist: PhrAllFoldedHistories): IttageAllFoldedHistories = {
    val tageFoldHist = Wire(new IttageAllFoldedHistories)

    TableInfos.zipWithIndex.foreach { case (tableInfo, tableIdx) =>
      val folded = tableInfo.getTageFoldedHistoryInfo.map { histInfo =>
        allFoldedPathHist.getHistWithInfo(histInfo).foldedHist
      }
      tageFoldHist.hist(tableIdx).forIdx    := folded(0)
      tageFoldHist.hist(tableIdx).forTag(0) := folded(1)
      tageFoldHist.hist(tableIdx).forTag(1) := Cat(folded(2), 0.U(1.W))
    }

    tageFoldHist
  }
}
