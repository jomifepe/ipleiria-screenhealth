package com.meicm.cas.digitalwellbeing

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.EventStats
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
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
import kotlin.collections.HashMap

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
        startTime.set(Calendar.HOUR_OF_DAY, 0)
        startTime.set(Calendar.MINUTE, 0)
        startTime.set(Calendar.SECOND, 0)

        val sdf = SimpleDateFormat("YYYY-MMM-dd HH:mm:ss", Locale.getDefault())
        Log.d(Const.LOG_TAG, "Querying between: ${sdf.format(startTime.time)} (${startTime.timeInMillis}) and ${sdf.format(endTime.time)} (${endTime.timeInMillis})")

        val manager = binding.root.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        //processLockUnlockSessions(manager, startTime, endTime)
        val appsSessions = processAppsUsageSessions(manager, startTime, endTime)
        val tableList = mutableListOf<Pair<String, Long>>()
        var totalPerApp = 0L
        var totalTime = 0L

        appsSessions.forEach{
            it.value.forEach{
                if(it.second == null){
                    totalPerApp += endTime.timeInMillis - it.first
                }else{
                    totalPerApp += it.second!! - it.first
                }
                //totalPerApp += it.second!! - it.first
            }
            if(it.value.size > 0) {
                tableList.add(Pair(getAppName(it.key), totalPerApp))
                totalTime += totalPerApp
                totalPerApp = 0L
            }
        }

        appTimeAdapter.list = tableList.sortedByDescending { it.second }.toList()
        val hsm = getHMS(totalTime)
        val totalTimeString = "${hsm.first} h ${hsm.second} min ${hsm.third} s"
        binding.tvTotalScreenTime.text = totalTimeString
        this.appTimeAdapter.notifyDataSetChanged()
    }

    private fun processAppsUsageSessions(manager: UsageStatsManager, startTime: Calendar, endTime: Calendar)
            : HashMap<String, MutableList<Pair<Long, Long?>>>
    {
        val usageEvents: UsageEvents =
            manager.queryEvents(startTime.timeInMillis, endTime.timeInMillis)
        val appSessions = hashMapOf<String, MutableList<Pair<Long, Long?>>>()
        val currentAppTimestamps = hashMapOf<String, Long?>()

        while (usageEvents.hasNextEvent()) {
            val currentUsageEvent: UsageEvents.Event = UsageEvents.Event()
            usageEvents.getNextEvent(currentUsageEvent)

            //if(getAppName(currentUsageEvent.packageName).compareTo("Instagram") == 0){
            if(getAppName(currentUsageEvent.packageName).compareTo("System UI") == 0) {

                    Log.d (Const.LOG_TAG, "[${currentUsageEvent.eventType}] Activity ${currentUsageEvent.packageName} at: ${epochToString(currentUsageEvent.timeStamp)}")
            }
            processEvent(currentUsageEvent, appSessions, currentAppTimestamps)
        }

        currentAppTimestamps.forEach{
            if(it.value != null){
                if(!appSessions.containsKey(it.key)){
                    appSessions[it.key] = mutableListOf()
                }
                appSessions.getValue(it.key).add(Pair(it.value!!, null))
                /*val hms = getHMS(Calendar.getInstance().timeInMillis - it.value!!)
                Log.d(
                    Const.LOG_TAG,
                    "APP: ${getAppName(it.key)} used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.value!!)} TO ${epochToString(Calendar.getInstance().timeInMillis)}"
                )*/
            }
        }

        //#####DEBUG ONLY#####
        /*var totalPerApp = 0L
        var total = 0L
        var diff = 0L
        appSessions.forEach{
            if(it.value.size > 0){
                Log.d(Const.LOG_TAG, "App: ${getAppName(it.key)}")
                Log.d(Const.LOG_TAG, "")
            }
            it.value.forEach{
                if(it.second == null){
                    diff = Calendar.getInstance().timeInMillis - it.first
                    val hms = getHMS(diff)
                    Log.d(
                        Const.LOG_TAG,
                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.first)} TO ${epochToString(Calendar.getInstance().timeInMillis)}"
                    )
                }else{
                    diff = it.second!! - it.first
                    val hms = getHMS(diff)
                    Log.d(
                        Const.LOG_TAG,
                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.first)} TO ${epochToString(it.second!!)}"
                    )
                }
                totalPerApp += diff

            }
            if(it.value.size > 0) {
                val hms = getHMS(totalPerApp)
                Log.d(Const.LOG_TAG, "App total: ${hms.first}h ${hms.second}min ${hms.third}s")
            }
            total += totalPerApp
            totalPerApp = 0L
        }
        val hms = getHMS(total)
        Log.d(Const.LOG_TAG, "Final total: ${hms.first}h ${hms.second}min ${hms.third}s")*/
        //#####DEBUG ONLY#####

        return appSessions
    }

    private fun processEvent(usageEvent : UsageEvents.Event, appSessions : HashMap<String,
            MutableList<Pair<Long, Long?>>>, currentAppTimestamps : HashMap<String, Long?>){

        if(!currentAppTimestamps.containsKey(usageEvent.packageName)){
            currentAppTimestamps[usageEvent.packageName] = null
        }

        //activity moved to the foreground
        if(usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED){
            currentAppTimestamps[usageEvent.packageName] = usageEvent.timeStamp
            return
        }


        //2 ACTIVITY PAUSED
        /*if(getAppName(usageEvent.packageName).compareTo("Instagram") == 0 && usageEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
            Log.d(Const.LOG_TAG, "Put at null")
        }*/

        if(getAppName(usageEvent.packageName).compareTo("System UI") == 0) {
            Log.d(Const.LOG_TAG, "comes here")
        }

        //An activity moved to the background or
        //an activity becomes invisible on the UI
        val appInfo = context!!.packageManager.getApplicationInfo(usageEvent.packageName, 0)
        val bitMask = appInfo.flags and ApplicationInfo.FLAG_SYSTEM

        if(bitMask == 1){
            val a = 5
        }

        /*if((usageEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED && bitMask == 1) ||
            usageEvent.eventType == UsageEvents.Event.ACTIVITY_STOPPED && bitMask != 1){*/
        
        if((usageEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED) ||
            usageEvent.eventType == UsageEvents.Event.ACTIVITY_STOPPED){

            if(currentAppTimestamps.getValue(usageEvent.packageName) == null){
                return
            }
            if(!appSessions.containsKey(usageEvent.packageName)){
                appSessions[usageEvent.packageName] = mutableListOf()
            }
            appSessions.getValue(usageEvent.packageName).add(Pair(currentAppTimestamps.getValue(usageEvent.packageName)!!, usageEvent.timeStamp))
            //if(getAppName(usageEvent.packageName).compareTo("Instagram") == 0) {
            if(getAppName(usageEvent.packageName).compareTo("System UI") == 0) {
                Log.d(Const.LOG_TAG, "Put at null")
            }
            currentAppTimestamps[usageEvent.packageName] = null
        }
    }

    private fun processLockUnlockSessions(manager: UsageStatsManager, startTime: Calendar, endTime: Calendar)
    {
        val usageEvents: UsageEvents =
            manager.queryEvents(startTime.timeInMillis, endTime.timeInMillis)
        var currentUnlockTimestamp : Long? = null
        while (usageEvents.hasNextEvent()) {
            val currentUsageEvent: UsageEvents.Event = UsageEvents.Event()
            usageEvents.getNextEvent(currentUsageEvent)
            if(currentUsageEvent.eventType == UsageEvents.Event.SCREEN_INTERACTIVE){
                currentUnlockTimestamp = currentUsageEvent.timeStamp
                Log.d (Const.LOG_TAG, "[${currentUsageEvent.eventType}] Activity ${currentUsageEvent.packageName} phone unlocked at: ${epochToString(currentUsageEvent.timeStamp)}")
            }

            if(currentUsageEvent.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                Log.d (Const.LOG_TAG, "[${currentUsageEvent.eventType}] Activity ${currentUsageEvent.packageName} phone locked at: ${epochToString(currentUsageEvent.timeStamp)}")
                if(currentUnlockTimestamp != null) {
                    val hms = getHMS(currentUsageEvent.timeStamp - currentUnlockTimestamp!!)
                    Log.d(
                        Const.LOG_TAG,
                        "[${UsageEvents.Event.SCREEN_INTERACTIVE}/${currentUsageEvent.eventType}] Unlock/Lock session of: ${hms.first} h ${hms.second} min ${hms.third} s"
                    )
                    currentUnlockTimestamp = null
                }
            }
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

    private fun epochToString(epoch: Long): String{
        val date = Calendar.getInstance()
        date.timeInMillis = epoch
        return "${date.get(Calendar.DAY_OF_MONTH)}/${date.get(Calendar.MONTH)+1}/${date.get(Calendar.YEAR)} " +
                "${date.get(Calendar.HOUR_OF_DAY)}h ${date.get(Calendar.MINUTE)}m ${date.get(Calendar.SECOND)}s"
    }

    private fun getAppName(packageName: String): String{
        val appInfo = context!!.packageManager.getApplicationInfo(packageName, 0)
        return appInfo.loadLabel(context!!.packageManager).toString()
    }
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
