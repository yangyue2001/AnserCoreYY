package yanqihu.frontend.bpu.ftb

import chisel3._
import chisel3.util._

import yanqihu.frontend.bpu.BpuTrain

class Ftb extends FtbModule with FtbHelper{
  class FtbIO extends FtbBundle {
    val s1Fire  = Input(Bool())
    val s1Pc    = Input(UInt(PcWidth.W))
    val s2Pc    = Input(UInt(PcWidth.W))
    val predictReq = Flipped(Decoupled(new FtbPredReq))

    val train = Flipped(Decoupled(new BpuTrain))
    val trainS1PC = Input(UInt(PcWidth.W))

    val prediction = Output(new FtbPrediction)
    val meta       = Output(new FtbMeta)
    val resetDown  = Output(Bool())
  }

  val io = IO(new FtbIO)
  //-----------------------alias------------------------------
  val s1Pc    = io.s1Pc
  val s2Pc    = io.s2Pc
  val s1Fire  = io.s1Fire

  val predictReqValid = io.predictReq.valid
  val predictReq = io.predictReq.bits
  val train = io.train.bits
  val trainValid = io.train.valid
  val trainFire = io.train.fire
  //----------------------memory------------------------------
  val ftb = Module(new FtbStorage)

//----------------------predirect-----------------------------
  //-----------------------s0---------------------------------//
  val readBankSel = UIntToOH(getBankIndex(predictReq.pc), NumBanks)
  val s0Stall = true.B
  ftb.io.predReadReq.valid := predictReqValid
  ftb.io.predReadReq.bits.bankMask := readBankSel
  ftb.io.predReadReq.bits.setIdx := getSetIndex(predictReq.pc)
  //-----------------------s1----------------------------------//
  val ftbRead = ftb.io.predictReadResp

  val wayHit = ftbRead.entries.map{ resp =>
      (resp.tag.tag === getTag(s1Pc)) && (resp.slots.branch.valid || resp.slots.tail.valid)
  }
  val ftbHitEntry = Mux1H(wayHit, ftbRead.entries)

  val s1branchSlot        = ftbHitEntry.slots.branch
  val s1tailSlot          = ftbHitEntry.slots.tail
  val s1jmpInfo           = ftbHitEntry.tag.jmpInfo

  //-----------------------s2------------------------------------//
  val s2BranchSlot       = RegEnable(s1branchSlot      , s1Fire)
  val s2TailSlot         = RegEnable(s1tailSlot        , s1Fire)
  val s2JmpInfo          = RegEnable(s1jmpInfo         , s1Fire)
  val s2WayHit           = RegEnable(VecInit(wayHit), s1Fire)

  val branchHit = s2BranchSlot.valid
  val tailHit = s2TailSlot.valid
  val fallThroughTarget = Mux(tailHit, s2Pc + s2TailSlot.offset + 1.U, s2Pc + FetchBlockNumSlots.U)
  val fallThroughOffset = Mux(tailHit, s2TailSlot.offset, (FetchBlockNumSlots-1).U)
  val branchTarget = getTarget(s2Pc, s2BranchSlot.tarStat, s2BranchSlot.lower)
  val tailCondTarget = getTarget(s2Pc, s2TailSlot.tarStat, s2TailSlot.lower(11, 0))
  val tailDirectTarget = getTarget(s2Pc, s2TailSlot.tarStat, s2TailSlot.lower)
  val tailTarget = Mux(s2TailSlot.share, tailCondTarget, tailDirectTarget)
//---------------------- train---------------------------------

  //--------------------------s0--------------------------------//
  val trainPc    = fetchBlockInfo.startAddr
  val trainFtbMeta = train.meta.ftb
  val fetchBlockInfo = train.fetchBlockInfo

  val ftbUpdate = trainFtbMeta.hit && fetchBlockInfo.mispredict &&
  !((fetchBlockInfo.endOffset === trainFtbMeta.brSlot.offset) && trainFtbMeta.brSlot.valid ||
   (fetchBlockInfo.endOffset === trainFtbMeta.tailSlot.offset) && trainFtbMeta.tailSlot.valid)

  val ftbAllocate = !trainFtbMeta.hit && fetchBlockInfo.mispredict
  val ftbAllocateValid = ftbAllocate && trainValid
  val ftbUpdateValid = ftbUpdate && trainValid

  val trainReadBank = getBankIndex(trainPc)

  val allocateReadBankConflict = trainReadBank === trainWriteBank
  val s0TrainFire = ftbUpdateValid || ftbAllocateValid && !allocateReadBankConflict

  val trainMetaBrSlot = trainFtbMeta.brSlot
  val trainMetaTailSlot = trainFtbMeta.tailSlot
  val newSlotBeforeBrSlot = fetchBlockInfo.endOffset < trainMetaBrSlot.offset
  val newSlotIsNotDirect = !trainMetaTailSlot.valid || (fetchBlockInfo.endOffset =/= trainMetaTailSlot.offset)

  val trainNewSlot = Wire(new BranchSlot)
  trainNewSlot.valid := true.B
  trainNewSlot.offset := fetchBlockInfo.endOffset
  trainNewSlot.lower := fetchBlockInfo.target(11, 0)
  val targetHigh = fetchBlockInfo.target(PcWidth-1, 12)
  val trainPcHigh = trainPc(PcWidth-1, 12)
  trainNewSlot.tarStat := Mux1H(Seq(
    (targetHigh === trainPcHigh) -> 0.U(2.W),
    (targetHigh > trainPcHigh) -> 1.U(2.W),
    (targetHigh < trainPcHigh) -> 2.U(2.W)
  ))

  val s0AllocateBrSlot = Mux(newSlotIsNotDirect, trainNewSlot, 0.U.asTypeOf(new BranchSlot))
  val s0UpdateBrSlot = Mux(trainMetaBrSlot.valid && !newSlotBeforeBrSlot, trainMetaBrSlot, trainNewSlot)

  val s0AllocateTailSlot = trainMetaTailSlot

  val s0UpdateTailSlot = Wire(new TailSlot)
  //s0UpdateTailSlot

  //val trainNewSlot = Wire(new )
  val s0TrainBrSlot = Mux(ftbAllocate, s0AllocateBrSlot, s0UpdateBrSlot)

  val s0TrainTailSlot = Mux(ftbAllocate, s0AllocateTailSlot, s0UpdateTailSlot)

  //s0TrainTailSlot := Mux(trainFtbMeta.)

  ftb.io.trainReadReq.bits.bankMask := UIntToOH(trainReadBank, NumBanks)
  ftb.io.trainReadReq.bits.setIdx := getSetIndex(trainPc)
  ftb.io.trainReadReq.valid := trainFire && ftbAllocate
//----------------------------s1--------------------------------//
  val s1TrainAllocateValid = RegNext(ftbAllocateValid, false.B)
  val s1TrainUpdateValid = RegNext(ftbUpdateValid, false.B)
  val s1TrainUpdateWayMask = RegEnable(trainFtbMeta.hitWay, ftbUpdateValid)
  val s1TrainBrSlot = RegEnable(s0TrainBrSlot, s0TrainFire)
  val s1TrainTailSlot = RegEnable(s0TrainTailSlot,s0TrainFire)

  val s1TrainPc = io.trainS1PC
  val trainWriteBank = getBankIndex(s1TrainPc)
  val trainS1PcTag = getTag(s1TrainPc)

  val trainReadResp = ftb.io.trainReadResp
  val emptyWay = trainReadResp.entries.map{resp =>
    !(resp.slots.branch.valid || resp.slots.tail.valid)
  }

  val trainReadHitWay = trainReadResp.entries.map{resp =>
    resp.tag.tag === trainS1PcTag
  }

  val trainWrite = Wire(new FtbWriteReq)
  trainWrite.bankMask := UIntToOH(trainWriteBank, NumBanks)
  trainWrite.setIdx := getSetIndex(s1TrainPc)
  trainWrite.tagEn := s1TrainAllocateValid
  trainWrite.slotEn := s1TrainAllocateValid || s1TrainUpdateValid
  //trainWrite.wayMask := Mux()

  ftb.io.trainWriteReq.valid := s1TrainAllocateValid || s1TrainUpdateValid
  ftb.io.trainWriteReq.bits.bankMask := UIntToOH(trainWriteBank, NumBanks)
  ftb.io.trainWriteReq.bits.setIdx := getSetIndex(s1TrainPc)
  //ftb.io.

//connect

  val prediction = Wire(new FtbPrediction)

  prediction.branchHit := branchHit
  prediction.tailHit := tailHit
  prediction.fallThroughTarget := fallThroughTarget
  prediction.branchTarget := branchTarget
  prediction.tailTarget := tailTarget
  prediction.fallThroughOffset := fallThroughOffset
  prediction.branchOffset := s2BranchSlot.offset
  prediction.tailOffset := s2TailSlot.offset

  val ftbMeta = Wire(new FtbMeta)
  ftbMeta.hit := branchHit || tailHit
  ftbMeta.hitWay := OHToUInt(s2WayHit)
  ftbMeta.brSlot := s2BranchSlot
  ftbMeta.tailSlot := s2TailSlot
//output
  io.prediction := prediction
  io.meta := ftbMeta
  io.resetDown := ftb.io.sramResetDone

  io.train.ready := !(ftbAllocateValid && allocateReadBankConflict)
}