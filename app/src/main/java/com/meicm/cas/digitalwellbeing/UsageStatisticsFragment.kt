package com.meicm.cas.digitalwellbeing

import android.app.AppOpsManager
import android.app.usage.EventStats
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.meicm.cas.digitalwellbeing.adapter.AppTimeUsageRecyclerAdapter
import com.meicm.cas.digitalwellbeing.adapter.RecyclerViewItemShortClick
import com.meicm.cas.digitalwellbeing.databinding.FragmentUsageStatisticsBinding
import com.meicm.cas.digitalwellbeing.util.Const
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class UsageStatisticsFragment : Fragment() {
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

        if (hasUsagePermission()) {
            getStats()
        }
        return binding.root
    }

    private fun getStats() {
        val startTime = Calendar.getInstance()
        startTime.set(Calendar.YEAR, 2020)
        startTime.set(Calendar.MONTH, 4)
        startTime.set(Calendar.DAY_OF_MONTH, 14)
        startTime.set(Calendar.HOUR_OF_DAY, 0)
        startTime.set(Calendar.MINUTE, 0)
        startTime.set(Calendar.SECOND, 0)

        val endTime = Calendar.getInstance()
        endTime.set(Calendar.YEAR, 2020)
        endTime.set(Calendar.MONTH, 4)
        endTime.set(Calendar.DAY_OF_MONTH, 14)
        endTime.set(Calendar.HOUR_OF_DAY, 23)
        endTime.set(Calendar.MINUTE, 59)
        endTime.set(Calendar.SECOND, 59)

        val sdf = SimpleDateFormat("YYYY-MMM-dd", Locale.getDefault())

        val cal:Calendar = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_WEEK, -1)
        Log.d(Const.LOG_TAG, cal.timeInMillis.toString())
        Log.d(Const.LOG_TAG, "currentTimeMillis ${System.currentTimeMillis()}")

        Log.d(Const.LOG_TAG, "Querying between: ${sdf.format(startTime.time)} (${startTime.timeInMillis}) and ${sdf.format(endTime.time)} (${endTime.timeInMillis})")

        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val queryUsageStats: List<UsageStats> = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
            startTime.timeInMillis, endTime.timeInMillis)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val eventStats: List<EventStats> = manager.queryEventStats(UsageStatsManager.INTERVAL_DAILY,
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

        if (queryUsageStats.isNotEmpty()) {
            val listUsageStats: MutableList<UsageStats> =
                queryUsageStats.sortedByDescending { it.totalTimeInForeground } as MutableList<UsageStats>
            appTimeAdapter.list = listUsageStats.filter { it.totalTimeInForeground > 0 }
            var totalTime: Long = 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryUsageStats.forEach { totalTime += it.totalTimeVisible }
                val hsm = getHMS(totalTime)
                val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
                binding.tvTotalScreenTime.text = totalTimeString
            } else {
                queryUsageStats.forEach { totalTime += it.totalTimeInForeground }
                val hsm = getHMS(totalTime)
                val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
                binding.tvTotalScreenTime.text = totalTimeString
            }

            appTimeAdapter.notifyDataSetChanged()
        }
    }

    private fun hasUsagePermission(): Boolean {
        try {
            val context = binding.root.context
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
