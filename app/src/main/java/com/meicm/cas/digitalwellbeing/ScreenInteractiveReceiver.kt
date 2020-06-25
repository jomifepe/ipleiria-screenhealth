package com.meicm.cas.digitalwellbeing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.util.Const
import compareTimestampsDateEqual
import kotlinx.coroutines.*
import java.util.*

class ScreenInteractiveReceiver : BroadcastReceiver() {
    private lateinit var alarmManager: AlarmManager
    private var alarmPI: PendingIntent? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        alarmManager = context!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val action = intent!!.action
        if (Intent.ACTION_SCREEN_ON == action) {
            Log.d(Const.LOG_TAG, "Unlocked")

            State.isUnlocked = true
            State.unlockTime = System.currentTimeMillis()

            runBlocking {
                launch(Dispatchers.IO) {
                    AppDatabase
                        .getDatabase(context)
                        .unlockDao()
                        .insert(Unlock(0, State.unlockTime, null))
                }
            }

            val pref = AppPreferences.with(context)
            if (pref.contains(Const.PREFS_KEY_SNOOZE_LONG)) {
                val timestamp = pref.getLong(Const.PREFS_KEY_SNOOZE_LONG, 0L)
                val snoozeIsToday =
                    compareTimestampsDateEqual(timestamp, System.currentTimeMillis())

                if (!snoozeIsToday) {
                    launchWarningRepeatingTimer(context, false)
                } else {
                    Log.d(Const.LOG_TAG, "Snooze detected, no notification timer started")
                }
            } else {
                launchWarningRepeatingTimer(context, false)
            }

        } else if (Intent.ACTION_SCREEN_OFF == action) {
            Log.d(Const.LOG_TAG, "Locked")

            State.isUnlocked = false

            runBlocking {
                launch(Dispatchers.Default) {
                    AppDatabase
                        .getDatabase(context)
                        .unlockDao()
                        .updateLastUnlockEndTimestamp(System.currentTimeMillis())
                }
            }

            // Cancel existing alarm
            if (alarmPI != null) {
                Log.d(Const.LOG_TAG, "Cancelling existing alarm")
                alarmManager.cancel(alarmPI)
            } else {
                Log.d(Const.LOG_TAG, "No alarms to cancel")
            }
        }
    }

    private fun launchWarningRepeatingTimer(context: Context, repeating: Boolean) {
        Log.d(Const.LOG_TAG, "Starting usage notification timer")
        val alarmIntent = Intent(context, UsageWarningBroadcaster::class.java)
        alarmPI = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 5)

        if (repeating) {
            alarmManager.setRepeating(AlarmManager.RTC, cal.timeInMillis, 60000, alarmPI)
        } else {
            alarmManager.set(AlarmManager.RTC, cal.timeInMillis, alarmPI)
        }
    }
}