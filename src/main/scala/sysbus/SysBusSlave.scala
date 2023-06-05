package rvvsoc.sysbus

import chisel3._

class SysBusSlaveBundle extends Bundle {
    val vec_data_i = Input(UInt(128.W))
    val vec_data_o = Output(UInt(128.W))
    val vec_en_i = Input(Bool())
    val dat_i = Input(UInt(32.W))
    val dat_o = Output(UInt(32.W))
    val ack_o = Output(Bool())
    val adr_i = Input(UInt(32.W))
    val cyc_i = Input(Bool())
    val err_o = Output(Bool())
    val rty_o = Output(Bool())
    val sel_i = Input(UInt(4.W))
    val stb_i = Input(Bool())
    val we_i = Input(Bool())
    val stall_o = Output(Bool())
}

class SysBusSlaveIO(in_bundle: Bundle) extends Bundle {
    val out = new SysBusSlaveBundle
    val in = in_bundle
}

class SysBusSlave(in_bundle: Bundle) extends Module {
    val io = IO(new SysBusSlaveIO(in_bundle))
}

