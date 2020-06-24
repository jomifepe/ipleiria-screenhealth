package com.meicm.cas.digitalwellbeing.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.databinding.ActivityMainBinding
import com.meicm.cas.digitalwellbeing.util.Const
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity() {
    private var startTime: Long
    private var endTime: Long

    init {
        startTime = getStartOfDayMillis()
        endTime = getEndOfDayMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        val navController = findNavController(R.id.myNavHostFragment)

        NavigationUI.setupActionBarWithNavController(this, navController)
        createNotificationChannel()

//        bt_date_range_backwards.setOnClickListener {
//            Toast.makeText(this, "Clicked", Toast.LENGTH_LONG).show()
//        }
//        bt_date_range_forward.setOnClickListener {
//            Toast.makeText(this, "Clicked", Toast.LENGTH_LONG).show()
//        }
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

    private fun getStartOfDayMillis(): Long {
        val start = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        return start.timeInMillis
    }
    private fun getEndOfDayMillis(): Long {
        val end = Calendar.getInstance()
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)
        return end.timeInMillis
    }
}
