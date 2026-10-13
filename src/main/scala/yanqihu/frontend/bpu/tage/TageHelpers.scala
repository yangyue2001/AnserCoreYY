package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.phr.PhrAllFoldedHistories


trait TableHelper extends HasTageParameters{

  def getBankIndex(pc: UInt): UInt ={
    require(pc.getWidth == pcWidth)
    pc(BankIdxWidth-1,0)
  }

  def getSetIndex(pc: UInt, hist: TageFoldedHist)(implicit info: TageTableInfo) :UInt ={
    val forIdx = hist.forIdx
    require(forIdx.getWidth == SetIdxWidth)
    pc(SetIdxWidth+BankIdxWidth-1, BankIdxWidth) ^ forIdx
  }

  def getTag(pc: UInt, hist: TageFoldedHist, offset: UInt)(implicit info: TageTableInfo): UInt = {
    val forTag0 = hist.forTag(0)
    val forTag1 = hist.forTag(1)
    require(forTag0.getWidth == TagWidth)
    require(forTag1.getWidth == TagWidth)
    pc(SetIdxWidth+BankIdxWidth+TagWidth-1, SetIdxWidth+BankIdxWidth) ^ forTag0 ^ forTag1 ^ offset
  }

  def getBaseSetIndex(pc: UInt): UInt = {
    pc(BaseTableSetIdxWidth+BankIdxWidth-1, BankIdxWidth)
  }

  def getFoldedHist(allFoldedPathHist: PhrAllFoldedHistories): TageAllFoldedHistories = {
    val tageFoldHist = Wire(new TageAllFoldedHistories)

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