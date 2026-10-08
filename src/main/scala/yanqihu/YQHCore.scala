package yanqihu

import chisel3._
import chisel3.util._
import yanqihu.frontend._

class YQHCore extends Module{
  implicit val p: Parameters = Parameters()

  val frontend = Module(new Frontend)
}