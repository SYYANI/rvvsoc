package rvvsoc

import chisel3.emitVerilog

import java.io.{File, FileWriter}

object Main extends App {
    println("Generating hardware...")
    emitVerilog(new rvvsoc(),Array("--target-dir", "out"))
    println("Hardware successfully generated!")
}
