package com.meicm.cas.digitalwellbeing.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.receiver.UsageWarningReceiver.Constant as Constant

class SnoozeUsageWarningReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Constant.ACTION_SNOOZE -> {
                val id = intent.getIntExtra(Constant.NOTIFICATION_ID, -1)
                if (id == -1) return
                Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] Received notification id #$id")
                snoozeNotifications(context!!, id)
            }
            else -> Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] No notification action found")
        }
    }

    private fun snoozeNotifications(context: Context, notificationId: Int) {
        Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] Registered snooze")
        AppPreferences.with(context).save(Const.PREF_KEY_SNOOZE_LONG, System.currentTimeMillis())
        Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] Clearing notification #$notificationId")
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}