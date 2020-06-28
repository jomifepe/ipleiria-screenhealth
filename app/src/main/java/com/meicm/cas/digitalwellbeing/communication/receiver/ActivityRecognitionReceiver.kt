package com.meicm.cas.digitalwellbeing.communication.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.AppState.currentNotificationId
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.service.ActivityRecognitionIntentService
import com.meicm.cas.digitalwellbeing.service.UnlockService
import com.meicm.cas.digitalwellbeing.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context!!.startService(Intent(context, ActivityRecognitionIntentService::class.java))
    }
}