package yanqihu.frontend.ifu

import chisel3._
import chisel3.util._

abstract class IfuBundle  extends Bundle with HasIfuParameters

abstract class IfuModule  extends Module with HasIfuParameters

