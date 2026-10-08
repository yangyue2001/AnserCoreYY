package yanqihu.frontend.ifu.predecode

import chisel3._
import chisel3.util._


class ExpandedInstruction extends Bundle {
  val bits = UInt(32.W)
}


/* RVCDecoder
  Intended for OpenXiangShan/XiangShan use only,
  assume M-ext, B-ext is implemented, also Zcb-ext as per RVA23

  fsIsOff indicates whether f/d-extention is enabled by CSR, `mstatus.fs === 0.U` for example
*/
class RVCDecoder(x: UInt, fsIsOff: Bool, xLen: Int, fLen: Int, useAddiForMv: Boolean = false) {
  def inst(bits: UInt) = {
    val res = Wire(new ExpandedInstruction)
    res.bits := bits
    res
  }

  def rs1p = Cat(1.U(2.W), x(9,7))
  def rs2p = Cat(1.U(2.W), x(4,2))
  def rs2 = x(6,2)
  def rd = x(11,7)
  def addi4spnImm = Cat(x(10,7), x(12,11), x(5), x(6), 0.U(2.W))
  def lbImm = Cat(x(5), x(6))
  def lhImm = Cat(x(5), 0.U(1.W))
  def lwImm = Cat(x(5), x(12,10), x(6), 0.U(2.W))
  def ldImm = Cat(x(6,5), x(12,10), 0.U(3.W))
  def lwspImm = Cat(x(3,2), x(12), x(6,4), 0.U(2.W))
  def ldspImm = Cat(x(4,2), x(12), x(6,5), 0.U(3.W))
  def swspImm = Cat(x(8,7), x(12,9), 0.U(2.W))
  def sdspImm = Cat(x(9,7), x(12,10), 0.U(3.W))
  def luiImm = Cat(Fill(15, x(12)), x(6,2), 0.U(12.W))
  def addi16spImm = Cat(Fill(3, x(12)), x(4,3), x(5), x(2), x(6), 0.U(4.W))
  def addiImm = Cat(Fill(7, x(12)), x(6,2))
  def jImm = Cat(Fill(10, x(12)), x(8), x(10,9), x(6), x(7), x(2), x(11), x(5,3), 0.U(1.W))
  def bImm = Cat(Fill(5, x(12)), x(6,5), x(2), x(11,10), x(4,3), 0.U(1.W))
  def shamt = Cat(x(12), x(6,2))
  def x0 = 0.U(5.W)
  def ra = 1.U(5.W)
  def sp = 2.U(5.W)

  def q0 = {
    def addi4spn = inst(Cat(addi4spnImm, sp, 0.U(3.W), rs2p, 0x13.U(7.W)))
    def ld = inst(Cat(ldImm, rs1p, 3.U(3.W), rs2p, 0x03.U(7.W)))
    def lw = inst(Cat(lwImm, rs1p, 2.U(3.W), rs2p, 0x03.U(7.W)))
    def fld = inst(Cat(ldImm, rs1p, 3.U(3.W), rs2p, 0x07.U(7.W)))
    def flw = {
      if (xLen == 32) inst(Cat(lwImm, rs1p, 2.U(3.W), rs2p, 0x07.U(7.W)))
      else ld
    }
    def sd = inst(Cat(ldImm >> 5, rs2p, rs1p, 3.U(3.W), ldImm(4,0), 0x23.U(7.W)))
    def sw = inst(Cat(lwImm >> 5, rs2p, rs1p, 2.U(3.W), lwImm(4,0), 0x23.U(7.W)))
    def fsd = inst(Cat(ldImm >> 5, rs2p, rs1p, 3.U(3.W), ldImm(4,0), 0x27.U(7.W)))
    def fsw = {
      if (xLen == 32) inst(Cat(lwImm >> 5, rs2p, rs1p, 2.U(3.W), lwImm(4,0), 0x27.U(7.W)))
      else sd
    }

    def zcb = {
      def lbu = Cat(lbImm, rs1p, 4.U(3.W), rs2p, 0x03.U(7.W))
      def lh = {
        val func3 = Mux(x(6), 1.U(3.W), 5.U(3.W))
        Cat(lhImm, rs1p, func3, rs2p, 0x03.U(7.W))
      }
      def sb = Cat(rs2p, rs1p, 0.U(3.W), 0.U(3.W), lbImm(1, 0), 0x23.U(7.W))
      def sh = Cat(rs2p, rs1p, 1.U(3.W), 0.U(3.W), lhImm(1, 0), 0x23.U(7.W))

      inst(VecInit(Seq(lbu, lh, sb, sh))(x(11, 10)))
    }

    Seq(addi4spn, fld, lw, flw, zcb, fsd, sw, fsw)
  }

  def q1 = {
    def addi = {
      val nop = inst(Cat(0.U(12.W), 0.U(5.W), 0.U(3.W), 0.U(5.W), 0x13.U(7.W)))
      Mux(rd === x0, nop, inst(Cat(addiImm, rd, 0.U(3.W), rd, 0x13.U(7.W))))
    }
    def addiw = inst(Cat(addiImm, rd, 0.U(3.W), rd, 0x1B.U(7.W)))
    def jal = {
      if (xLen == 32) inst(Cat(jImm(20), jImm(10,1), jImm(11), jImm(19,12), ra, 0x6F.U(7.W)))
      else addiw
    }
    def li = inst(Cat(addiImm, x0, 0.U(3.W), rd, 0x13.U(7.W)))
    def addi16sp = inst(Cat(addi16spImm, rd, 0.U(3.W), rd, 0x13.U(7.W)))
    def lui = {
      val me = inst(Cat(luiImm(31,12), rd, 0x37.U(7.W)))
      val zcmop = x(7) && (x(6,2) === 0.U) && (x(12,11) === 0.U)
      val nop = inst(Cat(0.U(12.W), 0.U(5.W), 0.U(3.W), 0.U(5.W), 0x13.U(7.W)))
      Mux(zcmop, nop, Mux(rd === sp, addi16sp, me))
    }
    def j = inst(Cat(jImm(20), jImm(10,1), jImm(11), jImm(19,12), x0, 0x6F.U(7.W)))
    def beqz = inst(Cat(bImm(12), bImm(10,5), x0, rs1p, 0.U(3.W), bImm(4,1), bImm(11), 0x63.U(7.W)))
    def bnez = inst(Cat(bImm(12), bImm(10,5), x0, rs1p, 1.U(3.W), bImm(4,1), bImm(11), 0x63.U(7.W)))
    def arith = {
      def unimp = 0.U // to do
      def srli = Cat(shamt, rs1p, 5.U(3.W), rs1p, 0x13.U(7.W))
      def srai = srli | (1 << 30).U
      def andi = Cat(addiImm, rs1p, 7.U(3.W), rs1p, 0x13.U(7.W))
      def rtype = {
        val funct = VecInit(Seq(0.U, 4.U, 6.U, 7.U, 0.U, 0.U, 0.U, 3.U))(Cat(x(12), x(6,5)))
        val sub = Mux(x(6,5) === 0.U, (1 << 30).U, 0.U)
        val opc = Mux(x(12), Mux(x(6), 0x33.U(7.W), 0x3B.U(7.W)), 0x33.U(7.W))
        def mul = Mux(Cat(x(12), x(6,5)) === 6.U, (1 << 25).U, 0.U)
        def zcaAndMul = Cat(rs2p, rs1p, funct, rs1p, opc) | sub | mul
        def zcb = {
          def zextb = Cat(0xFF.U, rs1p, 7.U(3.W), rs1p, 0x13.U(7.W))
          def not = Cat(0xFFF.U, rs1p, 4.U(3.W), rs1p, 0x13.U(7.W))
          def sextb = Cat(0x604.U, rs1p, 1.U(3.W), rs1p, 0x13.U(7.W))
          def sexth = Cat(0x605.U, rs1p, 1.U(3.W), rs1p, 0x13.U(7.W))
          def zextw = {
            if (xLen == 32) {
              unimp // Illegal instruction in RV32
            } else {
              Cat(4.U, x0, rs1p, 0.U(3.W), rs1p, 0x3B.U(7.W))
            }
          }
          def zexth = {
            val zexth_common = Cat(0x80.U, rs1p, 4.U(3.W), rs1p)
            if (xLen == 32) Cat(zexth_common, 0x33.U(7.W))
            else Cat(zexth_common, 0x3B.U(7.W))
          }
          VecInit(Seq(zextb, sextb, zexth, sexth, zextw, not, unimp, unimp))(x(4,2))
        }

        Mux(Cat(x(12), x(6,5)) === 7.U, zcb, zcaAndMul)
      }

      // c.zext.w => add.uw rdrs1, rdrs1, zero

      inst(VecInit(Seq(srli, srai, andi, rtype))(x(11,10)))
    }

    Seq(addi, jal, li, lui, arith, j, beqz, bnez)
  }

  def q2 = {
    val load_opc = 0x03.U(7.W)
    def slli = inst(Cat(shamt, rd, 1.U(3.W), rd, 0x13.U(7.W)))
    def ldsp = inst(Cat(ldspImm, sp, 3.U(3.W), rd, load_opc))
    def lwsp = inst(Cat(lwspImm, sp, 2.U(3.W), rd, load_opc))
    def fldsp = inst(Cat(ldspImm, sp, 3.U(3.W), rd, 0x07.U(7.W)))
    def flwsp = {
      if (xLen == 32) inst(Cat(lwspImm, sp, 2.U(3.W), rd, 0x07.U(7.W)))
      else ldsp
    }
    def sdsp = inst(Cat(sdspImm >> 5, rs2, sp, 3.U(3.W), sdspImm(4,0), 0x23.U(7.W)))
    def swsp = inst(Cat(swspImm >> 5, rs2, sp, 2.U(3.W), swspImm(4,0), 0x23.U(7.W)))
    def fsdsp = inst(Cat(sdspImm >> 5, rs2, sp, 3.U(3.W), sdspImm(4,0), 0x27.U(7.W)))
    def fswsp = {
      if (xLen == 32) inst(Cat(swspImm >> 5, rs2, sp, 2.U(3.W), swspImm(4,0), 0x27.U(7.W)))
      else sdsp
    }
    def jalr = {
      val mv = {
        if (useAddiForMv) inst(Cat(rs2, 0.U(3.W), rd, 0x13.U(7.W)))
        else inst(Cat(rs2, x0, 0.U(3.W), rd, 0x33.U(7.W)))
      }
      val add = inst(Cat(rs2, rd, 0.U(3.W), rd, 0x33.U(7.W)))
      val jr = inst(Cat(rs2, rd, 0.U(3.W), x0, 0x67.U(7.W)))
      val jr_mv = Mux(rs2.orR, mv, jr)
      val jalr = Cat(rs2, rd, 0.U(3.W), ra, 0x67.U(7.W))
      val ebreak = 0x00100073.U(32.W)
      val jalr_ebreak = inst(Mux(rd.orR, jalr, ebreak))
      val jalr_add = Mux(rs2.orR, add, jalr_ebreak)
      Mux(x(12), jalr_add, jr_mv)
    }
    Seq(slli, fldsp, lwsp, flwsp, jalr, fsdsp, swsp, fswsp)
  }

  def passthrough = inst(x)
  def q3 = Seq.fill(8)(passthrough)


  def decode = {
    val s = VecInit(q0 ++ q1 ++ q2 ++ q3)
    s(Cat(x(1,0), x(15,13)))
  }

  def q0_ill = {
    def immz = !(x(12, 5).orR)
    /* lsq_flsd (lq_fld / sq_fsd) is:
     * - for RV128, lq / sq, is always legal
     * - for RV32/64, fld / fsd, need d-extension and !fsIsOff, otherwise illegal
     * lsw is always legal
     * lsd_flsw is similar to lsq_flsd, just int for RV64/128 and fp for RV32, fp need only f-extension
     */
    def lsq_flsd = if (xLen == 128) false.B else if (fLen >= 64) fsIsOff else true.B
    def lsw = false.B
    def lsd_flsw = if (xLen >= 64) false.B else if (fLen >= 32) fsIsOff else true.B
    def ldst_zcb = x(12) || (x(11) && x(10) && x(6))
    Seq(immz, lsq_flsd, lsw, lsd_flsw, ldst_zcb, lsq_flsd, lsw, lsd_flsw)
  }

  def q1_ill = {
    def rd0 = if (xLen == 32) false.B else rd === 0.U
    def immz = !(x(12) | x(6, 2).orR)
    def mop = !x(11) && x(7)
    def lui_res = immz && !mop
    def zcb_res = x(12, 10).andR && x(6, 3).andR
    Seq(false.B, rd0, false.B, lui_res, zcb_res, false.B, false.B, false.B)
  }

  def q2_ill = {
    def rd0 = rd === 0.U
    // similar to lsq_flsd / lsw / lsq_flsw in q0_ill. But rd === 0.U is reserved for lwsp / ldsp / lqsp
    def lqsp_fldsp = if (xLen == 128) rd0 else if (fLen >= 64) fsIsOff else true.B
    def lwsp = rd0
    def ldsp_flwsp = if (xLen >= 64) rd0 else if (fLen >= 32) fsIsOff else true.B
    // no reserved rd === 0.U for swsp / sdsp / sqsp
    def sqsp_fsdsp = if (xLen == 128) false.B else if (fLen >= 64) fsIsOff else true.B
    def swsp = false.B
    def sdsp_fswsp = if (xLen >= 64) false.B else if (fLen >= 32) fsIsOff else true.B

    def jr_res = !(x(12, 2).orR)
    Seq(false.B, lqsp_fldsp, lwsp, ldsp_flwsp, jr_res, sqsp_fsdsp, swsp, sdsp_fswsp)
  }

  def q3_ill = Seq.fill(8)(false.B)

  def ill = {
    val s = VecInit(q0_ill ++ q1_ill ++ q2_ill ++ q3_ill)
    s(Cat(x(1,0), x(15,13)))
  }
}