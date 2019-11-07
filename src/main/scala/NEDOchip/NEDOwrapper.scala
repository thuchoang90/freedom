package uec.keystone.nedochip

import chisel3._
import chisel3.util._
import chisel3.experimental._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.fpgashells.clocks._

// *********************************************************************************
// NEDO wrapper - for doing a wrapper with actual ports (tri-state buffers at least)
// *********************************************************************************

class NEDOwrapper(implicit p :Parameters) extends RawModule {
  // The actual pins of this module.
  // This is a list of the ports 'to be wirebonded' / 'from the package'
  val clk_p = IO(Analog(1.W))
  val clk_n = IO(Analog(1.W))
  val rst_n = IO(Analog(1.W))
  val gpio = IO(Vec(p(PeripheryGPIOKey).head.width, Analog(1.W)))
  val jtag_tdi = IO(Analog(1.W))
  val jtag_tdo = IO(Analog(1.W))
  val jtag_tck = IO(Analog(1.W))
  val jtag_tms = IO(Analog(1.W))
  val jtag_rst = IO(Analog(1.W))
  val spi_cs = IO(Vec(p(PeripherySPIKey).head.csWidth, Analog(1.W)))
  val spi_sck = IO(Analog(1.W))
  val spi_dq = IO(Vec(4, Analog(1.W)))
  val qspi_cs =  IO(Vec(p(PeripherySPIFlashKey).head.csWidth, Analog(1.W)))
  val qspi_sck = IO(Analog(1.W))
  val qspi_dq = IO(Vec(4, Analog(1.W)))
  //val i2c_sda = IO(Analog(1.W))
  //val i2c_scl = IO(Analog(1.W))
  val uart_txd = IO(Analog(1.W))
  val uart_rxd = IO(Analog(1.W))

  // This clock and reset are only declared. We soon connect them
  val clock = Wire(Clock())
  val reset = Wire(Bool())

  // An option to dynamically assign
  var tlport : Option[TLUL] = None

  // All the modules declared here have this clock and reset
  withClockAndReset(clock, reset) {
    // Main clock
    val clock_XTAL = Module(new XTAL_DRV)
    clock := clock_XTAL.io.C
    clock_XTAL.io.E := true.B // Always enabled
    attach(clk_p, clock_XTAL.io.XP)
    attach(clk_n, clock_XTAL.io.XN)

    // Main reset
    val rst_gpio = Module(new GPIO_24_A)
    reset := !PinToGPIO_24_A.asInput(rst_gpio.io)
    attach(rst_n, rst_gpio.io.PAD)

    // The platform module
    val system = Module(new NEDOPlatform)

    // GPIOs
    (gpio zip system.io.pins.gpio.pins).foreach {
      case (g, i) =>
        val GPIO = Module(new GPIO_24_A)
        attach(g, GPIO.io.PAD)
        PinToGPIO_24_A(GPIO.io, i)
    }

    // JTAG
    val JTAG_TMS = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TMS.io, system.io.pins.jtag.TMS)
    attach(jtag_tck, JTAG_TMS.io.PAD)
    val JTAG_TCK = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TCK.io, system.io.pins.jtag.TCK)
    attach(jtag_tck, JTAG_TCK.io.PAD)
    val JTAG_TDI = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TDI.io, system.io.pins.jtag.TDI)
    attach(jtag_tck, JTAG_TDI.io.PAD)
    val JTAG_TDO = Module(new GPIO_24_A)
    PinToGPIO_24_A(JTAG_TDO.io, system.io.pins.jtag.TDO)
    attach(jtag_tck, JTAG_TDO.io.PAD)
    val JTAG_RST = Module(new GPIO_24_A)
    system.io.jtag_reset := PinToGPIO_24_A.asInput(JTAG_RST.io)
    attach(jtag_rst, JTAG_RST.io.PAD)

    // QSPI (SPI as flash memory)
    (qspi_cs zip system.io.pins.qspi.cs).foreach {
      case (g, i) =>
        val QSPI_CS = Module(new GPIO_24_A)
        PinToGPIO_24_A(QSPI_CS.io, i)
        attach(g, QSPI_CS.io.PAD)
    }
    (qspi_dq zip system.io.pins.qspi.dq).foreach {
      case (g, i) =>
        val QSPI_DQ = Module(new GPIO_24_A)
        PinToGPIO_24_A(QSPI_DQ.io, i)
        attach(g, QSPI_DQ.io.PAD)
    }
    val QSPI_SCK = Module(new GPIO_24_A)
    PinToGPIO_24_A(QSPI_SCK.io, system.io.pins.qspi.sck)
    attach(qspi_sck, QSPI_SCK.io.PAD)

    // SPI (SPI as SD?)
    (spi_cs zip system.io.pins.spi.cs).foreach {
      case (g, i) =>
        val SPI_CS = Module(new GPIO_24_A)
        PinToGPIO_24_A(SPI_CS.io, i)
        attach(g, SPI_CS.io.PAD)
    }
    (spi_dq zip system.io.pins.spi.dq).foreach {
      case (g, i) =>
        val SPI_DQ = Module(new GPIO_24_A)
        PinToGPIO_24_A(SPI_DQ.io, i)
        attach(g, SPI_DQ.io.PAD)
    }
    val SPI_SCK = Module(new GPIO_24_A)
    PinToGPIO_24_A(SPI_SCK.io, system.io.pins.spi.sck)
    attach(spi_sck, SPI_SCK.io.PAD)

    // UART
    val UART_RXD = Module(new GPIO_24_A)
    PinToGPIO_24_A(UART_RXD.io, system.io.pins.uart.rxd)
    attach(uart_rxd, UART_RXD.io.PAD)
    val UART_TXD = Module(new GPIO_24_A)
    PinToGPIO_24_A(UART_TXD.io, system.io.pins.uart.txd)
    attach(uart_txd, UART_TXD.io.PAD)

    // I2C
    //val I2C_SDA = Module(new GPIO_24_A)
    //PinToGPIO_24_A(I2C_SDA.io, system.io.pins.i2c.sda)
    //attach(i2c_sda, I2C_SDA.io.PAD)
    //val I2C_SCL = Module(new GPIO_24_A)
    //PinToGPIO_24_A(I2C_SCL.io, system.io.pins.i2c.scl)
    //attach(i2c_scl, I2C_SCL.io.PAD)

    // The memory port
    // TODO: This is awfully dirty. I mean it should get the work done
    //system.io.tlport.getElements.head
    tlport = Some(IO(new TLUL(system.sys.outer.memTLNode.in.head._1.params)))
    tlport.get <> system.io.tlport
  }
}

// **********************************************************************
// ** NEDO chip - for doing the only-input/output chip
// **********************************************************************

class NEDObase(implicit val p :Parameters) extends RawModule {
  // The actual pins of this module.
  val gpio_in = IO(Input(UInt(p(GPIOInKey).W)))
  val gpio_out = IO(Output(UInt((p(PeripheryGPIOKey).head.width-p(GPIOInKey)).W)))
  val jtag = IO(new Bundle {
    val jtag_TDI = (Input(Bool()))
    val jtag_TDO = (Output(Bool()))
    val jtag_TCK = (Input(Bool()))
    val jtag_TMS = (Input(Bool()))
  })
  val sdio = IO(new Bundle {
    val sdio_clk = (Output(Bool()))
    val sdio_cmd = (Output(Bool()))
    val sdio_dat_0 = (Input(Bool()))
    val sdio_dat_1 = (Analog(1.W))
    val sdio_dat_2 = (Analog(1.W))
    val sdio_dat_3 = (Output(Bool()))
  })
  val qspi = IO(new Bundle {
    val qspi_cs = (Output(UInt(p(PeripherySPIFlashKey).head.csWidth.W)))
    val qspi_sck = (Output(Bool()))
    val qspi_miso = (Input(Bool()))
    val qspi_mosi = (Output(Bool()))
    val qspi_wp = (Output(Bool()))
    val qspi_hold = (Output(Bool()))
  })
  val uart_txd = IO(Output(Bool()))
  val uart_rxd = IO(Input(Bool()))
  val uart_rtsn = IO(Output(Bool()))
  val uart_ctsn = IO(Input(Bool()))
  // These are later connected
  val clock = Wire(Clock())
  val reset = Wire(Bool()) // System reset (for cores)
  val areset = Wire(Bool()) // Global reset (a BUFFd version of the reset from the button)
  val ndreset = Wire(Bool()) // Debug reset (The reset you can trigger from JTAG)
  // An option to dynamically assign
  var tlportw : Option[TLUL] = None
  var cacheBlockBytesOpt: Option[Int] = None
  var memdevice: Option[MemoryDevice] = None

  // All the modules declared here have this clock and reset
  withClockAndReset(clock, reset) {
    // The platform module
    val system = Module(new NEDOPlatform)
    ndreset := system.io.ndreset
    cacheBlockBytesOpt = Some(system.sys.outer.cacheBlockBytes)

    // Merge all the gpio vector
    val vgpio_in = VecInit(gpio_in.toBools)
    val vgpio_out = Wire(Vec(p(PeripheryGPIOKey).head.width-p(GPIOInKey), Bool()))
    gpio_out := vgpio_out.asUInt()
    val gpio = vgpio_in ++ vgpio_out
    // GPIOs
    (gpio zip system.io.pins.gpio.pins).zipWithIndex.foreach {
      case ((g: Bool, pin: BasePin), i: Int) =>
        if(i < p(GPIOInKey)) BasePinToRegular(pin, g)
        else g := BasePinToRegular(pin)
    }

    // JTAG
    BasePinToRegular(system.io.pins.jtag.TMS, jtag.jtag_TMS)
    BasePinToRegular(system.io.pins.jtag.TCK, jtag.jtag_TCK)
    BasePinToRegular(system.io.pins.jtag.TDI, jtag.jtag_TDI)
    jtag.jtag_TDO := BasePinToRegular(system.io.pins.jtag.TDO)
    system.io.jtag_reset := areset

    // QSPI (SPI as flash memory)
    qspi.qspi_cs := BasePinToRegular(system.io.pins.qspi.cs)
    qspi.qspi_sck := BasePinToRegular(system.io.pins.qspi.sck)
    BasePinToRegular(system.io.pins.qspi.dq(0), qspi.qspi_miso)
    qspi.qspi_mosi := BasePinToRegular(system.io.pins.qspi.dq(1))
    qspi.qspi_wp := BasePinToRegular(system.io.pins.qspi.dq(2))
    qspi.qspi_hold := BasePinToRegular(system.io.pins.qspi.dq(3))

    // SPI (SPI as SD?)
    sdio.sdio_dat_3 := BasePinToRegular(system.io.pins.spi.cs.head)
    sdio.sdio_clk := BasePinToRegular(system.io.pins.spi.sck)
    BasePinToRegular(system.io.pins.spi.dq(0), RegNext(RegNext(sdio.sdio_dat_0))) // NOTE: We saw like this on SDIOOverlay
    sdio.sdio_cmd := BasePinToRegular(system.io.pins.spi.dq(1))
    BasePinToRegular(system.io.pins.spi.dq(2)) // Ignored
    BasePinToRegular(system.io.pins.spi.dq(3)) // Ignored

    // UART
    BasePinToRegular(system.io.pins.uart.rxd, uart_rxd)
    uart_txd := BasePinToRegular(system.io.pins.uart.txd)
    uart_rtsn := false.B

    // The memory port
    tlportw = Some(system.io.tlport)
    memdevice = Some(system.sys.outer.memdevice)
  }
  val cacheBlockBytes = 128//cacheBlockBytesOpt.get
}

class NEDOchip(implicit override val p :Parameters) extends NEDObase {
  // Some additional ports to connect to the chip
  val sys_clk = IO(Input(Clock()))
  val rst_n = IO(Input(Bool()))
  val jrst_n = IO(Input(Bool()))
  val tlport = IO(new TLUL(tlportw.get.params))
  // TL port connection
  tlport.a <> tlportw.get.a
  tlportw.get.d <> tlport.d
  // Clock and reset connection
  clock := sys_clk
  reset := !rst_n | ndreset
  areset := !jrst_n
}

// ********************************************************************
// NEDODPGA - Just an adaptation of NEDOchip to the FPGA
// ********************************************************************
import sifive.fpgashells.ip.xilinx.vc707mig._
import sifive.fpgashells.ip.xilinx._

class NEDOFPGA(implicit override val p :Parameters) extends NEDObase {
  var ddr: Option[VC707MIGIODDR] = None
  val sys_clock_p = IO(Input(Clock()))
  val sys_clock_n = IO(Input(Clock()))
  val sys_clock_ibufds = Module(new IBUFDS())
  val sys_clk_i = sys_clock_ibufds.io.O
  sys_clock_ibufds.io.I := sys_clock_p
  sys_clock_ibufds.io.IB := sys_clock_n
  val rst_0 = IO(Input(Bool()))
  val rst_1 = IO(Input(Bool()))
  val rst_2 = IO(Input(Bool()))
  val rst_3 = IO(Input(Bool()))
  val reset_0 = IBUF(rst_0)
  val reset_1 = IBUF(rst_1)
  val reset_2 = IBUF(rst_2)
  val reset_3 = IBUF(rst_3)


  withClockAndReset(clock, reset) {
    // Instance our converter, and connect everything
    val mod = Module(LazyModule(new TLULtoMIG(cacheBlockBytes, tlportw.get.params, memdevice.get)).module)

    // DDR port only
    ddr = Some(IO(new VC707MIGIODDR(mod.depth)))
    ddr.get <> mod.io.ddrport

    // TileLink Interface from platform
    mod.io.tlport.a <> tlportw.get.a
    tlportw.get.d <> mod.io.tlport.d

    // PLL instance
    val c = new PLLParameters(
      name ="pll",
      input = PLLInClockParameters(
        freqMHz = 50.0,
        feedback = true
      ),
      req = Seq(
        PLLOutClockParameters(
          freqMHz = 50.0
        )
      )
    )
    val pll = Module(new Series7MMCM(c))
    pll.io.clk_in1 := sys_clk_i
    pll.io.reset := reset_0

    gpio_out := Cat(reset_0, reset_1, reset_2, reset_3, mod.io.ddrport.init_calib_complete)

    // MIG connections, like resets and stuff
    mod.io.ddrport.sys_clk_i := sys_clk_i.asUInt()
    mod.io.ddrport.aresetn := reset_0
    mod.io.ddrport.sys_rst := reset_1

    // Main clock and reset assignments
    clock := pll.io.clk_out1.get
    reset := reset_2
    areset := reset_3
  }
}

// ********************************************************************
// NEDODPGAQuartus - Just an adaptation of NEDOchip to the FPGA in Quartus
// ********************************************************************

class NEDOFPGAQuartus(implicit val p :Parameters) extends RawModule {
  ///////// CLOCKS /////////
  val OSC_50_BANK2 = IO(Input(Clock()))
  val OSC_50_BANK3 = IO(Input(Clock()))
  val OSC_50_BANK4 = IO(Input(Clock()))
  val OSC_50_BANK5 = IO(Input(Clock()))
  val OSC_50_BANK6 = IO(Input(Clock()))
  val OSC_50_BANK7 = IO(Input(Clock()))
  val GCLKIN = IO(Input(Clock()))
  //val GCLKOUT_FPGA = IO(Output(Clock()))
  //val SMA_CLKOUT_p = IO(Output(Clock()))

  //////// CPU RESET //////////
  val CPU_RESET_n = IO(Input(Bool()))

  //////// MAX I/O //////////
  /*val MAX_CONF_D = IO(Analog((3+1).W))
  val MAX_I2C_SCLK = IO(Output(Bool()))
  val MAX_I2C_SDAT = IO(Analog(1.W))*/

  ///////// LED /////////
  val LED = IO(Output(Bits((7+1).W)))

  ///////// BUTTON /////////
  val BUTTON = IO(Input(Bits((3+1).W)))

  ///////// SW /////////
  val SW = IO(Input(Bits((7+1).W)))

  //////////// SLIDE SWITCH x 4 //////////
  val SLIDE_SW = IO(Input(Bits((3+1).W)))

  //////////// Temperature //////////
  /*val TEMP_SMCLK = IO(Output(Bool()))
  val TEMP_SMDAT = IO(Analog(1.W))
  val TEMP_INT_n = IO(Input(Bool()))*/

  //////////// Current //////////
  /*val CSENSE_ADC_F0 = IO(Output(Bool()))
  val CSENSE_SCK = IO(Analog(1.W))
  val CSENSE_SDI = IO(Output(Bool()))
  val CSENSE_SDO = IO(Input(Bool()))
  val CSENSE_CS_n = IO(Output(Bits((1+1).W)))*/

  ///////// FAN /////////
  //val FAN_CTRL = IO(Output(Bool()))

  //////////// EEPROM //////////
  /*val EEP_SCL = IO(Output(Bool()))
  val EEP_SDA = IO(Analog(1.W))*/

  //////////// SDCARD //////////
  val SD_CLK = IO(Output(Bool()))
  val SD_MOSI = IO(Output(Bool()))
  val SD_MISO = IO(Input(Bool()))
  val SD_CS_N = IO(Output(Bool()))
  val SD_INTERUPT_N = IO(Input(Bool()))
  val SD_WP_n = IO(Input(Bool()))

  //////////// Ethernet x 4 //////////
  /*val ETH_INT_n = IO(Input(Bits((3+1).W)))
  val ETH_MDC = IO(Output(Bits((3+1).W)))
  val ETH_MDIO = IO(Analog((3+1).W))
  val ETH_RST_n = IO(Output(Bool()))
  val ETH_RX_p = IO(Input(Bits((3+1).W)))
  val ETH_TX_p = IO(Output(Bits((3+1).W)))*/

  ////////////// PCIe x 8 //////////
  /*	val PCIE_PREST_n = IO(Input(Bool()))
    val PCIE_REFCLK_p = IO(Input(Bool()))
    val PCIE_RX_p = IO(Input(Bits((7+1).W)))
    val PCIE_SMBCLK = IO(Input(Bool()))
    val PCIE_SMBDAT = IO(Analog(1.W))
    val PCIE_TX_p = IO(Output(Bits((7+1).W)))
    val PCIE_WAKE_n = IO(Output(Bool()))
  */
  //////////// Flash and SRAM Address/Data Share Bus //////////
  /*val FSM_A = IO(Output(Bits((25+1).W)))
  val FSM_D = IO(Analog((15+1).W))*/

  //////////// Flash Control //////////
  /*val FLASH_ADV_n = IO(Output(Bool()))
  val FLASH_CE_n = IO(Output(Bool()))
  val FLASH_CLK = IO(Output(Bool()))
  val FLASH_OE_n = IO(Output(Bool()))
  val FLASH_RESET_n = IO(Output(Bool()))
  val FLASH_RYBY_n = IO(Input(Bool()))
  val FLASH_WE_n = IO(Output(Bool()))*/

  //////////// SSRAM Control //////////
  /*val SSRAM_ADV = IO(Output(Bool()))
  val SSRAM_BWA_n = IO(Output(Bool()))
  val SSRAM_BWB_n = IO(Output(Bool()))
  val SSRAM_CE_n = IO(Output(Bool()))
  val SSRAM_CKE_n = IO(Output(Bool()))
  val SSRAM_CLK = IO(Output(Bool()))
  val SSRAM_OE_n = IO(Output(Bool()))
  val SSRAM_WE_n = IO(Output(Bool()))*/

  //////////// USB OTG //////////
  /*val OTG_A = IO(Output(Bits((17+1).W)))
  val OTG_CS_n = IO(Output(Bool()))
  val OTG_D = IO(Analog((31+1).W))
  val OTG_DC_DACK = IO(Output(Bool()))
  val OTG_DC_DREQ = IO(Input(Bool()))
  val OTG_DC_IRQ = IO(Input(Bool()))
  val OTG_HC_DACK = IO(Output(Bool()))
  val OTG_HC_DREQ = IO(Input(Bool()))
  val OTG_HC_IRQ = IO(Input(Bool()))
  val OTG_OE_n = IO(Output(Bool()))
  val OTG_RESET_n = IO(Output(Bool()))
  val OTG_WE_n = IO(Output(Bool()))*/

  //////////// SATA //////////
  /*	val SATA_REFCLK_p = IO(Input(Bool()))
    val SATA_HOST_RX_p = IO(Input(Bits((1+1).W)))
    val SATA_HOST_TX_p = IO(Output(Bits((1+1).W)))
    val SATA_DEVICE_RX_p = IO(Input(Bits((1+1).W)))
    val SATA_DEVICE_TX_p = IO(Output(Bits((1+1).W)))
  */
  //////////// DDR2 SODIMM //////////
  val M1_DDR2_addr = IO(Output(Bits((15+1).W)))
  val M1_DDR2_ba = IO(Output(Bits((2+1).W)))
  val M1_DDR2_cas_n = IO(Output(Bool()))
  val M1_DDR2_cke = IO(Output(Bits((1+1).W)))
  val M1_DDR2_clk = IO(Output(Bits((1+1).W)))
  val M1_DDR2_clk_n = IO(Output(Bits((1+1).W)))
  val M1_DDR2_cs_n = IO(Output(Bits((1+1).W)))
  val M1_DDR2_dm = IO(Output(Bits((7+1).W)))
  val M1_DDR2_dq = IO(Analog((63+1).W))
  val M1_DDR2_dqs = IO(Analog((7+1).W))
  val M1_DDR2_dqsn = IO(Analog((7+1).W))
  val M1_DDR2_odt = IO(Output(Bits((1+1).W)))
  val M1_DDR2_ras_n = IO(Output(Bool()))
  //val M1_DDR2_SA = IO(Output(Bits((1+1).W)))
  //val M1_DDR2_SCL = IO(Output(Bool()))
  //val M1_DDR2_SDA = IO(Analog(1.W))
  val M1_DDR2_we_n = IO(Output(Bool()))
  val M1_DDR2_oct_rdn = IO(Input(Bool()))
  val M1_DDR2_oct_rup = IO(Input(Bool()))

  //////////// DDR2 SODIMM //////////
  /*	val M2_DDR2_addr = IO(Output(Bits((15+1).W)))
    val M2_DDR2_ba = IO(Output(Bits((2+1).W)))
    val M2_DDR2_cas_n = IO(Output(Bool()))
    val M2_DDR2_cke = IO(Output(Bits((1+1).W)))
    val M2_DDR2_clk = IO(Analog((1+1).W))
    val M2_DDR2_clk_n = IO(Analog((1+1).W))
    val M2_DDR2_cs_n = IO(Output(Bits((1+1).W)))
    val M2_DDR2_dm = IO(Output(Bits((7+1).W)))
    val M2_DDR2_dq = IO(Analog((63+1).W))
    val M2_DDR2_dqs = IO(Analog((7+1).W))
    val M2_DDR2_dqsn = IO(Analog((7+1).W))
    val M2_DDR2_odt = IO(Output(Bits((1+1).W)))
    val M2_DDR2_ras_n = IO(Output(Bool()))
    val M2_DDR2_SA = IO(Output(Bits((1+1).W)))
    val M2_DDR2_SCL = IO(Output(Bool()))
    val M2_DDR2_SDA = IO(Analog(1.W))
    val M2_DDR2_we_n = IO(Output(Bool()))
    val M2_DDR2_oct_rdn = IO(Input(Bool()))
    val M2_DDR2_oct_rup = IO(Input(Bool()))
  */
  ///////// GPIO /////////
  //val GPIO0_D = IO(Output(Bits((35+1).W)))
  //val GPIO1_D = IO(Analog((35+1).W))
  val jtag = IO(new Bundle {
    val jtag_TDI = (Input(Bool()))
    val jtag_TDO = (Output(Bool()))
    val jtag_TCK = (Input(Bool()))
    val jtag_TMS = (Input(Bool()))
  })
  val qspi = IO(new Bundle {
    val qspi_cs = (Output(UInt(p(PeripherySPIFlashKey).head.csWidth.W)))
    val qspi_sck = (Output(Bool()))
    val qspi_miso = (Input(Bool()))
    val qspi_mosi = (Output(Bool()))
    val qspi_wp = (Output(Bool()))
    val qspi_hold = (Output(Bool()))
  })

  ///////////  EXT_IO /////////
  //val EXT_IO = IO(Analog(1.W))

  //////////// HSMC_A //////////
  /*val HSMA_CLKIN_n1 = IO(Input(Bool()))
  val HSMA_CLKIN_n2 = IO(Input(Bool()))
  val HSMA_CLKIN_p1 = IO(Input(Bool()))
  val HSMA_CLKIN_p2 = IO(Input(Bool()))
  val HSMA_CLKIN0 = IO(Input(Bool()))
  val HSMA_CLKOUT_n2 = IO(Output(Bool()))
  val HSMA_CLKOUT_p2 = IO(Output(Bool()))
  val HSMA_D = IO(Analog((3+1).W))
  //val HSMA_GXB_RX_p = IO(Input(Bits((3+1).W)))
  //val HSMA_GXB_TX_p = IO(Output(Bits((3+1).W)))
  val HSMA_OUT_n1 = IO(Analog(1.W))
  val HSMA_OUT_p1 = IO(Analog(1.W))
  val HSMA_OUT0 = IO(Analog(1.W))
  //val HSMA_REFCLK_p = IO(Input(Bool()))
  val HSMA_RX_n = IO(Analog((16+1).W))
  val HSMA_RX_p = IO(Analog((16+1).W))
  val HSMA_TX_n = IO(Analog((16+1).W))
  val HSMA_TX_p = IO(Analog((16+1).W))*/

  //////////// HSMC_B //////////
  /*val HSMB_CLKIN_n1 = IO(Input(Bool()))
  val HSMB_CLKIN_n2 = IO(Input(Bool()))
  val HSMB_CLKIN_p1 = IO(Input(Bool()))
  val HSMB_CLKIN_p2 = IO(Input(Bool()))
  val HSMB_CLKIN0 = IO(Input(Bool()))
  val HSMB_CLKOUT_n2 = IO(Output(Bool()))
  val HSMB_CLKOUT_p2 = IO(Output(Bool()))
  val HSMB_D = IO(Analog((3+1).W))
  //val HSMB_GXB_RX_p = IO(Input(Bits((7+1).W)))
  //val HSMB_GXB_TX_p = IO(Output(Bits((7+1).W)))
  val HSMB_OUT_n1 = IO(Analog(1.W))
  val HSMB_OUT_p1 = IO(Analog(1.W))
  val HSMB_OUT0 = IO(Analog(1.W))
  //val HSMB_REFCLK_p = IO(Input(Bool()))
  val HSMB_RX_n = IO(Analog((16+1).W))
  val HSMB_RX_p = IO(Analog((16+1).W))
  val HSMB_TX_n = IO(Analog((16+1).W))
  val HSMB_TX_p = IO(Analog((16+1).W))*/

  //////////// HSMC I2C //////////
  /*val HSMC_SCL = IO(Output(Bool()))
  val HSMC_SDA = IO(Analog(1.W))*/

  //////////// 7-Segment Display //////////
  /*val SEG0_D = IO(Output(Bits((6+1).W)))
  val SEG1_D = IO(Output(Bits((6+1).W)))
  val SEG0_DP = IO(Output(Bool()))
  val SEG1_DP = IO(Output(Bool()))*/

  //////////// Uart //////////
  //val UART_CTS = IO(Output(Bool()))
  //val UART_RTS = IO(Input(Bool()))
  val UART_RXD = IO(Input(Bool()))
  val UART_TXD = IO(Output(Bool()))
  
  val clock = Wire(Clock())
  val reset = Wire(Bool())

  withClockAndReset(clock, reset) {
    // Instance our converter, and connect everything
    val chip = Module(new NEDOchip)
    val mod = Module(LazyModule(new TLULtoQuartusPlatform(chip.cacheBlockBytes, chip.tlportw.get.params)).module)
    
    // Clock and reset (for TL stuff)
    clock := mod.io.ckrst.dimmclk_clk
    reset := SLIDE_SW(3)
    chip.sys_clk := mod.io.ckrst.dimmclk_clk
    chip.rst_n := !SLIDE_SW(3)

    // Quartus Platform connections
    M1_DDR2_addr := mod.io.qport.memory_mem_a
    M1_DDR2_ba := mod.io.qport.memory_mem_ba
    M1_DDR2_clk := mod.io.qport.memory_mem_ck
    M1_DDR2_clk_n := mod.io.qport.memory_mem_ck_n
    M1_DDR2_cke := mod.io.qport.memory_mem_cke
    M1_DDR2_cs_n := mod.io.qport.memory_mem_cs_n
    M1_DDR2_dm := mod.io.qport.memory_mem_dm
    M1_DDR2_ras_n := mod.io.qport.memory_mem_ras_n
    M1_DDR2_cas_n := mod.io.qport.memory_mem_cas_n
    M1_DDR2_we_n := mod.io.qport.memory_mem_we_n
    attach(M1_DDR2_dq, mod.io.qport.memory_mem_dq)
    attach(M1_DDR2_dqs, mod.io.qport.memory_mem_dqs)
    attach(M1_DDR2_dqsn, mod.io.qport.memory_mem_dqs_n)
    M1_DDR2_odt := mod.io.qport.memory_mem_odt
    mod.io.qport.oct_rdn := M1_DDR2_oct_rdn
    mod.io.qport.oct_rup := M1_DDR2_oct_rup
    mod.io.ckrst.refclk_clk := OSC_50_BANK3.asUInt()
    mod.io.ckrst.reset_reset_n := CPU_RESET_n

    // TileLink Interface from platform
    mod.io.tlport.a <> chip.tlport.a
    chip.tlport.d <> mod.io.tlport.d
    
    // The rest of the platform connections
    val chipshell_led = chip.gpio_out 	// output [7:0]
    LED := Cat(
      mod.io.qport.mem_status_local_cal_fail,
      mod.io.qport.mem_status_local_cal_success,
      mod.io.qport.mem_status_local_init_done,
      SLIDE_SW(3),
      chipshell_led(3,0)
    )
    chip.gpio_in := SW(7,0)			// input  [7:0]
    jtag <> chip.jtag
    qspi <> chip.qspi
    chip.uart_rxd := UART_RXD	// UART_TXD
    UART_TXD := chip.uart_txd 	// UART_RXD
    // := chip.uart_rtsn 			// output: not used
    chip.uart_ctsn := false.B 		// input:  notused
    SD_CLK := chip.sdio.sdio_clk 	// output
    SD_MOSI := chip.sdio.sdio_cmd 	// output
    chip.sdio.sdio_dat_0 := SD_MISO 	// input
    SD_CS_N := chip.sdio.sdio_dat_3 	// output
    chip.jrst_n := !SLIDE_SW(2)
  }
}