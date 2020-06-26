package com.meicm.cas.digitalwellbeing.communication.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.compareTimestampsDateEqual
import com.meicm.cas.digitalwellbeing.util.getDateTimeStringFromEpoch
import kotlinx.coroutines.*
import java.util.*

class LockUnlockReceiver : BroadcastReceiver() {
    private lateinit var alarmManager: AlarmManager
    private var alarmPI: PendingIntent? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        alarmManager = context!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        when (intent?.action) {
            Const.ACTION_FIRST_LAUNCH -> tryLaunchUsageWarningTimer(context)
            Intent.ACTION_SCREEN_ON -> performUnlockActions(context)
            Intent.ACTION_SCREEN_OFF -> performLockActions(context)
        }
    }

    private fun performLockActions(context: Context) {
        Log.d(Const.LOG_TAG, "Locked")
        val lockTime = System.currentTimeMillis()
        AppState.isUnlocked = false

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase
                .getDatabase(context)
                .unlockDao()
                .updateLastUnlockEndTimestamp(lockTime)
            Log.d(Const.LOG_TAG, "Closing last unlock")
        }

        saveCurrentUsageWarningTimer(context)
        saveLockTime(context, lockTime)

        // TODO: Fix no alarm clear when the app is killed
        // Cancel existing alarm
        if (alarmPI != null) {
            Log.d(Const.LOG_TAG, "Cancelling existing alarm")
            alarmManager.cancel(alarmPI)
        } else {
            Log.d(Const.LOG_TAG, "No alarms to cancel")
        }
    }

    private fun saveLockTime(context: Context, lockTime: Long) {
        AppPreferences.with(context).save(Const.PREF_LOCK_TIME, lockTime)
        Log.d(Const.LOG_TAG, "Saved lock time to preferences")
    }

    private fun performUnlockActions(context: Context) {
        Log.d(Const.LOG_TAG, "Unlocked")

        AppState.isUnlocked = true
        AppState.unlockTime = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase
                .getDatabase(context)
                .unlockDao()
                .insert(Unlock(0, AppState.unlockTime, null))
            Log.d(Const.LOG_TAG, "Inserted unlock into the database")
        }

        tryLaunchUsageWarningTimer(context)
    }

    private fun saveCurrentUsageWarningTimer(context: Context) {
        if (AppState.lastUWTimerStart == null) return
        val elapsedTime = System.currentTimeMillis() - AppState.lastUWTimerStart!!
        AppPreferences.with(context).save(Const.PREF_UW_LAST_TIME, elapsedTime)
        Log.d(Const.LOG_TAG, "Saving current usage warning time: ${elapsedTime / 1000.0} s")
    }

    private fun tryLaunchUsageWarningTimer(context: Context) {
        val pref = AppPreferences.with(context)
        if (pref.contains(Const.PREF_KEY_SNOOZE_LONG)) {
            val timestamp = pref.getLong(Const.PREF_KEY_SNOOZE_LONG, 0L)
            val snoozeIsToday = compareTimestampsDateEqual(timestamp, System.currentTimeMillis())

            if (!snoozeIsToday) {
                launchWarningRepeatingTimer(context, true)
            } else {
                Log.d(Const.LOG_TAG, "Snooze detected, no usage warning timer started")
            }
        } else {
            launchWarningRepeatingTimer(context, true)
        }
    }

    private fun launchWarningRepeatingTimer(context: Context, repeating: Boolean) {
        val alarmIntent = Intent(context, UsageWarningNotificationReceiver::class.java)
        alarmPI = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 5)

//        val defaultTime = 30 * 6000L
        val defaultDuration = 5000L
        val repeatingTime = 60000L

        val currentTime = System.currentTimeMillis()
        val savedTime = AppPreferences.with(context).getLong(Const.PREF_UW_LAST_TIME, 0)
        val lastLockTime = AppPreferences.with(context).getLong(Const.PREF_LOCK_TIME, 0)

        if (savedTime != 0L && (currentTime - lastLockTime) <= Const.UW_UNLOCK_THRESHOLD_MS) {
            // TODO
        }

        val alarmTime = currentTime + (if (savedTime != 0L) repeatingTime - savedTime else defaultDuration)
        Log.d(Const.LOG_TAG, "Starting usage notification timer, triggering at: ${getDateTimeStringFromEpoch(alarmTime)} s")

        AppState.lastUWTimerStart = currentTime
        if (repeating) {
            alarmManager.setRepeating(AlarmManager.RTC, /* first */ alarmTime, /* repeat */ repeatingTime, alarmPI)
        } else {
            alarmManager.set(AlarmManager.RTC, alarmTime, alarmPI)
        }
    }
}