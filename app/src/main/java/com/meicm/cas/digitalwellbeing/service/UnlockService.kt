package com.meicm.cas.digitalwellbeing.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.meicm.cas.digitalwellbeing.ScreenInteractiveReceiver
import com.meicm.cas.digitalwellbeing.util.Const

class UnlockService: Service() {
    override fun onBind(intent: Intent?): IBinder? {
        Log.i("DW_LOGGING", "Service onBind")
        return null
    }

    override fun onCreate() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        val mReceiver: BroadcastReceiver =
            ScreenInteractiveReceiver()
        registerReceiver(mReceiver, filter)
        Log.d(Const.LOG_TAG, "Registered broadcast receiver")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onTaskRemoved(intent)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        tryToDestroyReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(Const.LOG_TAG, "Unlock Service destroyed")

        tryToDestroyReceiver()
    }

    private fun tryToDestroyReceiver() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val updateServiceIntent = Intent(this, ScreenInteractiveReceiver::class.java)
        val pendingUpdateIntent = PendingIntent.getService(this, 0, updateServiceIntent, 0)

        try {
            alarmManager.cancel(pendingUpdateIntent)
        } catch (e: Exception) {
            Log.e(Const.LOG_TAG, "AlarmManager update was not canceled. $e")
        }
    }

    //    override fun onDestroy() {
//        val broadcastIntent = Intent()
//        broadcastIntent.action = "restartservice"
//        broadcastIntent.setClass(this, BroadcastReceiverRestarter::class.java)
//        this.sendBroadcast(broadcastIntent)
//    }
}