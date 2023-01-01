package com.example.transferdata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.transferdata.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var usbManager: UsbManager
    lateinit var usbDevice: UsbDevice
    lateinit var usbConnection: UsbDeviceConnection
    private lateinit var bytes: ByteArray
    private val TIMEOUT = 0
    private val forceClaim = true
    private val usbInterfaceNdx = 1


    val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val newUsbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        newUsbDevice?.apply {
                            //call method to set up device communication
                            initUsbDevice(newUsbDevice)
                            Log.v("maryam", "USB Attached!")

                        }
                    } else {
                        Log.d("maryam", "permission denied for device $newUsbDevice")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.viaCable.setOnClickListener{
            usbManager= getSystemService(Context.USB_SERVICE) as UsbManager
            if (usbManager.deviceList.isEmpty()){
                Toast.makeText(this,"No USB device found",Toast.LENGTH_LONG).show()
            }
            else{
                usbDevice = usbManager.deviceList.map { z -> z.value }.first()
                val device = usbDevice.deviceName
                Toast.makeText(this,"deviceName = $device",Toast.LENGTH_LONG).show()
                binding.deviceName.text= usbManager.deviceList.toString()
                initUsbDevice(usbDevice)
            }

            startConnection()

            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbReceiver, filter)

        }
            /*
        var permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        registerReceiver(usbReceiver, filter)
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = manager.deviceList
            val device = deviceList["mName"]
//        manager.requestPermission(device,permissionIntent)

            Toast.makeText(this,"device$device",Toast.LENGTH_LONG).show()
            binding.deviceName.text = deviceList.toString()
        }*/

        }

    private fun initUsbDevice(usbDevice: UsbDevice?): Boolean {
        if (usbDevice == null) {
            return false
        }
            var usbInterface = usbDevice.getInterface(usbInterfaceNdx)
            val usbEndpointBlkOut = usbInterface.getEndpoint(0) ?: return false
            val usbEndpointBlkIn = usbInterface.getEndpoint(1) ?: return false
            val usbDeviceConnection = usbManager?.openDevice(usbDevice)?.apply {
                claimInterface(usbInterface, true)
            } ?: return false
            // All good
            usbDevice = usbDevice
            usbEndpointBlkIn = usbEndpointBlkIn
            usbEndpointBlkOut = usbEndpointBlkOut
            usbDeviceConnection = usbDeviceConnection
            usbInterface = tmpUsbInterface

            // Kick off USB device

            mcuUsbScope.launch {
                startUsbTransmiter()
            }

            mcuUsbScope.launch {
                startUsbReceiver()
            }

            return true

    }

    private fun startConnection(usbDevice: UsbDevice?) {
        usbDevice?.getInterface(0)?.also { intf ->
            intf.getEndpoint(0)?.also { endpoint ->
                usbManager.openDevice(usbDevice)?.apply {
                    claimInterface(intf, forceClaim)
                    bulkTransfer(endpoint, bytes, bytes.size, TIMEOUT) //do in another thread
                }
            }
        }
    }

}