package yanqihu.frontend.bpu

import chisel3._
import chisel3.util._


import yanqihu.frontend.FtqToBpuIO
import yanqihu.frontend.BpuToFtqIO
import yanqihu.frontend.bpu.phr._
import yanqihu.frontend.bpu.ftb._
import yanqihu.frontend.bpu.ittage._
import yanqihu.frontend.bpu.tage._
import yanqihu.frontend.bpu.ras._

class Bpu extends BpuModule {
   class BpuIO extends BpuBundle {
    val ctrl = Input(new BpuCtrl)
    val resetVector = Input(UInt(PcWidth.W))
    val fromFtq = Flipped(new FtqToBpuIO)
    val toFtq = new BpuToFtqIO
  }
  val io: BpuIO = IO(new BpuIO)
/*
//-----------------------alias------------------------------
  val redirectValid = io.fromFtq.redirect.valid
  val redirect = io.fromFtq.redirect.bits
  val trainValid = io.fromFtq.train.valid
  val train = io.fromFtq.train.bits
  //val ftqPtr = io.fromFtq.bpuPtr
  val ftqReady = false.B

  //-----------------------module-----------------------------
  val phr = Module(new Phr)
  val ftb = Module(new Ftb)
  val ittage = Module(new Ittage)
  val tage = Module(new Tage)
  val ras = Module(new Ras)

  //-----------------------wire-------------------------------
  val s0Fire = Wire(Bool())
  val s1Fire = Wire(Bool())
  val s2Fire = Wire(Bool())
  val s3Fire = Wire(Bool())

  val s1Ready = Wire(Bool())
  val s2Ready = Wire(Bool())
  val s3Ready = Wire(Bool())

  val s1Flush = Wire(Bool())
  val s2Flush = Wire(Bool())
  val s3Flush = Wire(Bool())

  val s1OutValid = Wire(Bool())
  val s2OutValid = Wire(Bool())

  val s1Taken    = Wire(Bool())
  val s2Taken    = Wire(Bool())

  val s1PredTarget = Wire(UInt(PcWidth.W))
  val s2OverrideTarget = Wire(UInt(PcWidth.W))
  val s2Override = Wire(Bool())



  //-----------------------reg--------------------------------
  private val s1Pc = RegEnable(s0Pc, s0Fire)
  private val s2Pc = RegEnable(s1Pc, s1Fire)

  private val s1Valid = RegInit(false.B)
  private val s2Valid = RegInit(false.B)

//-----------------------predirect--------------------------------//
  //-----------------------------s0-------------------------
  val s0Valid = RegInit(false.B)
  val s0OutValid = Wire(Bool())
  val s0Pc = Wire(0.U(PcWidth.W))
  val s0Stall = Wire(Bool())

  //-----------------------pc gen-----------------------------
  //s0Stall := ittage.io.ready && ftb.io.ready && tage.io.ready
  val resetDown = ftb.io.resetDown && tage.io.resetDown && ittage.io.resetDown
  when(resetDown){
    s0Valid := true.B
  }

  val s0PcHold = RegEnable(s0Pc, s0Stall)
  val s0PcHoldValid = RegNext(!s0Stall, false.B)

  private val sequentialNextPc = (s1Pc + FetchBlockNumSlots.U)
  s0Pc := MuxCase(sequentialNextPc, Seq(
    redirectValid -> redirect.target,  // 优先级最高
    s2Override    -> s2OverrideTarget   ,
    s1Taken       -> s1PredTarget  ,
    s0PcHoldValid -> s0PcHold      ,
  ))

  s0OutValid := s0Valid && !s0Stall
  s0Fire := s0OutValid && s1Ready

  val ftbPredictReq = Wire(new FtbPredReq)
  ftbPredictReq.pc := s0Pc
  ftb.io.predictReq.valid := s0OutValid
  ftb.io.predictReq := ftbPredictReq

  val tagePredictReq = Wire(new TagePredReq)
  tage.io.predictReq.valid := s0OutValid
  tagePredictReq.pc := s0Pc
  tagePredictReq.predPhr := false.B

  val ittagePredictReq = Wire(new IttagePredReq)
  ittage.io.predictReq.valid := s0OutValid
  ittagePredictReq.pc := s0Pc
  ittagePredictReq.predPhr := false.B

  //-----------------------------s1-------------------------

  when(s0Fire) {s1Pc := s0Pc}
  when(s1Ready) {s1Valid := s0OutValid}

  s1Flush := redirectValid || s2Override
  s1OutValid := s1Valid && !s1Flush

  s1Ready := !s1Valid || s2Ready  || s1Flush
  s1Fire := s1OutValid && s2Ready

  /*
  val uftbHit   = uftbPrediction.hit.reduce(_ || _)
  val uftbTaken = uftbPrediction.taken.reduce(_ || _)
  uftb.io.s1Pc := s1Pc
  s1Taken := s1Fire && uftbHit
  s1PredTarget := Mux(uftbTaken, uftbPrediction.target, uftbPrediction.fallThrough)
  */

  ftb.io.s1Fire := s1Fire
  ftb.io.s1Pc := s1Pc
  tage.io.s1Fire := s1Fire
  //ittage.io.s1Fire := s1Fire
  //-----------------------------s2--------------------------
  val s1ToS2Target = Reg(UInt(PcWidth.W))

  val ftbPrediction = ftb.io.prediction
  val tagePrediction = tage.io.prediction
  val ittagePrediction = ittage.io.prediction
  val rasPrediction = ras.io.prediction

  when(s2Ready) {s2Valid := s1OutValid}

  s2Flush := redirectValid
  s2OutValid := s2Valid  && !s2Flush
  s2Fire := s2OutValid && ftqReady
  s2Ready := (!s2Valid || ftqReady)

  val s2FtbHit = ftbPrediction.branchHit || ftbPrediction.tailHit

  val tailIsReturn = ftbPrediction.jmpInfo.ret
  val tailIsCall = ftbPrediction.jmpInfo.call
  val tailIsJalr = ftbPrediction.jmpInfo.jalr
  val tailIsUnCondJump = ftbPrediction.tailShare
  val tailNeedIttage = tailIsJalr && !tailIsReturn

  val tailTaken = ftbPrediction.tailHit && (tailIsUnCondJump || tagePrediction(1) )
  val branchTaken = ftbPrediction.branchHit && tagePrediction(0)

  val tailPredTarget = Mux1H(Seq(
    tailIsReturn -> rasPrediction.topRetAddr,
    (tailNeedIttage && ittagePrediction.taken) -> ittagePrediction.target,
    ((tailNeedIttage && ittagePrediction.taken) || !tailIsJalr) -> ftbPrediction.tailTarget
  ))

  val s2PredTarget = MuxCase(ftbPrediction.fallThroughTarget, Seq(
    branchTaken -> ftbPrediction.branchTarget,
    tailTaken -> tailPredTarget
  ))

  val s2SequentialTarget = (s2Pc + FetchBlockNumSlots.U)

  val s2Target = Mux(s2FtbHit, s2PredTarget, s2SequentialTarget)

  s2Override := s2Fire && (s2Target =/= s1ToS2Target)
  s2OverrideTarget := s2Target


  ftb.io.s2Pc := s2Pc
  //ras.io.specUpdate.valid := s2Fire && (tailIsReturn || tailIsCall)
  //ras.io.specUpdate.bits.isPush := tailIsCall
  //ras.io.specUpdate.bits.isPop := tailIsReturn
  //ras.io.specUpdate.bits.pushPc := Mux(ftbPrediction.jmpInfo.rviOutBlock, ftbPrediction.fallThroughTarget+2.U, ftbPrediction.fallThroughTarget)

  //-----------------------connect------------------------
  val bpuMeta = Wire(new BpuMeta)
  bpuMeta.ftb := ftb.io.meta
  bpuMeta.tage := tage.io.meta
  bpuMeta.ittage := tage.io.meta
  bpuMeta.ras := tage.io.meta
  bpuMeta.phr := phr.io.meta
 */
}