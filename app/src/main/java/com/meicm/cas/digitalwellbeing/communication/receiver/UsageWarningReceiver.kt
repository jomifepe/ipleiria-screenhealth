package com.meicm.cas.digitalwellbeing.communication.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.AppState.currentNotificationId
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.NotificationId

class UsageWarningReceiver : BroadcastReceiver() {
    object Constant {
        const val ACTION_EXTRAS: String = "usage_warning.extras"
        const val ACTION_SNOOZE: String = "android.intent.action.ACTION_SNOOZE"
        const val NOTIFICATION_ID: String = "usage_warning.extra.NOTIFICATION_ID"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        currentNotificationId = NotificationId.getNewId()
        Log.d(Const.LOG_TAG, "Generating notification id $currentNotificationId")
        val snoozeIntent: Intent = Intent(context, SnoozeUsageWarningReceiver::class.java).apply {
            action = Constant.ACTION_SNOOZE
        }
        val snoozePI: PendingIntent = PendingIntent.getBroadcast(context, 0, snoozeIntent, 0)

        val builder = NotificationCompat.Builder(context!!, Const.NOTIFICATION_CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Usage Warning #$currentNotificationId")
            .setContentText("You've been using your device for a long period of time. If you're not doing something important, consider resting for a bit.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_snooze_black, context.getString(
                    R.string.label_snooze
                ), snoozePI
            )

        val notificationManager = NotificationManagerCompat.from(context)

        val pref = AppPreferences.with(context)
        notificationManager.cancel(pref.getInt(Const.PREF_UW_LAST_NOTIFICATION_ID, -1))

        Log.d(Const.LOG_TAG, "Sending usage warning notification #$currentNotificationId")
        if (shouldNotify()) notificationManager.notify(currentNotificationId!!, builder.build())
        AppState.lastUWTimerStart = System.currentTimeMillis()

        pref.save(Const.PREF_UW_LAST_NOTIFICATION_ID, currentNotificationId!!)
        pref.remove(Const.PREF_UW_LAST_TIME)
    }

    private fun shouldNotify(): Boolean {
        // TODO: Analyze used apps
        return true
    }
}