package yanqihu.frontend.ftq

import chisel3._
import chisel3.util._
import yanqihu.frontend.HasFrontendParameters

case class FtqParameters(
  ftqSize: Int = 32
) {
}

trait HasFtqParameters extends HasFrontendParameters{
}