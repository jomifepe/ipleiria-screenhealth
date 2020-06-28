package com.meicm.cas.digitalwellbeing.communication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.meicm.cas.digitalwellbeing.service.UnlockService
import com.meicm.cas.digitalwellbeing.util.Const

class UnlockServiceRestartReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(Const.LOG_TAG, "[UnlockServiceRestartReceiver] Restarted UnlockService")
        context?.startService(Intent(context, UnlockService::class.java))
    }
}