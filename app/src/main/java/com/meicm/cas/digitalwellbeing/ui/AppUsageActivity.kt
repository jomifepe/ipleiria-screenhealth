package com.meicm.cas.digitalwellbeing.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.partial_time_picker.view.*
import kotlinx.android.synthetic.main.partial_title_subtitle_card.view.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_app_usage)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        appPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        startTime = getCalendarFromMillis(intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis()))
        endTime = getCalendarFromMillis(intent.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis()))

        supportActionBar?.title = getAppName(this, appPackage!!)
        try {
            val applicationIcon = getApplicationIcon(this, appPackage!!)
            supportActionBar?.setIcon(applicationIcon)
        } catch (e: Exception) {
            Log.d(Const.LOG_TAG, "Couldn't find icon for app ${appPackage}")
        }

        setupChart()
        initializeViews()
        setupTimePicker()
        subscribeViewModel()
        updateAppSessionsWithinRange()

        binding.timePicker.bt_date_range_backwards.setOnClickListener { incrementOrDecrementTimeRange(-1) }
        binding.timePicker.bt_date_range_forward.setOnClickListener { incrementOrDecrementTimeRange(1) }
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
            calculateSessionDurations(it)
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

    private fun calculateSessionDurations(data: List<AppSession>) {
        val intervals: MutableList<Pair<Triple<String, Long, Long?>, Int>> = mutableListOf(
            Pair(Triple("< 1 min", 0L, 60L), 0), Pair(Triple("1-2 min", 60L, 120L), 0),
            Pair(Triple("2-3 min", 120L, 180L), 0), Pair(Triple("3-5 min", 180L, 300L), 0),
            Pair(Triple("5-10 min", 300L, 600L), 0), Pair(Triple("10-15 min", 600L, 900L), 0),
            Pair(Triple("15-30 min", 900L, 1800L), 0), Pair(Triple("30-60 min", 1800L, 3600L), 0),
            Pair(Triple("1-5 hour", 3600L, 18000L), 0), Pair(Triple("5+ hours", 18000L, null), 0)
        )
        for ((i, session) in data.withIndex()) {
            val duration = ((session.endTimestamp ?: System.currentTimeMillis()) - session.startTimestamp) / 1000L
            val index = intervals.indexOfFirst { duration in it.first.second..(it.first.third ?: System.currentTimeMillis()) }
            if (index != -1) intervals[index] = intervals[index].copy(second = intervals[index].second + 1)
        }
        val entries = intervals
            .filter { it.second > 0 }
            .map { BarEntry(it.first.second.toFloat(), it.second.toFloat()) }
        val dataset = BarDataSet(entries, "Session Breakdown")
        val barData = BarData(dataset)
        barData.barWidth = 500f / intervals.size

        sessionsChart!!.xAxis.valueFormatter = SessionValueFormatter(intervals)
        sessionsChart!!.xAxis.labelCount = entries.size

        runOnUiThread {
            sessionsChart!!.data = barData
            sessionsChart!!.invalidate()
            sessionsChart!!.notifyDataSetChanged()
        }
    }

    private fun setupChart() {
        sessionsChart = binding.chartAppSessions
        sessionsChart?.let {
            val xAxis: XAxis = sessionsChart!!.xAxis
            xAxis.position = XAxisPosition.BOTTOM
            xAxis.setDrawAxisLine(true)
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 5f
            xAxis.textColor = ContextCompat.getColor(this, R.color.nordSnow1)

            val yAxisLeft: YAxis = sessionsChart!!.axisLeft
            yAxisLeft.setDrawAxisLine(true)
            yAxisLeft.setDrawGridLines(true)
            yAxisLeft.axisMinimum = 0f // this replaces setStartAtZero(true)
            yAxisLeft.textColor = ContextCompat.getColor(this, R.color.nordSnow1)

            val yAxisRight: YAxis = sessionsChart!!.axisRight
            yAxisRight.setDrawAxisLine(true)
            yAxisRight.setDrawGridLines(false)
            yAxisRight.axisMinimum = 0f // this replaces setStartAtZero(true)
            yAxisRight.textColor = ContextCompat.getColor(this, R.color.nordSnow1)

            sessionsChart!!.setFitBars(true)
        }
    }

    private fun updateTimeRangeLabel() {
        binding.timePicker.tv_date_range.text = getDateStringFromEpoch(startTime.timeInMillis)
    }

    private fun setupTimePicker() {
        updateTimeRangeLabel()
    }

    private fun initializeViews() {
        binding.screenTime.tv_label.text = getString(R.string.label_screen_time)
        binding.appLaunches.tv_label.text = getString(R.string.label_app_launches)
    }

    inner class SessionValueFormatter(
        private val sessionIntervals: MutableList<Pair<Triple<String, Long, Long?>, Int>>
    ) : ValueFormatter() {

        override fun getFormattedValue(value: Float): String {
            return value.toString()
        }

        override fun getAxisLabel(value: Float, axis: AxisBase): String {
            val valueLong = value.toLong()
            return sessionIntervals.find {
                valueLong in it.first.second..(it.first.third ?: System.currentTimeMillis())
            }?.first?.first ?: "None"
        }
    }
}