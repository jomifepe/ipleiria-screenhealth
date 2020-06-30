package com.meicm.cas.screenhealth.communication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meicm.cas.screenhealth.service.ActivityRecognitionIntentService

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context!!.startService(Intent(context, ActivityRecognitionIntentService::class.java))
    }
}