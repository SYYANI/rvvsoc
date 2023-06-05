package rvvsoc.core

import chisel3._
import chisel3.util._
import rvvsoc.utils.Info

class RegFileIO() extends Bundle {
  val raddr1 = Input(UInt(5.W))
  val raddr2 = Input(UInt(5.W))
  val rdata1 = Output(UInt(32.W))
  val rdata2 = Output(UInt(32.W))
  val wen    = Input(Bool())
  val waddr  = Input(UInt(5.W))
  val wdata  = Input(UInt(32.W))
}

class RegFile() extends Module {
  val io = IO(new RegFileIO)
  val regs = Mem(32, UInt(32.W))
  io.rdata1 := Mux(io.raddr1.orR, regs(io.raddr1), 0.U)
  io.rdata2 := Mux(io.raddr2.orR, regs(io.raddr2), 0.U)
  when(io.wen & io.waddr.orR) {
    regs(io.waddr) := io.wdata
  }
  final val AbiMap = Seq(
    "zero", //  x0
    "  ra", //  x1
    "  sp", //  x2
    "  gp", //  x3
    "  tp", //  x4
    "  t0", //  x5
    "  t1", //  x6
    "  t2", //  x7
    "  s0", //  x8
    "  s1", //  x9
    "  a0", // x10
    "  a1", // x11
    "  a2", // x12
    "  a3", // x13
    "  a4", // x14
    "  a5", // x15
    "  a6", // x16
    "  a7", // x17
    "  s2", // x18
    "  s3", // x19
    "  s4", // x20
    "  s5", // x21
    "  s6", // x22
    "  s7", // x23
    "  s8", // x24
    "  s9", // x25
    " s10", // x26
    " s11", // x27
    "  t3", // x28
    "  t4", // x29
    "  t5", // x30
    "  t6", // x31
  )

  def dump(regs: Mem[UInt], useAbiMap: Boolean = true) =
    (0 until 32).foldLeft(Printable.pack("[RegFile.dump]: \n"))((s, i) =>
      s + cf"${if (useAbiMap) AbiMap(i) else "x%02d".format(i)}: ${regs.read(i.U)}"
        + cf"${if (i % 8 != 7) ", " else "\n"}",
    )

//  Info(dump(regs))
}
