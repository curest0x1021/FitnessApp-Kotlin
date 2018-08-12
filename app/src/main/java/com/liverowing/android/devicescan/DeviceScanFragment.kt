package com.liverowing.android.devicescan

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.LceViewState
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.MvpLceViewStateFragment
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.data.RetainingLceViewState
import com.liverowing.android.R
import com.liverowing.android.ble.service.BleProfileService
import com.liverowing.android.ble.service.PM5Service
import com.liverowing.android.extensions.isPermissionGranted
import kotlinx.android.synthetic.main.fragment_device_scan.*
import timber.log.Timber


class DeviceScanFragment : MvpLceViewStateFragment<SwipeRefreshLayout, List<BluetoothDeviceAndStatus>, DeviceScanView, DeviceScanPresenter>(), DeviceScanView, SwipeRefreshLayout.OnRefreshListener {
    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_FINE_LOCATION = 2
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var viewDividerItemDecoration: DividerItemDecoration

    private val dataSet = mutableListOf<BluetoothDeviceAndStatus>()

    override fun createPresenter() = DeviceScanPresenter(activity!!)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_device_scan, container, false)
    }

    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.d("onServiceConnected($name, $service")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("onServiceDisconnected($name")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewManager = LinearLayoutManager(activity)
        viewDividerItemDecoration = DividerItemDecoration(activity, DividerItemDecoration.VERTICAL)
        viewAdapter = DeviceScanAdapter(dataSet) { _, device ->
            if (device.status != BluetoothDeviceAndStatus.Companion.Status.Disconnected) {
                presenter.disconnectFromDevice(device.device)
            } else {
                presenter.connectToDevice(device.device)

                Timber.d("Creating PM5Service..")

                val service = Intent(context, PM5Service::class.java)
                service.putExtra(BleProfileService.EXTRA_DEVICE_ADDRESS, device.device.address)
                service.putExtra(BleProfileService.EXTRA_DEVICE_NAME, device.device.name)
                context?.startService(service)
                Timber.d("Binding to the service...")
                context?.bindService(service, mServiceConnection, 0)
            }
        }

        contentView.setOnRefreshListener(this@DeviceScanFragment)
        recyclerView = f_device_scan_recyclerview.apply {
            setHasFixedSize(true)
            addItemDecoration(viewDividerItemDecoration)
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.stopScanning()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            presenter.loadDevices(false)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_FINE_LOCATION && isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            presenter.loadDevices(false)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)

    }

    override fun requestLocationPermission() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_FINE_LOCATION)
    }

    override fun loadData(pullToRefresh: Boolean) {
        presenter.loadDevices(pullToRefresh)
    }

    override fun onRefresh() {
        loadData(true)
    }

    override fun setData(data: List<BluetoothDeviceAndStatus>) {
        dataSet.clear()
        dataSet.addAll(data)
        viewAdapter.notifyDataSetChanged()
    }

    override fun showContent() {
        super.showContent()
        contentView.isRefreshing = false
    }

    override fun showError(e: Throwable?, pullToRefresh: Boolean) {
        super.showError(e, pullToRefresh)
        contentView.isRefreshing = false
    }

    override fun showLoading(pullToRefresh: Boolean) {
        super.showLoading(pullToRefresh)
        contentView.isRefreshing = pullToRefresh
    }

    override fun createViewState(): LceViewState<List<BluetoothDeviceAndStatus>, DeviceScanView> = RetainingLceViewState()

    override fun getData(): List<BluetoothDeviceAndStatus> {
        return dataSet
    }

    override fun getErrorMessage(e: Throwable?, pullToRefresh: Boolean): String {
        return "There was an error listing bluetooth devices:\n\n${e?.message}"
    }
}
