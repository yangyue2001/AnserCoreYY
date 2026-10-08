package yanqihu.mmu

import chisel3._
import chisel3.util._

import yanqihu.HasYQHParameters
import yanqihu.mmu.pmp.PmpParameters
import yanqihu.mmu.l1Tlb.L1TlbParameters
import yanqihu.mmu.l2Tlb.L2TlbParameters
import yanqihu.mmu.ptw.PtwParameters
import yanqihu.mmu.MmuConstant.ColtFactorWidth

case class MmuParameters(
  val pmpParameters: PmpParameters = PmpParameters(),
  val l1TlbParameters: L1TlbParameters = L1TlbParameters(),
  val l2TlbParameters: L2TlbParameters = L2TlbParameters(),
  val ptwParameters: PtwParameters = PtwParameters(),
)

trait HasMmuParameters extends HasYQHParameters {
  def mmuParameters = coreParams.mmuParameters

  def svMode = SvMode.Sv48
  def ppnWidth = paddrWidth - pageOffsetWidth
  def vpnWidth = svMode.Levels * MmuConstant.LevelVpnWidth
  def compressedPpnWidth = ppnWidth - ColtFactorWidth

  def levels = svMode.Levels
  def levelWidth = log2Ceil(levels)

  require(ppnWidth <= MmuConstant.PpnWidth)
}