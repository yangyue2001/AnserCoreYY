package yanqihu.frontend.ifu
import chisel3._
import chisel3.util._

import yanqihu.frontend.ifu.ibuffer.IBufferParameters
import yanqihu.frontend.ifu.icache.ICacheParameters
import yanqihu.frontend.HasFrontendParameters

case class IfuParameters(
  ibuffer: IBufferParameters = IBufferParameters(),
  icache: ICacheParameters = ICacheParameters(),
) {
}

trait HasIfuParameters extends HasFrontendParameters {
  def ifuParameters: IfuParameters = frontendParameters.ifuParameters

  def InstBitsWidth = FrontendBlockBytes * 8
}