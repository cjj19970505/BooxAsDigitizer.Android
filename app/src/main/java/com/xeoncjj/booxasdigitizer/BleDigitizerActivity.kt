package com.xeoncjj.booxasdigitizer

import android.Manifest
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelUuid
import android.util.Log
import android.view.WindowInsets
import android.widget.Button
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat


class BleDigitizerActivity: AppCompatActivity() {
    companion object {
        private const val LOG_TAG = "BleDigitizerActivity"
        private const val UUID_LONG_STYLE_PREFIX: String = "0000";
        private const val UUID_LONG_STYLE_POSTFIX: String = "-0000-1000-8000-00805F9B34FB";
    }

    private val hidGattServer = HidGattServer()

    private val manager: BluetoothManager by lazy {
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val advertiser: BluetoothLeAdvertiser
        get() = manager.adapter.bluetoothLeAdvertiser

    private lateinit var gattServer: BluetoothGattServer

    var i = 0
    @OptIn(ExperimentalUnsignedTypes::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.windowInsetsController!!.hide(
            WindowInsets.Type.statusBars()
        )
        setContentView(R.layout.activity_digitizer)

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        if (permission == PackageManager.PERMISSION_GRANTED) {
            gattServer = manager.openGattServer(applicationContext, hidGattServer).also {
                hidGattServer.init(it)
            }

            advertiser.let {
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
                it.startAdvertising(settings, data, SampleAdvertiseCallback)
            }
        }
        findViewById<Button>(R.id.test_button).setOnClickListener { view ->
            hidGattServer.batteryLevel = hidGattServer.batteryLevel + 1u
            if(i % 2 == 0){
                hidGattServer.keyboardLatestReport = ubyteArrayOf(0x00u, 0x0Au, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u).toByteArray()
            }else{
                hidGattServer.keyboardLatestReport = ubyteArrayOf(0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u).toByteArray()
            }
            ++i
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onDestroy() {
        super.onDestroy()
        advertiser.stopAdvertising(SampleAdvertiseCallback)
        gattServer.close()
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