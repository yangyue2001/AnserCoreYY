package yanqihu.frontend.bpu.tage

import chisel3._
import chisel3.util._


abstract class TageBundle  extends Bundle with HasTageParameters
abstract class TageModule  extends Module with HasTageParameters