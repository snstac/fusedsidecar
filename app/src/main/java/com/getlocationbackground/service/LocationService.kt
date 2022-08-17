package net.undef.fusedsidecar.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import net.undef.fusedsidecar.receiver.RestartBackgroundService
import com.google.android.gms.location.*
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import java.util.concurrent.TimeUnit

class LocationService : Service() {
    private val TAG = "LocationService"

    var counter = 0
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var provider: String = "FSA"
    var speed: Float = 0.0F
    var fixTime: Long = 0L
    var speedAccuracy: Float = 0.0F
    var vertAccuracy: Float = 9999999.0F
    var bearing: Float = 0.0F
    var bearingAccuracy: Float = 9999999.0F
    var altitude: Double = 0.0
    var horAccuracy: Float = 9999999.0F

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) createNotificationChanel() else startForeground(
            1,
            Notification()
        )
        requestLocationUpdates()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChanel() {
        val NOTIFICATION_CHANNEL_ID = "net.undef.fusedsidecar"
        val channelName = "Background Service"
        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager =
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle("App is running count: $counter")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(2, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startTimer()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stoptimertask()
        val broadcastIntent = Intent()
        broadcastIntent.action = "restartservice"
        broadcastIntent.setClass(this, RestartBackgroundService::class.java)
        this.sendBroadcast(broadcastIntent)
    }

    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    fun startTimer() {
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                var count = counter++
                if (latitude != 0.0 && longitude != 0.0) {

                    var point = "<point lat=\"$latitude\" lon=\"$longitude\" hae=\"$altitude\" ce=\"$horAccuracy\" le=\"$vertAccuracy\"/>"
                    var track = "<track speed=\"$speed\" eSpeed=\"$speedAccuracy\" course=\"$bearing\" eCourse=\"$bearingAccuracy\" />"

                    var xmlCOT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                            "<event version=\"2.0\" uid=\"External-GPS\" type=\"a-f-G-E-S\" time=\"2021-05-10T21:11:55.134Z\" start=\"2021-05-10T21:11:55.134Z\" stale=\"2021-05-10T21:16:05.134Z\" how=\"m-g\">" +
                             point +
                             track +
                            "<detail>" +
                            "<precisionlocation geopointsrc=\"GPS\" altitudesrc=\"GPS\"/>" +
                            "<remarks>FusedSidecar: $provider</remarks>" +
                            "</detail>" +
                            "</event>"

                    sendUDP(xmlCOT)

//                    Log.d(
//                        "Location: ",
//                        latitude.toString() + " " + longitude.toString() + " Count:" +
//                                count.toString()
//                    )
                }
            }
        }
        timer!!.schedule(
            timerTask,
            0,
            1000
        ) //1 * 60 * 1000 1 minute
    }

    private fun stoptimertask() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private val remoteHost = "localhost"
    private val remotePort = 4349
    private val socket = DatagramSocket()

    fun sendUDP(messageStr: String) {
        // this function sends the UDP message as String
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            //get the message from function parameters and make it into a byte array
            val sendData = messageStr.toByteArray()
            //craft the packet with the parameters
            val sendPacket = DatagramPacket(sendData, sendData.size, InetAddress.getByName(remoteHost), remotePort)
            socket.send(sendPacket)
            // Log.d(TAG, "Sent location")
        } catch (e: IOException) {
            Log.e(TAG, "IOException: " + e.message)
        }
    }

    private fun requestLocationUpdates() {
//        val request = LocationRequest()
//        request.interval = 10000
//        request.fastestInterval = 5000
//        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        var request = LocationRequest.create().apply {
            interval = TimeUnit.SECONDS.toMillis(10)
            fastestInterval = TimeUnit.SECONDS.toMillis(5)
            maxWaitTime = TimeUnit.MINUTES.toMillis(1)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val client : FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        var locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location: Location = locationResult.lastLocation
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    provider = location.provider
                    speed = location.speed
                    fixTime = location.time
                    horAccuracy = location.accuracy
                    bearing = location.bearing
                    altitude = location.altitude

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        speedAccuracy = location.speedAccuracyMetersPerSecond
                        vertAccuracy = location.verticalAccuracyMeters
                        bearingAccuracy = location.bearingAccuracyDegrees
                    }

                    Log.d("Location Service", "location update $location")
                }
            }
        }

        if (permission == PackageManager.PERMISSION_GRANTED) { // Request location updates and when an update is
            // received, store the location in Firebase
            client.requestLocationUpdates(request, locationCallback, null)
        }
    }
}