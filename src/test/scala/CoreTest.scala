package rvvsoc.core

import org.scalatest._
import chiseltest._
import rvvsoc.RVVSoC
class CoreTest extends FlatSpec with ChiselScalatestTester{
  "CoreTest" should "pass" in {
    test(new RVVSoC) { dut =>
      dut.clock.step(20)
    }
  }
}
