package com.meicm.cas.digitalwellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const
import java.util.*
import com.meicm.cas.digitalwellbeing.UsageWarningBroadcaster.Constant as Constant

class SnoozeUsageWarningBroadcaster: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (intent.action) {
                Constant.ACTION_SNOOZE -> {
                    val extras = intent.extras
                    extras?.let {
                        snoozeNotifications(context!!, extras.getInt(Constant.NOTIFICATION_ID))
                    }
                }
                else -> Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] No notification action found")
            }
        }
    }

    private fun snoozeNotifications(context: Context, notificationId: Int) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        AppPreferences.with(context).save(Const.PREFS_KEY_SNOOZE_LONG, System.currentTimeMillis())
        Log.d(Const.LOG_TAG, "Registered snooze")
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}