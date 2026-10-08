package yanqihu.frontend.ifu

import chisel3._
import chisel3.util._
import yanqihu.frontend.FtqToIfuIO
import yanqihu.frontend.IfuToFtqIO
import yanqihu.frontend.IfuToBackendIO



class Ifu extends IfuModule {
  class IfuIO extends IfuBundle {
    val redirectFlush = Bool()
    val fromFtq = Flipped(new FtqToIfuIO)
    val toFtq = Flipped(new IfuToFtqIO)
    val toBackend = Flipped(new IfuToBackendIO)
  }
  val io: IfuIO = IO(new IfuIO)
}