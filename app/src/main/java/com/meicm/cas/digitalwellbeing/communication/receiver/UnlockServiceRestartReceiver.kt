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
        when (intent?.action) {
            Const.ACTION_UNLOCK_SERVICE_RESTART -> {
                Log.d(Const.LOG_TAG, "Restarted Unlock Service")
                Toast.makeText(context, "Restarted Unlock Service", Toast.LENGTH_SHORT).show()
                context?.startService(Intent(context, UnlockService::class.java))
            }
        }
    }
}