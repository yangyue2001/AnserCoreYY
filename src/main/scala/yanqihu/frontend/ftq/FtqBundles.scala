package yanqihu.frontend.ftq

import chisel3._
import chisel3.util._

import utils.CircularQueuePtr
import yanqihu.frontend.FrontendParameters
import yanqihu.frontend.bpu.ftb.FtbMeta
import yanqihu.frontend.bpu.ras.RasMeta
import yanqihu.frontend.bpu.phr.PhrMeta
import yanqihu.frontend.bpu.tage.TageMeta
import yanqihu.frontend.bpu.ittage.IttageMeta
import yanqihu.Parameters
import yanqihu.frontend.CfiAttribute


class FtqPtr(implicit p: Parameters)  extends CircularQueuePtr[FtqPtr](p.frontendParameters.ftqParameters.ftqSize)

class FtqFetchBlcokEntry extends FtqBundle {
  val startAddr       = UInt(PcWidth.W)
  val target      = UInt(PcWidth.W)
  val endOffset   = UInt(FetchBlockOffsetWidth.W)
  val taken       = Bool()
  val mispredict = Bool()
  val cfiAttribute = new CfiAttribute
}


class FtqSpecEntry extends FtqBundle {
  val rasMeta = new RasMeta
  val phrMeta = new PhrMeta
}


class FtqMetaEntry extends FtqBundle {
  val ftbMeta = new FtbMeta
  val tageMeta = new TageMeta
  val ittageMeta = new IttageMeta
}


class FtqEntry extends FtqBundle {
  val valid = Bool()
  val state = FtbState() // 状态  invaild prefetch fetch  commit
  val spec = new FtqSpecEntry  // for redirect 的时候 恢复用的
  val pred = new FtqFetchBlcokEntry
  val meta = new FtqMetaEntry

  def isLookup = state === FtbState.lookup
  def isFetch = state === FtbState.fetch
  def isPredecode = state === FtbState.predecode
  def isWaitCommit = state === FtbState.waitCommit
}

object FtbState extends ChiselEnum{
  val lookup, fetch, predecode, waitCommit = Value
}

class FtqBackendReadResp extends FtqBundle{
  val fetchPc = UInt(PcWidth.W)
  val predTarget = UInt(PcWidth.W)
  val predTaken = Bool()
}


class FtqLookup extends FtqBundle {
  val lookupPc = UInt(PcWidth.W)
  val ftqPtr = new FtqPtr
}

class FtqFetch extends FtqBundle {
  val fetchPc = UInt(PcWidth.W)
  val ftqPtr = new FtqPtr
  val parallel = Bool()
}
