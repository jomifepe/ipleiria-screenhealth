package com.meicm.cas.digitalwellbeing.ui

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStats
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
import com.meicm.cas.digitalwellbeing.service.AppUsageGathererService
import com.meicm.cas.digitalwellbeing.service.UnlockService
import com.meicm.cas.digitalwellbeing.ui.adapter.AppTimeUsageRecyclerAdapter
import com.meicm.cas.digitalwellbeing.ui.adapter.RecyclerViewItemShortClick
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.fragment_usage_statistics.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.*

private const val MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS = 3

class UsageStatisticsFragment : Fragment() {
    private lateinit var binding: FragmentUsageStatisticsBinding
    private lateinit var appTimeAdapter: AppTimeUsageRecyclerAdapter
    private lateinit var usageViewModel: UsageViewModel

    private var appUsageStatsList: List<UsageStats> = listOf()
    private var appCategories: List<AppCategory> = listOf()

    private var unlockCount: Int = 0

    private var startTime: Long
    private var endTime: Long

    init {
        val start = Calendar.getInstance()
        start.setStartOfDay()
        val end = Calendar.getInstance()
        end.setEndOfDay()

        startTime = start.timeInMillis
        endTime = end.timeInMillis
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_usage_statistics, container, false
        )
        setHasOptionsMenu(true)

        setupEventBusListeners()
        setupList()
        subscribeViewModel()
        startUnlocksService()

        if (!hasUsagePermission()) {
            showUsagePermissionDialog()
        } else {
            enableDataGathering()
        }

        return binding.root
    }

    private fun startUnlocksService() {
        if (!isServiceRunning(requireContext(), UnlockService::class.java)) {
            val service = Intent(requireContext(), UnlockService::class.java).apply {
                action = Const.ACTION_FIRST_LAUNCH
            }
            requireContext().startService(service)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS -> {
                if (!hasUsagePermission()) return
                enableDataGathering()
            }
        }
    }

    private fun setupEventBusListeners() {
        EventBus.getDefault().register(this)
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
                    "Change to the time range: [Start: ${this.startTime}, End: ${this.endTime}]"
                )

                updateUnlocksWithinRange()
                updateAppSessionsWithinRange()
            }
        }
    }

    private fun enableDataGathering() {
        requireContext().startService(Intent(requireContext(), AppUsageGathererService::class.java))
    }

    private fun setupList() {
        appTimeAdapter = AppTimeUsageRecyclerAdapter(object : RecyclerViewItemShortClick {
            override fun onShortClick(view: View, position: Int) {
                return
                val stat: UsageStats = appUsageStatsList[position]
                val category: AppCategory? =
                    appCategories.find { it.appPackage == stat.packageName }
                Toast.makeText(binding.root.context, category?.category, Toast.LENGTH_SHORT).show()
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
            if (result.size > unlockCount) {
                unlockCount = result.size
                requireActivity().runOnUiThread {
                    tv_total_unlocks.text = unlockCount.toString()
                }
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
        data.forEach { app ->
            var totalTime = 0L
            app.value.forEach {
                totalTime += it.endTimestamp!! - it.startTimestamp
            }
            totalScreenTime += totalTime

            appSessionList.add(Pair(getAppName(requireContext(), app.key), totalTime))
        }

        val hsm = getHoursMinutesSeconds(totalScreenTime)
        val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"

        requireActivity().runOnUiThread {
            tv_total_screen_time.text = totalTimeString
            appTimeAdapter.list = appSessionList.toList().sortedByDescending { it.second }
        }
    }

    private fun showUsagePermissionDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Usage Access")
        builder.setMessage("This app needs to have access to your device's app usage and statistics in order to work")

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            showPermissionAccessSettings()
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, which ->
            Toast.makeText(
                context,
                "Sorry, but without usage access this app won't show anything", Toast.LENGTH_LONG
            ).show()
        }

        builder.show()
    }

    private fun showPermissionAccessSettings() {
        startActivityForResult(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS
        )
    }

    private fun hasUsagePermission(): Boolean {
        return try {
            val applicationInfo =
                requireContext().packageManager.getApplicationInfo(requireContext().packageName, 0)
            val appOpsManager =
                requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
}
