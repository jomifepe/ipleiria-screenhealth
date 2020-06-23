package com.meicm.cas.digitalwellbeing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.entity.Snooze
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.util.Const
import kotlinx.coroutines.*
import java.util.*
import kotlin.random.Random

class ScreenInteractiveReceiver: BroadcastReceiver() {
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
                        .insert(Unlock(0, System.currentTimeMillis(),null))
                }

                launch(Dispatchers.IO) {
                    val snooze = Snooze(System.currentTimeMillis())
                    val deferred: Deferred<Snooze?> = async(Dispatchers.IO) {
                        AppDatabase
                            .getDatabase(context)
                            .snoozeDao()
                            .getSnooze(snooze.timestamp)
                    }

                    val existingSnooze: Snooze? = deferred.await()
                    if (existingSnooze == null) {
                        Log.d(Const.LOG_TAG, "No existing snooze, starting alarm")
                        val alarmIntent = Intent(context, UsageWarningBroadcaster::class.java)
                        alarmPI = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

                        val cal = Calendar.getInstance()
                        cal.add(Calendar.SECOND, 5)

                        alarmManager.setRepeating(AlarmManager.RTC, cal.timeInMillis, 60000, alarmPI)
                    } else {
                        Log.d(Const.LOG_TAG, "Found snooze entry, not doing anything")
                    }
                }
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
}