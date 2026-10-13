package yanqihu.frontend.bpu.phr

import chisel3._
import chisel3.util._
import utils.QueuePtr
import utils.Rota._

class PhrPtr(historyLength: Int) extends QueuePtr[PhrPtr](historyLength)


class PhrMeta  extends PhrBundle {
  val ptr = new PhrPtr(PhrHistoryLength)
  val lowBits = UInt(PathHashHighWidth.W)
}

/** Path information inserted by a taken CFI prediction. */
class PhrUpdateReq  extends PhrBundle {
  val cfiPc = UInt(pcWidth.W)
  val target = UInt(pcWidth.W)
}

class PhrOverrideReq  extends PhrBundle {
  val taken       = Bool()
  val update = new PhrUpdateReq
}

class PhrRedirectReq  extends PhrBundle{
  val restoreMeta = new PhrMeta
  val overrideReq = new PhrOverrideReq
}


class PhrFoldedHistory(implicit info: FoldedHistoryInfo) extends PhrBundle with PhrHelper {
  require(FoldedLength >= Shamt)

  val foldInfo = info
  val foldedHist: UInt = UInt(FoldedLength.W)

/*
  def needOldestBits: Boolean = info.HistoryLength > info.FoldedLength
  def oldestBitToGetFromPhr: Seq[Int]     = (0 until maxUpdateNum).map(info.HistoryLength - _ - 1)
  def oldestBitPosInFolded:  Seq[Int]     = oldestBitToGetFromPhr.map(_ % info.FoldedLength)
  def oldestBitWrapAround:   Seq[Boolean] = oldestBitToGetFromPhr.map(_ / info.FoldedLength > 0)
  def oldestBitStart:        Int          = oldestBitPosInFolded.head

  def getOldestBitFromGhr(phr: Vec[Bool], histPtr: PhrPtr): Seq[Bool] =
    // TODO: wrap inc for histPtr value
    oldestBitToGetFromPhr.map(i => phr(i))

  // slow path, read bits from phr
  def update(phr: Vec[Bool], histPtr: PhrPtr, hashHigh: UInt, num: Int, shiftBits: UInt): PhrFoldedHistory = {
    val oldestBits = VecInit(getOldestBitFromGhr(phr, histPtr))
    update(oldestBits, num, shiftBits, hashHigh)
  }
*/

  def foldFromPhr(logicalPhr: UInt): PhrFoldedHistory= {
    require(logicalPhr.getWidth == MaxHistLens)
    val res = Wire(new PhrFoldedHistory)
    res.foldedHist:= computeFolded(logicalPhr, FoldedLength, HistoryLength)

    res
  }

  def update(logicalPhr: UInt, hash: UInt): PhrFoldedHistory = {
    require(hash.getWidth == PathHashWidth)
    require(logicalPhr.getWidth == MaxHistLens)

    def needOldestBits: Boolean = HistoryLength > FoldedLength

    val res = Wire(new PhrFoldedHistory)
    val newFoldedHist = if(needOldestBits){
      val res = Wire(Vec(FoldedLength, Bool()))

      (0 until PathHashHighWidth).foreach{ i =>
        res(i+Shamt) := foldedHist(i)
      }

      (0 until Shamt).foreach{ i =>
        //res(i) := evictedBits(i)
      }
      res.asUInt
    }else{
      Cat(foldedHist(FoldedLength-1, Shamt), 0.U(Shamt.W))
    }

    val hashFolded = computeFolded(hash, FoldedLength, HistoryLength)
    res.foldedHist := newFoldedHist ^ hashFolded
    res
  }
}




class PhrAllFoldedHistories extends PhrBundle{
  val allHist = MixedVec(AllFoldedHistoryInfo.toSeq.sortBy(_.asTuple).map{ flodInfo =>
    implicit val info = flodInfo
    new PhrFoldedHistory
  })

  // 貌似是一种匹配
  def getHistWithInfo(info: FoldedHistoryInfo): PhrFoldedHistory = {
    val selected = this.allHist.filter(_.foldInfo.equals(info))
    require(selected.length == 1)
    selected.head
  }

  def update(logicalPhr: UInt, hash: UInt): PhrAllFoldedHistories = {
    require(hash.getWidth == PathHashWidth)
    require(logicalPhr.getWidth == MaxHistLens)

    val res = Wire(new PhrAllFoldedHistories)
    for (i <- res.allHist.indices) {
      res.allHist(i) := this.allHist(i).update(logicalPhr, hash)
    }
    res
  }
/*
  def display(cond: Bool): Unit =
    for (h <- hist) {
      XSDebug(
        cond,
        p"hist len ${h.info.HistoryLength}, folded len ${h.info.FoldedLength}, value ${Binary(h.foldedHist)}\n"
      )
    }
*/
}

object PhrAllFoldedHistories{
 def foldFromPhr(logicalPhr: UInt): PhrAllFoldedHistories = {
    val res = Wire(new PhrAllFoldedHistories)
    res.allHist.foreach(_.foldFromPhr(logicalPhr))
    res
  }
}

