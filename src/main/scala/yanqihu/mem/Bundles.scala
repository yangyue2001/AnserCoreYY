package yanqihu.mem

import chisel3._
import chisel3.util._
import yanqihu.YQHBundle


object MemCmd extends ChiselEnum {
  val read  = Value("b00".U(2.W))
  val write = Value("b01".U(2.W))
  val exec  = Value("b10".U(2.W))
}



class TlbReq extends YQHBundle{
  val vaddr = UInt(vaddrWidth.W)
  val cmd = MemCmd()
}

class TlbExcp extends YQHBundle{
  val pageFault = Bool()
  val accessFault = Bool()
}

class TlbResp extends YQHBundle{
  val paddr = UInt(paddrWidth.W)
  val miss = Bool()
  val excp = new TlbExcp
}


class PmpReq extends YQHBundle{
  val paddr = UInt(paddrWidth.W)
  val size = UInt(2.W)
  val cmd = MemCmd ()
}

class PmpResp extends YQHBundle{
  val accessFault = Bool()
  val mmio = Bool()
  val cacheable = Bool()
}