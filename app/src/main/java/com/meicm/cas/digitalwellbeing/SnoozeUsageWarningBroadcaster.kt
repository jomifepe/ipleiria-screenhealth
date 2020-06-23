package com.meicm.cas.digitalwellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.entity.Snooze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import com.meicm.cas.digitalwellbeing.UsageWarningBroadcaster.Constant as Constant

class SnoozeUsageWarningBroadcaster: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        println()
        intent?.let {
            when (intent.action) {
                Constant.ACTION_SNOOZE -> snoozeNotifications(context!!)
            }
        }
    }

    private fun snoozeNotifications(context: Context) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        // Add snooze record to the database with the current date
        runBlocking {
            launch(Dispatchers.Default) {
                AppDatabase
                    .getDatabase(context)
                    .snoozeDao()
                    .insert(Snooze(0, cal.timeInMillis))
            }
        }
    }
}