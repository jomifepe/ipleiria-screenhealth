package com.meicm.cas.digitalwellbeing.service

import android.app.IntentService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.util.Const
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class AppUsageGathererService: IntentService(Const.SERVICE_NAME_DATA_GATHERER) {
    override fun onBind(intent: Intent?): IBinder? {
        Log.i(Const.LOG_TAG, "App usage gatherer bound")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(Const.LOG_TAG, "Registered app usage gatherer service")
        gatherUsageStats()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onHandleIntent(intent: Intent?) {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(Const.LOG_TAG, "App usage gatherer service destroyed")
    }

    private fun gatherUsageStats() {
        runBlocking {
            val openSessions = withContext(Dispatchers.IO) {
                return@withContext AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .appSessionDao()
                    .getOpenSessions()
            }
            if(openSessions.isNotEmpty())
            {
                closeOpenedSessions(openSessions)
            }

            val lastSession = withContext(Dispatchers.IO) {
                return@withContext AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .appSessionDao()
                    .getLastSession()
            }

            val startTime = if(lastSession == null){
                //TODO: Choose a better start time it should be old
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }else{
                lastSession.startTimestamp + 1
            }
            gatherUsageStats(startTime, System.currentTimeMillis())
        }
        loadUnlockEntries(startTime, endTime)
    }

    private fun closeOpenedSessions(openSessions: List<AppSession>) {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val appsSessions: HashMap<String, MutableList<Pair<Long, Long?>>> =
            processAppsUsageSessions(manager, openSessions.first().startTimestamp, System.currentTimeMillis())
        val packageToIndexMap =  hashMapOf<String, Int>()

        for ((index, value) in openSessions.withIndex()) {
            packageToIndexMap[value.appPackage] = index
        }

        for (sessions in appsSessions){
            if(!packageToIndexMap.containsKey(sessions.key)) {
                continue
            }
            //update end endTimestamp with the endTimestamp present on the first session of that package
            openSessions[packageToIndexMap[sessions.key]!!].endTimestamp = sessions.value.first().second
            packageToIndexMap.remove(sessions.key)
            if(packageToIndexMap.isEmpty()){
                break
            }
        }

        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .appSessionDao()
                    .updateSessions(openSessions)
            }
        }
    }

    private fun gatherUsageStats(startTime: Long, endTime: Long) {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val appsSessions: HashMap<String, MutableList<Pair<Long, Long?>>> =
            processAppsUsageSessions(manager, startTime, endTime)
        val usageStatsList = mutableListOf<Pair<String, Long>>()
        var totalPerApp = 0L
        var totalTime = 0L

        val dbAppSessionList = mutableListOf<AppSession>()
        appsSessions.forEach{session ->
            val pkg = session.key
            session.value.forEach{
                totalPerApp += if(it.second == null){
                    endTime - it.first
                }else{
                    it.second!! - it.first
                }

                dbAppSessionList.add(AppSession(0, pkg, it.first, it.second))
            }
            if(session.value.size > 0) {
                usageStatsList.add(Pair(getAppName(session.key), totalPerApp))
                totalTime += totalPerApp
                totalPerApp = 0L
            }
        }

        // Add the data to the database
        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .appSessionDao()
                    .insertAll(dbAppSessionList)
            }
        }
    }

    private fun processAppsUsageSessions(manager: UsageStatsManager, startTime: Long, endTime: Long)
            : HashMap<String, MutableList<Pair<Long, Long?>>>
    {
        val usageEvents: UsageEvents =
            manager.queryEvents(startTime, endTime)
        val appSessions = hashMapOf<String, MutableList<Pair<Long, Long?>>>()
        val currentAppTimestamps = hashMapOf<String, Long?>()

        while (usageEvents.hasNextEvent()) {
            val currentUsageEvent: UsageEvents.Event = UsageEvents.Event()
            usageEvents.getNextEvent(currentUsageEvent)
            processEventStats(currentUsageEvent, appSessions, currentAppTimestamps)
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

        logAppSessions(appSessions)
        return appSessions
    }

    private fun processEventStats(usageEvent : UsageEvents.Event, appSessions : HashMap<String,
            MutableList<Pair<Long, Long?>>>, currentAppTimestamps : HashMap<String, Long?>){

        if(!currentAppTimestamps.containsKey(usageEvent.packageName)){
            currentAppTimestamps[usageEvent.packageName] = null
        }

        //activity moved to the foreground
        if(usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED){
            currentAppTimestamps[usageEvent.packageName] = usageEvent.timeStamp
            return
        }

        //An activity moved to the background or
        //an activity becomes invisible on the UI
        if((usageEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED) ||
            usageEvent.eventType == UsageEvents.Event.ACTIVITY_STOPPED){
            if(currentAppTimestamps.getValue(usageEvent.packageName) == null){
                return
            }
            if(!appSessions.containsKey(usageEvent.packageName)){
                appSessions[usageEvent.packageName] = mutableListOf()
            }
            appSessions.getValue(usageEvent.packageName).add(Pair(currentAppTimestamps.getValue(usageEvent.packageName)!!, usageEvent.timeStamp))
            currentAppTimestamps[usageEvent.packageName] = null
        }
    }

    private fun loadUnlockEntries(startTime: Calendar, endTime: Calendar) {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        runBlocking {
            val lastUnlockTimestap: Long? = withContext(Dispatchers.IO) {
                val unlock = AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .unlockDao()
                    .getLastUnlock()
                return@withContext unlock?.startTimestamp
            }

            val startTimeStamp = lastUnlockTimestap ?: startTime.timeInMillis
            val usageEvents: UsageEvents = manager.queryEvents(startTimeStamp, endTime.timeInMillis)
            val listUnlocks = mutableListOf<Unlock>()

            var previousUnlock: Unlock? = null
            while (usageEvents.hasNextEvent()) {
                val event: UsageEvents.Event = UsageEvents.Event()
                usageEvents.getNextEvent(event)

                if (event.timeStamp < startTime.timeInMillis ||
                    event.timeStamp > endTime.timeInMillis) continue

                if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                    previousUnlock = Unlock(0, event.timeStamp, null)
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

            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .unlockDao()
                    .insertAll(listUnlocks)
            }
        }
    }

    private fun getAppName(packageName: String): String{
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        return appInfo.loadLabel(packageManager).toString()
    }

    private fun getHMS(timeInMillis: Long): Triple<Long, Long, Long> {
        val seconds: Long = (timeInMillis / 1000) % 60
        val minutes: Long = (timeInMillis / (1000 * 60)) % 60
        val hours: Long = (timeInMillis / (1000 * 60 * 60))

        return Triple(hours, minutes, seconds)
    }

    private fun epochToString(epoch: Long): String{
        val date = Calendar.getInstance()
        date.timeInMillis = epoch
        return "${date.get(Calendar.DAY_OF_MONTH)}/${date.get(Calendar.MONTH)+1}/${date.get(Calendar.YEAR)} " +
                "${date.get(Calendar.HOUR_OF_DAY)}h ${date.get(Calendar.MINUTE)}m ${date.get(Calendar.SECOND)}s"
    }

    private fun logAppSessions(appSessions: HashMap<String, MutableList<Pair<Long, Long?>>>) {
        var totalPerApp = 0L
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
                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.first)} TO ${epochToString(
                            Calendar.getInstance().timeInMillis)}"
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
        Log.d(Const.LOG_TAG, "Final total: ${hms.first}h ${hms.second}min ${hms.third}s")
    }
}