package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._
import utils.SaturateCounter

import yanqihu.frontend.bpu.BpuTrain
import yanqihu.frontend.bpu.phr.PhrAllFoldedHistories

class Tage extends TageModule with TableHelper {
  class TageIO extends TageBundle {
    val train       = Flipped(Valid(new BpuTrain))
    //s0
    val predictReq     = Flipped(Valid(new TagePredReq))
    val ready       = Output(Bool())
    //s1
    val s1Pc        = Input(UInt(pcWidth.W))
    val s1Offset    = Input(Vec(NumBr, UInt(FetchBlockOffsetWidth.W)))
    val s1Fire      = Input(Bool())
    val s1PredPhr   = Input(new PhrAllFoldedHistories)
    //s2
    val s2Pc        = Input(UInt(pcWidth.W))
    val meta        = Output( Vec(NumBr,new TageMeta) )
    val prediction = Output( Vec(NumBr,Bool()))
    val resetDown   = Output(Bool())
  }

  val io = IO(new TageIO)
//----------------------alias-----------------------------------------//
  val trainValid = io.train.valid
  val train = io.train.bits
  val predictValid = io.predictReq.valid
  val predict = io.predictReq.bits
  val predictPhr = predict.predPhr
  val s1Fire = io.s1Fire
  val s1Pc = io.s1Pc
  val s1Offset = io.s1Offset
  val s1PredPhr =io.s1PredPhr
  val s2Pc = io.s2Pc

  val tables = TableInfos.zipWithIndex.map { case(tableInfo, idx) =>
    implicit val info = tableInfo
    Module(new TageTable(idx))
  }
  val baseTable = Module(new BaseTable)

  // reset usefulCtr of all entries when usefulResetCtr saturated
  val usefulResetCtr = RegInit(SaturateCounter(UsefulResetCtrWidth, 0))
  // use the alternate prediction when counter is positive
  val useAltOnNaVec = RegInit(VecInit.fill(NumUseAltOnNa)(SaturateCounter(UseAltOnNaWidth, 0)))

//---------------------predict--------------------------------------//
  /*------------------------------------------------------
    predict pipeline stage stage 0
  --------------------------------------------------------*/
  val readBankSel = UIntToOH(getBankIndex(predict.pc), NumBanks)
  val s0Stall = trainValid || trainValid

  val s0PredFoldHist = getFoldedHist(predict.predPhr)
  s0PredFoldHist.hist.zip(tables).zip(TableInfos).foreach{case((his, table), tableInfo) =>
    implicit val info = tableInfo

    table.io.predReadReq.valid := predictValid
    table.io.predReadReq.bits.bankMask := readBankSel
    table.io.predReadReq.bits.setIdx := getSetIndex(predict.pc, his)
  }

  baseTable.io.predReadReq.valid := predictValid
  baseTable.io.predReadReq.bits.bankMask := readBankSel
  baseTable.io.predReadReq.bits.setIdx := getBaseSetIndex(predict.pc)

  /*------------------------------------------------------
    predict pipeline stage stage 1
    compare tag  provider alt
  --------------------------------------------------------*/
  val tableRead = tables.map(_.io.predReadResp)
  val s1PredFoldHist = getFoldedHist(s1PredPhr)
  // br table wayHitVec
  val brWayHit = (0 until NumBr).map{br =>
    TableInfos.zipWithIndex.map{case(tableInfo, tableIdx) =>
      implicit val info = tableInfo
      val brPcTag = getTag(s1Pc, s1PredFoldHist.hist(tableIdx),s1Offset(br))
      tableRead(tableIdx).entries.map{ entry =>
        entry.data.valid && (entry.data.tag === brPcTag)
      }
    }
  }

  // br table wayHit
  val brProviderTableHit = brWayHit.map{br =>
    br.map(_.reduce(_ || _))
  }

  // br providerHit
  val brProviderHit = brProviderTableHit.map{br =>
    br.reduce(_ || _)
  }
  // br providerHitIdx
  val brProviderHitIdxOH = brProviderTableHit.map{br =>
    PriorityEncoderOH(br.reverse).reverse
  }
  // br altTable wayHit
  val brAltTableHit = brProviderTableHit.zip(brProviderHitIdxOH).map{case (tableHits, providerOH) =>
    tableHits.zip(providerOH).map{case(tableHit, providerBit)=>
      tableHit && !providerBit
    }
  }
  //br altHit
  val brAltHit = brAltTableHit.map{ br =>
    br.reduce(_ || _)
  }
  // br altHitIdx
  val brAltHitIdxOH = brAltTableHit.map{ br =>
    PriorityEncoderOH(br.reverse).reverse
  }

  // // 根据命中的 table one-hot，选出该 table 的 way-hit 向量
  val brProvderHitWay = brProviderHitIdxOH.zipWithIndex.map{case ( tableIdxOH, brIdx)=>
    Mux1H(tableIdxOH, brWayHit(brIdx).map(wayHits => VecInit(wayHits)))
  }
  val brProviderTableEntry = brProviderHitIdxOH.zipWithIndex.map{case (brTableOH, brIdx) =>
    //根据命中的 table one-hot，选出该 table 的读响应
    val selectedTableResp = Mux1H(brTableOH, tableRead)
    // 根据 way-hit one-hot，选出该 way 的 entry
    Mux1H(brProvderHitWay(brIdx), selectedTableResp.entries)
  }

  val brAltHitWay = brAltHitIdxOH.zipWithIndex.map{case (brTableOH, brIdx) =>
    Mux1H(brTableOH, brWayHit(brIdx).map(wayHits => VecInit(wayHits)))
  }
  val brAltTableEntry = brAltHitIdxOH.zipWithIndex.map{case (brTableOH, brIdx) =>
    val selectedTableResp = Mux1H(brTableOH, tableRead)
    Mux1H(brAltHitWay(brIdx), selectedTableResp.entries)
  }

  val brProviderCtr = brProviderTableEntry.map(_.ctr)
  val brProviderUseful = brProviderTableEntry.map(_.useful)
  val brAltCtr = brAltTableEntry.map(_.ctr)
  val brAltUseful = brAltTableEntry.map(_.useful)
  val brBaseCtr = baseTable.io.predReadResp.entries

  //----------------------------------s2-----------------------------//
  val s2BrProviderValid = RegEnable(VecInit(brProviderHit), s1Fire)
  val s2BrProviderCtr = RegEnable(VecInit(brProviderCtr), s1Fire )
  val s2BrProviderUseful = RegEnable(VecInit(brProviderUseful), s1Fire)
  val s2BrProviderIdx = RegEnable(VecInit(brProviderHitIdxOH.map(OHToUInt(_))), s1Fire)
  val s2BrProviderWayIdx = RegEnable(VecInit(brProvderHitWay.map(OHToUInt(_))), s1Fire)

  val s2AltValid = RegEnable(VecInit(brAltHit), s1Fire)
  val s2BrAltCtr = RegEnable(VecInit(brAltCtr), s1Fire )
  val s2BrAltUseful = RegEnable(VecInit(brAltUseful), s1Fire)
  val s2BrAltIdx = RegEnable(VecInit(brAltHitIdxOH.map(OHToUInt(_))), s1Fire)
  val s2BrAltWayIdx = RegEnable(VecInit(brProvderHitWay.map(OHToUInt(_))), s1Fire)
  val s2BrBaseCtr = RegEnable(brBaseCtr, s1Fire)

  val useAltOnNa= useAltOnNaVec(s2Pc(UseAltOnNaIdxWidth-1,0)).isPositive
  val brProviderTaken = s2BrProviderCtr.map(_.isPositive)
  val brAltTaken = s2BrAltCtr.map(_.isPositive)
  val brBaseTaken = s2BrBaseCtr.map(_.isPositive)

  val brUseAlt = s2BrProviderCtr.zipWithIndex.map{ case(ctr, brIdx) =>
    ctr.isWeak && useAltOnNa && !s2BrProviderValid(brIdx)
  }

//---------------------train----------------------------------------//

  val tageMeta = train.meta.tage
  val ftbMeta = train.meta.ftb

  val brValid = VecInit(
    ftbMeta.brSlot.valid,
    ftbMeta.tailSlot.valid && !ftbMeta.tailSlot.share
  )
  val brOffset = VecInit(
    ftbMeta.brSlot.offset,
    ftbMeta.tailSlot.offset
  )
  val providerUpdate = tageMeta.map(_.providerValid)


//connect
  val tageMetas = Vec(NumBr, new TageMeta)
  tageMetas.zipWithIndex.foreach{ case(meta, brIdx) =>
    meta.providerValid := s2BrProviderValid(brIdx)
    meta.providerTableIdx := s2BrProviderIdx(brIdx)
    meta.providerTableWayIdx := s2BrProviderWayIdx(brIdx).asUInt
    meta.providerCtr := s2BrProviderCtr(brIdx)
    meta.providerUseful := s2BrProviderUseful(brIdx)
    meta.altUsed := brUseAlt(brIdx)
    meta.altTableValid := s2AltValid(brIdx)
    meta.altCtr.value := Mux(s2AltValid(brIdx), s2BrAltCtr(brIdx).value, s2BrBaseCtr(brIdx).value.pad(TakenCtrWidth))
    meta.altTableIdx := s2BrAltIdx(brIdx)
    meta.altTableWayIdx := s2BrAltWayIdx(brIdx).asUInt
  }

  val prediction = Vec(NumBr,Bool())
  prediction.zipWithIndex.foreach{ case(pred, brIdx) =>
    pred := Mux(brUseAlt(brIdx),
    Mux(s2AltValid(brIdx), brAltTaken(brIdx), brBaseTaken(brIdx)),
    brProviderTaken(brIdx))
  }

//output
  io.ready := !s0Stall
  io.meta := tageMetas
  io.prediction := prediction

}

