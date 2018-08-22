package com.liverowing.android

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.IBinder
import com.liverowing.android.model.messages.DeviceConnectRequest
import com.liverowing.android.model.messages.DeviceDisconnectRequest
import com.liverowing.android.pm.PMDevice
import com.liverowing.android.pm.ble.PM5BleDevice
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

class PMService : Service() {
    private val eventBus = EventBus.getDefault()
    private var device: PMDevice? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onStart(intent: Intent?, startId: Int) {
        super.onStart(intent, startId)
        Timber.d("** onStart")
        if (!eventBus.isRegistered(this)) {
            eventBus.register(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("** onDestroy")
        eventBus.unregister(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceConnectRequestMainThread(data: DeviceConnectRequest) {
        if (data.device is BluetoothDevice) {
            device = PM5BleDevice(this, data.device)
            device?.connect()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceDisconnectMainThread(data: DeviceDisconnectRequest) {

    }
}