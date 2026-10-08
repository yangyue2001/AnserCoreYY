package yanqihu.frontend.bpu

import chisel3._
import chisel3.util._
import yanqihu.frontend.bpu.ftb.FtbParameters
import yanqihu.frontend.bpu.ittage.IttageParameters
import yanqihu.frontend.bpu.phr.PhrParameters
import yanqihu.frontend.bpu.ras.RasParameters
import yanqihu.frontend.bpu.uftb.UftbParameters
import yanqihu.frontend.HasFrontendParameters
import yanqihu.frontend.bpu.tage.TageParameters

case class BpuParameters(
  rasParameters: RasParameters = RasParameters(),
  uftbParameters: UftbParameters = UftbParameters(),
  ftbParameters: FtbParameters = FtbParameters(),
  tageParameters: TageParameters = TageParameters(),
  ittageParameters: IttageParameters = IttageParameters(),
  phrParameters: PhrParameters = PhrParameters(),
) {
}

trait HasBpuParameters extends HasFrontendParameters{
  def bpuParameters: BpuParameters = frontendParameters.bpuParameters

  def NumBr = 2

  def AllFoldedHistoryInfo =
    bpuParameters.tageParameters.tableInfos.map {
      _.getFoldedHistoryInfoSet
    }.reduce(_ ++ _) ++
    bpuParameters.ittageParameters.tableInfos.map {
      _.getFoldedHistoryInfoSet
    }.reduce(_ ++ _)

  def MaxTableHistoryLength: Int = (
    bpuParameters.tageParameters.tableInfos.map(_.historyLength) ++
    bpuParameters.ittageParameters.tableInfos.map(_.historyLength)
  ).max

}