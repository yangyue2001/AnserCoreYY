package yanqihu.frontend.bpu.ittage

import chisel3._
import chisel3.util._
import chisel3.util.log2Ceil

import yanqihu.frontend.bpu.HasBpuParameters
import yanqihu.frontend.bpu.tage.TageTableInfo

case class IttageParameters(
  numBanks: Int = 2,
  numWays: Int = 2,
  ctrWidth: Int = 2,
  usefulCtrWidth: Int = 1,
  useAltOnNaWidth: Int = 4,
  numUseAltOnNa: Int = 32,
  usefulResetCtrWidth: Int = 8,
  offsetWidth: Int = 20,
  numRegionWays: Int = 16,
) {
  val tableInfos: Seq[TageTableInfo] = Seq(
    // TageTableInfo(Size, NumWays, HistoryLength, tagBits, numBanks)
    new TageTableInfo(256, numWays, 4, 9, numBanks),
    new TageTableInfo(256, numWays, 8, 9, numBanks) ,
    new TageTableInfo(512, numWays, 13, 9, numBanks),
    new TageTableInfo(512, numWays, 16, 9, numBanks),
    new TageTableInfo(512, numWays, 32, 9, numBanks),
  )
}


trait HasIttageParameters extends HasBpuParameters {
  def ittageParameters: IttageParameters = bpuParameters.ittageParameters

  def TableInfos: Seq[TageTableInfo] = ittageParameters.tableInfos
  def NumTables:  Int                  = TableInfos.length
  def TableIdxWidth: Int = log2Ceil(NumTables)

  def NumBanks:     Int = ittageParameters.numBanks
  def NumWays: Int = ittageParameters.numWays
  def BankIdxWidth: Int = log2Ceil(NumBanks)
  def WayIdxWidth: Int = log2Ceil(NumWays)

  def HasMultiWay: Boolean = NumWays > 1

  def ConfidenceCtrWidth: Int = ittageParameters.ctrWidth
  def UsefulCtrWidth:     Int = ittageParameters.usefulCtrWidth
  def UseAltOnNaWidth:    Int = ittageParameters.useAltOnNaWidth
  def TargetOffsetWidth:  Int = ittageParameters.offsetWidth
  def UsefulResetCtrWidth:Int = ittageParameters.usefulResetCtrWidth
  def NumUseAltOnNa: Int = ittageParameters.numUseAltOnNa
  def UseAltOnNaIdxWidth: Int = log2Ceil(NumUseAltOnNa)
  /* *** Region *** */
  def RegionNums:     Int     = ittageParameters.numRegionWays
  def RegionIdxWidth: Int = log2Ceil(RegionNums)
  def RegionWidth:     Int    = PcWidth - TargetOffsetWidth

  //per table parameters
  def NumSets(implicit info: TageTableInfo): Int = info.numSets
  def SetIdxWidth(implicit info: TageTableInfo): Int = log2Ceil(NumSets)
  def TagWidth(implicit info: TageTableInfo): Int = info.tagBits
}