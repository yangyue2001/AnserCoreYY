package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._
import scala.math.min
import yanqihu.frontend.bpu.HasBpuParameters
import yanqihu.frontend.bpu.phr.FoldedHistoryInfo

class TageTableInfo(
    val size:          Int,
    val numWays:       Int,
    val historyLength: Int,
    val tagBits:       Int,
    val numBanks:      Int,
) {
  require(size > 0, "Size must be > 0")
  require(numWays > 0, "NumWays must be > 0")
  require(historyLength > 0, "HistoryLength must be > 0")
  require(tagBits > 0, "TagBits must be > 0")
  require(numBanks > 0, "NumBanks must be > 0")


  def numSets: Int = size / numWays / numBanks

  def getFoldedHistoryInfoSet: Set[FoldedHistoryInfo] = {
    Set( // FoldedHistoryInfo(unfolded history length, folded history length)
      new FoldedHistoryInfo(historyLength, min(historyLength, log2Ceil(numSets))),
      new FoldedHistoryInfo(historyLength, min(historyLength, tagBits)),
      new FoldedHistoryInfo(historyLength, min(historyLength, tagBits - 1))
    )
  }

  def getTageFoldedHistoryInfo: List[FoldedHistoryInfo] = {
      List( // FoldedHistoryInfo(unfolded history length, folded history length)
        new FoldedHistoryInfo(historyLength, min(historyLength, log2Ceil(numSets))),
        new FoldedHistoryInfo(historyLength, min(historyLength, tagBits)),
        new FoldedHistoryInfo(historyLength, min(historyLength, tagBits - 1))
      )
  }
}


case class TageParameters(
  numBanks: Int = 4,
  numWays: Int = 2,
  takenCtrWidth: Int = 3,
  usefulCtrWidth: Int = 1,
  usefulResetCtrWidth: Int = 8,
  useAltOnNaWidth: Int = 7,
  numUseAltOnNa: Int = 128, // perSlot
  baseTakenCtrWidth: Int = 2,
  baseTableSize: Int = 4096,

) {
  val tableInfos: Seq[TageTableInfo] = Seq(
    // TageTableInfo(Size, NumWays, HistoryLength, tagBits, numBanks)
    new TageTableInfo(1024, numWays, 6, 13, numBanks),
    new TageTableInfo(1024, numWays, 9, 13, numBanks) ,
    new TageTableInfo(1024, numWays, 17, 13, numBanks),
    new TageTableInfo(1024, numWays, 31, 13, numBanks),
  )

  require(numBanks > 1)
  require(numWays > 1)

}

trait HasTageParameters extends HasBpuParameters {
  def tageParameters= bpuParameters.tageParameters

  def NumBanks = tageParameters.numBanks
  def NumWays = tageParameters.numWays
  def BankIdxWidth = log2Ceil(NumBanks)
  def WayIdxWidth = log2Ceil(NumWays)

  //table
  def TableInfos: Seq[TageTableInfo] = tageParameters.tableInfos
  def NumTables:     Int = TableInfos.length
  def TableIdxWidth: Int = log2Ceil(NumTables)

  def TakenCtrWidth = tageParameters.takenCtrWidth
  def UsefulCtrWidth = tageParameters.usefulCtrWidth
  def UsefulResetCtrWidth: Int = tageParameters.usefulResetCtrWidth
  def UseAltOnNaWidth:     Int = tageParameters.useAltOnNaWidth
  def NumUseAltOnNa = tageParameters.numUseAltOnNa
  def UseAltOnNaIdxWidth = log2Ceil(NumUseAltOnNa)

  //base
  def BaseTakenCtrWidth: Int = tageParameters.baseTakenCtrWidth
  def BaseTableSize: Int = tageParameters.baseTableSize
  def BaseTableSets: Int = BaseTableSize/ NumBanks / NumBr
  def BaseTableSetIdxWidth :Int = log2Ceil(BaseTableSets)

  // per table parameters
  def NumSets(implicit info:     TageTableInfo): Int = info.numSets
  def SetIdxWidth(implicit info: TageTableInfo): Int = log2Ceil(NumSets)
  def TagWidth(implicit info: TageTableInfo): Int = info.tagBits

}
