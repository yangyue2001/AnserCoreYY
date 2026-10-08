package yanqihu.frontend.bpu.ittage

import chisel3._
import chisel3.util._
import utils.SinglePortSRAMTemplate
import utils.SaturateCounter

import yanqihu.frontend.bpu.tage.TageTableInfo

class IttageTable(implicit info: TageTableInfo) extends IttageModule with IttageHelper {
  class IttageTableIO extends IttageBundle {
    val predReadReq  = Flipped(Valid(new IttageTableReadReq))
    val tableWriteReq   = Flipped(Valid(new IttageTableWriteReq))
    val trainReadReq = Flipped(Valid(new IttageTableReadReq))
    val predictReadResp = Output(new IttageTableResp)
    val trainReadResp = Output(new IttageTableResp)
    val resetUseful     = Input(Bool())
    val usefulReseting = Output(Bool())
    val sramResetDone   = Output(Bool())
  }

  val io = IO(new IttageTableIO)
//alias
  val predictReadReqValid = io.predReadReq.valid
  val predictReadReq = io.predReadReq.bits

  val trainReadReqValid = io.trainReadReq.valid
  val trainReadReq = io.trainReadReq.bits

  val trainWriteReqValid = io.tableWriteReq.valid
  val trainWriteReq = io.tableWriteReq.bits
  val resetUseful = io.resetUseful

//memory
  val dataSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new IttageData,
        set = NumSets,
        shouldReset = true,
        holdRead = true,
      ))
    }
  }

  val usefulSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new SaturateCounter(UsefulCtrWidth),
        set = NumSets,
        shouldReset = true,
        holdRead = true,
        externalReset = true,
      ))
    }
  }
  val ctrSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new SaturateCounter(ConfidenceCtrWidth),
        set = NumSets,
        shouldReset = true,
        holdRead = true,
      ))
    }
  }

  val offsetSram = Seq.fill(NumBanks){
    Seq.fill(NumWays){
      Module(new SinglePortSRAMTemplate(
        gen = new IttageOffset,
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
      val data = dataSram(bankIdx)(wayIdx)
      val useful = usefulSram(bankIdx)(wayIdx)
      val ctr = ctrSram(bankIdx)(wayIdx)
      val offset = offsetSram(bankIdx)(wayIdx)

      val writeValid = trainWriteReqValid && trainWriteReq.bankMask(bankIdx) && trainWriteReq.wayMask(wayIdx)

      data.io.en := readValid || writeValid && trainWriteReq.dataEn
      data.io.wen := writeValid
      data.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      data.io.wdata := trainWriteReq.entry.data

      ctr.io.en := readValid || writeValid && trainWriteReq.ctrEn
      ctr.io.wen := writeValid
      ctr.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      ctr.io.wdata := trainWriteReq.entry.ctr

      useful.io.en := readValid || writeValid && trainWriteReq.usefulEn
      useful.io.wen := writeValid
      useful.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      useful.io.resetReq := resetUseful
      useful.io.wdata := trainWriteReq.entry.useful

      offset.io.en := readValid || writeValid && trainWriteReq.offsetEn
      offset.io.wen := writeValid
      offset.io.addr := Mux1H(Seq(
        predictRead -> predictReadReq.setIdx,
        trainRead -> trainReadReq.setIdx,
        writeValid -> trainWriteReq.setIdx
      ))
      offset.io.wdata := trainWriteReq.entry.offset
    }
  }

  val dataRead = dataSram.map{ bank =>
    bank.map(_.io.rdata)
  }
  val ctrRead = ctrSram.map{ bank =>
    bank.map(_.io.rdata)
  }
  val usefulRead = usefulSram.map{ bank =>
    bank.map{ way =>
      Mux(usefulResetReg, SaturateCounter(UsefulResetCtrWidth, 0), way.io.rdata)
    }
  }
  val offsetRead = offsetSram.map{ bank =>
    bank.map(_.io.rdata)
  }

  val predReadBankSel = RegEnable(predictReadReq.bankMask, predictReadReqValid)
  val trainReadBankSel = RegEnable(trainReadReq.bankMask, trainReadReqValid)
  val usefulResetReg = RegNext(!usefulSram(0)(0).io.resetDown, false.B)

  val predictReadResp = Wire(new IttageTableResp)
  val trainReadResp = Wire(new IttageTableResp)

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
    entry.offset := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> offsetRead(bankIdx)(wayIdx)
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
    entry.useful := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> usefulRead(bankIdx)(wayIdx)
      }
    )
    entry.offset := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        trainReadBankSel(bankIdx) -> offsetRead(bankIdx)(wayIdx)
      }
    )
  }


  io.usefulReseting := !usefulSram(0)(0).io.resetDown
  io.sramResetDone := dataSram(0)(0).io.resetDown
  io.predictReadResp := predictReadResp
  io.trainReadResp := trainReadResp
}