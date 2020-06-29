package com.meicm.cas.digitalwellbeing.ui

import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.communication.MessageEvent
import com.meicm.cas.digitalwellbeing.communication.TimeRangeMessageEvent
import com.meicm.cas.digitalwellbeing.databinding.FragmentUsageStatisticsBinding
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.service.ActivityRecognitionIntentService
import com.meicm.cas.digitalwellbeing.service.AppUsageGathererService
import com.meicm.cas.digitalwellbeing.service.UnlockService
import com.meicm.cas.digitalwellbeing.ui.adapter.AppTimeUsageRecyclerAdapter
import com.meicm.cas.digitalwellbeing.ui.adapter.RecyclerViewItemShortClick
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.fragment_usage_statistics.*
import kotlinx.android.synthetic.main.partial_title_subtitle_card.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.*

class UsageStatisticsFragment : Fragment() {
    private lateinit var binding: FragmentUsageStatisticsBinding
    private lateinit var appTimeAdapter: AppTimeUsageRecyclerAdapter
    private lateinit var usageViewModel: UsageViewModel

    private var appSessions: HashMap<String, MutableList<AppSession>>
    private var appCategories: List<AppCategory> = listOf()
    private var unlockCount: Int = 0

    private var startTime: Long
    private var endTime: Long

    init {
        val start = Calendar.getInstance()
        start.setStartOfDay()
        val end = Calendar.getInstance()
        end.setEndOfDay()

        appSessions = hashMapOf()
        startTime = start.timeInMillis
        endTime = end.timeInMillis
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_usage_statistics, container, false
        )
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

                val sdf = SimpleDateFormat("YYYY-MMM-dd HH:mm:ss", Locale.getDefault())
                Log.d(
                    Const.LOG_TAG,
                    "Change to the time range: [Start: ${getDateTimeStringFromEpoch(this.startTime)}, " +
                            "End: ${getDateTimeStringFromEpoch(this.endTime)}]"
                )

                updateUnlocksWithinRange()
                updateAppSessionsWithinRange()
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
            it?.let { updateUnlocksWithinRange() }
        })
        usageViewModel.appCategories.observe(viewLifecycleOwner, Observer { data ->
            data?.let {
                this.appCategories = data
            }
        })
        usageViewModel.appSessions.observe(viewLifecycleOwner, Observer {
            it?.let { updateAppSessionsWithinRange() }
        })
    }

    private fun updateUnlocksWithinRange() {
        usageViewModel.getUnlocks(this.startTime, this.endTime) { result ->
            unlockCount = result.size
            requireActivity().runOnUiThread {
                unlock_count.tv_value.text = unlockCount.toString()
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
            screen_time.tv_value.text = totalTimeString
            app_launches.tv_value.text = totalLaunches.toString()
            appTimeAdapter.list = appSessionList.toList().sortedByDescending { it.second }
        }
    }

    private fun hasUsagePermission(): Boolean {
        return try {
            val applicationInfo = requireContext().packageManager.getApplicationInfo(requireContext().packageName, 0)
            val appOpsManager = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun setupStaticViews() {
        binding.screenTime.tv_label.text = getString(R.string.label_screen_time)
        binding.unlockCount.tv_label.text = getString(R.string.label_screen_unlocks)
        binding.appLaunches.tv_label.text = getString(R.string.label_app_launches)
    }
}
