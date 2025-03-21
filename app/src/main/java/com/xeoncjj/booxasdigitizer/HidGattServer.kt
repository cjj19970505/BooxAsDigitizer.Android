package com.xeoncjj.booxasdigitizer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID

data class CharacteristicConfiguration(var notification: Boolean, var indication: Boolean) {
    companion object {
        fun fromByte(b: Byte): CharacteristicConfiguration {
            val notification = ((b.toInt() and 0x1) != 0)
            val indication = ((b.toInt() and 0x2) != 0)
            return CharacteristicConfiguration(notification, indication)
        }
    }

    val configByte: Byte
        get() {
            var b: Int = 0
            if (notification) {
                b = b or 0x1
            }
            if (indication) {
                b = b or 0x2
            }
            return b.toByte()
        }
}

class HidGattServer() : BluetoothGattServerCallback() {
    companion object {
        private const val LOG_TAG = "HidGattServer"

        private const val UUID_LONG_STYLE_PREFIX: String = "0000";
        private const val UUID_LONG_STYLE_POSTFIX: String = "-0000-1000-8000-00805F9B34FB"

        private fun Int.toLongUUID(): UUID{
            return UUID.fromString(
                UUID_LONG_STYLE_PREFIX
                        + "%04x".format(this)
                        + UUID_LONG_STYLE_POSTFIX
            )
        }

        val SERVICE_DEVICE_INFORMATION_UUID: UUID = 0x180A.toLongUUID()
        val CHARACTERISTIC_PNP_ID: UUID = 0x2A50.toLongUUID()
        val SERVICE_BATTERY_REPORT_GUID: UUID = 0x180F.toLongUUID()
        val CHARACTERISTIC_BATTERY_LEVEL_UUID: UUID =0x2A19.toLongUUID()
        val SERVICE_HID_UUID: UUID = 0x1812.toLongUUID()
        val CHARACTERISTIC_REPORT_MAP_UUID: UUID =0x2A4B.toLongUUID()
        val DESCRIPTOR_EXTERNAL_REPORT_REFERENCE_UUID: UUID = 0x2907.toLongUUID()
        val CHARACTERISTIC_REPORT_UUID: UUID =0x2A4D.toLongUUID()
        val DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION_UUID: UUID = 0x2902.toLongUUID()
        val DESCRIPTOR_REPORT_REFERENCE_UUID: UUID = 0x2908.toLongUUID()
        val CHARACTERISTIC_HID_CONTROL_POINT_UUID: UUID =0x2A4C.toLongUUID()
        val CHARACTERISTIC_HID_INFORMATION_UUID: UUID = 0x2A4A.toLongUUID()
        val CHARACTERISTIC_PROTOCOL_MODE_UUID: UUID = 0x2A4E.toLongUUID()
    }

    private val connectedBluetoothDevice = LinkedHashSet<BluetoothDevice>()

    private val readValueMap =
        LinkedHashMap<Any, HidGattServer.(device: BluetoothDevice?, requestId: Int, offset: Int) -> Boolean>()

    private val writeValueMap = LinkedHashMap<Any, HidGattServer.(
        device: BluetoothDevice?,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) -> Boolean>()

    @SuppressLint("MissingPermission")
    fun handleClientConfigurationDescriptor(descriptor: BluetoothGattDescriptor) {
        val writeFun = fun HidGattServer.(
            device: BluetoothDevice?,
            requestId: Int,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ): Boolean {
            if (clientConfigurationDescriptorMap.contains(descriptor)) {
                var status: Int = BluetoothGatt.GATT_SUCCESS
                if (value != null && value.count() > 0) {
                    clientConfigurationDescriptorMap[descriptor] =
                        CharacteristicConfiguration.fromByte(value[offset])
                    status = BluetoothGatt.GATT_SUCCESS
                }
                return if (responseNeeded) {
                    gattServer.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                } else {
                    true
                }
            } else {
                Log.e(LOG_TAG, "ClientConfigurationDescriptor is not registered")
                return false
            }
        }

        val readFun = fun HidGattServer.(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
        ): Boolean {
            return if (clientConfigurationDescriptorMap.contains(descriptor)) {
                this.gattServer.sendResponse(
                    device!!,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    byteArrayOf(clientConfigurationDescriptorMap[descriptor]!!.configByte, 0)
                )
            } else {
                Log.e(LOG_TAG, "ClientConfigurationDescriptor is not registered")
                false
            }
        }
        clientConfigurationDescriptorMap[descriptor] = CharacteristicConfiguration(false, false)
        readValueMap[descriptor] = readFun
        writeValueMap[descriptor] = writeFun
    }


    @SuppressLint("MissingPermission")
    private fun addStaticValueToReadValueMap(byteArray: ByteArray): HidGattServer.(device: BluetoothDevice?, requestId: Int, offset: Int) -> Boolean {
        return fun HidGattServer.(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
        ): Boolean {
            return this.gattServer.sendResponse(
                device!!,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                byteArray
            )
        }
    }

    private val clientConfigurationDescriptorMap =
        LinkedHashMap<BluetoothGattDescriptor, CharacteristicConfiguration>()

    @OptIn(ExperimentalUnsignedTypes::class)
    val deviceInformationService = BluetoothGattService(SERVICE_DEVICE_INFORMATION_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            it.addCharacteristic(
                BluetoothGattCharacteristic(CHARACTERISTIC_PNP_ID, BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ).also {
                        readValueMap[it] = addStaticValueToReadValueMap(ubyteArrayOf(0x02u, 0u, 0u, 0u, 0u, 0u, 0u).toByteArray())
                }
            )
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @SuppressLint("MissingPermission")
    val hidService =
        BluetoothGattService(SERVICE_HID_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            it.addCharacteristic(
                BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).also {
                    it.addDescriptor(
                        BluetoothGattDescriptor(
                            DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
                            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                        ).also {
                            handleClientConfigurationDescriptor(it)
                        })
                    it.addDescriptor(BluetoothGattDescriptor(
                        DESCRIPTOR_REPORT_REFERENCE_UUID,
                        BluetoothGattDescriptor.PERMISSION_READ
                    ).also {
                        readValueMap[it] = addStaticValueToReadValueMap(
                            ubyteArrayOf(
                                0x01u, // Report ID: 1
                                0x01u  // Report Type: Input
                            ).toByteArray()
                        )
                    })

                    readValueMap[it] = { device: BluetoothDevice?, requestId: Int, offset: Int ->
                        this.gattServer.sendResponse(
                            device!!,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            _keyboardLatestReport
                        )
                    }
                })

            it.addCharacteristic(
                BluetoothGattCharacteristic(
                    CHARACTERISTIC_REPORT_MAP_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).also {
                    readValueMap[it] = addStaticValueToReadValueMap(
                        ubyteArrayOf(
                            0x05u, 0x01u,       // USAGE_PAGE (Generic Desktop)
                            0x09u, 0x06u,       // USAGE (Keyboard)
                            0xa1u, 0x01u,       // COLLECTION (Application)
                            0x05u, 0x07u,       //   USAGE_PAGE (Keyboard)
                            0x85u, 0x01u,       //   REPORT_ID (1)
                            0x19u, 0xe0u,       //   USAGE_MINIMUM (Keyboard LeftControl)
                            0x29u, 0xe7u,       //   USAGE_MAXIMUM (Keyboard Right GUI)
                            0x15u, 0x00u,       //   LOGICAL_MINIMUM (0)
                            0x25u, 0x01u,       //   LOGICAL_MAXIMUM (1)
                            0x75u, 0x01u,       //   REPORT_SIZE (1)
                            0x95u, 0x08u,       //   REPORT_COUNT (8)
                            0x81u, 0x02u,       //   INPUT (Data,Var,Abs)
                            0x75u, 0x08u,       //   REPORT_SIZE (8)
                            0x95u, 0x07u,       //   REPORT_COUNT (7)
                            0x19u, 0x00u,       //   USAGE_MINIMUM (Reserved (no event indicated))
                            0x29u, 0x65u,       //   USAGE_MAXIMUM (Keyboard Application)
                            0x15u, 0x00u,       //   LOGICAL_MINIMUM (0)
                            0x25u, 0x65u,       //   LOGICAL_MAXIMUM (101)
                            0x81u, 0x00u,       //   INPUT (Data,Ary,Abs)
                            0xc0u,             // END_COLLECTION
                        ).toByteArray()
                    )
                }
            )
            it.addCharacteristic(
                BluetoothGattCharacteristic(
                    CHARACTERISTIC_HID_INFORMATION_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).also {
                    readValueMap[it] = addStaticValueToReadValueMap(
                        ubyteArrayOf(
                            0x11u, 0x01u, // HID Version: 1101
                            0x00u,       // Country Code: 0
                            0x01u        // Not Normally Connectable, Remote Wake supported
                        ).toByteArray()
                    )
                }
            )
            it.addCharacteristic(
                BluetoothGattCharacteristic(
                    CHARACTERISTIC_HID_CONTROL_POINT_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                ).also {
                    writeValueMap[it] = fun HidGattServer.(
                        device: BluetoothDevice?,
                        requestId: Int,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?
                    ): Boolean {
                        if (responseNeeded) {
                            return gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                        return true
                    }
                }
            )
        }

    private lateinit var gattServer: BluetoothGattServer
    private var _batteryLevel: UInt = 50u

    @SuppressLint("MissingPermission")
    val batteryService = BluetoothGattService(
        SERVICE_BATTERY_REPORT_GUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).also {
        it.addCharacteristic(
            BluetoothGattCharacteristic(
                CHARACTERISTIC_BATTERY_LEVEL_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).also {
                readValueMap[it] = { device: BluetoothDevice?, requestId: Int, offset: Int ->
                    this.gattServer.sendResponse(
                        device!!,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf(_batteryLevel.toByte())
                    )
                }
                it.addDescriptor(
                    BluetoothGattDescriptor(
                        DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                    ).also {
                        handleClientConfigurationDescriptor(it)
                    })
            })
    }

    var batteryLevel: UInt
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        set(value) {
            _batteryLevel = value
            val batteryChar = batteryService.getCharacteristic(CHARACTERISTIC_BATTERY_LEVEL_UUID)
            if (batteryChar != null) {
                for (bluetoothDevice in connectedBluetoothDevice) {
                    batteryChar.value = byteArrayOf(_batteryLevel.toByte())
                    // I am stuck with this deprecated method because BOOX uses Android 11
                    gattServer.notifyCharacteristicChanged(bluetoothDevice, batteryChar, false)
                }
            }
        }
        get() {
            return _batteryLevel
        }

    @OptIn(ExperimentalUnsignedTypes::class)
    private var _keyboardLatestReport = ubyteArrayOf(0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u).toByteArray()
    var keyboardLatestReport: ByteArray
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        set(value) {
            _keyboardLatestReport = value

            val characteristic = hidService.getCharacteristic(CHARACTERISTIC_REPORT_UUID)
            if (characteristic != null) {
                for (bluetoothDevice in connectedBluetoothDevice) {
                    characteristic.value = _keyboardLatestReport
                    // I am stuck with this deprecated method because BOOX uses Android 11
                    gattServer.notifyCharacteristicChanged(bluetoothDevice, characteristic, false)
                }
            }
        }
        get(){
            return _keyboardLatestReport
        }

    // https://developer.android.com/reference/android/bluetooth/BluetoothGattServer.html#addService(android.bluetooth.BluetoothGattService)
    // The BluetoothGattServerCallback.onServiceAdded callback will indicate whether this service has been added successfully. Do not add another service before this callback.
    private val initAddingServices = arrayOf(deviceInformationService, batteryService, hidService)
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
        super.onServiceAdded(status, service)
        val addedServiceIndex = initAddingServices.indexOf(service)
        if(addedServiceIndex >= 0 && addedServiceIndex < initAddingServices.count()-1){
            gattServer.addService(initAddingServices[addedServiceIndex+1])
        }
        if(addedServiceIndex < initAddingServices.count()-1){
            Log.i(LOG_TAG, "All initial services added.")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun init(gattServer: BluetoothGattServer){
        this.gattServer = gattServer
        this.gattServer.addService(initAddingServices[0])
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
        super.onConnectionStateChange(device, status, newState)
        if (device == null) {
            return
        }
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            connectedBluetoothDevice.add(device)
            Log.i(LOG_TAG, "Device ${device.name} connected")
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            connectedBluetoothDevice.remove(device)
            Log.i(LOG_TAG, "Device ${device.name} disconnected")
        }
        Log.i(LOG_TAG, "Device ${device.name}, newState $newState")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCharacteristicReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic?
    ) {
        Log.i(LOG_TAG, "onCharacteristicReadRequest")
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        characteristic!!
        device!!
        if (readValueMap.contains(characteristic)) {
            readValueMap[characteristic]?.let { it(this, device, requestId, offset) }
        }
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice?,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic?,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) {
        Log.i(LOG_TAG, "onCharacteristicWriteRequest")
        super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
        characteristic!!
        device!!
        if (writeValueMap.contains(characteristic)) {
            writeValueMap[characteristic]?.let {
                it(this, device, requestId, preparedWrite, responseNeeded, offset, value)
            }
        }
    }


    override fun onDescriptorReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor?
    ) {
        Log.i(LOG_TAG, "onDescriptorReadRequest")
        super.onDescriptorReadRequest(device, requestId, offset, descriptor)
        descriptor!!
        device!!
        if (readValueMap.contains(descriptor)) {
            readValueMap[descriptor]?.let { it(this, device, requestId, offset) }
        }
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice?,
        requestId: Int,
        descriptor: BluetoothGattDescriptor?,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) {
        Log.i(LOG_TAG, "onDescriptorWriteRequest")
        super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

        descriptor!!
        device!!
        if (writeValueMap.contains(descriptor)) {
            writeValueMap[descriptor]?.let {
                it(this, device, requestId, preparedWrite, responseNeeded, offset, value)
            }
        }
    }
}