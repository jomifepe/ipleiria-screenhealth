package com.meicm.cas.screenhealth.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.meicm.cas.screenhealth.R
import com.meicm.cas.screenhealth.communication.MessageEvent
import com.meicm.cas.screenhealth.communication.TimeRangeMessageEvent
import com.meicm.cas.screenhealth.databinding.FragmentUsageStatisticsBinding
import com.meicm.cas.screenhealth.persistence.AppPreferences
import com.meicm.cas.screenhealth.persistence.entity.AppCategory
import com.meicm.cas.screenhealth.persistence.entity.AppSession
import com.meicm.cas.screenhealth.ui.adapter.AppTimeUsageRecyclerAdapter
import com.meicm.cas.screenhealth.ui.adapter.RecyclerViewItemShortClick
import com.meicm.cas.screenhealth.util.*
import com.meicm.cas.screenhealth.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.partial_title_subtitle_card.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class UsageStatisticsFragment : Fragment() {
    private lateinit var binding: FragmentUsageStatisticsBinding
    private lateinit var appTimeAdapter: AppTimeUsageRecyclerAdapter
    private lateinit var usageViewModel: UsageViewModel

    private var appCategories: List<AppCategory> = listOf()
    private var unlockCount: Int = 0

    private var startTime: Long = getStartOfDayCalendar().timeInMillis
    private var endTime: Long = getEndOfDayCalendar().timeInMillis

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_usage_statistics, container, false)
        setHasOptionsMenu(true)

        setupStaticViews()
        setupEventBusListeners()
        setupList()
        subscribeViewModel()

        return binding.root
    }

    private fun setupEventBusListeners() {
        val bus = EventBus.getDefault()
        if (!bus.isRegistered(this)) {
            bus.register(this)
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
                updateAppSessionsWithinRange()
                updateSnoozeWarning()
            }
        }
    }

    private fun setupList() {
        appTimeAdapter = AppTimeUsageRecyclerAdapter(object : RecyclerViewItemShortClick {
            override fun onShortClick(view: View, position: Int) {
                val appPackage: String = appTimeAdapter.list[position].first
                startActivity(Intent(requireContext(), AppUsageActivity::class.java).apply {
                    putExtra(AppUsageActivity.EXTRA_PACKAGE_NAME, appPackage)
                    putExtra(AppUsageActivity.EXTRA_START_TIME, startTime)
                    putExtra(AppUsageActivity.EXTRA_END_TIME, endTime)
                })
            }
        })
        binding.rvAppTime.layoutManager = LinearLayoutManager(
            binding.root.context,
            LinearLayoutManager.VERTICAL, false
        )
        binding.rvAppTime.adapter = appTimeAdapter
    }

    private fun subscribeViewModel() {
        usageViewModel = ViewModelProvider(this).get(UsageViewModel::class.java)

        usageViewModel.allUnlocks.observe(viewLifecycleOwner, Observer {
            if (it != null) updateUnlocksWithinRange()
            else requireActivity().runOnUiThread { binding.screenTime.progressBar.visibility = View.GONE }
        })
        usageViewModel.appCategories.observe(viewLifecycleOwner, Observer { data ->
            data?.let { this.appCategories = data }
        })
        usageViewModel.appSessions.observe(viewLifecycleOwner, Observer {
            if (it != null) updateAppSessionsWithinRange()
            else requireActivity().runOnUiThread {
                binding.pbPerAppUsage.visibility = View.GONE
                binding.appLaunches.progressBar.visibility = View.GONE
                binding.screenTime.progressBar.visibility = View.GONE
            }
        })
    }

    private fun updateUnlocksWithinRange() {
        usageViewModel.getUnlocks(this.startTime, this.endTime) { result ->
            unlockCount = result.size
            requireActivity().runOnUiThread {
                binding.unlockCount.tv_value.text = unlockCount.toString()
                binding.unlockCount.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateAppSessionsWithinRange() {
        usageViewModel.getAppSessions(this.startTime, this.endTime) {
            calculateTotalTimes(it)
        }
    }

    private fun calculateTotalTimes(data: HashMap<String, MutableList<AppSession>>) {
        val appSessionList = mutableListOf<Pair<String, Long>>()
        var totalScreenTime = 0L
        var totalLaunches = 0
        data.forEach { app ->
            var totalTime = 0L
            app.value.forEach {
                totalTime += it.endTimestamp!! - it.startTimestamp
                totalLaunches++
            }
            totalScreenTime += totalTime

            appSessionList.add(Pair(app.key, totalTime))
        }
        val totalTimeString = getHoursMinutesSecondsString(totalScreenTime)

        requireActivity().runOnUiThread {
            binding.screenTime.tv_value.text = totalTimeString
            binding.appLaunches.tv_value.text = totalLaunches.toString()
            appTimeAdapter.list = appSessionList.toList().sortedByDescending { it.second }
            binding.tvNoData.visibility = if (appSessionList.size > 0) View.GONE else View.VISIBLE
            binding.pbPerAppUsage.visibility = View.GONE
            binding.appLaunches.progressBar.visibility = View.GONE
            binding.screenTime.progressBar.visibility = View.GONE
        }
    }

    private fun setupStaticViews() {
        binding.screenTime.tv_label.text = getString(R.string.label_screen_time)
        binding.unlockCount.tv_label.text = getString(R.string.label_screen_unlocks)
        binding.appLaunches.tv_label.text = getString(R.string.label_app_launches)
        binding.screenTime.tv_value.text = "0h"
        binding.unlockCount.tv_value.text = "0"
        binding.appLaunches.tv_value.text = "0"

        binding.pbPerAppUsage.visibility = View.VISIBLE
        binding.appLaunches.progressBar.visibility = View.VISIBLE
        binding.screenTime.progressBar.visibility = View.VISIBLE
        binding.unlockCount.progressBar.visibility = View.VISIBLE

        updateSnoozeWarning()
    }

    private fun updateSnoozeWarning() {
        // if the time picker isn't on the current day, there's no need to show the snooze warning
        if (!compareTimestampsDateEqual(startTime, System.currentTimeMillis())) {
            binding.cvSnoozeWarning.visibility = View.GONE
            return
        }

        val pref = AppPreferences.with(requireContext())
        val snoozeTimestamp = pref.getLong(Const.PREF_KEY_SNOOZE_LONG, 0L)
        binding.cvSnoozeWarning.visibility =
            if (compareTimestampsDateEqual(snoozeTimestamp, System.currentTimeMillis())) View.VISIBLE
            else View.GONE
    }
}
