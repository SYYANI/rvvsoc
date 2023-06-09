package rvvsoc.sysbus

import chisel3._
import chisel3.util.{BitPat, Lookup}
import rvvsoc.utils.Warn

object SysBusTranslator{
    
}

class SysBusSlaveBundleIO(slavesN : Int) extends Bundle {
    val out = new SysBusSlaveBundle
    val in = Vec(slavesN, Flipped(new SysBusSlaveBundle))
}

class SysBusTranslator(map : Seq[(BitPat, UInt)], slaves : Seq[SysBusSlave]) extends Module {
    val slavesN = slaves.length
    val io = IO(new SysBusSlaveBundleIO(slavesN))

    // from slaves
    val slave_dat_o = Wire(Vec(slavesN, UInt(32.W)))
    val slave_ack_o = Wire(Vec(slavesN, Bool()))
    val slave_err_o = Wire(Vec(slavesN, Bool()))
    val slave_rty_o = Wire(Vec(slavesN, Bool()))
    val slave_stall_o = Wire(Vec(slavesN, Bool()))
    val slave_vdat_o = Wire(Vec(slavesN,UInt(128.W)))


    val prevSlave = RegInit(0.U)

    
    val slaveSel = Mux(slave_stall_o(prevSlave),
        prevSlave, Lookup(io.out.adr_i, 0.U, map)) 
        // the selected slave

    for(i <- 0 until slavesN){
        slave_vdat_o(i) := io.in(i).vec_data_o
        slave_dat_o(i) := io.in(i).dat_o
        slave_ack_o(i) := io.in(i).ack_o
        slave_err_o(i) := io.in(i).err_o
        slave_rty_o(i) := io.in(i).rty_o
        slave_stall_o(i) := io.in(i).stall_o

        val prevSelected = (prevSlave === i.U)
        val selected = (slaveSel === i.U)

        io.in(i).vec_data_i := io.out.vec_data_i
        io.in(i).dat_i := io.out.dat_i
        io.in(i).adr_i := io.out.adr_i
        io.in(i).cyc_i := Mux(prevSelected || selected, io.out.cyc_i, false.B)
        io.in(i).sel_i := io.out.sel_i
        io.in(i).we_i := io.out.we_i
        io.in(i).vec_en_i := io.out.vec_en_i

        io.in(i).stb_i := Mux(selected, io.out.stb_i, false.B)
    }
    io.out.vec_data_o := slave_vdat_o(prevSlave)
    io.out.dat_o := slave_dat_o(prevSlave)
    io.out.ack_o := slave_ack_o(prevSlave)
    io.out.err_o := slave_err_o(prevSlave)
    io.out.rty_o := slave_rty_o(prevSlave)
    io.out.stall_o := slave_stall_o(slaveSel)

    prevSlave := slaveSel
}
