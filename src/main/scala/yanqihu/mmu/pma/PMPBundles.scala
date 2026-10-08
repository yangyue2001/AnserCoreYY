package yanqihu.mmu.pma

import chisel3._
import chisel3.util._
import yanqihu.YQHBundle

class PmpReq extends YQHBundle{
  val addr = UInt(paddrWidth.W)
}

class PmpResp extends YQHBundle {
  val ld     = Bool()
  val st     = Bool()
  val instr  = Bool()
  val mmio   = Bool()
  val atomic = Bool()
}
