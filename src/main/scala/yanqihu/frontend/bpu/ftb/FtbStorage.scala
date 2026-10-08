package yanqihu.frontend.bpu.ftb

import chisel3._
import chisel3.util._
import utils.SinglePortSRAMTemplate

class FtbStorage  extends FtbModule {
  class FtbStorageIO extends FtbBundle {
    val predReadReq = Flipped(Valid(new FtbReadReq))
    val trainReadReq = Flipped(Valid(new FtbReadReq))
    val trainWriteReq = Flipped(Valid(new FtbWriteReq))
    val predictReadResp = Output(new FtbReadResp)
    val trainReadResp = Output(new FtbReadResp)
    val sramResetDone: Bool = Output(Bool())
  }
  val io = IO(new FtbStorageIO)
  //alias
  val predictReadReqValid = io.predReadReq.valid
  val predictReadReq = io.predReadReq.bits

  val trainReadReqValid = io.trainReadReq.valid
  val trainReadReq = io.trainReadReq.bits

  val trainWriteReqValid = io.trainWriteReq.valid
  val trainWriteReq = io.trainWriteReq.bits

  val tagSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new FtbTagInfo,
        set = NumSets,
        shouldReset = true,
        holdRead = true,
      ))
    }
  }

  val slotsSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new FtbSlotInfo,
        set = NumSets,
        shouldReset = true,
        holdRead = true,
      ))
    }
  }

  (0 until NumBanks).foreach { bankIdx =>
    val predictRead = predictReadReqValid && predictReadReq.bankMask(bankIdx)
    val trainRead = trainReadReqValid && trainReadReq.bankMask(bankIdx)
    val readValid = predictRead || trainRead
    (0 until NumWays).foreach { wayIdx =>
      val tag = tagSram(bankIdx)(wayIdx)
      val slots = slotsSram(bankIdx)(wayIdx)

      val writeValid = trainWriteReqValid && trainWriteReq.bankMask(bankIdx) && trainWriteReq.wayMask(wayIdx)

      tag.io.en := readValid || writeValid && trainWriteReq.tagEn
      tag.io.wen := writeValid
      tag.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      tag.io.wdata := trainWriteReq.entry.tag

      slots.io.en := readValid || writeValid && trainWriteReq.slotEn
      slots.io.wen := writeValid
      slots.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      slots.io.wdata := trainWriteReq.entry.slots


      assert(
        PopCount(VecInit(predictRead, trainRead , writeValid)) <= 1.U,
        "predictRead, trainRead and writeValid must be mutually exclusive "
      )
    }
  }

  val tagRead = tagSram.map{ bank =>
    bank.map(_.io.rdata)
  }
  val slotsRead = slotsSram.map{ bank =>
    bank.map(_.io.rdata)
  }

  val predReadBankSel = RegEnable(predictReadReq.bankMask, predictReadReqValid)
  val trainReadBankSel = RegEnable(trainReadReq.bankMask, trainReadReqValid)

  val predictReadResp = Wire(new FtbReadResp)
  val trainReadResp = Wire(new FtbReadResp)

  predictReadResp.entries.zipWithIndex.foreach{ case(entry, wayIdx) =>
    entry.tag := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> tagRead(bankIdx)(wayIdx)
      }
    )
    entry.slots := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> slotsRead(bankIdx)(wayIdx)
      }
    )
  }

  trainReadResp.entries.zipWithIndex.foreach{ case(entry, wayIdx) =>
    entry.tag := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> tagRead(bankIdx)(wayIdx)
      }
    )
    entry.slots := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> slotsRead(bankIdx)(wayIdx)
      }
    )
  }

  io.sramResetDone := tagSram(0)(0).io.resetDown
  io.predictReadResp := predictReadResp
  io.trainReadResp := trainReadResp

}
