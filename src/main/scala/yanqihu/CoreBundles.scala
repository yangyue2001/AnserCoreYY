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
  val resetVector = Output(UInt(pcWidth.W))
  val hartId = Output(UInt(hartIdWidth.W))
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
  val target = UInt(pcWidth.W)
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



object PrivMode extends ChiselEnum{
  val uMode = Value("b00".U(2.W))
  val sMode = Value("b01".U(2.W))
  val mMode = Value("b11".U(2.W))
}