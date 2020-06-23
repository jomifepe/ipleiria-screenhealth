package com.meicm.cas.digitalwellbeing

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.digitalwellbeing.util.Const
import kotlin.random.Random

class UsageWarningBroadcaster: BroadcastReceiver() {
    object Constant {
        const val ACTION_EXTRA: String = "usage_warning"
        const val ACTION_SNOOZE: String = "android.intent.action.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val snoozeIntent: Intent = Intent(context, SnoozeUsageWarningBroadcaster::class.java)
        snoozeIntent.action = Constant.ACTION_SNOOZE
        val snoozePI: PendingIntent = PendingIntent.getBroadcast(context, 0, snoozeIntent, 0)

        val builder = NotificationCompat.Builder(context!!, Const.NOTIFICATION_CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Usage Warning")
            .setContentText("You've been using your device for a long period of time. If you're not doing something important, consider resting for a bit.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(R.drawable.ic_snooze_black, context.getString(R.string.label_snooze), snoozePI)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(Random.nextInt(100), builder.build())
    }
}