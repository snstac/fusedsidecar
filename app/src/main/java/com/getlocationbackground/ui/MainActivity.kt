package net.undef.fusedsidecar.ui

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import net.undef.fusedsidecar.R
import net.undef.fusedsidecar.service.LocationService
import net.undef.fusedsidecar.util.Util
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    var mLocationService: LocationService = LocationService()
    lateinit var mServiceIntent: Intent
    lateinit var mActivity: Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mActivity = this@MainActivity


        if (!Util.isLocationEnabledOrNot(mActivity)) {
            Util.showAlertLocation(
                mActivity,
                getString(R.string.gps_enable),
                getString(R.string.please_turn_on_gps),
                getString(
                    R.string.ok
                )
            )
        }

        requestPermissionsSafely(
            arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION), 200
        )

        requestPermissionsSafely(
            arrayOf<String>(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 200
        )

        txtStartService.setOnClickListener {
            mLocationService = LocationService()
            mServiceIntent = Intent(this, mLocationService.javaClass)
            if (!Util.isMyServiceRunning(mLocationService.javaClass, mActivity)) {
                startService(mServiceIntent)
                Toast.makeText(
                    mActivity,
                    getString(R.string.service_start_successfully),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    mActivity,
                    getString(R.string.service_already_running),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun requestPermissionsSafely(
        permissions: Array<String>,
        requestCode: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions!!, requestCode)
        }
    }

    override fun onDestroy() {
        if (::mServiceIntent.isInitialized) {
            stopService(mServiceIntent)
        }
        super.onDestroy()
    }
}