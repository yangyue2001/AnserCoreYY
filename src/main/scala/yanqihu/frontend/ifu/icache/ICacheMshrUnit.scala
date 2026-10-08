package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._
import yanqihu.MemReq
import yanqihu.MemResp

class ICacheMissUnit extends ICacheModule{
  class ICacheMissUnitIO extends ICacheBundle{
    val flush = Input(Bool())
    val lookupReq = Vec(2, Flipped(Decoupled( new ICacheMissReq)))
    val fetchReq = Vec(2, Decoupled( new ICacheMissReq))
    //val fetchBypass =
    //val bypassResp = Valid
    val refill = Decoupled(new ICacheRefillReq)
    val memReq = Decoupled(new MemReq)
    val memResp = Decoupled(new MemResp)
  }

  val io = IO(new ICacheMissUnitIO)
//alias
  val flush = io.flush
  val lookupReq = io.flush
  //val
  val mshrs = VecInit.fill(NumMshrs){RegInit(0.U.asTypeOf(new MshrEntry))}
}