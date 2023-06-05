package rvvsoc.devices

import chisel3._
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.util.{Cat, is, log2Up, switch}
import rvvsoc.sysbus._
import rvvsoc.utils.{Debug, Info, Warn}

class RAMSlaveReflector extends SysBusSlave(new DumbBundle) {
  val db = Wire(new DumbBundle)
  io.in <> db
  val mem = Mem(1024, UInt(8.W))
  loadMemoryFromFile(mem, "prog/relu.hex")

  val state = RegInit(0.U(3.W))
  val STATE_IDLE = 0.U(3.W)
  val STATE_READ = 1.U(3.W)
  val STATE_WRITE = 2.U(3.W)

  val vstate = RegInit(Bool(),false.B)
  val vec_store_dat = Reg(UInt(128.W))
  val stored_dat = Reg(UInt(32.W))
  val target_adr = Reg(UInt(22.W))
  val bit_sel = Reg(UInt(4.W))
  val req = Reg(Bool())
  io.out.ack_o := req

  val ans = Seq(Wire(UInt(8.W)),
    Wire(UInt(8.W)),
    Wire(UInt(8.W)),
    Wire(UInt(8.W)))
  for (i <- 0 until 4)
    ans(i) := 0.U

  val vans = Seq(
    Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)),
    Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)),
    Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)),
    Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)), Wire(UInt(8.W)),
  )
  for (i <- 0 until 16)
    vans(i) := 0.U
  vstate := io.out.vec_en_i
  Warn("ram_io.out.vec_en_i: %x, ram_io.out.we_i: %x,io.out.vec_data_i: %x\n", io.out.vec_en_i, io.out.we_i, io.out.vec_data_i)
  when(io.out.cyc_i & io.out.stb_i) {
    req := true.B
    target_adr := io.out.adr_i
    bit_sel := io.out.sel_i
    when(io.out.we_i) {
      vec_store_dat := io.out.vec_data_i
      stored_dat := io.out.dat_i
      state := STATE_WRITE
    }.otherwise(
      state := STATE_READ
    )
  }.otherwise {
    switch(state) {
      is(STATE_WRITE) {
        req := false.B
        state := STATE_IDLE
      }
      is(STATE_READ) {
        req := false.B
        state := STATE_IDLE
      }
    }
  }

  when(state === STATE_WRITE) {
    when(vstate){
      Warn("ram_vec_store_dat: %x\n", vec_store_dat)
      for (i <- 0 until 16) {
        mem(target_adr + i.U(32.W)) := vec_store_dat((i + 1) * 8 - 1, i * 8)
      }
    }.otherwise{
      mem(target_adr) := stored_dat(7, 0)
      mem(target_adr + 1.U(32.W)) := stored_dat(15, 8)
      mem(target_adr + 2.U(32.W)) := stored_dat(23, 16)
      mem(target_adr + 3.U(32.W)) := stored_dat(31, 24)
    }
  }.otherwise {
    when(vstate){
      for(i <- 0 until 16){
        vans(i) := mem(target_adr + i.U(22.W))
      }
      Info("vans: %x\n" +
        "target_adr: %x\n", io.out.vec_data_o, target_adr)
    }.otherwise{
      for (i <- 0 until 4)
        when(bit_sel(i)) {
          ans(i) := mem(target_adr + i.U(22.W))
        }
    }
  }
  db.dumb := false.B
  io.out.err_o := false.B
  io.out.rty_o := false.B
  io.out.stall_o := false.B
  io.out.dat_o := Cat(ans.reverse)
  io.out.vec_data_o := Cat(vans.reverse)
}
