package rvvsoc.cache

import rvvsoc.sysbus.SysBusFilterBundle

import chisel3._
import chisel3.util._

class CacheSnooperBundle extends Bundle {
    val gg = Input(Bool())
}

class CacheSnooper extends Module{
    val io = IO(new CacheSnooperBundle)
}
