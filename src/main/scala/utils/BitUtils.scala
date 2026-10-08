package utils

import chisel3._
import chisel3.util._

import scala.math.min
import os.read

object SignExt {
  def apply(a: UInt, len: Int): UInt = {
    val aLen = a.getWidth
    require(len >= aLen)
    val signBit = a(aLen-1)
    if (aLen == len) a else Cat(Fill(len - aLen, signBit), a)
  }
}

object ZeroExt {
  def apply(a: UInt, len: Int): UInt = {
    val aLen = a.getWidth
    require(len >= aLen)
    if (aLen == len) a(len-1,0) else Cat(0.U((len - aLen).W), a)
  }
}


object HighMask {
  def apply(a: UInt): UInt = {
    apply(a.asBools)
  }
  def apply(a: UInt, includeSelf: Boolean): UInt = {
    apply(a.asBools, includeSelf)
  }

  def apply(a: Seq[Bool]): UInt = {
      apply(a, includeSelf = false)
  }
  def apply(a: Seq[Bool], includeSelf: Boolean): UInt = {
    require(a.nonEmpty, "HighMask input must not be empty")
    assert(
      PopCount(a) === 1.U,
      "HighMask expects exactly one bit set"
    )

    val result = Wire(Vec(a.length, Bool()))
    var lowerOr = false.B
    for (i <- a.indices) {
      if(includeSelf){
        lowerOr = lowerOr || a(i)
        result(i) := lowerOr
      } else {
        result(i) := lowerOr
        lowerOr = lowerOr || a(i)
      }
    }
    result.asUInt
  }

  def apply(a: Vec[Bool]): UInt = {
    apply(a.toSeq)
  }
  def apply(a: Vec[Bool], includeSelf: Boolean): UInt = {
    apply(a.toSeq, includeSelf)
  }
}


object LowMask {
  def apply(a: UInt): UInt = {
    apply(a.asBools)
  }
  def apply(a: UInt, includeSelf: Boolean): UInt = {
    apply(a.asBools, includeSelf)
  }

  def apply(a: Seq[Bool]): UInt = {
      apply(a, includeSelf = false)
  }

  def apply(a: Seq[Bool], includeSelf: Boolean): UInt = {
    require(a.nonEmpty, "LowMask input must not be empty")
    assert(
      PopCount(a) === 1.U,
      "LowMask expects exactly one bit set"
    )
    val result = Wire(Vec(a.length, Bool()))
    var upperOr = false.B
    for (i <- a.indices.reverse) {
      if(includeSelf){
        upperOr = upperOr || a(i)
        result(i) := upperOr
      } else {
        result(i) := upperOr
        upperOr = upperOr || a(i)
      }
    }
    result.asUInt
  }

  def apply(a: Vec[Bool]): UInt = {
    apply(a.toSeq)
  }
   def apply(a: Vec[Bool], includeSelf: Boolean): UInt = {
    apply(a.toSeq, includeSelf)
  }
}

/**
 * SelectFirstN: select n index from bit mask, low bit has high priority.
 */
object SelectFirstN {
  def apply(in: UInt, n: Int ): Vec[UInt] = {
    val sels = Wire(Vec(n, UInt(in.getWidth.W)))
    var mask = in

    for (i <- 0 until n) {
      sels(i) := PriorityEncoderOH(mask)
      mask = mask & ~sels(i)
    }
    sels
  }

  def apply(in: Seq[Bool], n:Int): Vec[UInt] = {
    apply(Cat(in), n)
  }

}

object AlignDown {
  def apply(x: UInt, align: Int ): UInt= {???}
  def apply(x: UInt, align: UInt): UInt= {???}
}

object AlignUp {
  def apply(x: UInt, align: Int ): UInt= {???}
  def apply(x: UInt, align: UInt): UInt= {???}
}


object IsOneHot{
  def apply(x: UInt): Bool = {
    x.orR && AtMostOneHot(x)
  }
}

object AtMostOneHot{
  def apply(x: UInt): Bool ={
    (x & (x - 1.U)) === 0.U
  }
}

object xorFold{
  def apply(x: UInt, inWidth: Int, outWidth: Int): UInt ={
    require(inWidth >= outWidth && outWidth > 0)
    val xWidth = x.getWidth

    val useWidth = math.min(xWidth, inWidth)

    val nChunks = (useWidth + outWidth -1)/ outWidth
    val chunks = (0 until nChunks).map{i =>
      val lo = i * outWidth
      val hi = math.min(lo + outWidth, useWidth) -1
      x(hi, lo).pad(outWidth)
    }

    chunks.reduce(_ ^ _)
  }

  def apply(x: UInt, outWidth: Int): UInt = xorFold(x, x.getWidth, outWidth)
}