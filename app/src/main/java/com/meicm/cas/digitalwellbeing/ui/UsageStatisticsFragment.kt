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


//    private fun loadData() {
//        getStats()
//        requireContext().startService(Intent(requireContext(), UnlockService::class.java))
//    }
//
//    private fun getStats() {
//        val endTime = Calendar.getInstance()
//        val startTime = Calendar.getInstance()
//        startTime.set(Calendar.HOUR_OF_DAY, 0)
//        startTime.set(Calendar.MINUTE, 0)
//        startTime.set(Calendar.SECOND, 0)
//
//        val sdf = SimpleDateFormat("YYYY-MMM-dd HH:mm:ss", Locale.getDefault())
//        Log.d(Const.LOG_TAG, "Querying between: ${sdf.format(startTime.time)} (${startTime.timeInMillis}) and ${sdf.format(endTime.time)} (${endTime.timeInMillis})")
//
//        getUsageStats(startTime, endTime)
//        getEventStats(startTime, endTime)
//    }
//
//    private fun getUsageStats(startTime: Calendar, endTime: Calendar) {
//        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
//
//        val appsSessions = processAppsUsageSessions(manager, startTime, endTime)
//        val tableList = mutableListOf<Pair<String, Long>>()
//        var totalPerApp = 0L
//        var totalTime = 0L
//
//        appsSessions.forEach{
//            it.value.forEach{
//                if(it.second == null){
//                    totalPerApp += endTime.timeInMillis - it.first
//                }else{
//                    totalPerApp += it.second!! - it.first
//                }
//            }
//            if(it.value.size > 0) {
//                tableList.add(Pair(com.meicm.cas.digitalwellbeing.util.getAppName(it.key), totalPerApp))
//                totalTime += totalPerApp
//                totalPerApp = 0L
//            }
//        }
//
//        appTimeAdapter.list = tableList.sortedByDescending { it.second }.toList()
//        val hsm = getHMS(totalTime)
//        val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
//        binding.tvTotalScreenTime.text = totalTimeString
//        this.appTimeAdapter.notifyDataSetChanged()
//    }
//
//    private fun processAppsUsageSessions(manager: UsageStatsManager, startTime: Calendar, endTime: Calendar)
//            : HashMap<String, MutableList<Pair<Long, Long?>>>
//    {
//        val usageEvents: UsageEvents =
//            manager.queryEvents(startTime.timeInMillis, endTime.timeInMillis)
//        val appSessions = hashMapOf<String, MutableList<Pair<Long, Long?>>>()
//        val currentAppTimestamps = hashMapOf<String, Long?>()
//
//        while (usageEvents.hasNextEvent()) {
//            val currentUsageEvent: UsageEvents.Event = UsageEvents.Event()
//            usageEvents.getNextEvent(currentUsageEvent)
//            processEventStats(currentUsageEvent, appSessions, currentAppTimestamps)
//        }
//
//        currentAppTimestamps.forEach{
//            if(it.value != null){
//                if(!appSessions.containsKey(it.key)){
//                    appSessions[it.key] = mutableListOf()
//                }
//                appSessions.getValue(it.key).add(Pair(it.value!!, null))
//                /*val hms = getHMS(Calendar.getInstance().timeInMillis - it.value!!)
//                Log.d(
//                    Const.LOG_TAG,
//                    "APP: ${com.meicm.cas.digitalwellbeing.util.getAppName(it.key)} used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.value!!)} TO ${epochToString(Calendar.getInstance().timeInMillis)}"
//                )*/
//            }
//        }
//
//        logAppSessions(appSessions)
//        return appSessions
//    }
//
//    private fun logAppSessions(appSessions: HashMap<String, MutableList<Pair<Long, Long?>>>) {
//        var totalPerApp = 0L
//        var total = 0L
//        var diff = 0L
//        appSessions.forEach{
//            if(it.value.size > 0){
//                Log.d(Const.LOG_TAG, "App: ${com.meicm.cas.digitalwellbeing.util.getAppName(it.key)}")
//                Log.d(Const.LOG_TAG, "")
//            }
//            it.value.forEach{
//                if(it.second == null){
//                    diff = Calendar.getInstance().timeInMillis - it.first
//                    val hms = getHMS(diff)
//                    Log.d(
//                        Const.LOG_TAG,
//                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.first)} TO ${epochToString(Calendar.getInstance().timeInMillis)}"
//                    )
//                }else{
//                    diff = it.second!! - it.first
//                    val hms = getHMS(diff)
//                    Log.d(
//                        Const.LOG_TAG,
//                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.first)} TO ${epochToString(it.second!!)}"
//                    )
//                }
//                totalPerApp += diff
//
//            }
//            if(it.value.size > 0) {
//                val hms = getHMS(totalPerApp)
//                Log.d(Const.LOG_TAG, "App total: ${hms.first}h ${hms.second}min ${hms.third}s")
//            }
//            total += totalPerApp
//            totalPerApp = 0L
//        }
//        val hms = getHMS(total)
//        Log.d(Const.LOG_TAG, "Final total: ${hms.first}h ${hms.second}min ${hms.third}s")
//    }
//
//    private fun getEventStats(startTime: Calendar, endTime: Calendar) {
//        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
//        val usageEvents: UsageEvents = manager.queryEvents(startTime.timeInMillis, endTime.timeInMillis)
//        val listUnlocks = mutableListOf<Unlock>()
//
//        var previousUnlock: Unlock? = null
//        while (usageEvents.hasNextEvent()) {
//            val event: UsageEvents.Event = UsageEvents.Event()
//            usageEvents.getNextEvent(event)
//
//            if (event.timeStamp < startTime.timeInMillis ||
//                event.timeStamp > endTime.timeInMillis) continue
//
//            if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
//                previousUnlock = Unlock(0, event.timeStamp, null)
//            } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
//                if (previousUnlock == null) continue
//                if (previousUnlock.startTimestamp == null) continue
//
//                previousUnlock.endTimestamp = event.timeStamp
//                listUnlocks.add(previousUnlock)
//
//                previousUnlock = null
//            }
//        }
//        // add the remaining unlock (current one) to the list
//        if (previousUnlock != null) listUnlocks.add(previousUnlock)
//
//        usageViewModel.insertUnlocksIfEmpty(listUnlocks)
//    }
//
//    private fun processEventStats(usageEvent : UsageEvents.Event, appSessions : HashMap<String,
//            MutableList<Pair<Long, Long?>>>, currentAppTimestamps : HashMap<String, Long?>){
//
//        if(!currentAppTimestamps.containsKey(usageEvent.packageName)){
//            currentAppTimestamps[usageEvent.packageName] = null
//        }
//
//        //activity moved to the foreground
//        if(usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED){
//            currentAppTimestamps[usageEvent.packageName] = usageEvent.timeStamp
//            return
//        }
//
//        //An activity moved to the background or
//        //an activity becomes invisible on the UI
//        val appInfo = requireContext().packageManager.getApplicationInfo(usageEvent.packageName, 0)
//        val bitMask = appInfo.flags and ApplicationInfo.FLAG_SYSTEM
//        if((usageEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED && bitMask == 1) ||
//            usageEvent.eventType == UsageEvents.Event.ACTIVITY_STOPPED && bitMask != 1){
//            if(currentAppTimestamps.getValue(usageEvent.packageName) == null){
//                return
//            }
//            if(!appSessions.containsKey(usageEvent.packageName)){
//                appSessions[usageEvent.packageName] = mutableListOf()
//            }
//            appSessions.getValue(usageEvent.packageName).add(Pair(currentAppTimestamps.getValue(usageEvent.packageName)!!, usageEvent.timeStamp))
//            currentAppTimestamps[usageEvent.packageName] = null
//        }
//    }
//
//
//    private fun epochToString(epoch: Long): String{
//        val date = Calendar.getInstance()
//        date.timeInMillis = epoch
//        return "${date.get(Calendar.DAY_OF_MONTH)}/${date.get(Calendar.MONTH)+1}/${date.get(Calendar.YEAR)} " +
//                "${date.get(Calendar.HOUR_OF_DAY)}h ${date.get(Calendar.MINUTE)}m ${date.get(Calendar.SECOND)}s"
//    }
//
//    private fun com.meicm.cas.digitalwellbeing.util.getAppName(packageName: String): String{
//        val appInfo = requireContext().packageManager.getApplicationInfo(packageName, 0)
//        return appInfo.loadLabel(requireContext().packageManager).toString()
//    }

    /*
    private fun getAppCategory(packageName: String): String{
        val appInfo = context!!.packageManager.getApplicationInfo(packageName, 0)
        //TODO: requires api 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d(
                Const.LOG_TAG,
                "Category: ${appInfo.category}"
            )
            return ""
        }
        return ""
    }*/
}
