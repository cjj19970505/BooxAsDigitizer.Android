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
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.xeoncjj.booxasdigitizer.HidHelper.Companion.getReportBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.collections.set
import kotlin.coroutines.cancellation.CancellationException

class BleServerCommBackend(val context:Context, val bluetoothManager: BluetoothManager): BadCommBackend, BluetoothGattServerCallback() {

    companion object {
        private const val LOG_TAG = "HidGattServer"

        private const val UUID_LONG_STYLE_PREFIX: String = "0000";
        private const val UUID_LONG_STYLE_POSTFIX: String = "-0000-1000-8000-00805F9B34FB"

        private const val REPORT_TYPE_INPUT: UByte = 0x01u
        private const val REPORT_TYPE_OUTPUT: UByte = 0x02u
        private const val REPORT_TYPE_FEATURE: UByte = 0x03u

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

    private lateinit var gattServer: BluetoothGattServer

    private val connectedBluetoothDevice = LinkedHashSet<BluetoothDevice>()

    private val readValueMap =
        LinkedHashMap<Any, BleServerCommBackend.(device: BluetoothDevice?, requestId: Int, offset: Int) -> Boolean>()

    private val clientConfigurationDescriptorMap =
        LinkedHashMap<BluetoothGattDescriptor, CharacteristicConfiguration>()


    private val writeValueMap = LinkedHashMap<Any, BleServerCommBackend.(
        device: BluetoothDevice?,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) -> Boolean>()

    @SuppressLint("MissingPermission")
    fun handleClientConfigurationDescriptor(descriptor: BluetoothGattDescriptor, log: String? = null) {
        val writeFun = fun BleServerCommBackend.(
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

        val readFun = fun BleServerCommBackend.(
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
    private fun addStaticValueToReadValueMap(byteArray: ByteArray, logContent: String? = null): BleServerCommBackend.(device: BluetoothDevice?, requestId: Int, offset: Int) -> Boolean {
        return fun BleServerCommBackend.(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
        ): Boolean {
            if(logContent != null){
                Log.i(LOG_TAG, logContent)
            }
            return this.gattServer.sendResponse(
                device!!,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                byteArray
            )
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    val deviceInformationService = BluetoothGattService(
        SERVICE_DEVICE_INFORMATION_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
        it.addCharacteristic(
            BluetoothGattCharacteristic(
                CHARACTERISTIC_PNP_ID, BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ).also {
                readValueMap[it] = addStaticValueToReadValueMap(ubyteArrayOf(0x02u, 0u, 0u, 0u, 0u, 0u, 0u).toByteArray())
            }
        )
    }

    // This map stores the latest report,
    // In case host want to read report characteristic
    val latestReportCache = LinkedHashMap<UByte, ByteArray>()
    val reportIdToCharacteristic = LinkedHashMap<UByte, BluetoothGattCharacteristic>()

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun buildReportCharacter(reportId: UByte, reportType: UByte, readable: Boolean, writable: Boolean): BluetoothGattCharacteristic{
        if(reportType == REPORT_TYPE_OUTPUT){
            TODO("Not yet implemented")
        }
        val characteristicProperty = if(reportType == REPORT_TYPE_INPUT){
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY
        }else if(reportType == REPORT_TYPE_FEATURE){
            if(readable && writable){
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE // TODO("Or writeNoRespose?")
            }else if(readable){
                BluetoothGattCharacteristic.PROPERTY_READ
            }else{
                BluetoothGattCharacteristic.PROPERTY_WRITE
            }
        }else{
            TODO("Not yet implemented")
        }
        val characteristicPermission = if(reportType == REPORT_TYPE_INPUT){
            BluetoothGattCharacteristic.PERMISSION_READ
        }else if(reportType == REPORT_TYPE_FEATURE){
            if(readable && writable){
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            }else if(readable){
                BluetoothGattCharacteristic.PERMISSION_READ
            }else{
                BluetoothGattCharacteristic.PERMISSION_WRITE
            }
        }else{
            TODO("Not yet implemented")
        }

        return BluetoothGattCharacteristic(CHARACTERISTIC_REPORT_UUID, characteristicProperty, characteristicPermission).also {
            if(reportType == REPORT_TYPE_INPUT){
                it.addDescriptor(BluetoothGattDescriptor(DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE).also {
                    handleClientConfigurationDescriptor(it)
                })
            }
            it.addDescriptor(BluetoothGattDescriptor(DESCRIPTOR_REPORT_REFERENCE_UUID, BluetoothGattDescriptor.PERMISSION_READ).also {
                readValueMap[it] = addStaticValueToReadValueMap(
                    ubyteArrayOf(
                        reportId,
                        reportType
                    ).toByteArray()
                )
            })
            reportIdToCharacteristic[reportId] = it
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @SuppressLint("MissingPermission")
    private fun buildHidService(hidDescriptor:HidDescriptor): BluetoothGattService{
        return BluetoothGattService(SERVICE_HID_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.touchpadId, REPORT_TYPE_INPUT, true, false).also { // GET_REPORT
                    readValueMap[it] = { device: BluetoothDevice?, requestId: Int, offset: Int ->
                        if(!latestReportCache.containsKey(hidDescriptor.touchpadId)){
                            latestReportCache[hidDescriptor.touchpadId] = HidHelper.TouchpadReport(hidDescriptor.touchpadId, false, false, 0u, 0u, 0u, 0u, 0u, false).getReportBuffer()
                        }
                        this.gattServer.sendResponse(
                            device!!,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            latestReportCache[hidDescriptor.touchpadId]
                        )
                    }
                })
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.maxCountId, REPORT_TYPE_FEATURE, true, false).also { // GET_FEATURE
                    val buttonType: UByte = 1u
                    val maxContactCount: UByte = 5u
                    val data = byteArrayOf(
                        hidDescriptor.maxCountId.toByte(),
                        ((buttonType.toInt() shl 4) or maxContactCount.toInt()).toByte()
                    )
                    readValueMap[it] = addStaticValueToReadValueMap(data, "GET_FEATURE maxCountId")
                }
            )
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.piphqaId, REPORT_TYPE_FEATURE, true, false).also { // GET_FEATURE
                    val pthqaBlob = ubyteArrayOf(
                        0xfcu, 0x28u, 0xfeu, 0x84u, 0x40u, 0xcbu, 0x9au, 0x87u, 0x0du, 0xbeu, 0x57u, 0x3cu, 0xb6u, 0x70u, 0x09u, 0x88u, 0x07u,
                        0x97u, 0x2du, 0x2bu, 0xe3u, 0x38u, 0x34u, 0xb6u, 0x6cu, 0xedu, 0xb0u, 0xf7u, 0xe5u, 0x9cu, 0xf6u, 0xc2u, 0x2eu, 0x84u,
                        0x1bu, 0xe8u, 0xb4u, 0x51u, 0x78u, 0x43u, 0x1fu, 0x28u, 0x4bu, 0x7cu, 0x2du, 0x53u, 0xafu, 0xfcu, 0x47u, 0x70u, 0x1bu,
                        0x59u, 0x6fu, 0x74u, 0x43u, 0xc4u, 0xf3u, 0x47u, 0x18u, 0x53u, 0x1au, 0xa2u, 0xa1u, 0x71u, 0xc7u, 0x95u, 0x0eu, 0x31u,
                        0x55u, 0x21u, 0xd3u, 0xb5u, 0x1eu, 0xe9u, 0x0cu, 0xbau, 0xecu, 0xb8u, 0x89u, 0x19u, 0x3eu, 0xb3u, 0xafu, 0x75u, 0x81u,
                        0x9du, 0x53u, 0xb9u, 0x41u, 0x57u, 0xf4u, 0x6du, 0x39u, 0x25u, 0x29u, 0x7cu, 0x87u, 0xd9u, 0xb4u, 0x98u, 0x45u, 0x7du,
                        0xa7u, 0x26u, 0x9cu, 0x65u, 0x3bu, 0x85u, 0x68u, 0x89u, 0xd7u, 0x3bu, 0xbdu, 0xffu, 0x14u, 0x67u, 0xf2u, 0x2bu, 0xf0u,
                        0x2au, 0x41u, 0x54u, 0xf0u, 0xfdu, 0x2cu, 0x66u, 0x7cu, 0xf8u, 0xc0u, 0x8fu, 0x33u, 0x13u, 0x03u, 0xf1u, 0xd3u, 0xc1u, 0x0bu,
                        0x89u, 0xd9u, 0x1bu, 0x62u, 0xcdu, 0x51u, 0xb7u, 0x80u, 0xb8u, 0xafu, 0x3au, 0x10u, 0xc1u, 0x8au, 0x5bu, 0xe8u, 0x8au,
                        0x56u, 0xf0u, 0x8cu, 0xaau, 0xfau, 0x35u, 0xe9u, 0x42u, 0xc4u, 0xd8u, 0x55u, 0xc3u, 0x38u, 0xccu, 0x2bu, 0x53u, 0x5cu,
                        0x69u, 0x52u, 0xd5u, 0xc8u, 0x73u, 0x02u, 0x38u, 0x7cu, 0x73u, 0xb6u, 0x41u, 0xe7u, 0xffu, 0x05u, 0xd8u, 0x2bu, 0x79u,
                        0x9au, 0xe2u, 0x34u, 0x60u, 0x8fu, 0xa3u, 0x32u, 0x1fu, 0x09u, 0x78u, 0x62u, 0xbcu, 0x80u, 0xe3u, 0x0fu, 0xbdu, 0x65u,
                        0x20u, 0x08u, 0x13u, 0xc1u, 0xe2u, 0xeeu, 0x53u, 0x2du, 0x86u, 0x7eu, 0xa7u, 0x5au, 0xc5u, 0xd3u, 0x7du, 0x98u, 0xbeu,
                        0x31u, 0x48u, 0x1fu, 0xfbu, 0xdau, 0xafu, 0xa2u, 0xa8u, 0x6au, 0x89u, 0xd6u, 0xbfu, 0xf2u, 0xd3u, 0x32u, 0x2au, 0x9au,
                        0xe4u, 0xcfu, 0x17u, 0xb7u, 0xb8u, 0xf4u, 0xe1u, 0x33u, 0x08u, 0x24u, 0x8bu, 0xc4u, 0x43u, 0xa5u, 0xe5u, 0x24u, 0xc2u
                    )
                    readValueMap[it] = addStaticValueToReadValueMap(pthqaBlob.toByteArray(), "GET_FEATURE piphqaId")
                }
            )
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.featureId, REPORT_TYPE_FEATURE, false, true).also { // SET_FEATURE
                    writeValueMap[it] = fun BleServerCommBackend.(
                        device: BluetoothDevice?,
                        requestId: Int,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?
                    ): Boolean {
                        Log.i(LOG_TAG, "SET_FEATURE featureId")
                        if (responseNeeded) {
                            return gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                        return true
                    }
                }
            )
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.functionSwitchId, REPORT_TYPE_FEATURE, false, true).also { // SET_FEATURE
                    writeValueMap[it] = fun BleServerCommBackend.(
                        device: BluetoothDevice?,
                        requestId: Int,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?
                    ): Boolean {
                        Log.i(LOG_TAG, "SET_FEATURE functionSwitchId")
                        if (responseNeeded) {
                            return gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                        return true
                    }
                }
            )
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.mouseId, REPORT_TYPE_INPUT, true, false).also { // GET_REPORT
                    readValueMap[it] = { device: BluetoothDevice?, requestId: Int, offset: Int ->
                        if(!latestReportCache.containsKey(hidDescriptor.mouseId)){
                            latestReportCache[hidDescriptor.mouseId] = ubyteArrayOf(0u,0u,0u,0u,0u,0u).toByteArray()
                        }
                        this.gattServer.sendResponse(
                            device!!,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            latestReportCache[hidDescriptor.mouseId]
                        )
                    }
                }
            )
            it.addCharacteristic(
                buildReportCharacter(hidDescriptor.penId, REPORT_TYPE_INPUT, true, false).also { // GET_REPORT
                    readValueMap[it] = { device: BluetoothDevice?, requestId: Int, offset: Int ->
                        if(!latestReportCache.containsKey(hidDescriptor.penId)){
                            latestReportCache[hidDescriptor.penId] = HidHelper.PenReport(hidDescriptor.penId, false, false, false, false, false, 0u, 0u, 0u, 0u, 0u).getReportBuffer()
                        }
                        this.gattServer.sendResponse(
                            device!!,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            latestReportCache[hidDescriptor.penId]
                        )
                    }
                }
            )
            it.addCharacteristic(
                BluetoothGattCharacteristic(
                    HidGattServer.Companion.CHARACTERISTIC_REPORT_MAP_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).also {
                    readValueMap[it] = addStaticValueToReadValueMap(hidDescriptor.descriptorData.toByteArray(), "Read CHARACTERISTIC_REPORT_MAP_UUID")
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
                        ).toByteArray(), "Read CHARACTERISTIC_HID_INFORMATION_UUID"
                    )
                }
            )
            it.addCharacteristic(
                BluetoothGattCharacteristic(
                    CHARACTERISTIC_HID_CONTROL_POINT_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                ).also {
                    writeValueMap[it] = fun BleServerCommBackend.(
                        device: BluetoothDevice?,
                        requestId: Int,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?
                    ): Boolean {
                        Log.i(LOG_TAG, "Write CHARACTERISTIC_HID_CONTROL_POINT_UUID")
                        if (responseNeeded) {
                            return gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                        return true
                    }
                }
            )
        }
    }

    private var hidService: BluetoothGattService? = null

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
                        byteArrayOf(31.toByte())
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

    private val outputChannel = Channel<ByteArray>()

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        try {
            outputChannel.receiveAsFlow().collect() {
                // Use ensureActive to throw cancellation exception since aoaOutputStream.write is not a suspend function, it is not aware of any cancellations.
                // https://developer.android.com/kotlin/coroutines/coroutines-best-practices#coroutine-cancellable
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.IO) {
                    val reportId = it[0].toUByte()
                    if(reportIdToCharacteristic.containsKey(reportId)){
                        reportIdToCharacteristic[reportId]!!.value = it
                        for (bluetoothDevice in connectedBluetoothDevice){
                            gattServer!!.notifyCharacteristicChanged(bluetoothDevice, reportIdToCharacteristic[reportId]!!, false)
                        }

                    }
                }
                currentCoroutineContext().ensureActive()
            }
        }catch (e: CancellationException){
            currentCoroutineContext().ensureActive()
        }
    }

    private var reportDescriptorInvalid: Boolean = true

    override suspend fun invalidReportDescriptor() {
        reportDescriptorInvalid = true
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @SuppressLint("MissingPermission")
    override suspend fun sendReport(
        report: ByteArray,
        newReportDescriptorFunc: suspend () -> HidDescriptor
    ) {
        if(reportDescriptorInvalid){
            reportDescriptorInvalid = false
            withContext(Dispatchers.Main){
                gattServer = bluetoothManager.openGattServer(context, this@BleServerCommBackend).also {
                    val reportDescriptor = newReportDescriptorFunc()
                    hidService = buildHidService(reportDescriptor)
                    initAddingServices[2] = hidService
                    it.addService(initAddingServices[0])
                }
                val bleAdvertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
                bleAdvertiser.let {
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                        .setConnectable(true)
                        .setTimeout(0)
                        .build()
                    val data = AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .addServiceUuid(ParcelUuid(HidGattServer.SERVICE_DEVICE_INFORMATION_UUID))
                        .addServiceUuid(ParcelUuid(HidGattServer.SERVICE_BATTERY_REPORT_GUID))
                        .addServiceUuid(ParcelUuid(HidGattServer.SERVICE_HID_UUID))
                        .build()
                    it.startAdvertising(settings, data,
                        BleDigitizerActivity.SampleAdvertiseCallback
                    )
                }
            }
        }
        if(connectedBluetoothDevice.count() > 0){
            outputChannel.send(report)
        }
    }

    // https://developer.android.com/reference/android/bluetooth/BluetoothGattServer.html#addService(android.bluetooth.BluetoothGattService)
    // The BluetoothGattServerCallback.onServiceAdded callback will indicate whether this service has been added successfully. Do not add another service before this callback.
    private val initAddingServices = arrayOf(deviceInformationService, batteryService, null)
    @RequiresPermission(allOf = [android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE])
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

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
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

    object SampleAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(LOG_TAG, "Started advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(LOG_TAG, "Failed to start advertising: $errorCode")
        }
    }
}