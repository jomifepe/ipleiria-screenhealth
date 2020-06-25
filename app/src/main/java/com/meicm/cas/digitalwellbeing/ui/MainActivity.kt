package com.meicm.cas.digitalwellbeing.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.meicm.cas.digitalwellbeing.util.setEndOfDay
import com.meicm.cas.digitalwellbeing.util.setStartOfDay
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.communication.TimeRangeMessageEvent
import com.meicm.cas.digitalwellbeing.databinding.ActivityMainBinding
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.getDateStringFromEpoch
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import java.util.*


class MainActivity : AppCompatActivity() {
    private var startTime: Calendar
    private var endTime: Calendar

    init {
        startTime = getStartOfDayCalendar()
        endTime = getEndOfDayCalendar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding =
            DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        val navController = findNavController(R.id.myNavHostFragment)

        NavigationUI.setupActionBarWithNavController(this, navController)

        incrementAppRunCount()
        createNotificationChannel()
        updateTimeRangeLabel()

        bt_date_range_backwards.setOnClickListener {
            incrementOrDecrementTimeRange(-1)
        }
        bt_date_range_forward.setOnClickListener {
            incrementOrDecrementTimeRange(1)
        }
    }

    private fun incrementAppRunCount() {
        val pref = AppPreferences.with(this)
        val runCount = pref.getInt(Const.PREF_APP_RUN, 0)
        if (runCount < 2) pref.save(Const.PREF_APP_RUN, runCount + 1)
    }

    private fun incrementOrDecrementTimeRange(days: Int) {
        startTime.add(Calendar.DAY_OF_YEAR, days)
        endTime.add(Calendar.DAY_OF_YEAR, days)
        EventBus.getDefault()
            .post(TimeRangeMessageEvent(startTime.timeInMillis, endTime.timeInMillis))
        updateTimeRangeLabel()
    }

    private fun updateTimeRangeLabel() {
        tv_date_range.text = getDateStringFromEpoch(startTime.timeInMillis)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "General"
            val channel = NotificationChannel(
                Const.NOTIFICATION_CHANNEL_GENERAL,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "General notifications"
            channel.setShowBadge(true)

            val notificationManager = this.getSystemService(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun getStartOfDayCalendar(): Calendar {
        val start = Calendar.getInstance()
        start.setStartOfDay()
        return start
    }

    private fun getEndOfDayCalendar(): Calendar {
        val end = Calendar.getInstance()
        end.setEndOfDay()
        return end
    }
}
