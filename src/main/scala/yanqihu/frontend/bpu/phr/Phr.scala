package yanqihu.frontend.bpu.phr

import chisel3._
import chisel3.util._

import utils.Rota._

class Phr extends PhrModule with PhrHelper {
  class PhrIO extends PhrBundle{
    //predict
    val s0Fire = Input(Bool())
    val s1Fire = Input(Bool())
    val s2Fire = Input(Bool())
    val s0FoldedPhr = Output(new PhrAllFoldedHistories)
    val s1FoldedPhr = Output(new PhrAllFoldedHistories)
    val meta = Output(new PhrMeta)
    val s1SpecUpate = Flipped(Valid(new PhrUpdateReq))
    val s2Override = Flipped(Valid(new PhrOverrideReq))
    val s3Override = Flipped(Valid(new PhrOverrideReq))
    //redirect
    val phrRedirectReq = Flipped(Valid(new PhrRedirectReq))
    //train
    val trainMeta = Input(new PhrMeta)
    val trainFoldedPhr = Output(new PhrAllFoldedHistories)
  }
  val io = IO(new PhrIO)

  //alias
  val trainMeta = io.trainMeta
  val s1SpecUpateValid = io.s1SpecUpate.valid
  val s1SpecUpate = io.s1SpecUpate.bits

  val s2OverrideValid = io.s2Override.valid
  val s2Override = io.s2Override.bits

  val s3OverrideValid = io.s3Override.valid
  val s3Override = io.s3Override.bits

  val redirectValid = io.phrRedirectReq.valid
  val redirect = io.phrRedirectReq.bits

  //redirect
  val redirectMeta = redirect.restoreMeta
  val redirectOverride = redirect.overrideReq
  val redirectUpdateHash = computePhrUpdate(redirectOverride.update)
  val redirectShamt = redirectUpdateHash(Shamt-1, 0)
  val redirectLowBits = Mux(redirect.overrideReq.taken, redirectUpdateHash(PathHashHighWidth, Shamt) ^ redirectMeta.lowBits,redirectMeta.lowBits)

  val redirectPhrRead = Cat(readLogicalPhr(phr,redirectMeta.ptr)(MaxHistLens-1, PathHashHighWidth), redirectMeta.lowBits)
  val redirectFoldedPhr = PhrAllFoldedHistories.foldFromPhr(redirectPhrRead)
  val redirectUpdatePhrAllFoldedHistories = Mux(redirectOverride.taken, redirectFoldedPhr.update(redirectPhrRead,redirectUpdateHash), redirectFoldedPhr)
  val redirectUpdatePtr = Mux(redirectOverride.taken, redirectMeta.ptr+1.U, redirectMeta.ptr)
  //s0
  //ptr update
  val ptrUpdateEn = s1SpecUpateValid || s2OverrideValid || s3OverrideValid || redirectValid
  //phr update
  val phrLowBitsUpdateEn = s1SpecUpateValid || s2OverrideValid || s3OverrideValid || redirectValid
  val phrShamtUpdateEn = s1SpecUpateValid || s2OverrideValid && s2Override.taken ||
  s3OverrideValid && s2Override.taken || redirectValid && redirect.overrideReq.taken
  //fold phr update
  val foldPhrUpdateEn = s1SpecUpateValid || s2OverrideValid || s3OverrideValid || redirectValid

  val phrNextPtr = MuxCase(s1UpdatePtr, Seq(
    redirectValid   -> redirectUpdatePtr,
    s3OverrideValid -> s3UpdatePtr,
    s2OverrideValid -> s2UpdatePtr,
  ))

  val phrLowBits = MuxCase(s1UpdateLowBits,Seq(
    redirectValid   -> redirectLowBits,
    s3OverrideValid -> s3UpdateLowBits,
    s2OverrideValid -> s2UpdateLowBits,
  ))

  val phrShamt = MuxCase(s1UpdateShamt,Seq(
    redirectValid   -> redirectShamt,
    s3OverrideValid -> s3UpdateShamt,
    s2OverrideValid -> s2UpdateShamt,
  ))
  val phrWritePtr = MuxCase(phrPtr.value, Seq(
    redirectValid   -> redirectMeta.ptr.value,
    s3OverrideValid -> s3Meta.ptr.value,
    s2OverrideValid -> s2Meta.ptr.value,
  ))

  val s0PhrAllFoldedHistories = Wire(new PhrAllFoldedHistories)
  s0PhrAllFoldedHistories := MuxCase(s1PhrAllFoldedHistories, Seq(
    redirectValid   -> redirectUpdatePhrAllFoldedHistories,
    s3OverrideValid -> s3UpdatePhrAllFoldedHistories,
    s2OverrideValid -> s2UpdatePhrAllFoldedHistories,
    s1SpecUpateValid-> s1UpdatePhrAllFoldedHistories
  ))

  //s1
  val phrPtr = RegInit(0.U.asTypeOf( new PhrPtr(PhrHistoryChunks)))
  val phr = RegInit(VecInit(Seq.fill(PhrHistoryChunks)(UInt(Shamt.W))))
  val s1PhrAllFoldedHistories = Reg(new PhrAllFoldedHistories)

  when(ptrUpdateEn){
    phrPtr := phrNextPtr
  }

  when(phrLowBitsUpdateEn){
    val phrLowBitsChunks = phrLowBits.asTypeOf(Vec(PathHashHighChunks, UInt(Shamt.W)))
    phr.zipWithIndex.foreach{ case(chunk, chunksIdx) =>
      val numChunks = PhrHistoryChunks
      (0 until PathHashHighChunks).foreach{ wchunkIdx =>
        val idx = Math.floorMod(chunksIdx+1+wchunkIdx, numChunks)
        when(idx.U === phrWritePtr){
          chunk := phrLowBitsChunks(wchunkIdx)
        }
      }
    }
  }

  when(phrShamtUpdateEn){
    phr(phrWritePtr) := phrShamt
  }

  when(foldPhrUpdateEn){
    s1PhrAllFoldedHistories := s0PhrAllFoldedHistories
  }

  val s1Meta = Wire(new PhrMeta)
  s1Meta.ptr := phrPtr
  s1Meta.lowBits := s1LogicalPhr(PathHashHighWidth, 0)

  val s1UpdateHash = computePhrUpdate(s1SpecUpate)
  val s1LogicalPhr = readLogicalPhr(phr, s1Meta.ptr)
  val s1UpdatePhrAllFoldedHistories = s1PhrAllFoldedHistories.update(s1LogicalPhr, s1UpdateHash)
  val s1UpdateShamt = s1UpdateHash(Shamt-1, 0)
  val s1UpdateLowBits = s1Meta.lowBits ^ s1UpdateHash(PathHashWidth,Shamt)
  val s1UpdatePtr = s1Meta.ptr +1.U

  //s2
  val s2Meta = RegEnable(s1Meta, io.s1Fire)
  val s2PhrAllFoldedHistories = RegEnable(s1PhrAllFoldedHistories, io.s1Fire)
  val s2UpdateHash = computePhrUpdate(s2Override.update)
  val s2LogicalPhr = readLogicalPhr(phr, s2Meta.ptr)
  val s2UpdatePhrAllFoldedHistories = Mux(s2Override.taken,s2PhrAllFoldedHistories.update(s2LogicalPhr, s2UpdateHash), s2PhrAllFoldedHistories)

  val s2UpdateShamt = s2UpdateHash(Shamt-1, 0)
  val s2UpdateLowBits = Mux(s2Override.taken, s2Meta.lowBits ^ s2UpdateHash(PathHashWidth,Shamt), s2Meta.lowBits)
  val s2UpdatePtr = Mux(s2Override.taken, s2Meta.ptr+1.U, s2Meta.ptr)
  //s3
  val s3Meta = RegEnable(s2Meta, io.s2Fire)
  val s3PhrAllFoldedHistories = RegEnable(s2PhrAllFoldedHistories, io.s2Fire)
  val s3UpdateHash = computePhrUpdate(s3Override.update)
  val s3LogicalPhr = readLogicalPhr(phr, s3Meta.ptr)
  val s3UpdatePhrAllFoldedHistories = Mux(s3Override.taken,s3PhrAllFoldedHistories.update(s3LogicalPhr, s3UpdateHash), s3PhrAllFoldedHistories)

  val s3UpdateShamt = s3UpdateHash(Shamt-1, 0)
  val s3UpdateLowBits = Mux(s3Override.taken, s3Meta.lowBits ^ s3UpdateHash(PathHashWidth,Shamt), s3Meta.lowBits)

  val s3UpdatePtr = Mux(s3Override.taken, s3Meta.ptr+1.U, s3Meta.ptr)


//**************train**************************
  val trainPhrRead = Cat(readLogicalPhr(phr, trainMeta.ptr)(MaxHistLens-1, PathHashHighWidth), trainMeta.lowBits)
  val trainFoldedPhr = PhrAllFoldedHistories.foldFromPhr(trainPhrRead)

  //output
  io.s0FoldedPhr := s0PhrAllFoldedHistories
  io.trainFoldedPhr := trainFoldedPhr
  io.meta := s3Meta
}
