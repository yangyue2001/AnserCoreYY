package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._

trait ICacheHelper{
  this: HasICacheParameters =>

  def getTagBankIndex(setIdx: UInt): UInt = {
    require(setIdx.getWidth == SetIdxWidth)
    setIdx(TagBankIdxWidth-1, 0)
  }

  def getTag(physicalPc: UInt): UInt = {
    val fullPc = Cat(physicalPc, 0.U(InstOffsetWidth.W))
    require(fullPc.getWidth == paddrWidth)

    fullPc(paddrWidth-1, SetIdxWidth)
  }

  def getSetIndex(pc: UInt): UInt = {
    require(pc.getWidth == pcWidth)
    val fullPc = Cat(pc, 0.U(InstOffsetWidth.W))
    fullPc(SetIdxWidth+OffsetWidth-1, OffsetWidth)
  }

  def getTagSetIndex(setIdx: UInt): UInt = {
    require(setIdx.getWidth == SetIdxWidth)
    setIdx(SetIdxWidth-1, TagBankIdxWidth)
  }

  def isCrossCacheline(pc: UInt): Bool = {
    require(pc.getWidth == pcWidth)
    val fullPc = Cat(pc, 0.U(InstOffsetWidth.W))
    val cachelineOffset = fullPc(OffsetWidth-1,1)
    cachelineOffset > ((CachelineBytes - FrontendBlockBytes)/MinInstBytes).U
  }
  //def getData
}