package rvvsoc.devices

import chisel3._
import rvvsoc.sysbus._

class FlashSlaveReflector extends SysBusSlave(Flipped(new SysBusSlaveBundle)) {
    io.in <> io.out
}
