package com.meicm.cas.digitalwellbeing.ui

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.EventStats
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.ScreenInteractiveReceiver
import com.meicm.cas.digitalwellbeing.UnlockService
import com.meicm.cas.digitalwellbeing.ui.adapter.AppTimeUsageRecyclerAdapter
import com.meicm.cas.digitalwellbeing.ui.adapter.RecyclerViewItemShortClick
import com.meicm.cas.digitalwellbeing.databinding.FragmentUsageStatisticsBinding
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.viewmodel.UsageViewModel
import kotlinx.android.synthetic.main.fragment_usage_statistics.*
import java.text.SimpleDateFormat
import java.util.*

private const val MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS = 3

class UsageStatisticsFragment: Fragment() {
    private lateinit var binding: FragmentUsageStatisticsBinding
    private lateinit var appTimeAdapter: AppTimeUsageRecyclerAdapter
    private lateinit var usageViewModel: UsageViewModel

    private var appUsageStatsList: List<UsageStats> = listOf()
    private var appCategories: List<AppCategory> = listOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate<FragmentUsageStatisticsBinding>(
            inflater,
            R.layout.fragment_usage_statistics, container, false)
        setHasOptionsMenu(true)

        setupList()
        subscribeViewModel()

        if (!hasUsagePermission()) {
            showUsagePermissionDialog()
        } else {
            loadData()
        }

        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS -> {
                if (!hasUsagePermission()) return
                loadData()
            }
        }
    }

    private fun setupList() {
        appTimeAdapter = AppTimeUsageRecyclerAdapter(object: RecyclerViewItemShortClick {
            override fun onShortClick(view: View, position: Int) {
                var stat: UsageStats = appUsageStatsList[position]
                var category: AppCategory? = appCategories.find { it.appPackage == stat.packageName }
                Toast.makeText(binding.root.context, category?.category, Toast.LENGTH_SHORT).show()
            }
        })
        binding.rvAppTime.layoutManager = LinearLayoutManager(binding.root.context,
            LinearLayoutManager.VERTICAL, false)
        binding.rvAppTime.adapter = appTimeAdapter
    }

    private fun subscribeViewModel() {
        usageViewModel = ViewModelProvider(this).get(UsageViewModel::class.java)
        usageViewModel.allUnlocks.observe(viewLifecycleOwner, Observer { unlocks ->
            unlocks?.let { tv_total_unlocks.text = unlocks.size.toString() }
        })
        usageViewModel.appCategories.observe(viewLifecycleOwner, Observer { data ->
            data?.let {
                this.appCategories = data
            }
        })
    }

    private fun showUsagePermissionDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Usage Access")
        builder.setMessage("This app needs to have access to your device's app usage and statistics in order to work")

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            showPermissionAccessSettings()
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, which ->
            Toast.makeText(context,
                "Sorry, but without usage access this app won't show anything", Toast.LENGTH_LONG).show()
        }

        builder.show()
    }

    private fun showPermissionAccessSettings() {
        startActivityForResult(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS
        )
    }

    private fun hasUsagePermission(): Boolean {
        try {
            val applicationInfo = requireContext().packageManager.getApplicationInfo(requireContext().packageName, 0)
            val appOpsManager = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    private fun loadData() {
        getStats()
        requireContext().startService(Intent(requireContext(), UnlockService::class.java))
    }

    private fun getStats() {
        val endTime = Calendar.getInstance()
        val startTime = Calendar.getInstance()
//        startTime.add(Calendar.DAY_OF_WEEK, -1)
        startTime.set(Calendar.HOUR_OF_DAY, 0)
        startTime.set(Calendar.MINUTE, 0)
        startTime.set(Calendar.SECOND, 0)

        val sdf = SimpleDateFormat("YYYY-MMM-dd HH:mm:ss", Locale.getDefault())
        Log.d(Const.LOG_TAG, "Querying between: ${sdf.format(startTime.time)} (${startTime.timeInMillis}) and ${sdf.format(endTime.time)} (${endTime.timeInMillis})")

        getUsageStats(startTime, endTime)
        getEventStats(startTime, endTime)
    }

    private fun getUsageStats(startTime: Calendar, endTime: Calendar) {
        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats: List<UsageStats> = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime.timeInMillis, endTime.timeInMillis)

        if (stats.isNotEmpty()) {
//            val stats = aggregateStats.values
            var listUsageStats = stats
//            var listUsageStats: List<UsageStats> = stats.filter {
//                it.totalTimeInForeground > 0 && startTime.timeInMillis <= it.firstTimeStamp
//            }

            listUsageStats = listUsageStats.sortedByDescending { it.totalTimeInForeground }.toMutableList()
            appUsageStatsList = listUsageStats
            appTimeAdapter.list = listUsageStats

            var totalTime: Long = 0L
            listUsageStats.forEach { totalTime += it.totalTimeInForeground }
            val hsm = getHMS(totalTime)
            val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
            binding.tvTotalScreenTime.text = totalTimeString

            val packages: List<String> = listUsageStats.map { it.packageName }
            usageViewModel.categorizeApplications(packages)

            appTimeAdapter.notifyDataSetChanged()
        }
    }

    private fun getEventStats(startTime: Calendar, endTime: Calendar) {
        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val usageEvents: UsageEvents = manager.queryEvents(startTime.timeInMillis, endTime.timeInMillis)
        val listUnlocks = mutableListOf<Unlock>()

        var previousUnlock: Unlock? = null
        while (usageEvents.hasNextEvent()) {
            val event: UsageEvents.Event = UsageEvents.Event()
            usageEvents.getNextEvent(event)

            if (event.timeStamp < startTime.timeInMillis ||
                event.timeStamp > endTime.timeInMillis) continue

            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                previousUnlock =
                    Unlock(
                        0,
                        event.timeStamp,
                        null
                    )
            } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                if (previousUnlock == null) continue
                if (previousUnlock.startTimestamp == null) continue
                previousUnlock.endTimestamp = event.timeStamp
                listUnlocks.add(previousUnlock)

                previousUnlock = null
            }
        }
        // add the remaining unlock (current one) to the list
        if (previousUnlock != null) listUnlocks.add(previousUnlock)

        usageViewModel.insertUnlocksIfEmpty(listUnlocks)
    }

    // TODO: remove
    @RequiresApi(Build.VERSION_CODES.P)
    private fun loadEventStats(startTime: Calendar, endTime: Calendar) {
        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var eventStats: List<EventStats> = manager.queryEventStats(UsageStatsManager.INTERVAL_DAILY,
            startTime.timeInMillis, endTime.timeInMillis)
        eventStats = eventStats.filter { startTime.timeInMillis <= it.firstTimeStamp }
        if (eventStats.isNotEmpty()) {
            addUnlocksStatsToDatabase(eventStats)
            Log.d(Const.LOG_TAG, "Event codes: https://developer.android.com/reference/kotlin/android/app/usage/UsageEvents.Event")
            eventStats.forEach {
                val hms = getHMS(it.lastTimeStamp - it.firstTimeStamp)
                Log.d(Const.LOG_TAG, "Event: ${it.eventType} Count: ${it.count} Total_Time: ${hms.first} h ${hms.second} min ${hms.third} s")
            }
        } else {
            Log.d(Const.LOG_TAG, "No event stats found")
        }
    }

    // TODO: remove
    @RequiresApi(Build.VERSION_CODES.P)
    private fun addUnlocksStatsToDatabase(stats: List<EventStats>) {
        var unlockCount: Int = 0
        stats.filter { it.eventType == 15 }
             .forEach { unlockCount += it.count }
        val unlocks = mutableListOf<Unlock>()
        for (i in 0..unlockCount) unlocks.add(
            Unlock(
                0,
                null,
                null
            )
        )
        usageViewModel.insertUnlocksIfEmpty(unlocks)
    }

    private fun getHMS(timeInMillis: Long): Triple<Long, Long, Long> {
        val seconds: Long = (timeInMillis / 1000) % 60
        val minutes: Long = (timeInMillis / (1000 * 60)) % 60
        val hours: Long = (timeInMillis / (1000 * 60 * 60))

        return Triple(hours, minutes, seconds)
    }
}
