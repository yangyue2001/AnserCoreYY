package yanqihu.frontend

import chisel3._
import chisel3.util._

import yanqihu.frontend.bpu._
import yanqihu.frontend.ifu._
import yanqihu.frontend.ftq._
import yanqihu.frontend.ifu.predecode.PredecodeInfo

class BpuToFtqIO extends FrontendBundle{
  val s1Fetch = Decoupled(UInt(pcWidth.W))
  val s3Result = Decoupled(new BpuS3Result)
}

class FtqToBpuIO extends FrontendBundle{
  val redirect    = Valid(new BpuRedirect)
  val train       = Decoupled(new BpuTrain)
  val bpuPtr      = Output(new FtqPtr)
}

class FtqToIfuIO extends FrontendBundle{
  val s3Override = Valid(new FtqPtr)
  val lookUp = Decoupled(new FtqLookup)
  val fetch = Decoupled(new FtqFetch)
}


class IfuToFtqIO  extends FrontendBundle {
  val ftqPtr = Output(new FtqPtr)
  val preDecodeInfo = Valid(new PredecodeInfo)
}

class IfuToBackendIO extends FrontendBundle{
}


class FtqToBackendIO extends FrontendBundle{

}


class IfuToMmuIO extends FrontendBundle{
  //val itlbReq =
  //val kill
  //val pmp
}


class MmuToIfuIO extends FrontendBundle{
}


/* *** public const & type *** */
class CfiAttribute extends Bundle {
  val cfiType: UInt = UInt(2.W)
  val rasAction:  UInt = UInt(2.W)

  def isNone:        Bool = cfiType === 0.U(2.W)
  def isConditional: Bool = cfiType === 1.U(2.W)
  def isDirect:      Bool = cfiType === 2.U(2.W)
  def isIndirect:    Bool = cfiType === 3.U(2.W)
  // hasPop = isPop || isPushAndPop, hasPush = isPush || isPushAndPop
  def hasPop:  Bool = rasAction(0)
  def hasPush: Bool = rasAction(1)

  def needIttage: Bool = isIndirect && !hasPop
}


object FrontendExcept extends ChiselEnum{
  val pageFault, accessFault, hardwareError = Value
}
