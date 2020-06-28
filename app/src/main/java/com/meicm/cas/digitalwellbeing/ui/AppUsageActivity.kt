package com.meicm.cas.digitalwellbeing.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.communication.TimeRangeMessageEvent
import com.meicm.cas.digitalwellbeing.databinding.ActivityAppUsageBinding
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.activity_app_usage.*
import kotlinx.android.synthetic.main.activity_app_usage.time_picker
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_usage_statistics.*
import kotlinx.android.synthetic.main.partial_time_picker.view.*
import kotlinx.android.synthetic.main.partial_title_subtitle_card.view.*
import org.greenrobot.eventbus.EventBus
import java.util.*

class AppUsageActivity : AppCompatActivity() {
    companion object Constant {
        const val EXTRA_PACKAGE_NAME = "${Const.BASE_PACKAGE}.activity.extra.PACKAGE_NAME"
        const val EXTRA_START_TIME = "${Const.BASE_PACKAGE}.activity.extra.START_TIME"
        const val EXTRA_END_TIME = "${Const.BASE_PACKAGE}.activity.extra.END_TIME"
    }

    private lateinit var binding: ActivityAppUsageBinding
    private lateinit var viewModel: UsageViewModel
    private lateinit var startTime: Calendar
    private lateinit var endTime: Calendar
    private var appPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_app_usage)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        appPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        startTime = getCalendarFromMillis(intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis()))
        endTime = getCalendarFromMillis(intent.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis()))

        setupStaticViews()
        setupTimePicker()
        subscribeViewModel()
        updateAppSessionsWithinRange()

        time_picker.bt_date_range_backwards.setOnClickListener { incrementOrDecrementTimeRange(-1) }
        time_picker.bt_date_range_forward.setOnClickListener { incrementOrDecrementTimeRange(1) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun subscribeViewModel() {
        viewModel = ViewModelProvider(this).get(UsageViewModel::class.java)
        viewModel.appSessions.observe(this, Observer {
            it?.let { updateAppSessionsWithinRange() }
        })
    }

    private fun updateAppSessionsWithinRange() {
        viewModel.getAppSessions(appPackage!!, startTime.timeInMillis, endTime.timeInMillis) {
            calculateTotalTimes(it)
        }
    }

    private fun incrementOrDecrementTimeRange(days: Int) {
        startTime.add(Calendar.DAY_OF_YEAR, days)
        endTime.add(Calendar.DAY_OF_YEAR, days)
        Log.d(Const.LOG_TAG, "start: ${getDateTimeStringFromEpoch(startTime.timeInMillis)}, " +
                "end: ${getDateTimeStringFromEpoch(endTime.timeInMillis)}")
        updateTimeRangeLabel()
        updateAppSessionsWithinRange()
    }

    private fun calculateTotalTimes(data: List<AppSession>) {
        var totalTime = 0L
        data.forEach { totalTime += it.endTimestamp!! - it.startTimestamp }
        val totalTimeString = getHoursMinutesSecondsString(totalTime)

        runOnUiThread {
            binding.screenTime.tv_value.text = totalTimeString
            binding.appLaunches.tv_value.text = data.size.toString()
        }
    }

    private fun updateTimeRangeLabel() {
        time_picker.tv_date_range.text = getDateStringFromEpoch(startTime.timeInMillis)
    }

    private fun setupTimePicker() {
        updateTimeRangeLabel()
    }

    private fun setupStaticViews() {
        binding.screenTime.tv_label.text = getString(R.string.label_screen_time)
        binding.appLaunches.tv_label.text = getString(R.string.label_app_launches)
    }
}