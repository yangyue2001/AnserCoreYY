package yanqihu.frontend.ifu.icache

import chisel3._
import chisel3.util._

import utils.SinglePortSRAMTemplate

class ICache extends ICacheModule{
  class ICacheIO extends ICacheBundle{
    val flush = Input(Bool())
    val fencei = Input(Bool())
    //lookup
    val lookup = Flipped(Decoupled(UInt(pcWidth.W)))
    //val tlbResp = Input(new TlbResp)
    //val pmpResp = Input(new PmpResp)
    //fetch
    val fetch = Flipped(Decoupled(new ICacheFetchReq))
    val inst = Output(UInt((InstBitsWidth).W))

    //memory
  }
  val io = IO(new ICacheIO)

//alias


//------------------module-------------------------------//
  val wayLookup = Module(new ICacheWayLookup)
  val mshrUnit = Module(new ICacheMissUnit)


//------------------lookup-------------------------------//
  val icacheTag = Seq.fill(TagNumBanks){
    Seq.fill(numWays){
      Module(new SinglePortSRAMTemplate(
        gen = UInt(tagWidth.W),
        set = TagSetIdxWidth,
        holdRead = true,
      ))
    }
  }
  val icacheValid = Seq.fill(TagNumBanks){
    Seq.fill(numWays){
     RegInit(false.B)
    }
  }

  val icacheData = Seq.fill(DataNumBanks){
    Seq.fill(numWays){
      Module(new SinglePortSRAMTemplate(
        gen = UInt(DataWidth.W),
        set = DataSetIdxWidth,
        holdRead = true,
      ))
    }
  }




//-------------------fetch-------------------------------//
}
