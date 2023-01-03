package com.example.transferdata

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.transferdata.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    val outPackets = Channel<ByteArray>(120)
    val inPackets = Channel<ByteArray>(120)
    private lateinit var mcuUsbScope: CoroutineScope

    private val usbMaxPacketSize = 64
    private val rcvDataBuff = ByteArray(usbMaxPacketSize)

    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbEndpointBlkIn: UsbEndpoint? = null
    private var usbEndpointBlkOut: UsbEndpoint? = null
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null

    private lateinit var bytes: ByteArray
    private val TIMEOUT = 0
    private val forceClaim = true
    private val usbInterfaceNdx = 0


    private val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val newUsbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            //call method to set up device communication
                            initUsbDevice(newUsbDevice)
                            Log.v("maryam", "USB Attached!")
                    } else {
                        shutDownUsb()
                        Log.v("maryam", "USB Detached! $newUsbDevice")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        // Create unique coroutine scope
        mcuUsbScope= CoroutineScope(Job() + Dispatchers.IO)


        var permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbReceiver, filter)
            // Get USB service
            usbManager= getSystemService(Context.USB_SERVICE) as UsbManager
            if (usbManager.deviceList.isEmpty()){
                Toast.makeText(this,"No USB device found",Toast.LENGTH_LONG).show()
            }
            else{
                // See if device already attached
                usbDevice = usbManager.deviceList.map { z -> z.value }.first()
                val device = usbDevice?.productName
                usbManager.requestPermission(usbDevice,permissionIntent)

                Toast.makeText(this,"deviceName = $device",Toast.LENGTH_LONG).show()
                binding.deviceName.text= usbManager.deviceList.toString()
                initUsbDevice(usbDevice)
                startConnection(usbDevice)

            }

//            startConnection(usbDevice)



        binding.Send.setOnClickListener {
            usbDevice = usbManager.deviceList.map { z -> z.value }.first()
            val device = usbDevice?.productName

            Toast.makeText(this,"deviceName = $device",Toast.LENGTH_LONG).show()
            binding.deviceName.text= usbManager.deviceList.toString()
            initUsbDevice(usbDevice)
        }

        }

    private fun initUsbDevice(newUsbDevice: UsbDevice?): Boolean {
        // Shut down existing
        shutDownUsb()

        if (newUsbDevice == null) {
            return false
        }
            var tmpUsbInterface = newUsbDevice.getInterface(usbInterfaceNdx)
            val tmpUsbEndpointBlkOut = tmpUsbInterface.getEndpoint(0) ?: return false
            val tmpUsbEndpointBlkIn = tmpUsbInterface.getEndpoint(1) ?: return false
            val tmpUsbDeviceConnection = usbManager?.openDevice(usbDevice)?.apply {
                claimInterface(usbInterface, true)
            } ?: return false
            // All good
            usbDevice = newUsbDevice
            usbEndpointBlkIn = tmpUsbEndpointBlkIn
            usbEndpointBlkOut = tmpUsbEndpointBlkOut
            usbDeviceConnection = tmpUsbDeviceConnection
            usbInterface = tmpUsbInterface

            // Kick off USB device

            mcuUsbScope.launch {
                startConnection(usbDevice)
            }

            mcuUsbScope.launch {
                startUsbReceiver()
            }

            return true

    }

    /**
     *  Kick off USB receiver "USB IN"
     * */
    private suspend fun startUsbReceiver() {
        var counter= 0
        var lastSet: ByteArray
        var lastSetSize= 0
        while (true){
            var xferLen= usbDeviceConnection?.bulkTransfer(
                usbEndpointBlkIn,
                rcvDataBuff,
                usbMaxPacketSize,
                250
            )?:0
            if (xferLen > 0) {
                val subSet: ByteArray = rcvDataBuff.sliceArray(0 until xferLen)
                inPackets.send(subSet)
            }
        }

    }

    /**
     * Shutdown USB device
     *  Normally called when USB device is disconnected
     *  or when McuUsbInterface is destroyed
     */
    private fun shutDownUsb(){
        usbDevice= null
        usbEndpointBlkIn= null
        usbEndpointBlkOut= null
        usbDeviceConnection= null
        usbInterface= null
    }

    /**
     * Kick off USB transmitter "USB OUT"
     */
    private suspend fun startUsbTransmiter() {
        while (true) {
            val xmlPacket= outPackets.receive()
            usbDeviceConnection?.bulkTransfer(
                usbEndpointBlkOut,
                xmlPacket,
                xmlPacket.size,
                250
            )
        }

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