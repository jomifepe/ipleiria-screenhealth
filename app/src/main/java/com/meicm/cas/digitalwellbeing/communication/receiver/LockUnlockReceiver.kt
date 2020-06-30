package com.meicm.cas.digitalwellbeing.communication.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.service.ActivityRecognitionIntentService
import com.meicm.cas.digitalwellbeing.service.AppUsageGathererService
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.util.Const.ACTIVITY_UPDATE_TIME
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.*

class LockUnlockReceiver : BroadcastReceiver() {
    private lateinit var alarmManager: AlarmManager
    private var alarmPI: PendingIntent? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        alarmManager = context!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Log.d(Const.LOG_TAG, "[LockUnlockReceiver] onReceive")
        when (intent?.action) {
            Const.ACTION_FIRST_LAUNCH -> tryLaunchUsageWarningTimer(context)
            Intent.ACTION_SCREEN_ON -> performUnlockActions(context)
            Intent.ACTION_SCREEN_OFF -> performLockActions(context)
        }
    }

    private fun performLockActions(context: Context) {
        Log.d(Const.LOG_TAG, "[LockUnlockReceiver] Locked screen")
        val lockTime = System.currentTimeMillis()
        AppState.isUnlocked = false

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase
                .getDatabase(context)
                .unlockDao()
                .updateLastUnlockEndTimestamp(lockTime)
            Log.d(Const.LOG_TAG, "[LockUnlockReceiver] Closing last unlock")
        }

        saveCurrentUsageWarningTimer(context, lockTime)
        saveLockTime(context, lockTime)

        // Cancel existing alarm
        val pi = PendingIntent.getBroadcast(context, 0,
            Intent(context, UsageWarningNotificationReceiver::class.java), 0)
        alarmManager.cancel(pi)
        Log.d(Const.LOG_TAG, "[LockUnlockReceiver] Cancelling existing alarms")
    }

    private fun saveLockTime(context: Context, lockTime: Long) {
        AppPreferences.with(context).save(Const.PREF_LOCK_TIME, lockTime)
        Log.d(Const.LOG_TAG, "[LockUnlockReceiver] Saved lock time to preferences")
    }

    private fun performUnlockActions(context: Context) {
        Log.d(Const.LOG_TAG, "[LockUnlockReceiver] Unlocked screen")

        AppState.isUnlocked = true
        AppState.unlockTime = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase
                .getDatabase(context)
                .unlockDao()
                .insert(Unlock(0, AppState.unlockTime!!, null))
            Log.d(Const.LOG_TAG, "[LockUnlockReceiver] Inserted unlock into the database")
        }

        tryLaunchUsageWarningTimer(context)
        if (hasUsagePermission(context)) {
            context.startService(Intent(context, AppUsageGathererService::class.java))
        }
    }

    /**
     * Saves the current elapsed time of the usage warning notification trigger timer
     * (time since unlock or since the last usage warning notification)
     */
    private fun saveCurrentUsageWarningTimer(context: Context, lockTime: Long) {
        if (AppState.lastUWTimerStart == null) return
        var elapsedTime = lockTime - AppState.lastUWTimerStart!!
        if (elapsedTime > Const.UW_TIME_TO_TRIGGER) elapsedTime = 0L
        AppPreferences.with(context).save(Const.PREF_LAST_UW_TIMER_ELAPSED, elapsedTime)
//        Log.d(Const.LOG_TAG, "Saving current usage warning time: ${elapsedTime / 1000.0} s")
    }

    private fun tryLaunchUsageWarningTimer(context: Context) {
        val pref = AppPreferences.with(context)
        if (pref.contains(Const.PREF_KEY_SNOOZE_LONG)) {
            val timestamp = pref.getLong(Const.PREF_KEY_SNOOZE_LONG, 0L)
            val snoozeIsToday = compareTimestampsDateEqual(timestamp, System.currentTimeMillis())

            if (!snoozeIsToday) {
                launchWarningRepeatingTimer(context, true)
            } else {
                Log.d(
                    Const.LOG_TAG,
                    "[LockUnlockReceiver] Snooze detected, no usage warning timer started"
                )
            }
        } else {
            launchWarningRepeatingTimer(context, true)
        }
    }

    private fun launchWarningRepeatingTimer(context: Context, repeating: Boolean) {
        val alarmIntent = Intent(context, UsageWarningNotificationReceiver::class.java)
        alarmPI = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

        val savedLastTimerElapsed =
            AppPreferences.with(context).getLong(Const.PREF_LAST_UW_TIMER_ELAPSED, 0)
        val lastLockTimestamp = AppPreferences.with(context).getLong(Const.PREF_LOCK_TIME, 0)

        /* if there's a saved last timer elapsed time and the last lock time wasn't too long ago
           this is used to prevent quick lock & unlocks from resetting the timer */
        val currentTimestamp = System.currentTimeMillis()
        val alarmTimestamp =
            currentTimestamp + if (savedLastTimerElapsed != 0L && (currentTimestamp - lastLockTimestamp) <= Const.UW_UNLOCK_THRESHOLD_MS) {
                Const.UW_TIME_TO_TRIGGER - savedLastTimerElapsed
            } else {
                Const.UW_TIME_TO_TRIGGER
            }

        val hsm = getHoursMinutesSecondsString(alarmTimestamp - currentTimestamp)
        Log.d(
            Const.LOG_TAG, "[LockUnlockReceiver] Starting usage notification timer, " +
                    "triggering at: ${getDateTimeStringFromEpoch(alarmTimestamp)} ($alarmTimestamp)" +
                    " - $hsm from now"
        )

        if (AppState.lastUWTimerStart == null) AppState.lastUWTimerStart = currentTimestamp
        if (repeating) {
            alarmManager.setRepeating(
                AlarmManager.RTC,
                alarmTimestamp,
                Const.UW_TIME_TO_TRIGGER,
                alarmPI
            )
            return
        }
        alarmManager.set(AlarmManager.RTC, alarmTimestamp, alarmPI)
    }
}