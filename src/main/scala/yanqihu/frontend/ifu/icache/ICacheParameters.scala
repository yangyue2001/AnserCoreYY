package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._
import yanqihu.frontend.ifu.HasIfuParameters
import yanqihu.frontend.ifu.icache
import utils.EccType

case class ICacheParameters(
  //icache
  sizeKB: Int = 32,
  nWays: Int = 4,
  ecc: String = "parity",
  //mshr
  mshrSize: Int = 8,
  //waylookup
  wayLookupSize: Int = 16,
){
  require( nWays > 1)
}

trait HasICacheParameters extends HasIfuParameters{
  def icacheParameters = ifuParameters.icache

  def TagNumBanks = 2
  def DataNumBanks = 8

  def BlockBytes = 64
  def BlockBits = BlockBytes * 8

  def NumWays = icacheParameters.nWays
  def NumSets = icacheParameters.sizeKB * 1024 / BlockBytes / NumWays



  def SetIdxWidth = log2Ceil(NumSets) // cacheline
  def WayIdxWidth = log2Ceil(NumWays)
  def OffsetWidth = log2Ceil(BlockBytes)
  def TagBankIdxWidth = log2Ceil(TagNumBanks)

  def VaddrAliasWidth =  math.max(0, OffsetWidth + SetIdxWidth - pageOffsetWidth)

  def HasVaddrAlias = VaddrAliasWidth > 0


  def TagSetIdxWidth = SetIdxWidth - TagBankIdxWidth // tag sram 2banks
  def DataSetIdxWidth = SetIdxWidth  // data sram 8banks  1 cacheline need 1 set

  def DataWidth = 64

  def TagWithECCWidth = EccType(icacheParameters.ecc, TagWidth).codeWidth
  def DataWithECCWidth = EccType(icacheParameters.ecc, DataWidth).codeWidth
  def TagWidth = paddrWidth - SetIdxWidth - WayIdxWidth - OffsetWidth

  require(TagNumBanks > 1)

  //wayLookup
  def WayLookupSize = icacheParameters.wayLookupSize
  def WayLookupWidth = log2Ceil(WayLookupSize)

  //mshr
  def NumMshrs = icacheParameters.mshrSize

}