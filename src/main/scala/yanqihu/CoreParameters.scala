package yanqihu

import chisel3._
import chisel3.util._
import yanqihu.frontend.FrontendParameters
import yanqihu.mmu.MmuParameters
import yanqihu.mmu.MmuConstant
import yanqihu.mmu.SvMode

object RV64ISASpec{
  val XLEN: Int = 64
}


case class Parameters(
  paddrBits: Int = 56,
  instOffsetBytes: Int = 2,
  hartIdWidth: Int = 1,

  frontendParameters: FrontendParameters = FrontendParameters(),
  mmuParameters: MmuParameters = MmuParameters(),
  //backendParameters: BackendParameters = BackendParameters(),
  //memParameters: MemParameters = MemParameters(),
)


trait HasYQHParameters{
  implicit val p : Parameters = Parameters()

  def coreParams = p
  def HartIdWidth = p.hartIdWidth
  def pageOffsetWidth = MmuConstant.PageOffsetWidth
  def vaddrWidth = pageOffsetWidth + (SvMode.Sv48.Levels * MmuConstant.LevelVpnWidth)
  def paddrWidth = p.paddrBits
  def MinInstBytes = p.instOffsetBytes
  def InstOffsetWidth = log2Ceil(MinInstBytes)
  def PcWidth = vaddrWidth - InstOffsetWidth

  def FrontendBlockBytes = p.frontendParameters.frontendBlockBytes
  def FetchBlockNumSlots = FrontendBlockBytes / MinInstBytes
  def FetchBlockOffsetWidth = log2Ceil(FetchBlockNumSlots)

  def DecodeWidth = 4
  def RobIdxWidth = 32

   //L1 To L2
  def ICacheToL2DataWidth = 256
  def ICacheToL2DataBytes = ICacheToL2DataWidth / 8
  def ICacheToL2AddrOffsetWidth = log2Ceil(ICacheToL2DataBytes)
  def ICacheToL2BeatAddrWidth = paddrWidth - ICacheToL2AddrOffsetWidth

}