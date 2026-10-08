package yanqihu.frontend.bpu.phr

import chisel3._
import chisel3.util._
import scala.math.min
import utils.ParallelXOR
import utils.Rota._
import utils.Rota


trait PhrHelper extends HasPhrParameters{
  def computeFolded(bits: UInt, compLen: Int, histLen: Int): UInt = {
    require (histLen > 0)
    val width = bits.getWidth

    if(width >= histLen){
      val effectiveBit = bits(histLen-1, 0)
      val nChunks      = (histLen + compLen - 1) / compLen
      val histChunks   = (0 until nChunks) map { i => effectiveBit(min((i + 1) * compLen, histLen) - 1, i * compLen) }
      ParallelXOR(histChunks)
    }else {
      val nChunks = (width + compLen - 1) / compLen
      val histChunks = (0 until nChunks) map { i => bits(min((i + 1) * compLen, width) - 1, i * compLen) }
      ParallelXOR(histChunks)
    }
  }

  def computePhrUpdate(req: PhrUpdateReq): UInt ={
    val pathHash =  Cat(0.U(2.W),req.cfiPc(8,0), 0.U(4.W)) ^ req.target(15,1)
    pathHash
  }

  def readLogicalPhr(phr: Vec[UInt], ptr: PhrPtr): UInt = {
    require(phr.getWidth == PhrHistoryChunks)

    val read = Wire(Vec(MaxHistChunks, UInt(Shamt.W)))
    (0 until MaxHistChunks).foreach{ i =>
      val phrRota = Rota(phr, i+1, Right)
      read(i) := phrRota(ptr.value)
    }

    read.asUInt(MaxHistLens, 0)
  }

}
