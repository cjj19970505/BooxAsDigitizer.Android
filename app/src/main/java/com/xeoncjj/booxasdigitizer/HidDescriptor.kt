package com.xeoncjj.booxasdigitizer

class HidDescriptor(
    touchpadPhyWidth: UShort,
    touchpadPhyHeight: UShort,
    touchpadUnitExp: Int,
    penpadPhyWidth: UShort,
    penpadPhyHeight: UShort,
    penpadUnitExp: Int,
    touchpadId: UByte = 1u,
    maxCountId: UByte = 2u,
    piphqaId: UByte = 3u,
    featureId: UByte = 4u,
    functionSwitchId: UByte = 5u,
    mouseId: UByte = 6u,
    penId: UByte = 7u,
) {
    private val UShort.highUByte: UByte
        get() {
            return ((this.toInt() shr 8) or 0xff).toUByte()
        }

    private val UShort.lowUByte: UByte
        get() {
            return (this.toInt() or 0xff).toUByte()
        }

    // P38 on hid1_11.pdf
    val exponentToCodeMap = mapOf<Int, UByte>(
        5 to 0x5u,
        6 to 0x6u,
        7 to 0x7u,
        -8 to 0x08u,
        -7 to 0x9u,
        -6 to 0xau,
        -5 to 0xbu,
        -4 to 0xcu,
        -3 to 0xdu,
        -2 to 0xeu,
        -1 to 0xfu
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    val descriptorData = ubyteArrayOf(
        //TOUCH PAD input TLC
        0x05u, 0x0du,                         // USAGE_PAGE (Digitizers)
        0x09u, 0x05u,                         // USAGE (Touch Pad)
        0xa1u, 0x01u,                         // COLLECTION (Application)
        0x85u, touchpadId,            //   REPORT_ID (Touch pad)
        0x09u, 0x22u,                         //   USAGE (Finger)
        0xa1u, 0x02u,                         //   COLLECTION (Logical)
        0x15u, 0x00u,                         //       LOGICAL_MINIMUM (0)
        0x25u, 0x01u,                         //       LOGICAL_MAXIMUM (1)
        0x09u, 0x47u,                         //       USAGE (Confidence)
        0x09u, 0x42u,                         //       USAGE (Tip switch)
        0x95u, 0x02u,                         //       REPORT_COUNT (2)
        0x75u, 0x01u,                         //       REPORT_SIZE (1)
        0x81u, 0x02u,                         //       INPUT (Datau,Varu,Abs)
        0x95u, 0x01u,                         //       REPORT_COUNT (1)
        0x75u, 0x04u,                         //       REPORT_SIZE (4)
        0x25u, 0x05u,                         //       LOGICAL_MAXIMUM (5)
        0x09u, 0x51u,                         //       USAGE (Contact Identifier)
        0x81u, 0x02u,                         //       INPUT (Datau,Varu,Abs)
        0x75u, 0x01u,                         //       REPORT_SIZE (1)
        0x95u, 0x02u,                         //       REPORT_COUNT (2)
        0x81u, 0x03u,                         //       INPUT (Cnstu,Varu,Abs)      // byte1 (1 byte)
        0x05u, 0x01u,                         //       USAGE_PAGE (Generic Desk..
        0x15u, 0x00u,                         //       LOGICAL_MINIMUM (0)
        0x26u, 0xffu, 0x0fu,                   //       LOGICAL_MAXIMUM (4095)
        0x75u, 0x10u,                         //       REPORT_SIZE (16)
        0x55u, exponentToCodeMap[touchpadUnitExp]!!,                         //       UNIT_EXPONENT (-2)
        0x65u, 0x13u,                         //       UNIT(Inchu,EngLinear)
        0x09u, 0x30u,                         //       USAGE (X)
        0x35u, 0x00u,                         //       PHYSICAL_MINIMUM (0)
        0x46u, touchpadPhyWidth.lowUByte, touchpadPhyWidth.highUByte,                   //       PHYSICAL_MAXIMUM (TOUCHPAD_PHY_WIDTH)
        0x95u, 0x01u,                         //       REPORT_COUNT (1)
        0x81u, 0x02u,                         //       INPUT (Datau,Varu,Abs)      // x (2 byte)
        0x46u, touchpadPhyHeight.lowUByte, touchpadPhyHeight.highUByte,                   //       PHYSICAL_MAXIMUM (TOUCHPAD_PHY_HEIGHT)
        0x09u, 0x31u,                         //       USAGE (Y)
        0x81u, 0x02u,                         //       INPUT (Datau,Varu,Abs)      // y (2 byte)
        0xc0u,                               //    END_COLLECTION
        0x55u, 0x0Cu,                         //    UNIT_EXPONENT (-4)
        0x66u, 0x01u, 0x10u,                   //    UNIT (Seconds)
        0x47u, 0xffu, 0xffu, 0x00u, 0x00u,      //     PHYSICAL_MAXIMUM (65535)
        0x27u, 0xffu, 0xffu, 0x00u, 0x00u,         //  LOGICAL_MAXIMUM (65535)
        0x75u, 0x10u,                           //  REPORT_SIZE (16)
        0x95u, 0x01u,                           //  REPORT_COUNT (1)
        0x05u, 0x0du,                         //    USAGE_PAGE (Digitizers)
        0x09u, 0x56u,                         //    USAGE (Scan Time)
        0x81u, 0x02u,                           //  INPUT (Datau,Varu,Abs)         // scan time (2 byte)
        0x09u, 0x54u,                         //    USAGE (Contact count)
        0x25u, 0x7fu,                           //  LOGICAL_MAXIMUM (127)
        0x95u, 0x01u,                         //    REPORT_COUNT (1)
        0x75u, 0x08u,                         //    REPORT_SIZE (8)
        0x81u, 0x02u,                         //    INPUT (Datau,Varu,Abs)         // contact count (1 byte)
        0x05u, 0x09u,                         //    USAGE_PAGE (Button)
        0x09u, 0x01u,                         //    USAGE_(Button 1)
        0x25u, 0x01u,                         //    LOGICAL_MAXIMUM (1)
        0x75u, 0x01u,                         //    REPORT_SIZE (1)
        0x95u, 0x01u,                         //    REPORT_COUNT (1)
        0x81u, 0x02u,                         //    INPUT (Datau,Varu,Abs)
        0x95u, 0x07u,                          //   REPORT_COUNT (7)
        0x81u, 0x03u,                         //    INPUT (Cnstu,Varu,Abs)         // btn1 (1 byte)u, end of report REPORTID_TOUCHPAD
        0x05u, 0x0du,                         //    USAGE_PAGE (Digitizer)
        0x85u, maxCountId,            //   REPORT_ID (Feature)
        0x09u, 0x55u,                         //    USAGE (Contact Count Maximum)
        0x09u, 0x59u,                         //    USAGE (Pad TYpe)
        0x75u, 0x04u,                         //    REPORT_SIZE (4)
        0x95u, 0x02u,                         //    REPORT_COUNT (2)
        0x25u, 0x0fu,                         //    LOGICAL_MAXIMUM (15)
        0xb1u, 0x02u,                         //    FEATURE (Datau,Varu,Abs)
        0x06u, 0x00u, 0xffu,                   //    USAGE_PAGE (Vendor Defined)
        0x85u, piphqaId,               //    REPORT_ID (PTPHQA)
        0x09u, 0xC5u,                         //    USAGE (Vendor Usage 0xC5)
        0x15u, 0x00u,                         //    LOGICAL_MINIMUM (0)
        0x26u, 0xffu, 0x00u,                   //    LOGICAL_MAXIMUM (0xff)
        0x75u, 0x08u,                         //    REPORT_SIZE (8)
        0x96u, 0x00u, 0x01u,                   //    REPORT_COUNT (0x100 (256))
        0xb1u, 0x02u,                         //    FEATURE (Datau,Varu,Abs)
        0xc0u,                               // END_COLLECTION
        //CONFIG TLC
        0x05u, 0x0du,                         //    USAGE_PAGE (Digitizer)
        0x09u, 0x0Eu,                         //    USAGE (Configuration)
        0xa1u, 0x01u,                         //   COLLECTION (Application)
        0x85u, featureId,             //   REPORT_ID (Feature)
        0x09u, 0x22u,                         //   USAGE (Finger)
        0xa1u, 0x02u,                         //   COLLECTION (logical)
        0x09u, 0x52u,                         //    USAGE (Input Mode)
        0x15u, 0x00u,                         //    LOGICAL_MINIMUM (0)
        0x25u, 0x0au,                         //    LOGICAL_MAXIMUM (10)
        0x75u, 0x08u,                         //    REPORT_SIZE (8)
        0x95u, 0x01u,                         //    REPORT_COUNT (1)
        0xb1u, 0x02u,                         //    FEATURE (Datau,Varu,Abs
        0xc0u,                               //   END_COLLECTION
        0x09u, 0x22u,                         //   USAGE (Finger)
        0xa1u, 0x00u,                         //   COLLECTION (physical)
        0x85u, functionSwitchId,     //     REPORT_ID (Feature)
        0x09u, 0x57u,                         //     USAGE(Surface switch)
        0x09u, 0x58u,                         //     USAGE(Button switch)
        0x75u, 0x01u,                         //     REPORT_SIZE (1)
        0x95u, 0x02u,                         //     REPORT_COUNT (2)
        0x25u, 0x01u,                         //     LOGICAL_MAXIMUM (1)
        0xb1u, 0x02u,                         //     FEATURE (Datau,Varu,Abs)
        0x95u, 0x06u,                         //     REPORT_COUNT (6)
        0xb1u, 0x03u,                         //     FEATURE (Cnstu,Varu,Abs)
        0xc0u,                               //   END_COLLECTION
        0xc0u,                               // END_COLLECTION
        //MOUSE TLC
        0x05u, 0x01u,                         // USAGE_PAGE (Generic Desktop)
        0x09u, 0x02u,                         // USAGE (Mouse)
        0xa1u, 0x01u,                         // COLLECTION (Application)
        0x85u, mouseId,               //   REPORT_ID (Mouse)
        0x09u, 0x01u,                         //   USAGE (Pointer)
        0xa1u, 0x00u,                         //   COLLECTION (Physical)
        0x05u, 0x09u,                         //     USAGE_PAGE (Button)
        0x19u, 0x01u,                         //     USAGE_MINIMUM (Button 1)
        0x29u, 0x02u,                         //     USAGE_MAXIMUM (Button 2)
        0x25u, 0x01u,                         //     LOGICAL_MAXIMUM (1)
        0x75u, 0x01u,                         //     REPORT_SIZE (1)
        0x95u, 0x02u,                         //     REPORT_COUNT (2)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0x95u, 0x06u,                         //     REPORT_COUNT (6)
        0x81u, 0x03u,                         //     INPUT (Cnstu,Varu,Abs)
        0x05u, 0x01u,                         //     USAGE_PAGE (Generic Desktop)
        0x09u, 0x30u,                         //     USAGE (X)
        0x09u, 0x31u,                         //     USAGE (Y)
        0x75u, 0x10u,                         //     REPORT_SIZE (16)
        0x95u, 0x02u,                         //     REPORT_COUNT (2)
        0x25u, 0x0au,                          //    LOGICAL_MAXIMUM (10)
        0x81u, 0x06u,                         //     INPUT (Datau,Varu,Rel)
        0xc0u,                               //   END_COLLECTION
        0xc0u,                                //END_COLLECTION

        // Integrated Windows Pen TLC
        0x05u, 0x0du,                         // USAGE_PAGE (Digitizers)
        0x09u, 0x02u,                         // USAGE (Pen)
        0xa1u, 0x01u,                         // COLLECTION (Application)
        0x85u, penId,                 //   REPORT_ID (Pen)
        0x09u, 0x20u,                         //   USAGE (Stylus)
        0xa1u, 0x00u,                         //   COLLECTION (Physical)
        0x09u, 0x42u,                         //     USAGE (Tip Switch)
        0x09u, 0x44u,                         //     USAGE (Barrel Switch)
        0x09u, 0x3cu,                         //     USAGE (Invert)
        0x09u, 0x45u,                         //     USAGE (Eraser Switch)
        0x15u, 0x00u,                         //     LOGICAL_MINIMUM (0)
        0x25u, 0x01u,                         //     LOGICAL_MAXIMUM (1)
        0x75u, 0x01u,                         //     REPORT_SIZE (1)
        0x95u, 0x04u,                         //     REPORT_COUNT (4)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0x95u, 0x01u,                         //     REPORT_COUNT (1)
        0x81u, 0x03u,                         //     INPUT (Cnstu,Varu,Abs)
        0x09u, 0x32u,                         //     USAGE (In Range)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0x95u, 0x02u,                         //     REPORT_COUNT (2)
        0x81u, 0x03u,                         //     INPUT (Cnstu,Varu,Abs)
        0x05u, 0x01u,                         //     USAGE_PAGE (Generic Desktop)
        0x09u, 0x30u,                         //     USAGE (X)
        0x75u, 0x10u,                         //     REPORT_SIZE (16)
        0x95u, 0x01u,                         //     REPORT_COUNT (1)
        0xa4u,                               //     PUSH
        0x55u, exponentToCodeMap[penpadUnitExp]!!,                         //     UNIT_EXPONENT (-3)
        0x65u, 0x13u,                         //     UNIT (Inchu,EngLinear)
        0x35u, 0x00u,                         //     PHYSICAL_MINIMUM (0)
        0x46u, penpadPhyWidth.lowUByte, penpadPhyWidth.highUByte,                   //     PHYSICAL_MAXIMUM (8250)
        0x26u, 0xf8u, 0x52u,                   //     LOGICAL_MAXIMUM (21240)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0x09u, 0x31u,                         //     USAGE (Y)
        0x46u, penpadPhyHeight.lowUByte, penpadPhyHeight.highUByte,                   //     PHYSICAL_MAXIMUM (6188)
        0x26u, 0x6cu, 0x3eu,                   //     LOGICAL_MAXIMUM (15980)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0xb4u,                               //     POP
        0x05u, 0x0du,                         //     USAGE_PAGE (Digitizers)
        0x09u, 0x30u,                         //     USAGE (Tip Pressure)
        0x26u, 0xffu, 0x00u,                   //     LOGICAL_MAXIMUM (255)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0x75u, 0x08u,                         //     REPORT_SIZE (8)
        0x09u, 0x3du,                         //     USAGE (X Tilt)
        0x15u, 0x81u,                         //     LOGICAL_MINIMUM (-127)
        0x25u, 0x7fu,                         //     LOGICAL_MAXIMUM (127)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0x09u, 0x3eu,                         //     USAGE (Y Tilt)
        0x15u, 0x81u,                         //     LOGICAL_MINIMUM (-127)
        0x25u, 0x7fu,                         //     LOGICAL_MAXIMUM (127)
        0x81u, 0x02u,                         //     INPUT (Datau,Varu,Abs)
        0xc0u,                               //   END_COLLECTION
        0xc0u                                // END_COLLECTION
    );
}