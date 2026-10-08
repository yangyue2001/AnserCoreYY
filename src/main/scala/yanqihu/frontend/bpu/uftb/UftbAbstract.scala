package yanqihu.frontend.bpu.uftb

import chisel3._
import chisel3.util._

abstract class UftbBundle extends Bundle with HasUftbParameters

abstract class UftbModule extends Module with HasUftbParameters