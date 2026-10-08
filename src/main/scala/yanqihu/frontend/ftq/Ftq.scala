package yanqihu.frontend.ftq

import chisel3._
import chisel3.util._
import yanqihu.frontend.FtqToBpuIO
import yanqihu.frontend.BpuToFtqIO
import yanqihu.frontend.IfuToFtqIO
import yanqihu.frontend.FtqToIfuIO
import utils.HasCircularQueuePtrHelper
import yanqihu.frontend.bpu.ftb.TailSlot
import yanqihu.frontend.ftq.FtbState.predecode

class Ftq extends FtqModule with HasCircularQueuePtrHelper {
  class FtqIO extends FtqBundle {
//ftq <=> bpu
    val fromBpu = Flipped(new BpuToFtqIO)
    val toBpu = new FtqToBpuIO
// ftq <=> ifu
    val fromIfu = Flipped(new IfuToFtqIO)
    val toIfu = new FtqToIfuIO
// ftq <=> backend
  }

  val io = IO(new FtqIO)
//---------------alias----------------------------//
  val fromBpu = io.fromBpu
  //s1 Fetch
  val s1FetchValid = fromBpu.s1Fetch.valid
  val s1Fetch = fromBpu.s1Fetch.bits
  val s1FetchFire = fromBpu.s1Fetch.fire

  val s3ResultValid = fromBpu.s3Result.valid
  val s3Result = fromBpu.s3Result.bits
  val s3ResultFire = fromBpu.s3Result.fire
  //s3 Prediction
  val s3Prediction = s3Result.prediction
  val s3FetchValid = s3Result.s3FetchValid
  val s3FetchFire = s3ResultFire && s3FetchValid

  val s3Fetch = s3Result.s3FetchPC
  val s3Meta = s3Result.meta

  //predecode
  val fromIfu = io.fromIfu
  val preDecodeValid = fromIfu.preDecodeInfo.valid
  val preDecode = fromIfu.preDecodeInfo.bits

  //lookup
  val lookupFire = io.toIfu.lookUp.fire
  //fetch
  val fetchFire = io.toIfu.fetch.fire
  //commit
  val commitFire = io.toBpu.train.fire
//---------------FIFO----------------------------//
  val bpuPtr = RegInit(0.U.asTypeOf(new FtqPtr))
  val lookupPtr = RegInit(0.U.asTypeOf(new FtqPtr))
  val fetchPtr = RegInit(0.U.asTypeOf(new FtqPtr))
  val commitPtr = RegInit(0.U.asTypeOf(new FtqPtr))
  val ftqFlush = false.B
  val ftqFlushFtq = new FtqPtr

  val ftqEntries = RegInit(VecInit.fill(FtqSize)(0.U.asTypeOf(new FtqEntry)))


  when(ftqFlush){
    bpuPtr := ftqFlushFtq + 1.U
  }.elsewhen(s1FetchFire || s3FetchFire){
    bpuPtr := bpuPtr + 1.U
  }

  when(ftqFlush){
    lookupPtr := ftqFlushFtq + 1.U
  }.elsewhen(lookupFire){
    lookupPtr := lookupPtr + 1.U
  }

  when(ftqFlush){
    fetchPtr := ftqFlushFtq + 1.U
  }.elsewhen(fetchFire){
    fetchPtr := fetchPtr + 1.U
  }

  when(commitFire){
    commitPtr := commitPtr + 1.U
  }

  val full = isFull(bpuPtr, commitPtr)
  val flushMask = circularRangMask(ftqFlushFtq, bpuPtr)

  ftqEntries.zipWithIndex.foreach{case(entry, idx) =>
    val s3ResultEn = s3ResultFire && (s3Result.s3FtqPtr.value === idx.U)
    val commitEn = false.B
    val bpuFetchEn = (s1FetchFire || s3FetchFire) && (bpuPtr.value === idx.U)
    val preDecodeEn = preDecodeValid && (fromIfu.ftqPtr === idx.U)
    //val lookupEn = io.toIfu.lookUp.fire &&
    //val fetchEn = io.toIfu.fetch.fire
    when(flushMask(idx)){
      entry.valid := false.B
    }.elsewhen(commitEn || bpuFetchEn){
      //entry.valid := false.B
    }

  //spec meta
    when(s3ResultEn){
      entry.meta.ftbMeta.brSlot := s3Result.meta.ftb.brSlot
      entry.meta.ftbMeta.hitWay := s3Result.meta.ftb.hitWay

      entry.meta.tageMeta:= s3Result.meta.tage
      entry.meta.ittageMeta := s3Result.meta.ittage
      entry.spec.phrMeta := s3Result.meta.phr
      entry.spec.rasMeta := s3Result.meta.ras
    }

  //pred
    when(bpuFetchEn){
      entry.pred.startAddr := bpuFetchPc
    }



  }



  val lookupBypass = lookupPtr === bpuPtr
  val fetchParallel = lookupPtr === fetchPtr
  val bpuFetchPc = Mux(s3FetchValid, s3Fetch, s1Fetch)

  //lookup
  val lookupEntry = ftqEntries(lookupPtr.value)
  val lookupValid = lookupEntry.isLookup && lookupEntry.valid || lookupBypass && (s1FetchFire || s3FetchFire)
  val ftqLookup = Wire(new FtqLookup)
  ftqLookup.ftqPtr := Mux(lookupBypass, bpuPtr, lookupPtr)
  ftqLookup.lookupPc := Mux(lookupBypass, bpuFetchPc, lookupEntry.pred.startAddr)

  //fetch
  val fetchEntry = ftqEntries(fetchPtr.value)
  val fetchValid = fetchEntry.isFetch || fetchParallel && lookupValid
  val ftqFetch = Wire(new FtqFetch)
  ftqFetch.ftqPtr := Mux(fetchParallel && lookupBypass, bpuPtr, fetchPtr)
  ftqFetch.fetchPc := Mux(fetchParallel && lookupBypass, bpuFetchPc, fetchEntry.pred.startAddr)
  ftqFetch.parallel := fetchParallel

  //commit
  val commitEntry = ftqEntries(commitPtr.value)

//to BPU
  io.fromBpu.s1Fetch.ready := !full
  io.fromBpu.s3Result.ready := !full

//to IFU
  io.toIfu.s3Override.valid := s3Prediction.s3Override
  io.toIfu.s3Override.bits := s3Result.s3FtqPtr

  io.toIfu.lookUp.valid := lookupValid && !ftqFlush
  io.toIfu.lookUp.bits := ftqLookup
  io.toIfu.fetch.valid := fetchValid && !ftqFlush
  io.toIfu.fetch.bits := ftqFetch

//to Backend

//to MEM

}

