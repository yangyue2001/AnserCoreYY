package yanqihu.frontend.bpu.ittage

import chisel3._
import chisel3.util._
import utils.SaturateCounter
import utils.SelectFirstN
import utils.ReplacementPolicy

import yanqihu.frontend.bpu.BpuTrain
import yanqihu.frontend.bpu.tage.TageTableInfo
import yanqihu.frontend.bpu.phr.PhrAllFoldedHistories
import yanqihu.frontend.bpu.ittage
import utils.HighMask
import chisel3.util.random.LFSR


class Ittage extends IttageModule with IttageHelper {
  class IttageIO extends IttageBundle{

    val resetDown   = Output(Bool())
    //s1
    val predictReq     = Flipped(Decoupled(new IttagePredReq))
    //s2
    val s2PredPhr = Input(new PhrAllFoldedHistories)
    val s2Fire      = Input(Bool() )
    val s2Pc        = Input(UInt(PcWidth.W))
    //s3
    val s3Pc        = Input(UInt(PcWidth.W))
    val meta        = Output(new IttageMeta)
    val prediction = Output(new IttagePrediction)

    //train
    val train       = Flipped(Decoupled(new BpuTrain))
    val trainS0Phr = Input(new PhrAllFoldedHistories)
    val trainS1Phr = Input(new PhrAllFoldedHistories)
    val s1TrainPc   = Input(UInt(PcWidth.W))
  }

  val io = IO(new IttageIO)
  //-----------------------alias-----------------------------//
  val trainValid = io.train.valid
  val train = io.train.bits
  val trainS0Phr = io.trainS0Phr
  val trainS1Phr = io.trainS1Phr
  val s1TrainPc = io.s1TrainPc

  val predictReqValid = io.predictReq.valid
  val predictReq = io.predictReq.bits
  val s2PredPhr = io.s2PredPhr
  val s2Pc = io.s2Pc
  val s2Fire = io.s2Fire


//---------------------------memory----------------------------------//
  val tables = TableInfos.map{ tableInfo =>
    implicit val info = tableInfo
    Module(new IttageTable)
  }

  // reset usefulCtr of all entries when usefulResetCtr saturated
  val usefulResetCtr = RegInit(SaturateCounter(UsefulResetCtrWidth, 0))
  // use the alternate prediction when counter is positive
  val useAltOnNaVec = RegInit(VecInit.fill(NumUseAltOnNa)(SaturateCounter(UseAltOnNaWidth, 0)))

  val regions = RegInit(VecInit.fill(RegionNums)(0.U.asTypeOf(new RegionEntry)))


//----------------------------predict------------------------------//
  //---------------------------s0--------------------------------//
  val predReadBank = getBankIndex(predictReq.pc)
  val predReadValid = predictReqValid
  val s0Stall = predReadValid && ((trainReadValid && predReadBank === trainReadBank) || (trainWriteValid && predReadBank === trainWriteBank))
  val s0PredFoldHist = getFoldedHist(predictReq.predPhr)
  TableInfos.zipWithIndex.foreach{ case(tableInfo, idx) =>
    implicit val info = tableInfo

    tables(idx).io.predReadReq.valid := predictReqValid
    tables(idx).io.predReadReq.bits.bankMask := UIntToOH(predReadBank, NumBanks)
    tables(idx).io.predReadReq.bits.setIdx := getSetIndex(predictReq.pc, s0PredFoldHist.hist(idx))

  }
  //---------------------------s1---------------------------------//
  val tableRead = tables.map(_.io.predictReadResp)
  val s1PredFoldHist = getFoldedHist(s2PredPhr)
  //Seq[table][way]
  val wayHit = TableInfos.zipWithIndex.map{case(tableInfo, tableIdx) =>
    implicit val info = tableInfo
    val pcTag = getTag(s2Pc, s1PredFoldHist.hist(tableIdx))
    tableRead(tableIdx).entries.map{ entry =>
      entry.data.valid && (entry.data.tag === pcTag)
    }
  }

  val tableHitVec = wayHit.map(_.reduce(_ || _))
  //provider
  val s1ProviderTableIdxOH = PriorityEncoderOH(tableHitVec.reverse).reverse
  val s1ProviderValid = tableHitVec.reduce(_ || _)
  val providerHitWay = Mux1H(s1ProviderTableIdxOH, wayHit.map(VecInit(_)))
  val s1ProvderTableEntry =  Mux1H(providerHitWay, Mux1H(s1ProviderTableIdxOH,tableRead).entries)
  //alt
  val altTableHitVec = tableHitVec.zip(s1ProviderTableIdxOH).map{case(tableHit, provider)=>
    tableHit && !provider
  }
  val s1AltTableIdxOH = PriorityEncoderOH(altTableHitVec.reverse).reverse
  val s1AltValid = altTableHitVec.reduce(_ || _)
  val altHitWay = Mux1H(s1AltTableIdxOH, wayHit.map(VecInit(_)))
  val s1AltTableEntry =  Mux1H(altHitWay, Mux1H(s1AltTableIdxOH,tableRead).entries)

  val s1ProviderRegionPtr = s1ProvderTableEntry.data.pointer
  val s1ProviderCtr       = s1ProvderTableEntry.ctr
  val s1ProviderUseful    = s1ProvderTableEntry.useful
  val s1ProviderOffset    = s1ProvderTableEntry.offset

  val s1AltRegionPtr      = s1AltTableEntry.data.pointer
  val s1AltCtr            = s1AltTableEntry.ctr
  val s1AltUseful         = s1AltTableEntry.useful
  val s1AltOffset         = s1AltTableEntry.offset
  //-----------------------------s2--------------------------------/
  val s2ProviderRegionPtr = RegEnable(s1ProviderRegionPtr, s2Fire)
  val s2ProviderCtr       = RegEnable(s1ProviderCtr      , s2Fire)
  val s2ProviderUseful    = RegEnable(s1ProviderUseful   , s2Fire)
  val s2ProviderOffset    = RegEnable(s1ProviderOffset   , s2Fire)
  val s2ProviderValid     = RegEnable(s1ProviderValid    , s2Fire)
  val s2ProviderWayIdx    = RegEnable(OHToUInt(providerHitWay) , s2Fire)
  val s2ProviderTableIdx  = RegEnable(OHToUInt(s1ProviderTableIdxOH), s2Fire)

  val s2AltRegionPtr = RegEnable(s1AltRegionPtr, s2Fire)
  val s2AltCtr       = RegEnable(s1AltCtr      , s2Fire)
  val s2AltUseful    = RegEnable(s1AltUseful   , s2Fire)
  val s2AltOffset    = RegEnable(s1AltOffset   , s2Fire)
  val s2AltValid     = RegEnable(s1AltValid    , s2Fire)
  val s2AltWayIdx    = RegEnable(OHToUInt(altHitWay) , s2Fire)
  val s2AltTableIdx  = RegEnable(OHToUInt(s1AltTableIdxOH), s2Fire)

  val useAlt = useAltOnNaVec(s2Pc(UseAltOnNaIdxWidth-1,0)).isPositive && s2ProviderCtr.isWeak

  val providerTarget = Cat(Mux(s2ProviderOffset.usePcRegion, getRegion(s2Pc),regions(s2ProviderRegionPtr).region), s2ProviderOffset.offset)

  val altTarget = Cat(Mux(s2AltOffset.usePcRegion, getRegion(s2Pc),regions(s2AltRegionPtr).region), s2AltOffset.offset)

//----------------------------train--------------------------------//
  val ftbMeta = train.meta.ftb
  val trainIttageMeta = train.meta.ittage
  val fetchBlcokInfo = train.fetchBlockInfo
  val s0TrainFoldHist = getFoldedHist(trainS0Phr).hist
  val updateValid = trainValid && fetchBlcokInfo.cfiAttribute.needIttage && ftbMeta.hit

  val trainS0Fire = io.train.fire
  val providerCorrect = fetchBlcokInfo.target === trainIttageMeta.providerTarget
  val altCorrect = fetchBlcokInfo.target === trainIttageMeta.altTarget

  val s0ProviderUpdateValid = updateValid && trainIttageMeta.providerValid && (fetchBlcokInfo.mispredict || !trainIttageMeta.providerCtr.isSaturatePositive || s0ProviderUpdateUsefulEn)
  val s0AltUpdateValid = updateValid && trainIttageMeta.altTableValid && trainIttageMeta.altUsed && (fetchBlcokInfo.mispredict || !trainIttageMeta.altCtr.isSaturatePositive)

  val s0ProviderAllocateValid = updateValid && fetchBlcokInfo.mispredict && (!trainIttageMeta.providerValid ||  trainIttageMeta.providerTableIdx =/= (NumTables-1).U)

  val s0ProviderUpdateCtrEn  = !trainIttageMeta.providerCtr.isSaturate
  val s0ProviderUpdateOffsetEn = trainIttageMeta.providerCtr.isSaturateNegative && fetchBlcokInfo.mispredict
  val s0ProviderUpdateUsefulEn = providerCorrect ^ altCorrect && !trainIttageMeta.providerUseful.isSaturate
  val s0ProviderUpdateCtr = Mux(fetchBlcokInfo.mispredict,
  trainIttageMeta.providerCtr - 1,
  trainIttageMeta.providerCtr + 1)
  val s0ProviderUpdateUseful = Mux(providerCorrect,
  trainIttageMeta.providerUseful + 1,
  trainIttageMeta.providerUseful - 1)

  val s0AltUpdateCtrEn = !trainIttageMeta.altCtr.isSaturate
  val s0AltUpdateOffsetEn = trainIttageMeta.altCtr.isSaturateNegative && fetchBlcokInfo.mispredict
  val s0AltUpdateCtr = Mux(fetchBlcokInfo.mispredict,
  trainIttageMeta.altCtr - 1,
  trainIttageMeta.altCtr + 1)

  val trainReadBank = getBankIndex(fetchBlcokInfo.target)
  TableInfos.zipWithIndex.foreach{ case(tableInfo, idx) =>
    implicit val info = tableInfo

    tables(idx).io.trainReadReq.valid := s0ProviderAllocateFire
    tables(idx).io.trainReadReq.bits.bankMask := UIntToOH(trainReadBank)
    tables(idx).io.trainReadReq.bits.setIdx := getSetIndex(fetchBlcokInfo.startAddr, s0TrainFoldHist(idx))
  }

  val trainReadValid = s0ProviderAllocateValid
  val trainS0Ready = !(trainReadValid && trainWriteValid && trainReadBank === trainWriteBank)
  val s0ProviderUpdateFire = s0ProviderUpdateValid && trainS0Ready
  val s0AltUpdateFire = s0AltUpdateValid && trainS0Ready
  val s0ProviderAllocateFire = s0ProviderAllocateValid && trainS0Ready

  // train 1
  val providerUpdateValid = RegNext(s0ProviderUpdateValid,false.B)
  val altUpdateValid = RegNext(s0AltUpdateValid,false.B)
  val providerAllocateValid = RegNext(s0ProviderAllocateValid,false.B)

  val oldProviderIdx = RegEnable(trainIttageMeta.providerTableIdx, s0ProviderUpdateFire)
  val oldProviderUpdateCtrEn = RegEnable(s0ProviderUpdateCtrEn, s0ProviderUpdateFire)
  val oldProviderUpdateOffsetEn = RegEnable(s0ProviderUpdateOffsetEn, s0ProviderUpdateFire)
  val oldProviderUpdateUsefulEn = RegEnable(s0ProviderUpdateUsefulEn, s0ProviderUpdateFire)
  val oldProviderUpdateCtr = RegEnable(s0ProviderUpdateCtr, s0ProviderUpdateFire)
  val oldProviderUpdateUseful = RegEnable(s0ProviderUpdateUseful, s0ProviderUpdateFire)
  val oldProviderUpdateWayMask = RegEnable(trainIttageMeta.providerWayIdx, s0ProviderUpdateFire)

  val s1Target = RegEnable(fetchBlcokInfo.target, updateValid && trainS0Ready)

  val oldAltIdx = RegEnable(trainIttageMeta.altTableIdx, s0AltUpdateFire)
  val oldAltUpdateCtrEn = RegEnable(s0AltUpdateCtrEn, s0AltUpdateFire)
  val oldAltUpdateOffsetEn = RegEnable(s0AltUpdateOffsetEn, s0AltUpdateFire)
  val oldAltUpdateCtr = RegEnable(s0AltUpdateCtr, s0AltUpdateFire)
  val oldAltUpdateWayMask = RegEnable(trainIttageMeta.altWayIdx, s0AltUpdateFire)

  //重新判断provider 和alt 以及可以分配的table
  val trainTableRead = tables.map(_.io.trainReadResp)
  val s1TrainFoldHist = getFoldedHist(trainS1Phr).hist
  val trainWayHit = Vec(NumTables, Vec(NumWays, Bool()))
  val canAllocateTable = Vec(NumTables, Vec(NumWays, Bool()))

  TableInfos.zipWithIndex.foreach{case(tableInfo, tableIdx) =>
    implicit val info = tableInfo
    val pcTag = getTag(s1TrainPc, s1TrainFoldHist(tableIdx))
    trainTableRead(tableIdx).entries.zipWithIndex.foreach{ case(entry, wayIdx) =>
      trainWayHit(tableIdx)(wayIdx):= entry.data.valid && (entry.data.tag === pcTag)
      canAllocateTable(tableIdx)(wayIdx) := !entry.data.valid || (entry.ctr.isSaturateNegative && entry.useful.isSaturateNegative)
    }
  }
  val trainTableHitVec = trainWayHit.map(_.reduce(_ || _))

  val Seq(newProviderIdxOH, newAltIdxOH) =
    SelectFirstN(trainTableHitVec.reverse, 2).toSeq.map(Reverse(_))

  val allocateMask =  HighMask(newProviderIdxOH)
  val lfsr = LFSR(NumTables)
  val candidateTable = canAllocateTable.zipWithIndex.map{ case(ways, tableIdx) =>
    ways.reduce(_ || _) && allocateMask(tableIdx)
  }

  //to 这里时序比较差， 先选出了最长的priovider 然后随后又mask了， 然后又加入了一些新的逻辑， 如果差可以把训练变成三级流水，并且需要思考一些转发给预测。如果撞上了的话。
  val candidateTableLfsr = candidateTable.zipWithIndex.map{case(table, tableIdx) =>
    table && lfsr(tableIdx)
  }
  val allocateTable = Mux(candidateTableLfsr.reduce(_ || _),
  VecInit(PriorityEncoderOH(candidateTableLfsr)),
  VecInit(PriorityEncoderOH(candidateTable))
  )

  //多way 的情况下 采用固定优先级
  val allocateTableWay = PriorityEncoderOH( Mux1H(allocateTable, canAllocateTable).asUInt)

  val realAllocate = providerAllocateValid && !newProviderIdxOH(NumTables-1)
  val canAllocate = realAllocate && candidateTable.reduce(_ || _)
  val allocateFail = realAllocate && !candidateTable.reduce(_ || _)
  when(canAllocate || allocateFail){
    usefulResetCtr := Mux(canAllocate, usefulResetCtr-1, Mux(
      usefulResetCtr.isSaturatePositive,
      SaturateCounter(UsefulResetCtrWidth, 0),
      usefulResetCtr + 1
    ))
  }

  val trainS1Valid = (providerUpdateValid || altUpdateValid || realAllocate)
  val resetUseful = usefulResetCtr.isSaturatePositive && allocateFail

  // 更新Tage
  val oldProviderIdxOH = UIntToOH(oldProviderIdx)
  val oldAltIdxOH = UIntToOH(oldAltIdx)
  val providerUpdateIdxOH = Mux(providerAllocateValid, newProviderIdxOH, oldProviderIdxOH)
  val altUpdateIdxOH = Mux(providerAllocateValid, newAltIdxOH, oldAltIdxOH)
  //to do 尝试如果是allocate 采用最新的结果
  val providerUpdateCtrEn     = oldProviderUpdateCtrEn
  val providerUpdateOffsetEn  = oldProviderUpdateOffsetEn
  val providerUpdateUsefulEn  = oldProviderUpdateUsefulEn
  val providerUpdateCtr       = oldProviderUpdateCtr
  val providerUpdateUseful    = oldProviderUpdateUseful
  val providerUpdateWayMask   = oldProviderUpdateWayMask
  val altUpdateCtrEn          = oldAltUpdateCtrEn
  val altUpdateCtr            = oldAltUpdateCtr
  val altUpdateOffsetEn       = oldAltUpdateOffsetEn
  val altUpdateWayMask        = oldAltUpdateWayMask

  //to do  不够细致， 比如说update 如果只更新useful 但是useful再刷新， 那么其实不用阻塞，同时如果是allocate 的时候 update 是否采用最新的进行判断也是一个问题。
  val trainWriteValid = realAllocate || providerUpdateValid || altUpdateValid

  val trainWriteBank = getBankIndex(s1TrainPc)
  val updateOffset = Wire(new IttageOffset)
  updateOffset.offset := getOffset(s1Target)
  updateOffset.usePcRegion := getRegion(s1Target) === getRegion(s1Target)

  val s1TargetRegion = getRegion(s1Target)
  val s1TargetRegionHitVec = regions.map{ entry =>
    entry.valid && entry.region === s1TargetRegion
  }
  val regionEmptyVec = VecInit(regions.map(!_.valid)).asUInt
  val replacememt = ReplacementPolicy("plru", RegionNums)

  val replace = Mux(regionEmptyVec.orR, PriorityEncoderOH(regionEmptyVec), replacememt.getReplaceWayOH)

  TableInfos.zipWithIndex.foreach{case(tableInfo, tableIdx) =>
    implicit val info = tableInfo

    val providerUpdate = providerUpdateValid && providerUpdateIdxOH(tableIdx)
    val altUpdate = altUpdateValid && altUpdateIdxOH(tableIdx)
    val allocate = allocateTable(tableIdx)

    val tableProviderUpdateCtrEn = providerUpdate && providerUpdateCtrEn
    val tableProviderUpdateOffsetEn = providerUpdate && providerUpdateOffsetEn
    val tableProviderUpdateUsefulEn = providerUpdate && providerUpdateUsefulEn
    val tableAltUpdateCtrEn = altUpdate && altUpdateCtrEn
    val tableAltUpdateOffsetEn = altUpdate && altUpdateOffsetEn

    val tableWrite = Wire(new IttageTableWriteReq)
    tableWrite.bankMask := UIntToOH(trainWriteBank)
    tableWrite.setIdx := getSetIndex(s1TrainPc, s1TrainFoldHist(tableIdx))
    tableWrite.wayMask := Mux1H(Seq(
      allocate -> allocateTableWay,
      providerUpdate -> providerUpdateWayMask,
      altUpdate -> altUpdateWayMask
    ))

    tableWrite.ctrEn := tableAltUpdateCtrEn || allocate || tableProviderUpdateCtrEn
    tableWrite.dataEn := allocate
    tableWrite.usefulEn := tableProviderUpdateUsefulEn || allocate
    tableWrite.offsetEn := tableProviderUpdateOffsetEn || allocate || tableAltUpdateOffsetEn

    tables(tableIdx).io.resetUseful := resetUseful
    tables(tableIdx).io.tableWriteReq.valid := providerUpdate || altUpdate || allocate
    tableWrite.entry.ctr := Mux1H(Seq(
      allocate -> SaturateCounter(ConfidenceCtrWidth, SaturateCounter.SaturateNegative(ConfidenceCtrWidth)),
      providerUpdate -> providerUpdateCtr,
      altUpdate -> altUpdateCtr
    ))
    tableWrite.entry.useful := Mux1H(Seq(
      allocate -> SaturateCounter(UsefulCtrWidth, SaturateCounter.SaturateNegative(UsefulCtrWidth)),
      providerUpdate -> providerUpdateUseful
    ))
    tableWrite.entry.offset := updateOffset

    val updateData = Wire(new IttageData)
    updateData.valid := true.B
    updateData.tag := getTag(s1TrainPc, s1TrainFoldHist(tableIdx))
    updateData.pointer := OHToUInt(replace)
    tableWrite.entry.data := updateData

    assert(
        PopCount(VecInit(providerUpdate, altUpdate , allocate)) <= 1.U,
        "providerUpdate, altUpdate and allocate must be mutually exclusive "
    )
  }


  //----------------------connection------------------------------
  val ittageMeta = Wire(new IttageMeta)
  ittageMeta.providerValid := s2ProviderValid
  ittageMeta.providerTableIdx := s2ProviderTableIdx
  ittageMeta.providerWayIdx := s2ProviderWayIdx
  ittageMeta.providerTarget := providerTarget
  ittageMeta.providerCtr := s2ProviderCtr
  ittageMeta.providerUseful := s2ProviderUseful

  ittageMeta.altUsed := useAlt
  ittageMeta.altTableValid := s2AltValid
  ittageMeta.altTableIdx := s2AltTableIdx
  ittageMeta.altWayIdx := s2AltWayIdx
  ittageMeta.altTarget := altTarget
  ittageMeta.altCtr := s2AltCtr

  val prediction = new IttagePrediction

  prediction.taken := s2ProviderValid && (!useAlt || s2AltValid)
  prediction.target := Mux(useAlt, altTarget, providerTarget)

  //----------------------output------------------------------
  io.resetDown := tables(getMaxSetIndexTableIdx).io.sramResetDone
  io.predictReq.ready := !s0Stall
  io.meta := ittageMeta
  io.prediction := prediction
  io.train.ready := trainS0Ready
}