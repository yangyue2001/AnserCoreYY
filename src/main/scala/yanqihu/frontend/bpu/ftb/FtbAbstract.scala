package yanqihu.frontend.bpu.ftb

import chisel3._
import chisel3.util._


abstract class FtbBundle  extends Bundle with HasFtbParameters
abstract class FtbModule  extends Module with HasFtbParameters