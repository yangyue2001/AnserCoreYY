package yanqihu.frontend.bpu.ftb

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.HasBpuParameters
import utils.ReplacementPolicy

case class FtbParameters(
  numWays: Int = 4,
  ftbSize: Int = 2048,
  numBanks: Int = 4,
  tagBits: Int = 20,
  replacement: String = "plru",
) {
  require(numWays > 0)
  require(ftbSize > 0)
  require(numBanks > 0)
  require(ftbSize % numBanks == 0)
  require(tagBits > 0)
}

trait HasFtbParameters extends HasBpuParameters {
  def ftbParameters = bpuParameters.ftbParameters

  def NumWays = ftbParameters.numWays
  def NumBanks = ftbParameters.numBanks
  def TagWidth = ftbParameters.tagBits
  def WayIdxWidth = log2Ceil(NumWays)
  def BankIdxWidth = log2Ceil(NumBanks)
  def FtbSize = ftbParameters.ftbSize
  def NumSets = FtbSize/ NumWays / NumBanks
  def SetIdxWidth = log2Ceil(NumSets)
  def Replacement = ReplacementPolicy(ftbParameters.replacement, NumWays)
}
