package yanqihu.frontend

import chisel3._
import chisel3.util._

import yanqihu.frontend.ifu.HasIfuParameters


trait FrontendHelper {
  this: HasFrontendParameters =>

  def getPageEndSlot(startPc: UInt): (Bool, UInt) = {
    require(startPc.getWidth == PcWidth)
    val pageSlots = pageOffsetWidth / InstOffsetWidth

    val fullPc = Cat(startPc, 0.U(1.W))
    val startSlotInPage = fullPc(pageOffsetWidth-1, 1)

    val crossPage = startSlotInPage > (pageSlots - FetchBlockNumSlots).U
    val pageEndSlot = Fill(FetchBlockOffsetWidth, 1.U(1.W)) - startSlotInPage(FetchBlockOffsetWidth-1, 0)

    (crossPage, pageEndSlot)
  }

  def fallThroughPagePc(startPc: UInt): UInt ={
    require(startPc.getWidth == PcWidth)
    val fullPc = Cat(startPc, 0.U(1.W))
    val nextPagePc = fullPc(vaddrWidth-1, pageOffsetWidth) + 1.U
    Cat(nextPagePc, 0.U(pageOffsetWidth.W))(vaddrWidth-1, 1)
  }
}