package uec.keystone.nedochip

import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._
import freechips.rocketchip.util._
import sifive.blocks.devices.pinctrl.{BasePin, EnhancedPin, EnhancedPinCtrl, Pin, PinCtrl}
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

class NEDOSystem(implicit p: Parameters) extends RocketSubsystem
    with HasPeripheryMaskROMSlave
    with HasPeripheryDebug
    with HasPeripheryUART
    with HasPeripherySPI
    with HasPeripherySPIFlash
    with HasPeripheryGPIO
    with HasPeripheryI2C {
  override lazy val module = new NEDOSystemModule(this)
}

class NEDOSystemModule[+L <: NEDOSystem](_outer: L)
  extends RocketSubsystemModuleImp(_outer)
    with HasPeripheryDebugModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripherySPIModuleImp
    with HasPeripheryGPIOModuleImp
    with HasPeripherySPIFlashModuleImp
    with HasPeripheryI2CModuleImp {
  // Reset vector is set to the location of the mask rom
  val maskROMParams = p(PeripheryMaskROMKey)
  global_reset_vector := maskROMParams(0).address.U
}

object PinGen {
  def apply(): BasePin =  {
    val pin = new BasePin()
    pin
  }
}

//-------------------------------------------------------------------------
// E300ArtyDevKitPlatformIO
//-------------------------------------------------------------------------

class NEDOPlatformIO(implicit val p: Parameters) extends Bundle {
  val pins = new Bundle {
    val jtag = new JTAGPins(() => PinGen(), false)
    val gpio = new GPIOPins(() => PinGen(), p(PeripheryGPIOKey)(0))
    val qspi = new SPIPins(() => PinGen(), p(PeripherySPIFlashKey)(0))
    val uart = new UARTPins(() => PinGen())
    val i2c = new I2CPins(() => PinGen())
    val spi = new SPIPins(() => PinGen(), p(PeripherySPIKey)(0))
  }
}


class NEDOPlatform(implicit val p: Parameters) extends Module {
  val sys = Module(LazyModule(new NEDOSystem).module)
  val io = new NEDOPlatformIO

  // Add in debug-controlled reset.
  sys.reset := ResetCatchAndSync(clock, reset.toBool, 20)

  //-----------------------------------------------------------------------
  // Check for unsupported rocket-chip connections
  //-----------------------------------------------------------------------

  require (p(NExtTopInterrupts) == 0, "No Top-level interrupts supported");

  //-----------------------------------------------------------------------
  // Default Pin connections before attaching pinmux

  for (iof_0 <- sys.gpio(0).iof_0.get) {
    iof_0.default()
  }

  for (iof_1 <- sys.gpio(0).iof_1.get) {
    iof_1.default()
  }

  // I2C
  //I2CPinsFromPort(io.pins.i2c, sys.i2c(0), clock = sys.clock, reset = sys.reset, syncStages = 0)

  // UART0
  //UARTPinsFromPort(io.pins.uart, sys.uart(0), clock = sys.clock, reset = sys.reset, syncStages = 0)

  //-----------------------------------------------------------------------
  // Drive actual Pads
  //-----------------------------------------------------------------------

  // Result of Pin Mux
  GPIOPinsFromPort(io.pins.gpio, sys.gpio(0))

  // Dedicated SPI Pads
  //SPIPinsFromPort(io.pins.qspi, sys.qspi(0), clock = sys.clock, reset = sys.reset, syncStages = 3)
  //SPIPinsFromPort(io.pins.spi, sys.spi(0), clock = sys.clock, reset = sys.reset, syncStages = 0)

  // JTAG Debug Interface
  val sjtag = sys.debug.systemjtag.get
  JTAGPinsFromPort(io.pins.jtag, sjtag.jtag)
  sjtag.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
}