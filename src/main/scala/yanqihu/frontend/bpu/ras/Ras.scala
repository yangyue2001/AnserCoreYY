package yanqihu.frontend.bpu.ras

import chisel3._
import chisel3.util._

class Ras extends RasModule {
  class RasIO extends RasBundle{
    val specPop = Input(Bool())
    val prediction = Output(new RasPrediction)
    val specPush = Flipped(Valid(new RasUpdateReq))
    val meta = Output(new RasMeta)
    val recover = Flipped(Valid(new RasRecoverReq))
  }
  val io = IO(new RasIO)
  //------------------------alias--------------------------
  val specPushValid = io.specPush.valid
  val specPush = io.specPush.bits
  val specPop = io.specPop
  val recoverValid = io.recover.valid
  val recover = io.recover.bits

  val stack = Reg(Vec(NumEntrys, UInt(pcWidth.W)))
  val sp = Reg(new RasPtr(NumEntrys))

/*
  when(recover.valid) {
    sp := recover.bits.sp
  }.elsewhen(specUpdate.valid){
    when(specUpdate.bits.isPush){
      sp := sp +1.U
    }.otherwise{
      sp := sp -1.U
    }
  }

  when(recover.valid) {
    stack(recover.bits.sp) := recover.bits.topAddr
  }.elsewhen(specUpdate.valid){
    when(specUpdate.bits.isPush){
      stack(sp) := specUpdate.bits.pushAddr
    }
  }

  //connect
  val meta       = Wire(new RasMeta(p))
  meta.sp := sp
  meta.topAddr := stack(sp)
  io.pred := stack(sp)
  */
}
