package rvvsoc.devices

import chisel3._
import rvvsoc.sysbus._

class SerialPortSlaveReflector extends SysBusSlave(Flipped(new SysBusSlaveBundle)) {
    io.in <> io.out
}

