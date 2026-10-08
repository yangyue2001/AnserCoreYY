package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._
import utils.HasCircularQueuePtrHelper

class ICacheWayLookup extends ICacheModule with HasCircularQueuePtrHelper {
  class WayLookupIO extends ICacheBundle{
    //val flush = Input(Bool())
    //val lookupPush = Flipped(Decoupled(new WayLookupPacket))
    //val fetchPop = Input(Bool())
    //val fetchEntry = Output(new WayLookupPacket)
    //val icacheRefill = Flipped(Valid(new IcacheRefillReq))
  }

  val io = IO(new WayLookupIO)
  /*
//alias
  val flush = io.flush
  val pushValid = io.lookupPush.fire
  val lookupPush = io.lookupPush.bits
  val fetchPop = io.fetchPop
  val icacheRefillValid = io.icacheRefill.valid
  val icacheRefill = io.icacheRefill.bits

//FIFO
  val entries = Reg(Vec(WayLookupSize, new WayLookupEntry))
  val exceptEntry = RegInit(0.U.asTypeOf(new WayLookupExceptionEntry))
  val pushPtr = RegInit(0.U.asTypeOf(new WayLookupPtr))
  val popPtr = RegInit(0.U.asTypeOf(new WayLookupPtr))
  val full = isFull(pushPtr, popPtr)
  val empty = isEmpty(pushPtr, popPtr)

  when(flush){
    pushPtr := 0.U.asTypeOf(new WayLookupPtr)
  }.elsewhen(pushValid){
    pushPtr := pushPtr + 1.U
  }

  when(flush){
    popPtr := 0.U.asTypeOf(new WayLookupPtr)
  }.elsewhen(fetchPop){
    popPtr := popPtr + 1.U
  }

  val exceptEntryValid = exceptEntry.except.pageFault || exceptEntry.except.accessFault
  when(flush){
    exceptEntry := 0.U.asTypeOf(new WayLookupExceptionEntry)
  }.elsewhen(pushValid && !exceptEntryValid){
    exceptEntry.ptr := pushPtr.value
    exceptEntry.except := lookupPush.except
  }

  entries.zipWithIndex.foreach{ case(entry, idx) =>
    val pushEn = pushValid && (pushPtr.value === idx.U)
    when(pushEn){
      entry.itlb := lookupPush.lookup.itlb
    }

    entry.tag.zipWithIndex.foreach{ case(tag, idx) =>
      when(pushEn){
        tag.vSetIdx := lookupPush.lookup.tag(idx).vSetIdx
      }

      val refillHit = (tag.vSetIdx === icacheRefill.setIdx) && icacheRefillValid

      when(pushEn){
        tag.wayMask := lookupPush.lookup.tag(idx).wayMask
      }.elsewhen(refillHit){
        tag.wayMask := tag.wayMask ^ icacheRefill.wayMask
      }
    }
  }

  val popHitExcept = popPtr.value === exceptEntry.ptr
  val entryExcept = Wire(new WayLookupException)
  entryExcept.pageFault := popHitExcept && exceptEntry.except.pageFault
  entryExcept.accessFault := popHitExcept && exceptEntry.except.accessFault

//Output
  val popEntryPack = Wire(new WayLookupPacket)
  popEntryPack.lookup := entries(popPtr.value)
  popEntryPack.except := entryExcept

  io.lookupPush.ready := !full
  io.fetchEntry := Mux(empty,lookupPush ,popEntryPack)


//---------------------assert-----------------------------------//
 assert(!(empty&&fetchPop && !pushValid), "when wayLookup empty pop ")

 */
}