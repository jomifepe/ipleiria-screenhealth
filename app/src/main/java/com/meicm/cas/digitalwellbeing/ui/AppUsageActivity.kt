package com.meicm.cas.digitalwellbeing.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.databinding.ActivityAppUsageBinding
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.partial_time_picker.view.*
import kotlinx.android.synthetic.main.partial_title_subtitle_card.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var sessionsChart: HorizontalBarChart? = null
    private var color: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_app_usage)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        color = ContextCompat.getColor(this, R.color.nordSnow1)
        appPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        startTime =
            getCalendarFromMillis(intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis()))
        endTime =
            getCalendarFromMillis(intent.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis()))

        supportActionBar?.title = getAppName(this, appPackage!!)

        try {
            val applicationIcon = getApplicationIcon(this, appPackage!!)
            supportActionBar?.setIcon(applicationIcon)
        } catch (e: Exception) {
            Log.d(Const.LOG_TAG, "Couldn't find icon for app ${appPackage}")
        }

        initializeViews()
        subscribeViewModel()
        loadAppCategory()
        updateAppSessionsWithinRange()

        binding.timePicker.bt_date_range_backwards.setOnClickListener {
            incrementOrDecrementTimeRange(-1)
        }
        binding.timePicker.bt_date_range_forward.setOnClickListener {
            incrementOrDecrementTimeRange(1)
        }
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
        viewModel.appCategories.observe(this, Observer {
            it?.let { updateAppCategory(it) }
        })
    }

    private fun updateAppCategory(categories: List<AppCategory>) {
        val category = categories.find { it.appPackage == this.appPackage }
        supportActionBar?.subtitle = category?.category
    }

    private fun updateAppSessionsWithinRange() {
        viewModel.getAppSessions(appPackage!!, startTime.timeInMillis, endTime.timeInMillis) {
            calculateTotalTimes(it)
            calculateSessionDurations(it)
        }
    }

    private fun loadAppCategory() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.getAppCategory(appPackage!!) { category ->
                runOnUiThread { supportActionBar?.subtitle = category }
            }
        }
    }

    private fun incrementOrDecrementTimeRange(days: Int) {
        startTime.add(Calendar.DAY_OF_YEAR, days)
        endTime.add(Calendar.DAY_OF_YEAR, days)
        Log.d(
            Const.LOG_TAG, "start: ${getDateTimeStringFromEpoch(startTime.timeInMillis)}, " +
                    "end: ${getDateTimeStringFromEpoch(endTime.timeInMillis)}"
        )
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

    private fun calculateSessionDurations(data: List<AppSession>) {
        val intervals: MutableList<SessionDuration> = mutableListOf(
            SessionDuration("< 1 min", 0L, 60L, 0), SessionDuration("1-2 min", 61L, 120L, 0),
            SessionDuration("2-3 min", 121L, 180L, 0), SessionDuration("3-5 min", 181L, 300L, 0),
            SessionDuration("5-10 min", 301L, 600L, 0), SessionDuration("10-15 min", 601L, 900L, 0),
            SessionDuration("15-30 min", 901L, 1800L, 0), SessionDuration("30-60 min", 1801L, 3600L, 0),
            SessionDuration("1-5 hour", 3601L, 18000L, 0), SessionDuration("5+ hours", 18001L, null, 0)
        )

        for (session in data) {
            val duration = ((session.endTimestamp ?: System.currentTimeMillis()) - session.startTimestamp) / 1000L
            if (duration == 0L) continue
            intervals.find { duration in it.minDurationSec..(it.maxDurationSec ?: System.currentTimeMillis()) }
            val index = intervals.indexOfFirst {
                duration in it.minDurationSec..(it.maxDurationSec ?: System.currentTimeMillis())
            }
            if (index != -1) intervals[index].count += 1
        }

        val entries = mutableListOf<BarEntry>()
        var barCount = 0
        for (interval in intervals) {
            if (interval.count == 0) continue
            entries.add(BarEntry((++barCount).toFloat(), interval.count.toFloat()))
        }

        val barData = BarData(BarDataSet(entries, "Session Breakdown"))
        barData.barWidth = 0.9f
        barData.setValueTextColor(color!!)

        sessionsChart!!.xAxis.valueFormatter = SessionValueFormatter(intervals)
        sessionsChart!!.xAxis.labelCount = barCount

        runOnUiThread {
            sessionsChart!!.data = barData
            sessionsChart!!.invalidate()
            sessionsChart!!.notifyDataSetChanged()
        }
    }

    private fun updateTimeRangeLabel() {
        binding.timePicker.tv_date_range.text = getDateStringFromEpoch(startTime.timeInMillis, "MMM dd, YYYY")
    }

    private fun initializeViews() {
        binding.screenTime.tv_label.text = getString(R.string.label_screen_time)
        binding.appLaunches.tv_label.text = getString(R.string.label_app_launches)
        binding.screenTime.tv_value.text = "0h"
        binding.appLaunches.tv_label.text = "0"

        /* time picker */
        updateTimeRangeLabel()

        /* charts */
        sessionsChart = binding.chartAppSessions
        sessionsChart?.let {
            val xAxis: XAxis = sessionsChart!!.xAxis
            xAxis.position = XAxisPosition.BOTTOM
            xAxis.setDrawAxisLine(true)
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = color!!

            val yAxisLeft: YAxis = sessionsChart!!.axisLeft
            yAxisLeft.setDrawAxisLine(true)
            yAxisLeft.setDrawGridLines(true)
            yAxisLeft.axisMinimum = 0f // this replaces setStartAtZero(true)
            yAxisLeft.textColor = color!!
            yAxisLeft.isEnabled = false

            val yAxisRight: YAxis = sessionsChart!!.axisRight
            yAxisRight.setDrawAxisLine(true)
            yAxisRight.setDrawGridLines(true)
            yAxisRight.axisMinimum = 0f // this replaces setStartAtZero(true)
            yAxisRight.textColor = color!!

            sessionsChart!!.legend.isEnabled = false
            sessionsChart!!.description.isEnabled = false
            sessionsChart!!.setPinchZoom(false)
            sessionsChart!!.setScaleEnabled(false)
            sessionsChart!!.setFitBars(true)
        }
    }

    inner class SessionValueFormatter(private val sessionIntervals: MutableList<SessionDuration>) :
        ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return value.toString()
        }

        override fun getAxisLabel(value: Float, axis: AxisBase): String {
            return sessionIntervals[value.toInt()].label
        }
    }

    data class SessionDuration(
        val label: String,
        val minDurationSec: Long,
        val maxDurationSec: Long?,
        var count: Int = 0
    )
}