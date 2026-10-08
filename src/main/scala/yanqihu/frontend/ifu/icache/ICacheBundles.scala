package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._
import utils.CircularQueuePtr
import yanqihu.Parameters


class ICacheFetchReq extends ICacheBundle{
  val fetchPc = UInt(PcWidth.W)
  val wayMask = Vec(2, UInt(NumWays.W))
  val parallel = Bool()
}


class ICacheRefillReq extends ICacheBundle{
  val setIdx = UInt(SetIdxWidth.W)
  val pTag = UInt(TagWidth.W)
  val wayMask = UInt(NumWays.W)
  val data = UInt(BlockBits.W)
}

class ICacheMissReq extends ICacheBundle{
  val vAlias = UInt(VaddrAliasWidth.W)
  val blkPAddr = UInt(ICacheToL2BeatAddrWidth.W)
  val crossCacheline = Bool()
}

//wayLookup

class WayLookupPtr(implicit p: Parameters) extends CircularQueuePtr[WayLookupPtr](p.frontendParameters.ifuParameters.icache.wayLookupSize)



class WayLookupTlbEntry extends ICacheBundle{
  val pTag = UInt(TagWidth.W)
  val isNC = Bool()
  val isMMIO = Bool()
}


class WayLookupException extends ICacheBundle{
  val pageFault = Bool()
  val accessFault = Bool()
}


class WayLookupExceptionEntry extends ICacheBundle{
  val ptr = UInt(WayLookupWidth.W)
  val except = new WayLookupException
}


class WayLookupEntry extends ICacheBundle{
  val itlb = new WayLookupTlbEntry
  val vSetIdx = UInt(SetIdxWidth.W)
  val crossCacheline = Bool()
  val wayMask = Vec(2, UInt(NumWays.W))
}

class MshrEntry extends ICacheBundle{
  val valid = Bool()
  //val flush = Bool()
  val issue = Bool()
  val waitResp = Bool()
  val paddr = UInt(ICacheToL2BeatAddrWidth.W)
  val vAlias = UInt(VaddrAliasWidth.W)
  val fetch = Bool()
}