package yanqihu.frontend

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.BpuParameters
import yanqihu.frontend.ifu.IfuParameters
import yanqihu.frontend.ftq.FtqParameters
import yanqihu.HasYQHParameters

case class FrontendParameters(
    bpuParameters:            BpuParameters = BpuParameters(),
    ftqParameters:            FtqParameters = FtqParameters(),
    ifuParameters:            IfuParameters = IfuParameters(),
    frontendBlockBytes: Int = 24, //fetch and predirect
) {
}


trait HasFrontendParameters extends HasYQHParameters {
  def frontendParameters = coreParams.frontendParameters

  def FtqSize = frontendParameters.ftqParameters.ftqSize
  def FtqSizeWidth = log2Ceil(FtqSize)
}

