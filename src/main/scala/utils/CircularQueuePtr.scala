package utils
import chisel3._
import chisel3.util._


class CircularQueuePtr[T <: CircularQueuePtr[T]](val entries: Int) extends Bundle {
  val PTR_WIDTH = log2Up(entries)
  val flag = Bool()
  val value = UInt(PTR_WIDTH.W)

  override def toPrintable: Printable = {
    p"$flag:$value"
  }

  final def +(v: UInt): T = {
    val entries = this.entries
    val new_ptr = Wire(this.asInstanceOf[T].cloneType)
    if(isPow2(entries)){
      new_ptr := (Cat(this.flag, this.value) + v).asTypeOf(new_ptr)
    } else {
      val new_value = this.value +& v
      val diff = Cat(0.U(1.W), new_value).asSInt - Cat(0.U(1.W), entries.U.asTypeOf(new_value)).asSInt
      val reverse_flag = diff >= 0.S
      new_ptr.flag := Mux(reverse_flag, !this.flag, this.flag)
      new_ptr.value := Mux(reverse_flag,
        diff.asUInt,
        new_value
      )
    }
    new_ptr
  }

  final def -(v: UInt): T = {
    val flipped_new_ptr = this + (this.entries.U - v)
    val new_ptr = Wire(this.asInstanceOf[T].cloneType)
    new_ptr.flag := !flipped_new_ptr.flag
    new_ptr.value := flipped_new_ptr.value
    new_ptr
  }

  final def === (that: T): Bool = {
    this.flag === that.flag &&
    this.value === that.value
  }

  final def === (that: UInt): Bool = this.value === that

  final def =/= (that: T): Bool = {
    this.flag =/= that.flag ||
    this.value =/= that.value
  }

  final def > (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value > that.value
    differentFlag ^ compare
  }

  final def < (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value < that.value
    differentFlag ^ compare
  }

  final def >= (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value >= that.value
    differentFlag ^ compare
  }

  final def <= (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value <= that.value
    differentFlag ^ compare
  }

  def toOH: UInt = UIntToOH(value, entries)
}








//No Circular flag
class QueuePtr[T <: QueuePtr[T]](val entries: Int) extends Bundle {
  val PTR_WIDTH = log2Up(entries)
  val value = UInt(PTR_WIDTH.W)

  override def toPrintable: Printable = {
    p"$value"
  }

  final def +(v: UInt): T = {
    val entries = this.entries
    val new_ptr = Wire(this.asInstanceOf[T].cloneType)
    if(isPow2(entries)){
      new_ptr.value := (this.value + v)(PTR_WIDTH-1, 0)
    } else {
      val new_value = (this.value +& v)(PTR_WIDTH, 0)
      val diff = Cat(0.U(1.W), new_value).asSInt - Cat(0.U(1.W), entries.U.asTypeOf(new_value)).asSInt
      val reverse_flag = diff >= 0.S
      new_ptr.value := Mux(reverse_flag,
        diff.asUInt,
        new_value
      )
    }
    new_ptr
  }


  def toOH: UInt = UIntToOH(value, entries)
}






class HighRunPtr[T <: HighRunPtr[T]](val entries: Int) extends Bundle {
  val PTR_WIDTH = log2Ceil(entries)
  val gap = (1 << PTR_WIDTH) - entries
  val GAP_WIDTH = log2Ceil(gap)
  override def toPrintable: Printable = {
    p"$value"
  }
  require(gap !=1)
  require(entries > 1)
  require(gap == 0 || isPow2(gap),
    s"HighRunPtr requires entries to be high-contiguous, like 6, 12, 14, 24,28; got $entries")

  //hardware
  val value = UInt(PTR_WIDTH.W)

  final def +(v: UInt): T = {
    val entries = this.entries
    val newPtr = Wire(this.asInstanceOf[T].cloneType)
    if(isPow2(entries)){
      newPtr.value := (this.value + v)(PTR_WIDTH-1, 0)
    } else {
      assert( v <= gap.U)
      val nxtPtr = (this.value + v)(PTR_WIDTH-1, 0)
      val entriesValue = entries.U(PTR_WIDTH.W)
      val wrap = nxtPtr(PTR_WIDTH-1, GAP_WIDTH) === entriesValue(PTR_WIDTH-1, GAP_WIDTH)
      val nxtPtrLow = nxtPtr(GAP_WIDTH-1,0)
      val nxtPtrHigh = nxtPtr(PTR_WIDTH-1, GAP_WIDTH) & Fill(PTR_WIDTH-GAP_WIDTH, !wrap)
      newPtr.value := Cat(nxtPtrHigh, nxtPtrLow)
    }
    newPtr
  }

  final def -(v: UInt): T = {
    val newPtr = Wire(this.asInstanceOf[T].cloneType)
    if(isPow2(entries)){
      newPtr.value := (this.value - v)(PTR_WIDTH-1, 0)
    } else {
      assert( v<= gap.U)
      val nxtPtr = (this.value - v)(PTR_WIDTH-1, 0)
      val entriesValue = entries.U(PTR_WIDTH.W)
      val wrap = nxtPtr(PTR_WIDTH-1, GAP_WIDTH) === entriesValue(PTR_WIDTH-1, GAP_WIDTH)
      val nxtPtrLow = nxtPtr(GAP_WIDTH-1,0)
      val nxtPtrHigh = Cat(nxtPtr(PTR_WIDTH-1, GAP_WIDTH+1), !wrap)
      newPtr.value := Cat(nxtPtrHigh, nxtPtrLow)
    }

    newPtr
  }

}


trait HasCircularQueuePtrHelper {

  def isEmpty[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): Bool = {
    enq_ptr === deq_ptr
  }

  def isFull[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): Bool = {
    (enq_ptr.flag =/= deq_ptr.flag) && (enq_ptr.value === deq_ptr.value)
  }

  def distanceBetween[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): UInt = {
    assert(enq_ptr.entries == deq_ptr.entries)
    Mux(enq_ptr.flag === deq_ptr.flag,
      enq_ptr.value - deq_ptr.value,
      enq_ptr.entries.U + enq_ptr.value - deq_ptr.value)
  }

  def hasFreeEntries[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): UInt = {
    val free_deq_ptr = enq_ptr
    val free_enq_ptr = WireInit(deq_ptr)
    free_enq_ptr.flag := !deq_ptr.flag
    distanceBetween(free_enq_ptr, free_deq_ptr)
  }

  def circularRangMask[T <: CircularQueuePtr[T]](startPtr: T, endPtr: T): UInt ={
    require(startPtr.entries == endPtr.entries)

    val numEntries = startPtr.entries
    val startLowerMask = UIntToOH(startPtr.value, numEntries) - 1.U
    val endLowerMask = UIntToOH(endPtr.value, numEntries) - 1.U

    //[start, end)
    val rangMask = startLowerMask ^ endLowerMask

    Mux(
      startPtr.flag === endPtr.flag,
      rangMask,
      ~rangMask
    )
  }

  def isAfter[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left > right

  def isBefore[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left < right

  def isNotAfter[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left <= right

  def isNotBefore[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left >= right
}
