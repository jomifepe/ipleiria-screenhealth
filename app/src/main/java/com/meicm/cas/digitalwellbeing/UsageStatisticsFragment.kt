package com.meicm.cas.digitalwellbeing

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.EventStats
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.meicm.cas.digitalwellbeing.adapter.AppTimeUsageRecyclerAdapter
import com.meicm.cas.digitalwellbeing.adapter.RecyclerViewItemShortClick
import com.meicm.cas.digitalwellbeing.databinding.FragmentUsageStatisticsBinding
import com.meicm.cas.digitalwellbeing.util.Const
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

private const val MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS = 3

class UsageStatisticsFragment: Fragment() {
    lateinit var binding: FragmentUsageStatisticsBinding
    lateinit var appTimeAdapter: AppTimeUsageRecyclerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate<FragmentUsageStatisticsBinding>(
            inflater, R.layout.fragment_usage_statistics, container, false)
        setHasOptionsMenu(true)

        appTimeAdapter = AppTimeUsageRecyclerAdapter(object: RecyclerViewItemShortClick {
            override fun onShortClick(view: View, position: Int) {
                Toast.makeText(binding.root.context, "Clicked app", Toast.LENGTH_SHORT).show()
            }
        })
        binding.rvAppTime.layoutManager = LinearLayoutManager(binding.root.context,
            LinearLayoutManager.VERTICAL, false)
        binding.rvAppTime.adapter = appTimeAdapter

        if (!hasUsagePermission()) {
            showUsagePermissionDialog()
        } else {
            getStats()
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

    private fun showUsagePermissionDialog() {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle("Usage Access")
        builder.setMessage("This app needs to have access to your device's app usage and statistics in order to work")

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            showPermissionAccessSettings()
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, which ->
            Toast.makeText(context!!,
                "Sorry, but without usage access this app won't show anything", Toast.LENGTH_LONG).show()
        }

        builder.show()
    }

    private fun showPermissionAccessSettings() {
        startActivityForResult(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS)
    }

    private fun hasUsagePermission(): Boolean {
        try {
            val applicationInfo = context!!.packageManager.getApplicationInfo(context!!.packageName, 0)
            val appOpsManager = context!!.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
        context!!.startService(Intent(context!!, UnlockService::class.java))
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

        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats: List<UsageStats> = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime.timeInMillis, endTime.timeInMillis)

//        val aggregateStats: Map<String, UsageStats> =
//            manager.queryAndAggregateUsageStats(startTime.timeInMillis, endTime.timeInMillis)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val eventStats: List<EventStats> = manager.queryEventStats(UsageStatsManager.INTERVAL_WEEKLY,
                startTime.timeInMillis, endTime.timeInMillis)
            if (eventStats.isNotEmpty()) {
                Log.d(Const.LOG_TAG, "Event codes: https://developer.android.com/reference/kotlin/android/app/usage/UsageEvents.Event")
                eventStats.forEach {
                    val hms = getHMS(it.totalTime)
                    Log.d(Const.LOG_TAG, "Event: ${it.eventType} Count: ${it.count} Total_Time: ${hms.first} h ${hms.second} min ${hms.third} s")
                }
            } else {
                Log.d(Const.LOG_TAG, "No event stats found")
            }
        }

        if (stats.isNotEmpty()) {
//            val stats = aggregateStats.values
            var listUsageStats: List<UsageStats> = stats.filter {
                it.totalTimeInForeground > 0 && startTime.timeInMillis < it.lastTimeUsed
            }
//            var filteredList: MutableList<UsageStats> = ArrayList(listUsageStats)
//            for (stat1: UsageStats in listUsageStats) {
//                for ((index, stat2) in listUsageStats.withIndex()) {
//                    if (stat1.packageName != stat2.packageName) continue
//                    if (stat1.lastTimeUsed > stat2.lastTimeUsed) filteredList.removeAt(index)
//                }
//            }
            listUsageStats = listUsageStats.sortedByDescending { it.totalTimeInForeground }.toMutableList()
            appTimeAdapter.list = listUsageStats

            var totalTime: Long = 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listUsageStats.forEach { totalTime += it.totalTimeVisible }
                val hsm = getHMS(totalTime)
                val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
                binding.tvTotalScreenTime.text = totalTimeString
            } else {
                listUsageStats.forEach { totalTime += it.totalTimeInForeground }
                val hsm = getHMS(totalTime)
                val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
                binding.tvTotalScreenTime.text = totalTimeString
            }

            appTimeAdapter.notifyDataSetChanged()
        }
    }

    private fun getHMS(timeInMillis: Long): Triple<Long, Long, Long> {
        val seconds: Long = (timeInMillis / 1000) % 60
        val minutes: Long = (timeInMillis / (1000 * 60)) % 60
        val hours: Long = (timeInMillis / (1000 * 60 * 60))

        return Triple(hours, minutes, seconds)

//        return Triple(TimeUnit.MILLISECONDS.toHours(timeInMillis),
//            TimeUnit.MILLISECONDS.toMinutes(timeInMillis),
//            TimeUnit.MILLISECONDS.toSeconds(timeInMillis))
    }
}
