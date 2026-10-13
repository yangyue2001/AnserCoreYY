package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._

import yanqihu.frontend.ifu.HasIfuParameters

case class ICacheParameters(
  //icache
  sizeKB: Int = 32,
  nWays: Int = 4,
  //mshr
  mshrSize: Int = 8,
  //waylookup
  wayLookupSize: Int = 16,
){
  require( nWays > 1)
}

trait HasICacheParameters extends HasIfuParameters{
  def icacheParameters = ifuParameters.icache

  def TagNumBanks = ICacheConstant.TagNumBanks
  def DataNumBanks = ICacheConstant.DataNumBanks

  def numWays = icacheParameters.nWays
  def numSets = icacheParameters.sizeKB * 1024 / CachelineBytes / numWays

  def SetIdxWidth = log2Ceil(numSets) // cacheline
  def WayIdxWidth = log2Ceil(numWays)
  def OffsetWidth = log2Ceil(CachelineBytes)
  def TagBankIdxWidth = log2Ceil(TagNumBanks)

  def VaddrAliasWidth =  math.max(0, OffsetWidth + SetIdxWidth - PageOffsetWidth)
  def HasVaddrAlias = VaddrAliasWidth > 0


  def TagSetIdxWidth = SetIdxWidth - TagBankIdxWidth
  def DataSetIdxWidth = SetIdxWidth
  def tagWidth = paddrWidth - SetIdxWidth - WayIdxWidth - OffsetWidth
  def DataWidth = CachelineBits / DataNumBanks

  require(TagNumBanks > 1)

  //wayLookup
  def WayLookupSize = icacheParameters.wayLookupSize
  def WayLookupWidth = log2Ceil(WayLookupSize)

  //mshr
  def NumMshrs = icacheParameters.mshrSize

}