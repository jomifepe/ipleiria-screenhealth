package com.meicm.cas.digitalwellbeing.service

import android.app.AlarmManager
import android.app.IntentService
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.communication.receiver.LockUnlockReceiver
import com.meicm.cas.digitalwellbeing.communication.receiver.UnlockServiceRestartReceiver
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const


class UnlockService: Service() {
    private lateinit var lockUnlockReceiver: LockUnlockReceiver

    override fun onCreate() {
        super.onCreate()
        val filter =
            IntentFilter(Const.ACTION_FIRST_LAUNCH)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
        lockUnlockReceiver = LockUnlockReceiver()
        registerReceiver(lockUnlockReceiver, filter)

        Log.d(Const.LOG_TAG, "[UnlockService] Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            // send broadcast to trigger and "unlock" because the service was down
            Const.ACTION_FIRST_LAUNCH -> sendBroadcast(Intent(Const.ACTION_FIRST_LAUNCH))
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(Const.LOG_TAG, "[UnlockService] onTaskRemoved")
        saveCurrentUsageWarningTimer()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(Const.LOG_TAG, "[UnlockService] Service destroyed")
        unregisterReceiver(lockUnlockReceiver)
        sendBroadcast(Intent(this, UnlockServiceRestartReceiver::class.java))
    }

    private fun saveCurrentUsageWarningTimer() {
        if (AppState.lastUWTimerStart == null) return
        val elapsedTime = System.currentTimeMillis() - AppState.lastUWTimerStart!!
        AppPreferences.with(this).save(Const.PREF_LAST_UW_TIMER_ELAPSED, elapsedTime)
        Log.d(Const.LOG_TAG, "[UnlockService] Saving current usage warning time: ${elapsedTime / 1000.0} s")
    }

    private fun tryToDestroyReceiver() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val updateServiceIntent = Intent(this, LockUnlockReceiver::class.java)
        val pendingUpdateIntent = PendingIntent.getService(this, 0, updateServiceIntent, 0)

        try {
            alarmManager.cancel(pendingUpdateIntent)
        } catch (e: Exception) {
            Log.e(Const.LOG_TAG, "[UnlockService] AlarmManager update was not canceled. $e")
        }
    }
}