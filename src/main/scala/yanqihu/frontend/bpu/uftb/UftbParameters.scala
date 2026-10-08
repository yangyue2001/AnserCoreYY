package yanqihu.frontend.bpu.uftb

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.HasBpuParameters

case class UftbParameters(
  numWays: Int = 32,
  tagWidth: Int = 16,
  ctrWidth: Int = 2,
) {
}

trait HasUftbParameters extends HasBpuParameters{
  def uftbParameters= bpuParameters.uftbParameters

  def NumWays = uftbParameters.numWays
  def TagWidth = uftbParameters.tagWidth
  def CtrWidth = uftbParameters.ctrWidth
}
