package yanqihu.frontend.ftq

import chisel3._
import chisel3.util._



abstract class FtqBundle  extends Bundle with HasFtqParameters
abstract class FtqModule  extends Module with HasFtqParameters