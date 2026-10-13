package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._

class ICacheMissUnit extends ICacheModule{
  class ICacheMissUnitIO extends ICacheBundle{
    val flush = Input(Bool())
    val lookupReq = Flipped(Decoupled( new ICacheMissReq))
    val fetchReq = Flipped(Decoupled( new ICacheMissReq))
    //val fetchBypass =
    //val bypassResp = Valid
    val refill = Decoupled(new ICacheRefillReq)
  }

  val io = IO(new ICacheMissUnitIO)
//alias
  val flush = io.flush
  val lookupReq = io.lookupReq
  val fetchReq = io.fetchReq
  //val
  val mshrs = RegInit(VecInit.fill(NumMshrs)(0.U.asTypeOf(new MshrEntry)))
}