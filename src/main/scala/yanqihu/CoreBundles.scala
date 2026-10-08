package yanqihu

import chisel3._
import chisel3.util._
import yanqihu.frontend.ftq.FtqPtr
import yanqihu.frontend.IfuToBackendIO
/*
class BackendToFrontendIO extends YQHBundle{
  val frontendCsr = Output(new )
  val redirect = Valid(new )

}
*/

class FrontEndToMemIO extends YQHBundle{

}

class MemToFrontend extends YQHBundle{

}

class TopToFrontendIO extends YQHBundle{
  val resetVector = Output(UInt(PcWidth.W))
  val hartId = Output(UInt(HartIdWidth.W))
}


class BackendToFrontendIO extends YQHBundle{
  val redirect = new BackendRedirect
}

class FrontendToBackendIO extends YQHBundle {
    // Frontend -> Backend
    //val fromFtq     = new FtqToCtrlIO
    val fromIfu     = new IfuToBackendIO
    // Backend -> Frontend
    //val toFtq       = Flipped(new CtrlToFtqIO)
    val redirect = Flipped(new BackendRedirect)
  }



// FrontEnd <=> BackEnd
class BackendRedirect extends YQHBundle{
  val ftqPtr = new FtqPtr
  val cfiIndex = UInt(FetchBlockOffsetWidth.W)
  val target = UInt(PcWidth.W)
  val taken = Bool()
  val reason = RedirectCause()
}

class BackendFtqReadReq extends YQHBundle{
  val branchRead = new FtqPtr
  val expectRead = new FtqPtr
}

object RedirectCause extends ChiselEnum {
  val mispredict, replay  = Value
}


object TlbExcept extends ChiselEnum {
  val pageFault, accessFault = Value
}

object TlbCmd extends ChiselEnum{
  val read, write, exec = Value
}


class TlbEntry extends YQHBundle{
  val pbmt = UInt(2.W)
  //val asid =
  //val ppn =
  //val page_size =
  //val
}

class TlbReq extends YQHBundle{
  val vaddr = UInt(vaddrWidth.W)
  val cmd = TlbCmd()
}

class ITlbIO extends YQHBundle{
  val req = Valid(new TlbReq)
  val kill = Output(Bool())
  val resp = Input(new TlbResp)
}

class TlbResp extends YQHBundle{
  val exceptValid = Bool()
  val except = TlbExcept()
  val paddr = UInt(paddrWidth.W)
  val hit = Bool()
  val pbmt = UInt(2.W)
}


class PmpReq extends YQHBundle{
  val pAddr = UInt(paddrWidth.W)
}

class PmpResp extends YQHBundle{
  val ld     = Output(Bool())
  val st     = Output(Bool())
  val instr  = Output(Bool())
  val mmio   = Output(Bool())
  val atomic = Output(Bool())
}

class MemReq extends YQHBundle{
  val addr = UInt(paddrWidth.W)
  val id = UInt(3.W)
}

class MemResp extends YQHBundle{
  val data = UInt(ICacheToL2DataWidth.W)
  val id = UInt(3.W)
}