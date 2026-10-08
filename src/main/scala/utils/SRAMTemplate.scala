package utils

import chisel3._
import chisel3.util._

class SRAMMacro1RW(
    set: Int,
    dataBits: Int,
) extends Module {
  val io = IO(new Bundle{
    val en    = Input(Bool())
    val wen   = Input(Bool()) // 1: write, 0: read
    val addr  = Input(UInt(log2Ceil(set).W))
    val wdata = Input(UInt(dataBits.W))
    val rdata = Output(UInt(dataBits.W))
  })
  //require
  require(set > 0)
  require(dataBits > 0)

  private val mem = SyncReadMem(set, UInt(dataBits.W))

  private val ren = io.en && !io.wen
  private val wen = io.en && io.wen

  when (wen) {
    mem.write(io.addr, io.wdata)
  }

  io.rdata := mem.read(io.addr, ren)

}


class SinglePortSRAMTemplate[T <: Data]
(
  gen: T, set: Int,
  shouldReset: Boolean = false, holdRead: Boolean = false,
  externalReset: Boolean = false,
  resetValue: Option[T] = None
) extends Module {
  val io = IO(new Bundle {
    val resetReq = Input(Bool())
    val en    = Input(Bool())
    val wen   = Input(Bool()) // 1: write, 0: read
    val addr  = Input(UInt(log2Ceil(set).W))
    val wdata = Input(gen.cloneType)
    val rdata = Output(gen.cloneType)
    val resetDown = Output(Bool())
  })
  val resetEn = shouldReset || externalReset

  val resetState = WireDefault(false.B)
  val resetSet = WireDefault(0.U(log2Ceil(set).W))
  //reset
  if (resetEn) {
    val resetStateReg = RegInit(shouldReset.B)
    val extResetReq = if(externalReset) io.resetReq else false.B

    val (counterValue, resetFinish) = Counter(resetStateReg, set)

    when (resetFinish || extResetReq) { resetStateReg := !resetFinish }

    resetState := resetStateReg
    resetSet := counterValue
  }


  val resetData = resetValue.getOrElse(0.U.asTypeOf(gen))

  assert(!(resetState && io.en), "SRAM access during reset")

  val en = if(resetEn) {io.en || resetState} else {io.en}

  val wen = if(resetEn) {io.wen || resetState} else{io.wen}
  val addr = if(resetEn) {Mux(resetState, resetSet, io.addr)} else {io.addr}
  val wdata = if(resetEn){Mux(resetState, resetData, io.wdata).asUInt} else{io.wdata}
  val ren = io.en && !io.wen
  //memory
  val dataBits = gen.getWidth
  val array = Module(new SRAMMacro1RW(set, dataBits))
  array.io.en    := en
  array.io.wen   := wen
  array.io.addr  := addr
  array.io.wdata := wdata

  val rawResp = array.io.rdata.asTypeOf(gen)
  val resp = Wire(gen.cloneType)

  if (holdRead) {
    val readRespValid = RegNext(ren)
    val respDelay = RegEnable(rawResp,readRespValid)
    resp := Mux(readRespValid, rawResp, respDelay)
  } else {
    resp := rawResp
  }

  //output
  io.resetDown := !resetState
  io.rdata     := resp

}
