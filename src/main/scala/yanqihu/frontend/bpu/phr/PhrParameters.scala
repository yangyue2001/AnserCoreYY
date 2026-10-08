package yanqihu.frontend.bpu.phr

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.NamedTuple
import yanqihu.frontend.bpu.HasBpuParameters

class FoldedHistoryInfo(
    val HistoryLength: Int,
    val FoldedLength:  Int
) extends NamedTuple[(Int, Int)] {
  require(HistoryLength > 0, "HistoryLength must be > 0")
  require(FoldedLength > 0, "FoldedLength must be > 0")

  def asTuple: (Int, Int) =
    (HistoryLength, FoldedLength)
}

case class PhrParameters(
  shamt: Int = 2,
  pathHashWidth: Int = 14,
  ftqFullFix: Int =  2,
){
  def nextMultipleOf(number: Int, factor: Int): Int = (number + factor - 1) / factor * factor

  require(pathHashWidth % shamt == 0)
}

trait HasPhrParameters extends HasBpuParameters{
  def phrParameters = bpuParameters.phrParameters

  def Shamt = phrParameters.shamt
  def PathHashWidth     = phrParameters.pathHashWidth
  def PathHashHighWidth = PathHashWidth - Shamt
  def PathHashHighChunks = (phrParameters.nextMultipleOf(PathHashHighWidth, Shamt) / Shamt)

  def MaxHistLens       = MaxTableHistoryLength
  def MaxHistChunks = (phrParameters.nextMultipleOf(MaxHistLens, Shamt) / Shamt)

  def FtqFullFix = phrParameters.ftqFullFix
  def HistoryLengthRaw = MaxHistLens + (FtqSize+ FtqFullFix)*Shamt
  def PhrHistoryLength = phrParameters.nextMultipleOf(HistoryLengthRaw, Shamt) // 对齐方便处理

  def PhrHistoryChunks = PhrHistoryLength / Shamt
  //per FoldedHistoryInfo
  def HistoryLength(implicit info: FoldedHistoryInfo): Int = info.HistoryLength
  def FoldedLength(implicit info: FoldedHistoryInfo): Int = info.FoldedLength
}