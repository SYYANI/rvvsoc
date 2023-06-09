package rvvsoc.core

import chisel3._
import chisel3.util._
import rvvsoc.atomic._
import rvvsoc.utils.{Debug, Info, Trace, Error}

class DMemRequest extends Bundle {
    val addr = Input(UInt(32.W))
    val wr_data = Input(UInt(32.W))

    val vwr_data = Input(UInt(128.W))
    val vec_en = Input(Bool())

    val wr_en = Input(Bool())
    val rd_en = Input(Bool())
    val lr_en = Input(Bool())
    val sc_en = Input(Bool())
    val amo_en = Input(Bool())
    val mem_type = Input(Bits(MEM_X.getWidth.W))
    val amo_op = Input(Bits(AMO_OP.AMO_X.getWidth.W))
}

class DMemExceptions extends Bundle {
    val wr_addr_invalid_expt = Output(Bool())
    val rd_addr_invalid_expt = Output(Bool())
    val wr_access_err_expt = Output(Bool())
    val rd_access_err_expt = Output(Bool())
}

class DMemResponse extends Bundle {
    val vrd_data = Output(UInt(128.W))
    val rd_data = Output(UInt(32.W))
    val expt = new DMemExceptions
    val locked = Output(Bool())
}

class DMemCoreIO extends Bundle {
    val req = new DMemRequest
    val res = new DMemResponse
}

class DMemIO extends Bundle {
    val core = new DMemCoreIO
    val bus = Flipped(new SysBusBundle)
    val lrsc_syn = Flipped(new LRSCSynchronizerCoreIO)
    val amo_syn = Flipped(new AMOSynchronizerCoreIO)
}

import AtomicConsts._

class DMem extends Module {
    val io = IO(new DMemIO)
    val amo_locked = Wire(Bool())

    // -------- LR & SC --------
    val modify_en = Wire(Bool())
    val modify_addr = Wire(UInt())

    io.lrsc_syn.lr_en := io.core.req.lr_en
    io.lrsc_syn.sc_en := io.core.req.sc_en

    io.lrsc_syn.addr := modify_addr
    io.lrsc_syn.modify_en := modify_en
    io.lrsc_syn.in_amo := amo_locked
    val sc_valid = io.lrsc_syn.sc_valid
    // reservation hasn't been broken, store conditional succeed

    // -------- Sysbus --------
    val en = io.core.req.sc_en || io.core.req.rd_en || io.core.req.wr_en

    val prev_wr_data = RegInit(0.U(32.W))
    val prev_vwr_data = RegInit(0.U(128.W))
    val prev_addr = RegInit(0.U(32.W))
    val prev_en = RegInit(false.B)
    val prev_vec_en = RegInit(false.B)
    val prev_wr_en = RegInit(false.B)
    val prev_rd_en = RegInit(false.B)
    val prev_sc_en = RegInit(false.B)
    val prev_sc_valid = RegInit(false.B)
    val prev_mem_type = RegInit(MEM_X)
    val prev_mask = RegInit(0.U(4.W))
    val prev_addr_err = RegInit(false.B)
    val prev_amo_en = RegInit(false.B)
    val prev_amo_op = RegInit(AMO_OP.AMO_X)
    val prev_dmem_pending = RegInit(false.B)

    val dmem_pending = io.amo_syn.pending
    val cur_locked = io.bus.res.locked || amo_locked || (prev_dmem_pending && prev_en)
    when (!cur_locked) {
        prev_wr_data := io.core.req.wr_data
        prev_vwr_data := io.core.req.vwr_data
        prev_addr := io.core.req.addr
        prev_wr_en := io.core.req.wr_en || sc_valid
        prev_vec_en := io.core.req.vec_en
        prev_rd_en := io.core.req.rd_en
        prev_sc_en := io.core.req.sc_en
        prev_en := en
        prev_sc_valid := sc_valid
        prev_mem_type := io.core.req.mem_type
        prev_amo_en := io.core.req.amo_en
        prev_amo_op := io.core.req.amo_op
    }
    prev_dmem_pending := dmem_pending

    val cur_wr_data = Mux(cur_locked, prev_wr_data, io.core.req.wr_data)
    val cur_vwr_data = Mux(cur_locked, prev_vwr_data, io.core.req.vwr_data)
    val cur_addr = Mux(cur_locked, prev_addr, io.core.req.addr)
//    Debug("dmem_io.core.req.addr: %x\n", io.core.req.addr)
    val cur_en = Mux(cur_locked, prev_en, en)
    val cur_vec_en = Mux(cur_locked, prev_vec_en, io.core.req.vec_en)
    val cur_wr_en = Mux(cur_locked, prev_wr_en, io.core.req.wr_en || sc_valid)
//    Debug("dmem_io.core.req.wr_en: %x ,cur_wr_en: %x ,io.bus.req.wen: %x\n",io.core.req.wr_en,cur_wr_en,io.bus.req.wen)
    val cur_rd_en = Mux(cur_locked, prev_rd_en, io.core.req.rd_en)
    val cur_sc_en = Mux(cur_locked, prev_sc_en, io.core.req.sc_en)
    val cur_sc_valid = Mux(cur_locked, prev_sc_valid, sc_valid)
    val cur_mem_type = Mux(cur_locked, prev_mem_type, io.core.req.mem_type)
    val cur_amo_en = Mux(cur_locked, prev_amo_en, io.core.req.amo_en)
    val cur_amo_op = Mux(cur_locked, prev_amo_op, io.core.req.amo_op)

    modify_en := io.core.req.wr_en || (prev_wr_en && io.bus.res.locked) ||
        io.core.req.amo_en
    modify_addr := Mux(prev_wr_en && io.bus.res.locked, cur_addr, io.core.req.addr)

//    Debug("dmem_ven_signal: %x\n",cur_vec_en)
    val byte_masks = Seq(
        0.U(4.W) -> "b0001".U(4.W),
        1.U(4.W) -> "b0010".U(4.W),
        2.U(4.W) -> "b0100".U(4.W),
        3.U(4.W) -> "b1000".U(4.W),
    )
    val hword_masks = Seq(
        0.U(4.W) -> "b0011".U(4.W),
        2.U(4.W) -> "b1100".U(4.W),
    )

    val byte_wr_datas = Seq(
        0.U(4.W) -> Cat(0.U(24.W), cur_wr_data(7, 0)),
        1.U(4.W) -> Cat(0.U(16.W), cur_wr_data(7, 0), 0.U(8.W)),
        2.U(4.W) -> Cat(0.U(8.W), cur_wr_data(7, 0), 0.U(16.W)),
        3.U(4.W) -> Cat(cur_wr_data(7, 0), 0.U(24.W))
    )
    val hword_wr_datas = Seq(
        0.U(4.W) -> Cat(0.U(16.W), cur_wr_data(15, 0)),
        2.U(4.W) -> Cat(cur_wr_data(15, 0), 0.U(16.W))
    )

    val mask = MuxLookup(cur_mem_type, 0.U(4.W), Seq(
        MEM_B -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_masks),
        MEM_BU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_masks),
        MEM_H -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_masks),
        MEM_HU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_masks),
        MEM_W -> 15.U(4.W) // 1111
    ))
    prev_mask := mask

    val wr_data = MuxLookup(cur_mem_type, 0.U(4.W), Seq(
        MEM_B -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_wr_datas),
        MEM_BU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), byte_wr_datas),
        MEM_H -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_wr_datas),
        MEM_HU -> MuxLookup(cur_addr(1, 0), 0.U(4.W), hword_wr_datas),
        MEM_W -> cur_wr_data // 1111
    ))

    val vwr_data = cur_vwr_data

    val addr_err = MuxLookup(cur_mem_type, false.B, Seq(
        MEM_H -> (cur_addr(0) =/= 0.U(1.W)), // half word r/w: must be 2-aligned
        MEM_HU -> (cur_addr(0) =/= 0.U(1.W)),
        MEM_W -> (cur_addr(1, 0) =/= 0.U(2.W)) // full word r/w: must be 4-aligned
    ))
    prev_addr_err := addr_err

    // ---------- AMO ----------
    val amo = Module(new AMO)
    amo.io.request := cur_amo_en
    amo.io.req.addr := cur_addr
    amo.io.req.rs2_data := cur_wr_data
    amo.io.req.amo_op := cur_amo_op
    amo.io.bus.res := io.bus.res
    amo.io.syn <> io.amo_syn

    amo_locked := amo.io.res.locked

    when (!cur_amo_en) {
        io.bus.req.sel := mask
        io.bus.req.wen := cur_wr_en && (!addr_err) && (!dmem_pending)
        io.bus.req.ren := cur_rd_en && (!addr_err) && (!dmem_pending)
        io.bus.req.en := cur_en && (!addr_err)
        io.bus.req.ven := cur_vec_en && (!addr_err)
        io.bus.req.addr := cur_addr
        io.bus.req.data_wr := wr_data
        io.bus.req.vec_data_wr := vwr_data
    }
    .otherwise {
        io.bus.req := amo.io.bus.req
    }

    val bus_data = io.bus.res.data_rd
    val v_bus_rd_data = io.bus.res.vec_data_rd
    val byte_data = MuxLookup(prev_mask, 0.U(8.W), Seq(
        "b0001".U -> bus_data(7, 0),
        "b0010".U -> bus_data(15, 8),
        "b0100".U -> bus_data(23, 16),
        "b1000".U -> bus_data(31, 24)
    ))
    val hword_data = MuxLookup(prev_mask, 0.U(16.W), Seq(
        "b0011".U -> bus_data(15, 0),
        "b1100".U -> bus_data(31, 16)
    ))

    val ext_data = MuxLookup(prev_mem_type, bus_data, Seq(
        MEM_B -> Cat(Fill(24, byte_data(7)), byte_data(7, 0)),
        MEM_BU -> Cat(Fill(24, 0.U(1.W)), byte_data(7, 0)),
        MEM_H -> Cat(Fill(16, hword_data(15)), hword_data(15, 0)),
        MEM_HU -> Cat(Fill(16, 0.U(1.W)), hword_data(15, 0))
    ))

    val prev_read_data = RegInit(UInt(32.W), 0.U)
    val prev_vread_data = RegInit(UInt(128.W), 0.U)
    val prev_locked = RegInit(Bool(), false.B)
    prev_locked := io.bus.res.locked

    val cur_read_data = Mux(prev_sc_en, Mux(prev_sc_valid, 0.U(32.W), 1.U(32.W)),
        Mux(prev_rd_en, ext_data, 0.U(32.W)))
    val cur_v_read_data = Mux(prev_sc_en, Mux(prev_sc_valid, 0.U(32.W), 1.U(32.W)),
        Mux(prev_rd_en, v_bus_rd_data, 0.U(32.W)))

    when ((!io.bus.res.locked && prev_locked) || cur_sc_en) {
        prev_read_data := cur_read_data
        prev_vread_data := cur_v_read_data
    }

    val read_data = Mux(prev_locked || prev_sc_en, cur_read_data, prev_read_data)
    val vread_data = Mux(prev_locked || prev_sc_en, cur_v_read_data, prev_vread_data)
    when (!prev_amo_en) {
        io.core.res.rd_data := read_data
        io.core.res.vrd_data := vread_data
    }
    .otherwise {
        io.core.res.rd_data := amo.io.res.data
        io.core.res.vrd_data := 0.U
    }

    io.core.res.locked := cur_locked
    io.core.res.expt.wr_addr_invalid_expt := (prev_wr_en || prev_amo_en) && prev_addr_err
    io.core.res.expt.wr_access_err_expt := (prev_wr_en || prev_amo_en) && (!prev_addr_err) && io.bus.res.err
    io.core.res.expt.rd_addr_invalid_expt := prev_rd_en && prev_addr_err
    io.core.res.expt.rd_access_err_expt := prev_rd_en && (!prev_addr_err) && io.bus.res.err
}