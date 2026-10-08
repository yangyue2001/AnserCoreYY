package yanqihu.frontend.bpu.ras

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.HasBpuParameters

case class RasParameters(
    numEntrys: Int = 16,
) {
}

trait HasRasParameters extends HasBpuParameters {
  def rasParameters = bpuParameters.rasParameters

  def NumEntrys = rasParameters.numEntrys
  def NumEntrysWidth = log2Ceil(NumEntrys)
}