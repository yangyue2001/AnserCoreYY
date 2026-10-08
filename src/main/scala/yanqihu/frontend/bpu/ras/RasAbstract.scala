package yanqihu.frontend.bpu.ras

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu._


abstract class RasBundle  extends BpuBundle with HasRasParameters

abstract class RasModule  extends BpuModule with HasRasParameters