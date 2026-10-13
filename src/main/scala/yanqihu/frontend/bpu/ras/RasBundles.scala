package yanqihu.frontend.bpu.ras

import chisel3._
import chisel3.util._
import utils.QueuePtr


class RasPtr(numEntrys: Int) extends QueuePtr[RasPtr](numEntrys)

class RasMeta extends RasBundle{
  val sp      = new RasPtr(NumEntrys)
  val topAddr = UInt(pcWidth.W)
}

class RasUpdateReq extends RasBundle {
  val pushPc    = UInt(pcWidth.W)
}

class RasRecoverReq extends RasBundle {
  val meta     = new RasMeta
  val update   = new RasUpdateReq
}

class RasPrediction extends RasBundle {
  val topRetAddr = UInt(pcWidth.W)
}