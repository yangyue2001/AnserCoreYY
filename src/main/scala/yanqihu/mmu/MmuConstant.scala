package yanqihu.mmu

import chisel3._
import chisel3.util._

sealed trait SvMode {
    def Levels: Int
}

object SvMode {
  case object Sv39 extends SvMode {val Levels = 3}
  case object Sv48 extends SvMode {val Levels = 4}
  case object Sv57 extends SvMode {val Levels = 5}
}

object Pbmt extends ChiselEnum{
  val pma = Value("b00".U(2.W))
  val nc = Value("b01".U(2.W))
  val io = Value("b10".U(2.W))
}

object MmuConstant{
  val PageOffsetWidth:Int = 12
  val LevelVpnWidth = 9
  val NapotWidth: Int = 1
  val ReservedWidth: Int   = 7
  val PpnWidth: Int = 44
  val RswWidth: Int        = 2
  val FlagWidth: Int       = 8
  val AlignmentGranule: Int = 4096 //4 KB
  val SatpAsidWidth: Int = 16

  //priv Mode
  val MMode = 3
  val HSMode = 2
  val SMode = 1
  val UMode = 0
  //Colt
  val ColtFactor = 4
  val ColtFactorWidth = log2Ceil(ColtFactor)
}

