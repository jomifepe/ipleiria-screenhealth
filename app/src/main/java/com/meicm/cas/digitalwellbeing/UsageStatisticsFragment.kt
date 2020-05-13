package com.meicm.cas.digitalwellbeing

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.meicm.cas.digitalwellbeing.databinding.FragmentUsageStatisticsBinding
import com.meicm.cas.digitalwellbeing.util.Const
import java.text.SimpleDateFormat
import java.util.*

class UsageStatisticsFragment : Fragment() {
    lateinit var bContext: Context

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val binding = DataBindingUtil.inflate<FragmentUsageStatisticsBinding>(
            inflater, R.layout.fragment_usage_statistics, container, false)
        bContext = binding.root.context
        setHasOptionsMenu(true)

        if (hasUsagePermission()) {
            getStats()
        }
        return binding.root
    }

    private fun getStats() {
        val startTime = GregorianCalendar(2020, 5, 1)
        val endTime = GregorianCalendar(2020, 5, 134)

        val sdf = SimpleDateFormat("YYYY-MMM-dd", Locale.getDefault())
        Log.d(Const.LOG_TAG, "Querying between: ${sdf.format(startTime.time)} and ${sdf.format(endTime.time)}")

        val manager = bContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val queryUsageStats: List<UsageStats> = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
            startTime.timeInMillis, endTime.timeInMillis)

        Log.d(Const.LOG_TAG, "queryUsageStats size:" + queryUsageStats.size.toString())

        for (us in queryUsageStats) {
            Log.d(Const.LOG_TAG, us.packageName + " = " + us.totalTimeInForeground)
        }
    }

    private fun hasUsagePermission(): Boolean {
        try {
            val applicationInfo = bContext.packageManager.getApplicationInfo(bContext.packageName, 0)
            val appOpsManager = bContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
}
