package com.meicm.cas.digitalwellbeing.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meicm.cas.digitalwellbeing.State
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.compareTimestampsDateEqual
import com.meicm.cas.digitalwellbeing.util.getDateStringFromEpoch
import kotlinx.coroutines.*
import java.util.*

class ScreenInteractiveReceiver : BroadcastReceiver() {
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

        State.isUnlocked = false

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase
                .getDatabase(context)
                .unlockDao()
                .updateLastUnlockEndTimestamp(System.currentTimeMillis())
            Log.d(Const.LOG_TAG, "Closing last unlock")
        }

        // TODO: Fix no alarm clear when the app is killed
        // Cancel existing alarm
        if (alarmPI != null) {
            Log.d(Const.LOG_TAG, "Cancelling existing alarm")
            alarmManager.cancel(alarmPI)
        } else {
            Log.d(Const.LOG_TAG, "No alarms to cancel")
        }
    }

    private fun performUnlockActions(context: Context) {
        Log.d(Const.LOG_TAG, "Unlocked")

        State.isUnlocked = true
        State.unlockTime = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase
                .getDatabase(context)
                .unlockDao()
                .insert(Unlock(0,
                    State.unlockTime, null))
            Log.d(Const.LOG_TAG, "Inserted unlock into the database")
        }

        tryLaunchUsageWarningTimer(context)
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
        val alarmIntent = Intent(context, UsageWarningReceiver::class.java)
        alarmPI = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 5)

//        val defaultTime = 30 * 6000L
        val defaultDuration = 5000L
        val alarmTime = System.currentTimeMillis() +
                AppPreferences.with(context).getLong(Const.PREF_UW_LAST_TIME, defaultDuration)
        Log.d(Const.LOG_TAG, "Starting usage notification timer, triggering at: ${getDateStringFromEpoch(alarmTime)} s")

        if (repeating) {
            alarmManager.setRepeating(AlarmManager.RTC, /* first trigger */ alarmTime, /* repeat */ 60000, alarmPI)
        } else {
            alarmManager.set(AlarmManager.RTC, alarmTime, alarmPI)
        }
    }
}