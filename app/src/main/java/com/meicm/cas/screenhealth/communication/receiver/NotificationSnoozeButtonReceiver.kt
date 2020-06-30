package com.meicm.cas.screenhealth.communication.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.screenhealth.AppState.currentNotificationId
import com.meicm.cas.screenhealth.persistence.AppPreferences
import com.meicm.cas.screenhealth.util.Const
import com.meicm.cas.screenhealth.communication.receiver.UsageWarningNotificationReceiver.Constant as Constant

class NotificationSnoozeButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Constant.ACTION_SNOOZE -> {
                val notificationId = currentNotificationId ?: return
                snoozeNotifications(context!!, notificationId)
                //Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] Received notification id #notificationId")
            }
            else -> Log.d(
                Const.LOG_TAG,
                "[SnoozeUsageWarningBroadcaster] No notification action found"
            )
        }
    }

    private fun snoozeNotifications(context: Context, notificationId: Int) {
        Log.d(Const.LOG_TAG, "[SnoozeUsageWarningBroadcaster] Registered snooze")
        AppPreferences.with(context).save(Const.PREF_KEY_SNOOZE_LONG, System.currentTimeMillis())
        NotificationManagerCompat.from(context).cancel(notificationId)

        // try to cancel existing alarms (scheduled usage warning notifications)
        val pi = PendingIntent.getBroadcast(context, 0,
            Intent(context, UsageWarningNotificationReceiver::class.java), 0)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    }
}