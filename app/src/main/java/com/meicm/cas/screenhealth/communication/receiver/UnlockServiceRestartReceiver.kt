package com.meicm.cas.screenhealth.communication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meicm.cas.screenhealth.service.UnlockService
import com.meicm.cas.screenhealth.util.Const

class UnlockServiceRestartReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(Const.LOG_TAG, "[UnlockServiceRestartReceiver] Restarted UnlockService")
        context?.startService(Intent(context, UnlockService::class.java))
    }
}