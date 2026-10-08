package yanqihu.frontend.ifu.ibuffer

import chisel3._
import chisel3.util._
import yanqihu.frontend.ftq.FtqPtr
import utils.Rota

class Ibuffer extends IBufferModule {
  class IbufferIO extends IBufferBundle{
    val flush = Input(Bool())
    val enq = Flipped(Decoupled(new IBufferReq))
    val deq = Vec(DecodeWidth, Valid(new InstPacket))
    val deqReady = Input(Bool())
  }
  val io = IO(new IbufferIO)
  //alias
  val flush = io.flush
  val enqValid = io.enq.valid
  val enqFire = io.enq.fire
  val enq = io.enq.bits
  val deqReady = io.deqReady
//-------------FIFO---------------------------------//
  val ibuffer = Reg(Vec(IBufferSize, new IBufferEntry))
  val enqBankPtr = RegInit(1.U(NumWriteBanks.W))
  val enqRowPtr = RegInit(1.U(WriteBankDepth.W))
  val deqBankPtr = RegInit(1.U(NumReadBanks.W))
  val deqRowPtr = RegInit(1.U(ReadBankDepth.W))
  val numEntries = RegInit(0.U(IBufferCountWidth.W))

  val NxtNumEntries = Wire(UInt(IBufferCountWidth.W))
  val ibufferEmpty = numEntries === 0.U

  when(flush){
    enqBankPtr := 1.U(NumWriteBanks.W)
  }
  when(flush){
    enqRowPtr := 1.U(WriteBankDepth.W)
  }
  when(flush){
    deqBankPtr := 1.U(NumReadBanks.W)
  }
  when(flush){
    deqRowPtr := 1.U(ReadBankDepth.W)
  }
  when(flush){
    numEntries := 0.U(IBufferCountWidth.W)
  }


  val readBank = Wire(Vec(ReadBankDepth, Vec(NumReadBanks, new IBufferEntry)))
  for(bank <- 0 until NumReadBanks){
    for(row <-0 until ReadBankDepth){
      readBank(bank)(row) := ibuffer(bank + row* NumReadBanks)
    }
  }

  val readWindows0 = Mux1H(deqRowPtr, readBank)
  val readWindows1 = Mux1H(deqRowPtr, Rota(readBank, 1, Rota.Left))
  val ibufferRead = Wire(Vec(NumReadBanks, new IBufferEntry))
  val ibufferReadValid = Wire(Vec(NumReadBanks, Bool()))
  val ibufferValid = numEntries =/= 0.U

  for(bank <- 0 until NumReadBanks){
    val readWindows = VecInit(readWindows0.toSeq.drop(bank) ++ readWindows1.toSeq.take(bank))
    ibufferRead(bank) := Mux1H(deqBankPtr, readWindows)
    ibufferReadValid(bank) := numEntries > bank.U
  }

  val deqFire = ibufferValid && deqReady
  val firstFtqPtr = Reg(new FtqPtr)
  val nxtFtqPtr= Wire(new FtqPtr)
  val endOffset = RegInit(0.U(FetchBlockOffsetWidth.W))
  val nxtEndOffset = Wire(UInt(FetchBlockOffsetWidth.W))

  when(flush){
    endOffset := 0.U(FetchBlockOffsetWidth.W)
  }

  when(enqFire && ibufferEmpty){
    firstFtqPtr := enq.ftqPtr
  }.elsewhen(deqFire) {
    firstFtqPtr := enq.ftqPtr
  }

  val readEndMask = VecInit(ibufferRead.zip(ibufferReadValid).map{case(entry, valid)=>
    entry.instEnd && valid
  }).asUInt

  val isLastInFetchBlockMask = VecInit(ibufferRead.zip(ibufferReadValid).map{case(entry, valid)=>
    entry.isLastInFetchBlock && valid
  }).asUInt

  val ibufferInst = Wire(Vec(DecodeWidth, new InstPacket))
  val ibufferInstValid = Wire(Vec(DecodeWidth, Bool()))
  val ibufferInstEndSlot = Wire(Vec(DecodeWidth, UInt(log2Ceil(NumReadBanks).W)))

  for(instIdx <- 0 until DecodeWidth){
    val firstSlotIdx = instIdx
    val lastSlotIdx = 2 * instIdx +1
    val candidateCount = lastSlotIdx - firstSlotIdx + 1
    // inst0: read0 ~ read1
    // inst1: read1 ~ read3
    // inst2: read2 ~ read5
    // ...
    val instWindows = ibufferRead.toSeq.drop(firstSlotIdx).take(candidateCount)
    val endsBeforeWindow =(
      if (firstSlotIdx == 0) {
        0.U
      } else {
        PopCount(readEndMask(firstSlotIdx - 1, 0))
      }
    )

    val candidateSelect = Wire(Vec(candidateCount, Bool()))
    val candidateInst = Wire(Vec(candidateCount, UInt(32.W)))
    val candidateFtqOffset = Wire(Vec(candidateCount, UInt(FtqSizeWidth.W)))
    val candidateSlotIdx = Wire(Vec(candidateCount, UInt(log2Ceil(NumReadBanks).W)))

    var endCount = endsBeforeWindow

    for( idx <- 0 until candidateCount){
      val slotIdx = firstSlotIdx + idx
      val isEnd = readEndMask(slotIdx)
      endCount = endCount + isEnd
      candidateSelect(idx) := isEnd && (endCount === (instIdx +1).U)

      candidateInst(idx) :=(
        if(idx == 0){
          Cat(0.U(16.W), instWindows(0).slot)
        } else {
          Mux(
            readEndMask(slotIdx-1),
            Cat(0.U(16.W), instWindows(idx).slot),
            Cat(instWindows(idx).slot, instWindows(idx-1).slot)
          )
        }
      )

      candidateFtqOffset(idx) :=(
        if(slotIdx == 0){
          0.U
        } else {
          PopCount(isLastInFetchBlockMask(slotIdx-1 , 0))
        }
      )

      candidateSlotIdx(idx) := slotIdx.U
    }

    ibufferInstValid(instIdx) := candidateSelect.reduce(_ || _)

    val endEntry = Mux1H(candidateSelect, instWindows)
    val instPacket = Wire(new InstPacket)

    instPacket.isLastInFetchBlock := endEntry.isLastInFetchBlock
    instPacket.ftqPtr := firstFtqPtr + Mux1H(candidateSelect, candidateFtqOffset)
    instPacket.inst := Mux1H(candidateSelect, candidateInst)


    ibufferInst(instIdx) := instPacket
    ibufferInstEndSlot(instIdx) := Mux1H(candidateSelect, candidateSlotIdx)
  }

  val lastConsumedSlot = Mux1H(Reverse(PriorityEncoderOH(Reverse(ibufferInstValid.asUInt))), ibufferInstEndSlot)

  val instPacket = Vec(DecodeWidth, Reg(new InstPacket))
  val instPacketValid = Vec(DecodeWidth, RegInit(false.B))
//output
  io.deq.zipWithIndex.foreach{case(deq, idx) =>
    deq.valid := instPacketValid(idx)
    deq.bits := instPacket(idx)
  }

}