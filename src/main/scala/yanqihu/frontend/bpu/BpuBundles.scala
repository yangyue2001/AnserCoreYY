package yanqihu.frontend.bpu

import chisel3._
import chisel3.util._

import yanqihu.frontend.bpu.ras.RasMeta
import yanqihu.frontend.bpu.tage.TageMeta
import yanqihu.frontend.bpu.ftb.FtbMeta
import yanqihu.frontend.bpu.phr.PhrMeta
import yanqihu.frontend.bpu.ittage.IttageMeta
import yanqihu.frontend.ftq.FtqPtr
import yanqihu.frontend.ftq.FtqFetchBlcokEntry


/* *** public *** */
// Csr -> Bpu
class BpuCtrl extends Bundle {
  //predictor enable
  val uftbEnable: Bool = Bool()
  val abtbEnable: Bool = Bool()
  val ftbEnable: Bool = Bool()
  val tageEnable:   Bool = Bool()
  val ittageEnable: Bool = Bool()
  val rasEnable:    Bool = Bool()
}


class BpuMeta extends BpuBundle {
  val ftb = new FtbMeta
  val tage = new TageMeta
  val ittage = new IttageMeta
  val phr = new PhrMeta
  val ras = new RasMeta
}


class BpuS3Result extends BpuBundle{
  val meta = new BpuMeta
  val prediction = new BpuPrediction
  val s3FetchValid = Bool()
  val s3FetchPC = UInt(PcWidth.W)
  val s3FtqPtr = new FtqPtr
}

// Bpu -> Ftq
class BpuPrediction extends BpuBundle {
  val target  = UInt(PcWidth.W)
  val endOffset = UInt(FetchBlockOffsetWidth.W)
  val taken = Bool()
  val s3Override = Bool()
}

class BpuRedirectMeta extends BpuBundle {
  val phr = new PhrMeta
  val ras = new RasMeta
}

class BpuTrainMeta extends BpuBundle {
  val ftb    = new FtbMeta
  val tage   = Vec(NumBr,new TageMeta)
  val ittage = new IttageMeta
  val phr    = new PhrMeta
}

class BpuRedirect extends BpuBundle {
  val cfiPc     = UInt(PcWidth.W)
  val target    = UInt(PcWidth.W)
  val taken     = Bool()
  val rasAction = UInt(2.W)
  val meta      = new BpuRedirectMeta
}


// Backend & Ftq -> Bpu
class BpuTrain extends BpuBundle {
  val fetchBlockInfo = new FtqFetchBlcokEntry
  val meta = new BpuTrainMeta
}
