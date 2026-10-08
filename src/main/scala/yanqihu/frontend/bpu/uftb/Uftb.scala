package yanqihu.frontend.bpu.uftb

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.BpuParameters
import utils.SaturateCounter

class Uftb extends UftbModule with UftbHelper {
  class UftbIO extends UftbBundle {
    val s1Pc = Input(UInt(PcWidth.W))
    val prediction = Output(new UftbPrediction)
    val meta = Output(new UftbMeta)
    val train      = Flipped(Valid(new UftbTrainReq))
  }
/*
  val io = IO(new UftbIO)
  val predictPc = io.s1Pc
  val trainValid = io.train.valid
  val train = io.train.bits

//------------------------predirect-------------------------//
  val uftb = RegInit(VecInit(Seq.fill(NumWays)(0.U.asTypeOf(new UftbEntry))))
  val ctr = RegInit(
  VecInit.fill(NumWays)(
    VecInit.fill(NumBr)(
      SaturateCounter(CtrWidth, 0)
    )
  )
)
  val pcTag = getTag(predictPc)
  val wayHit = uftb.map{ entry =>
    entry.tag === pcTag
  }

  val hitBrSlot = Mux1H(wayHit, uftb.map(_.brSlot))
  val hitTailSlot = Mux1H(wayHit, uftb.map(_.tailSlot))
  val hitJalr = Mux1H(wayHit, uftb.map(_.isJalr))
  val hitFallThroughCarry = Mux1H(wayHit, uftb.map(_.fallThroughCarry))
  val hitFallThroughLower = Mux1H(wayHit, uftb.map(_.fallThroughLower))
  val hitCtr = Mux1H(wayHit, ctr)
  val branchCtr = hitCtr(0)
  val tailCtr = hitCtr(1)

  val fallThroughTarget = getFallThroughTarget(predictPc, hitFallThroughCarry, hitFallThroughLower)
  val branchTarget = getTarget(predictPc, hitBrSlot.tarStat, hitBrSlot.lower)
  val tailCondTarget = getTarget(predictPc, hitTailSlot.tarStat, hitTailSlot.lower(11, 0))
  val tailDirectTarget = getTarget(predictPc, hitTailSlot.tarStat, hitTailSlot.lower)
  val tailTarget = Mux(hitTailSlot.share, tailCondTarget, tailDirectTarget)

  val branchSlotTaken = hitBrSlot.valid && branchCtr.isPositive
  val tailSlotTaken = hitTailSlot.valid && ( hitTailSlot.share || tailCtr.isNegative)

  val taken = branchSlotTaken || tailSlotTaken
  val cfiOffset = Mux(branchSlotTaken, hitBrSlot.offset, hitTailSlot.offset)


//------------------train--------------------//
//train0
  val uftbHit = train.uftbMeta.brSlot.valid || train.uftbMeta.tailSlot.valid
  val alias = train.uftbMeta.isEqual(train.ftbMeta)

  val allocate = trainValid && (!uftbHit || alias) && train.s3Override

  val branchCtrUpdate = trainValid && !alias && train.uftbMeta.brSlot.valid
  val tailCtrUpdate = trainValid && !alias && train.uftbMeta.tailSlot.valid && !train.uftbMeta.tailSlot.share
  val update = Cat(branchCtrUpdate, tailCtrUpdate)
  val trainHitWay = uftb.map(_.tag === getTag(train.trainPc))
  val trainHitCtr = Mux1H(trainHitWay, ctr)
//train1
  val t1Update = RegEnable(update, trainValid)
  val t1Allocate = RegEnable(allocate, trainValid)
  val replaceWay = Wire(Vec(NumWays, Bool()))
  val t1HitWay = RegEnable(VecInit(trainHitWay), trainValid)
  val t1HitCtr = RegEnable(trainHitCtr, trainValid)

  ctr.zipWithIndex.foreach{case(ctr, wayIdx) =>

  }
  //Connect
  val prediction = Wire(new UftbPrediction)
  prediction.taken := taken
  prediction.cfiOffset := cfiOffset
  prediction.isJalr := hitTailSlot.valid && hitJalr
  prediction.target := tailTarget

  //Output
  io.prediction := prediction
*/
}