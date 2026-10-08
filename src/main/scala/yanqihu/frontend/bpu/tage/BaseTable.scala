package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._
import utils.SinglePortSRAMTemplate
import utils.SaturateCounter
import yanqihu.mem.MemCmd.write

class BaseTable extends TageModule{
  class BaseTableIO extends TageBundle{
    val predReadReq = Flipped(Valid(new BaseTableReadReq))
    val tableWriteReq   = Flipped(Valid(new BaseTableWriteReq))
    val predReadResp = Output(new BaseTableResp)
    val sramResetDone   = Output(Bool())      //sram初始化
  }

  val io = IO(new BaseTableIO)
  //alias
  val predReadReqValid= io.predReadReq.valid
  val predReadReq = io.predReadReq.bits
  val trainWriteReqValid = io.tableWriteReq.valid
  val trainWriteReq = io.tableWriteReq.bits
//memory
  val baseSram = Seq.tabulate(NumBanks, NumBr){ (bankIdx, brIdx) =>
    Module(new SinglePortSRAMTemplate(
      gen = new SaturateCounter(BaseTakenCtrWidth),
      set = BaseTableSets,
      shouldReset = true,
      holdRead = true,
    ))
  }

  (0 until NumBanks).foreach { bankIdx =>
    val readValid = predReadReqValid && predReadReq.bankMask(bankIdx)
    (0 until NumBr).foreach { brIdx =>
      val base = baseSram(bankIdx)(brIdx)

      val writeValid = trainWriteReqValid && trainWriteReq.bankMask(bankIdx) &&trainWriteReq.brMask(brIdx)

      base.io.en := readValid || writeValid
      base.io.wen := writeValid
      base.io.addr := Mux(writeValid, trainWriteReq.setIdx, predReadReq.setIdx)
      base.io.wdata := trainWriteReq.entry(brIdx)
    }
  }

  val predReadBankSel = RegEnable(predReadReq.bankMask, predReadReqValid)

  val baseRead = baseSram.map{bank =>
    bank.map(_.io.rdata)
  }

  val predictReadResp = Wire(new BaseTableResp)

  predictReadResp.entries.zipWithIndex.foreach{case(entry, brIdx) =>
    entry := Mux1H(
      (0 until NumBanks).map { bankIdx =>
        predReadBankSel(bankIdx) -> baseRead(bankIdx)(brIdx)
      }
    )
  }

  //output
  io.sramResetDone := baseSram(0)(0).io.resetDown
  io.predReadResp := predictReadResp
}