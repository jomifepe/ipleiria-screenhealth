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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER
import com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT
import com.meicm.cas.digitalwellbeing.communication.receiver.LockUnlockReceiver
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.communication.receiver.UnlockServiceRestartReceiver
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.Const.ACTIVITY_UPDATE_TIME


class UnlockService: Service() {
    private lateinit var lockUnlockReceiver: LockUnlockReceiver

    private var activityRecognitionPendingIntent: PendingIntent? = null
    private var activityRecognitionClient: ActivityRecognitionClient? = null

    private val activityTypesToMonitor = listOf(
        DetectedActivity.IN_VEHICLE,
        DetectedActivity.ON_BICYCLE,
        DetectedActivity.ON_FOOT,
        DetectedActivity.RUNNING,
        DetectedActivity.STILL,
        DetectedActivity.WALKING
    )

    override fun onBind(intent: Intent?): IBinder? {
        Log.i("DW_LOGGING", "Service onBind")
        return null
    }

    override fun onCreate() {
        val filter =
            IntentFilter(Const.ACTION_FIRST_LAUNCH)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
        lockUnlockReceiver = LockUnlockReceiver()
        registerReceiver(lockUnlockReceiver, filter)

        startActivityRecognition()

//        if (isAppFirstRun(this)) sendBroadcast(Intent(Const.ACTION_FIRST_LAUNCH))
        Log.d(Const.LOG_TAG, "[UnlockService] Registered broadcast receiver")
        Log.d(Const.LOG_TAG, "[UnlockService] Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(Const.LOG_TAG, "[UnlockService] On start command")
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

    override fun onDestroy() {
        //super.onDestroy()
        Log.d(Const.LOG_TAG, "[UnlockService] Unlock Service destroyed")
        stopActivityRecognition()
        unregisterReceiver(lockUnlockReceiver)
        sendBroadcast(Intent(this, UnlockServiceRestartReceiver::class.java))
    }

    private fun saveCurrentUsageWarningTimer() {
        if (AppState.lastUWTimerStart == null) return
        val elapsedTime = System.currentTimeMillis() - AppState.lastUWTimerStart!!
        AppPreferences.with(this).save(Const.PREF_LAST_UW_TIMER_ELAPSED, elapsedTime)
        Log.d(
            Const.LOG_TAG,
            "[UnlockService] Saving current usage warning time: ${elapsedTime / 1000.0} s"
        )
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

    private fun startActivityRecognition() {
        try {
            Toast.makeText(this, "#####startActivityRecognition#####", Toast.LENGTH_SHORT).show()
            Log.d(
                Const.LOG_TAG,
                "#####startActivityRecognition#####"
            )

            if (activityRecognitionPendingIntent != null && activityRecognitionClient != null) return
            if (GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS
            ) return

            activityRecognitionPendingIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, ActivityRecognitionIntentService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            activityRecognitionClient = ActivityRecognition.getClient(this)
            activityRecognitionClient!!.requestActivityUpdates(
                ACTIVITY_UPDATE_TIME,
                activityRecognitionPendingIntent
            )
           activityRecognitionClient!!.requestActivityTransitionUpdates(
                buildTransitionRequest(), activityRecognitionPendingIntent)

            Log.d(
                Const.LOG_TAG,
                "#####Trying to start the service#####"
            )

        } catch (e: Exception) {
            Log.d(
                Const.LOG_TAG,
                "Error starting activity recognition service"
            )
        }
    }

    private fun stopActivityRecognition() {
        try {
            if (activityRecognitionClient == null || activityRecognitionPendingIntent == null) return
            activityRecognitionClient!!.removeActivityUpdates(activityRecognitionPendingIntent!!)
            activityRecognitionClient!!.removeActivityTransitionUpdates(
                activityRecognitionPendingIntent!!
            )
            activityRecognitionClient = null
            activityRecognitionPendingIntent = null
            Log.d(
                Const.LOG_TAG,
                "Success stopping activity recognition service"
            )
        } catch (e: Exception) {
            Log.d(
                Const.LOG_TAG,
                "Error stopping activity recognition service"
            )
        }
    }

    private fun buildTransitionRequest(): ActivityTransitionRequest {
        val transitions = mutableListOf<ActivityTransition>()
        activityTypesToMonitor.forEach {
            transitions += createActivityTransaction(it, ACTIVITY_TRANSITION_ENTER)
            transitions += createActivityTransaction(it, ACTIVITY_TRANSITION_EXIT)
        }
        return ActivityTransitionRequest(transitions)
    }

    private fun createActivityTransaction(
        activity: Int,
        activityTransition: Int
    ): ActivityTransition {
        return ActivityTransition.Builder()
            .setActivityType(activity)
            .setActivityTransition(activityTransition)
            .build()
    }

//    override fun onDestroy() {
//        val broadcastIntent = Intent()
//        broadcastIntent.action = "restartservice"
//        broadcastIntent.setClass(this, BroadcastReceiverRestarter::class.java)
//        this.sendBroadcast(broadcastIntent)
//    }
}