package rvvsoc

import chisel3._
import chisel3.util._
import rvvsoc.core._
import rvvsoc.sysbus._
import rvvsoc.devices._
import rvvsoc.atomic._

class SoCIO extends Bundle {
    val devs = new DebugDevicesIO

    val serial = Flipped(new SysBusSlaveBundle)
    val flash = Flipped(new SysBusSlaveBundle)
    val plic_interface = Flipped(new PLICInterface)
}

class RVVSoC extends Module {
    val io = IO(new SoCIO)

    
    val not_to_cache = Seq(
        // BitPat("b00000000011111111111111111111???") -> true.B,
        // BitPat("b000000000111111111111111110?????") -> true.B,
        // BitPat("b000000000111111111111111100?????") -> true.B,
        // BitPat("b00000000011111111111111111110???") -> true.B,
        // BitPat("b0000000001111111111111111110????") -> true.B,
        // BitPat("b1111111111111111111111111???????") -> true.B
        BitPat("b????????????????????????????????") -> true.B
    )


    // --------- Cores ----------
    val core0 = Module(new Core(0, not_to_cache))
    val core1 = Module(new Core(1, not_to_cache))
    io.devs.out_devs <> core0.io.devs.out_devs
    io.devs.in_devs <> core0.io.devs.in_devs
    io.devs.in_devs <> core1.io.devs.in_devs

    // -------- Device ----------
    val ram_slave = Module(new RAMSlaveReflector())
    val ram2_slave = Module(new RAMSlaveReflector())
    val serial_slave = Module(new SerialPortSlaveReflector())
    val flash_slave = Module(new FlashSlaveReflector())
    val irq_client0 = Module(new Client)
    val irq_client1 = Module(new Client)
    val plic = Module(new PLIC)
    val rom = Module(new ROM("prog/relu.bin"))


    io.serial <> serial_slave.io.in
    io.flash <> flash_slave.io.in

    val bridge = Wire(new PLICIO)
    bridge <> plic.io.in
    io.plic_interface <> bridge.external
    bridge.core0_ext_irq_r <> core0.io.ext_irq_r
    bridge.core1_ext_irq_r <> core1.io.ext_irq_r

    core0.io.irq_client <> irq_client0.io.in
    core1.io.irq_client <> irq_client1.io.in

    val bus_map = Seq(
        BitPat("b0000000000??????????????????????") -> 1.U(3.W),
        BitPat("b00000000011111111111111111111???") -> 2.U(3.W),
        BitPat("b000000000111111111111111100?????") -> 3.U(3.W),
        BitPat("b000000000111111111111111110?????") -> 4.U(3.W),
        BitPat("b00000000011111111111111111110???") -> 5.U(3.W),
        BitPat("b0000000001111111111111111110????") -> 6.U(3.W),
        BitPat("b1111111111111111111111111???????") -> 7.U(3.W)
    )

    val bus_slaves: Seq[SysBusSlave] = Array(
        ram_slave,
        ram2_slave,
        serial_slave,
        irq_client0,
        irq_client1,
        plic,
        flash_slave,
        rom
    )

    // ------- Connector --------
    val arbiter = Module(new SysBusArbiter(2))
    arbiter.io.in(0) <> core0.io.bus_request
    arbiter.io.in(1) <> core1.io.bus_request

    val amo_syn = Module(new AMOSynchronizer)
    amo_syn.io.core0 <> core0.io.amo_syn
    amo_syn.io.core1 <> core1.io.amo_syn

    val lrsc_syn = Module(new LRSCSynchronizer)
    lrsc_syn.io.core0 <> core0.io.lrsc_syn
    lrsc_syn.io.core1 <> core1.io.lrsc_syn

    val translator = Module(new SysBusTranslator(bus_map, bus_slaves))

    ram_slave.io.out    <> translator.io.in(0)
    ram2_slave.io.out   <> translator.io.in(1)
    serial_slave.io.out <> translator.io.in(2)
    irq_client0.io.out  <> translator.io.in(3)
    irq_client1.io.out  <> translator.io.in(4)
    plic.io.out         <> translator.io.in(5)
    flash_slave.io.out  <> translator.io.in(6)
    rom.io.out          <> translator.io.in(7)

    arbiter.io.out <> translator.io.out

}
