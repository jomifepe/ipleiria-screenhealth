package com.meicm.cas.screenhealth.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.meicm.cas.screenhealth.R
import com.meicm.cas.screenhealth.communication.MessageEvent
import com.meicm.cas.screenhealth.communication.TimeRangeMessageEvent
import com.meicm.cas.screenhealth.databinding.FragmentInsightsBinding
import com.meicm.cas.screenhealth.persistence.entity.Unlock
import com.meicm.cas.screenhealth.util.Const
import com.meicm.cas.screenhealth.util.getEndOfDayCalendar
import com.meicm.cas.screenhealth.util.getStartOfDayCalendar
import com.meicm.cas.screenhealth.viewmodel.UsageViewModel
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class InsightsFragment : Fragment() {
    private lateinit var binding: FragmentInsightsBinding
    private var startTime: Long = getStartOfDayCalendar().timeInMillis
    private var endTime: Long = getEndOfDayCalendar().timeInMillis
    private lateinit var viewModel: UsageViewModel
    private var unlockCount: Int = 0
    private var unlocksChart: HorizontalBarChart? = null
    private var chartTextColor: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_insights, container, false)
        setHasOptionsMenu(true)

        initializeViews()
        subscribeViewModel()
        setupEventBusListeners()

        return binding.root
    }

    private fun setupEventBusListeners() {
        val bus = EventBus.getDefault()
        if (!bus.isRegistered(this)) {
            bus.register(this)
        }
    }

    private fun subscribeViewModel() {
        viewModel = ViewModelProvider(this).get(UsageViewModel::class.java)
        // this observer is used as a trigger to a more specific database call
        viewModel.allUnlocks.observe(viewLifecycleOwner, Observer {
            if (it != null) updateUnlocksWithinRange()
        })
    }

    private fun updateUnlocksWithinRange() {
        viewModel.getUnlocks(this.startTime, this.endTime) { result ->
            calculateSessionDurations(result)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(messageEvent: MessageEvent) {
        when (messageEvent.message) {
            Const.EVENT_TIME_RANGE -> {
                val event = messageEvent as TimeRangeMessageEvent
                this.startTime = event.startTimestamp
                this.endTime = event.endTimestamp

                updateUnlocksWithinRange()
            }
        }
    }

    private fun calculateSessionDurations(data: List<Unlock>) {
        val intervals: MutableList<UnlockDuration> = mutableListOf(
            UnlockDuration("< 1 min", 0L, 60L, 0), UnlockDuration("1-5 min", 61L, 300L, 0),
            UnlockDuration("5-10 min", 301L, 600L, 0), UnlockDuration("10-15 min", 601L, 900L, 0),
            UnlockDuration("15-30 min", 901L, 1800L, 0), UnlockDuration("30-60 min", 1801L, 3600L, 0),
            UnlockDuration("1-2 hours", 3601L, 7200L, 0), UnlockDuration("2-5 hours", 7201L, 18000L, 0),
            UnlockDuration("5+ hour", 18001L, null, 0)
        )

        for (unlock in data) {
            val duration = ((unlock.endTimestamp ?: System.currentTimeMillis()) - unlock.startTimestamp) / 1000L
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

        val barData = BarData(BarDataSet(entries, "Unlock Duration Breakdown"))
        barData.barWidth = 0.9f
        barData.setValueTextColor(chartTextColor!!)

        unlocksChart!!.xAxis.valueFormatter = SessionValueFormatter(intervals)
        unlocksChart!!.xAxis.labelCount = barCount

        requireActivity().runOnUiThread {
            unlocksChart!!.data = barData
            unlocksChart!!.invalidate()
            unlocksChart!!.notifyDataSetChanged()
        }
    }

    private fun initializeViews() {
        chartTextColor = ContextCompat.getColor(requireContext(), R.color.nordSnow1)

        unlocksChart = binding.chartUnlocks
        unlocksChart?.let {
            val xAxis: XAxis = unlocksChart!!.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawAxisLine(true)
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = chartTextColor!!

            val yAxisLeft: YAxis = unlocksChart!!.axisLeft
            yAxisLeft.setDrawAxisLine(true)
            yAxisLeft.setDrawGridLines(true)
            yAxisLeft.axisMinimum = 0f // this replaces setStartAtZero(true)
            yAxisLeft.textColor = chartTextColor!!
            yAxisLeft.isEnabled = false

            val yAxisRight: YAxis = unlocksChart!!.axisRight
            yAxisRight.setDrawAxisLine(true)
            yAxisRight.setDrawGridLines(true)
            yAxisRight.axisMinimum = 0f // this replaces setStartAtZero(true)
            yAxisRight.textColor = chartTextColor!!

            unlocksChart!!.legend.isEnabled = false
            unlocksChart!!.description.isEnabled = false
            unlocksChart!!.setPinchZoom(false)
            unlocksChart!!.setScaleEnabled(false)
            unlocksChart!!.setFitBars(true)
            unlocksChart!!.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.nordPolarNight4))
        }
    }

    inner class SessionValueFormatter(private val sessionIntervals: MutableList<UnlockDuration>) :
        ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return value.toString()
        }

        override fun getAxisLabel(value: Float, axis: AxisBase): String {
            return sessionIntervals[value.toInt()].label
        }
    }

    data class UnlockDuration(
        val label: String,
        val minDurationSec: Long,
        val maxDurationSec: Long?,
        var count: Int = 0
    )
}
