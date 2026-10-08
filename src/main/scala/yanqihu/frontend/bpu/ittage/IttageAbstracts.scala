package yanqihu.frontend.bpu.ittage

import chisel3._
import chisel3.util._


abstract class IttageBundle  extends Bundle with HasIttageParameters

abstract class IttageModule  extends Module with HasIttageParameters
