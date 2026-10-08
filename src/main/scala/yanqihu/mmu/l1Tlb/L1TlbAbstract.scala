package yanqihu.mmu.l1Tlb

import chisel3._
import chisel3.util._
import yanqihu.mmu.MmuConstant.SatpAsidWidth

abstract class L1TlbBundle extends Bundle with HasL1TlbParameters
abstract class L1TlbModule extends Module with HasL1TlbParameters


abstract class L1TlbEntry extends L1TlbBundle{
  val valid = Bool()
  val ppn = UInt(ppnWidth.W)
  val vpn = UInt(vpnWidth.W)
  val pageFault = Bool()
  val accessFault = Bool()
  val g = Bool()
  val asid = UInt(SatpAsidWidth.W)
  val size = PageSize()
}

abstract class L1TlbReq extends L1TlbBundle{
  val vaddr = UInt(vaddrWidth.W)
}


abstract class L1TlbResp extends L1TlbBundle{
  val paddr = UInt(paddrWidth.W)
  val hit = Bool()
}



