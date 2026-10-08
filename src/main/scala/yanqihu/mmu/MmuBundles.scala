package yanqihu.mmu

import chisel3._
import chisel3.util._
import yanqihu.mmu.MmuConstant._


class SfenceBundle extends MmuBundle{
  val allVaddr = Bool()
  val vpn = UInt(vpnWidth.W)
  val allAsid = Bool()
  val asid = UInt(SatpAsidWidth.W)
}