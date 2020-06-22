package com.meicm.cas.digitalwellbeing

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.util.Const
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class UnlockService: Service() {
    override fun onBind(intent: Intent?): IBinder? {
        Log.i("DW_LOGGING", "Service onBind")
        return null
    }

    override fun onCreate() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        val mReceiver: BroadcastReceiver = Receiver()
        registerReceiver(mReceiver, filter)
        Log.d(Const.LOG_TAG, "Registered broadcast receiver")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onTaskRemoved(intent)
        return START_STICKY
    }

    class Receiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent!!.action
            if (Intent.ACTION_SCREEN_ON == action) {
                State.isUnlocked = true
                State.unlockTime = System.currentTimeMillis()

                Log.d(Const.LOG_TAG, "Unlocked")

                runBlocking {
                    launch(Dispatchers.Default) {
                        AppDatabase
                            .getDatabase(context!!)
                            .unlockDao()
                            .insert(
                                Unlock(
                                    0,
                                    System.currentTimeMillis(),
                                    null
                                )
                            )
                    }
                }

//                val alarmManager = context!!.getSystemService(Context.ALARM_SERVICE) as AlarmManager
//                val alarmIntent = Intent(context, UsageAlarm::class.java)
//                val pending = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)
//
//                var cal = Calendar.getInstance()
//                cal.add(Calendar.SECOND, 5)
//
//                alarmManager.setRepeating(AlarmManager.RTC, cal.timeInMillis, 60000, pending)
//                Log.d(Const.LOG_TAG, "Started alarm")

            } else if (Intent.ACTION_SCREEN_OFF == action) {
                Log.d(Const.LOG_TAG, "Locked")
                State.isUnlocked = false

                runBlocking {
                    launch(Dispatchers.Default) {
                        AppDatabase
                            .getDatabase(context!!)
                            .unlockDao()
                            .updateLastUnlockEndTimestamp(System.currentTimeMillis())
                    }
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

//    override fun onDestroy() {
//        val broadcastIntent = Intent()
//        broadcastIntent.action = "restartservice"
//        broadcastIntent.setClass(this, BroadcastReceiverRestarter::class.java)
//        this.sendBroadcast(broadcastIntent)
//    }
}