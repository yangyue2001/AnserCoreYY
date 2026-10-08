package yanqihu.frontend.ifu.predecode

import chisel3._
import chisel3.util._
import yanqihu.frontend.ifu.IfuBundle
import yanqihu.frontend.ifu.IfuModule
import yanqihu.frontend.FrontendHelper

import utils.LowMask
import yanqihu.frontend.CfiAttribute

class PreDecode extends IfuModule with PreDecodeHelper with FrontendHelper {
  class PreDecodeIO extends IfuBundle{
    val req = Input(new PreDecodeReq)
    val flush = Input(Bool())
    val fire = Input(Bool())
    val preDecodeInfo = Output(new PredecodeInfo)
  }
  val io = IO(new PreDecodeIO)

//-------------------------alias-----------------------------//
  val instReq = io.req
  val inst = instReq.inst
  val fire = io.fire
  val flush = io.flush

//---------------------------decode--------------------------//
  val lastIsHalfRviReg = RegInit(false.B)
  val lastHalfRviReg = Reg(UInt(16.W))

  val mayBeRvc = Wire(Vec(FetchBlockNumSlots, Bool()))
  val isRvc = Wire(Vec(FetchBlockNumSlots, Bool()))
  val instEnd = Wire(Vec(FetchBlockNumSlots, Bool()))
  val rawInst = Wire(Vec(FetchBlockNumSlots, UInt(32.W)))

  instEnd(0) := lastIsHalfRviReg || mayBeRvc(0)
  isRvc(0) := mayBeRvc(0) && !lastIsHalfRviReg
  rawInst(0) := Mux(
    lastIsHalfRviReg,
    Cat(inst(0), lastHalfRviReg),
    Cat(0.U(16.W), inst(0))
  )

  for(i <- 1 until FetchBlockNumSlots){
    val instStart  = instEnd(i-1)
    instEnd(i) := !instStart || mayBeRvc(i)
    isRvc(i) := mayBeRvc(i) && instStart
    rawInst(i) := Mux(
      instStart,
      Cat(0.U(16.W), inst(i)),
      Cat(inst(i), inst(i-1))
    )
  }

  for(i <- 0 until FetchBlockNumSlots){
    mayBeRvc(i) := isRVC(inst(i))
  }

  val cfiAttribute = rawInst.zip(isRvc).map{case (inst, rvc) =>
    getCfiType(inst, rvc)
  }

  when(fire || flush){
    lastIsHalfRviReg := !(flush || Mux1H(preDecodeOffsetOH, instEnd))
  }

  when(fire){
    lastHalfRviReg := Mux1H(preDecodeOffsetOH, inst)
  }

  //-----------------------check---------------------------------------//
  val predEndOffsetOH = UIntToOH(instReq.predEndOffset, FetchBlockNumSlots)
  val predFetchBlockEndPc = instReq.startPc + instReq.predEndOffset

  val jalVec = cfiAttribute.map(_.jal)
  val jalrVec = cfiAttribute.map(_.jalr)
  val retVec = cfiAttribute.map(_.ret)
  val takenVec = jalVec.zipWithIndex.map{case(jal, idx)=>
    (jal || jalrVec(idx)) && instEnd(idx)
  }
  val firstTakenOffsetOH = PriorityEncoderOH(takenVec)
  val firstTakenOffset = OHToUInt(firstTakenOffsetOH)
  val firstTakenCfiAttribute = Mux1H(firstTakenOffsetOH, cfiAttribute)
  val firstTakenIsRvc = Mux1H(firstTakenOffsetOH,isRvc)
  val firstTakenEndPc = instReq.startPc + firstTakenOffset
  val firstTakenStartPc = Mux(firstTakenIsRvc, firstTakenEndPc, firstTakenEndPc-1.U)

  val firstTakenTarget = Mux(
    firstTakenCfiAttribute.jal,
    firstTakenStartPc + getJalOffset(Mux1H(firstTakenOffsetOH, rawInst), Mux1H(firstTakenOffsetOH,isRvc)),
    instReq.rasTopAddr
  )


  val endOffsetCfiAttribute = Mux1H(predEndOffsetOH, cfiAttribute)
  val endOffsetIsRvc = Mux1H(predEndOffsetOH,isRvc)
  val endOffsetStartPc = Mux(endOffsetIsRvc, predFetchBlockEndPc, predFetchBlockEndPc-1.U)

  val endOffsetIsNotCfi = !(endOffsetCfiAttribute.branch || endOffsetCfiAttribute.jal || endOffsetCfiAttribute.jalr)
  val branchOffset = getBrOffset(Mux1H(predEndOffsetOH, rawInst), endOffsetIsRvc)
  val jalOffset = getJalOffset(Mux1H(predEndOffsetOH, rawInst), endOffsetIsRvc)
  val endOffsetTarget = endOffsetStartPc + Mux(endOffsetCfiAttribute.branch, branchOffset, jalOffset)

  val (fallThroughCorssPage, pageBoundedEndOffset) = getPageEndSlot(instReq.startPc)
  val pageBoundedEndOffsetMask = LowMask(UIntToOH(pageBoundedEndOffset, FetchBlockNumSlots),true)

  val fallThroughTarget = Mux(
    fallThroughCorssPage,
    fallThroughPagePc(instReq.startPc),
    instReq.startPc + FetchBlockNumSlots.U
  )

  val fallThroughOffset = Mux(
    fallThroughCorssPage,
    pageBoundedEndOffset,
    (FetchBlockNumSlots-1).U
  )

  val pageBoundedTakenVec = takenVec.zipWithIndex.map{case(taken, idx)=>
    taken && (!fallThroughCorssPage || pageBoundedEndOffsetMask(idx))
  }

  val predTakenButNotCfi = instReq.predTaken && (!Mux1H(predEndOffsetOH, instEnd) || endOffsetIsNotCfi)
  val predTargetWrong = instReq.predTaken && (endOffsetCfiAttribute.branch || endOffsetCfiAttribute.jal) && instReq.predTarget =/= endOffsetTarget
  val hasEarlierTakenCfi = (LowMask(predEndOffsetOH) & VecInit(takenVec).asUInt).orR
  val predNotTakenButTaken = !instReq.predTaken && Mux1H(predEndOffsetOH, takenVec)

  val redirectValid = predTakenButNotCfi || predTargetWrong || hasEarlierTakenCfi || predNotTakenButTaken

  val redirectTarget = Mux(
    hasEarlierTakenCfi,
    firstTakenTarget,
    Mux(
      predTakenButNotCfi,
      Mux(pageBoundedTakenVec.reduce(_||_), firstTakenTarget, fallThroughTarget),
      endOffsetTarget
    )
  )

  val preDecodeOffset = Mux(
    hasEarlierTakenCfi,
    firstTakenOffset,
    Mux(
      predTakenButNotCfi,
      Mux(pageBoundedTakenVec.reduce(_||_), firstTakenOffset, fallThroughOffset),
      instReq.predEndOffset
    )
  )

  val preDecodeOffsetOH = UIntToOH(preDecodeOffset, FetchBlockNumSlots)

  //output
  val predecode = Wire(new PredecodeInfo)
  predecode.redirectValid := redirectValid
  predecode.endOffset := preDecodeOffset
  predecode.target := redirectTarget
  predecode.taken := predNotTakenButTaken || predTargetWrong || hasEarlierTakenCfi || (predTakenButNotCfi && pageBoundedTakenVec.reduce(_||_))
  val predecodeCfiAtr = Wire(new CfiAttribute)

}