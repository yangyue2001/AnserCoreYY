package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._
import utils.SinglePortSRAMTemplate
import utils.SaturateCounter

class TageTable(tableIdx : Int)(implicit info: TageTableInfo) extends TageModule with TableHelper {
  class TageTableIO extends TageBundle {
    val predReadReq  = Flipped(Valid(new TageTableReadReq))
    val trainReadReq = Flipped(Valid(new TageTableReadReq))
    val tableWriteReq   = Flipped(Valid(new TageTableWriteReq))
    val trainReadResp = Output(new TageTableResp)
    val predReadResp = Output(new TageTableResp)
    val resetUseful     = Input(Bool())
    val usefulReseting = Output(Bool())
    val sramResetDone   = Output(Bool())
  }

  val io: TageTableIO = IO(new TageTableIO)
//alias
  val predictReadReqValid = io.predReadReq.valid
  val predictReadReq = io.predReadReq.bits
  val trainReadReqValid = io.trainReadReq.valid
  val trainReadReq = io.trainReadReq.bits
  val trainWriteReqValid = io.tableWriteReq.valid
  val trainWriteReq = io.tableWriteReq.bits
  val resetUseful = io.resetUseful

//memory
  val dataSram = Seq.tabulate(NumBanks, NumWays){ (bankIdx, wayIdx) =>
    Module(new SinglePortSRAMTemplate(
      gen = new TageTableData,
      set = NumSets,
      shouldReset = true,
      holdRead = true,
    ))
  }

  val ctrSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new SaturateCounter(TakenCtrWidth),
        set = NumSets,
        shouldReset = true,
        holdRead = true,
      ))
    }
  }

  val usefulSram = Seq.tabulate(NumBanks, NumWays){ (bankIdx, wayIdx) =>
    Module(new SinglePortSRAMTemplate(
      gen = new SaturateCounter(UsefulCtrWidth),
      set = NumSets,
      shouldReset = true,
      holdRead = true,
      externalReset = true,
    ))
  }

  (0 until NumBanks).foreach { bankIdx =>
    val predictRead = predictReadReqValid && predictReadReq.bankMask(bankIdx)
    val trainRead = trainReadReqValid && trainReadReq.bankMask(bankIdx)
    val readValid = predictRead || trainRead
    (0 until NumWays).foreach { wayIdx =>
      val data = dataSram(bankIdx)(wayIdx)
      val useful = usefulSram(bankIdx)(wayIdx)
      val ctr = ctrSram(bankIdx)(wayIdx)

      val writeValid = trainWriteReqValid && trainWriteReq.bankMask(bankIdx) &&trainWriteReq.wayMask(wayIdx)
      data.io.en := readValid || writeValid && trainWriteReq.dataEn(wayIdx)
      data.io.wen := writeValid
      data.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      data.io.wdata := trainWriteReq.entry(wayIdx).data

      ctr.io.en := readValid || writeValid && trainWriteReq.ctrEn(wayIdx)
      ctr.io.wen := writeValid
      ctr.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      ctr.io.wdata := trainWriteReq.entry(wayIdx).ctr

      useful.io.en := readValid || writeValid && trainWriteReq.usefulEn(wayIdx)
      useful.io.wen := writeValid
      useful.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      useful.io.resetReq := resetUseful
      useful.io.wdata := trainWriteReq.entry(wayIdx).useful

    }
  }

  val dataRead = dataSram.map{ bank =>
    bank.map(_.io.rdata)
  }
  val ctrRead = ctrSram.map{ bank =>
    bank.map(_.io.rdata)
  }
  val usefulRead = usefulSram.map{ bank =>
    bank.map(_.io.rdata)
  }

  val predReadBankSel = RegEnable(predictReadReq.bankMask, predictReadReqValid)
  val trainReadBankSel = RegEnable(trainReadReq.bankMask, trainReadReqValid)

  val predictReadResp = Wire(new TageTableResp)
  val trainReadResp = Wire(new TageTableResp)
  val usefulReseting = !usefulSram(0)(0).io.resetDown

  predictReadResp.entries.zipWithIndex.foreach{ case(entry, wayIdx) =>
    entry.data := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> dataRead(bankIdx)(wayIdx)
      }
    )
    entry.ctr := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> ctrRead(bankIdx)(wayIdx)
      }
    )
    entry.useful := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> usefulRead(bankIdx)(wayIdx)
      }
    )
  }

  trainReadResp.entries.zipWithIndex.foreach{ case(entry, wayIdx) =>
    entry.data := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> dataRead(bankIdx)(wayIdx)
      }
    )
    entry.ctr := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> ctrRead(bankIdx)(wayIdx)
      }
    )
    entry.useful := Mux(usefulReseting, 0.U.asTypeOf(new SaturateCounter(UsefulCtrWidth)),
    Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> usefulRead(bankIdx)(wayIdx)
      }
    ))
  }

  io.usefulReseting := usefulReseting
  io.sramResetDone := dataSram(0)(0).io.resetDown
  io.predReadResp := predictReadResp
  io.trainReadResp := trainReadResp
}