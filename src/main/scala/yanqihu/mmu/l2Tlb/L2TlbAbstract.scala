package yanqihu.mmu.l2Tlb


import chisel3._
import chisel3.util._

abstract class L2TlbBundle extends Bundle with HasL2TlbParameters
abstract class L2TlbModule extends Module with HasL2TlbParameters