package com.meicm.cas.digitalwellbeing.ui

import android.app.*
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.databinding.ActivityMainBinding
import com.meicm.cas.digitalwellbeing.util.Const

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this,
            R.layout.activity_main
        )
        val navController = findNavController(R.id.myNavHostFragment)

        NavigationUI.setupActionBarWithNavController(this, navController)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: String = "General"
            val channel = NotificationChannel(Const.NOTIFICATION_CHANNEL_GENERAL, name, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "General notifications"
            channel.setShowBadge(true)

            val notificationManager = this.getSystemService(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
        }
    }
}
