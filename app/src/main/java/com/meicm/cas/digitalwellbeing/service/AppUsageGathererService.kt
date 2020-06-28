package com.meicm.cas.digitalwellbeing.service

import android.app.IntentService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.util.Log
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.remote.GooglePlayCategory
import com.meicm.cas.digitalwellbeing.remote.GooglePlayService
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.getAppName
import com.meicm.cas.digitalwellbeing.util.getInstalledPackages
import kotlinx.coroutines.*
import com.meicm.cas.digitalwellbeing.util.setStartOfDay
import java.util.*

class AppUsageGathererService : IntentService(Const.SERVICE_NAME_DATA_GATHERER) {
    var isGatheringUsageStats: Boolean = false
    var isGatheringUnlocks: Boolean = false
    var isGatheringCategories: Boolean = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(Const.LOG_TAG, "[AppUsageGathererService] Created service")
        startGatheringData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            Const.ACTION_GATHER_DATA -> {
                Log.d(Const.LOG_TAG, "[AppUsageGathererService] Received trigger to gather data")
                startGatheringData()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(Const.LOG_TAG, "[AppUsageGathererService] Service destroyed")
    }

    private fun startGatheringData() {
        if (!isGatheringUsageStats) gatherUsageStats()
        if (!isGatheringCategories) gatherAppCategories()
        if (!isGatheringUnlocks) loadUnlockEntries()
    }
    
    private fun gatherUsageStats() {
        Log.d(Const.LOG_TAG, "[AppUsageGathererService] Starting to gather usage stats...")
        CoroutineScope(Dispatchers.IO + CoroutineName("GatherUsageStats")).launch {
            isGatheringUsageStats = true

            val openSessions = AppDatabase
                .getDatabase(this@AppUsageGathererService)
                .appSessionDao()
                .getOpenSessions()

            /* Query the usage api to check if the open apps were closed since the last time
               the database was updated */
            closeOpenSessions(openSessions)

            /* Get the last app session from the database */
            val lastSession =
                AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .appSessionDao()
                    .getLastSession()

            val startTime = if (lastSession == null) {
                //TODO: Choose a better start time it should be old
                val cal = Calendar.getInstance()
                cal.setStartOfDay()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            } else {
                lastSession.startTimestamp + 1
            }
            processUsageStats(startTime, System.currentTimeMillis())

            isGatheringUsageStats = false
        }
    }

    private fun closeOpenSessions(openSessions: List<AppSession>) {
        if (openSessions.isEmpty()) return
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val appSessions: HashMap<String, MutableList<Pair<Long, Long?>>> =
            processAppsUsageSessions(
                manager,
                openSessions.first().startTimestamp,
                System.currentTimeMillis()
            )

        openSessions.forEach {
            if (appSessions.containsKey(it.appPackage)) {
                it.endTimestamp = appSessions.getValue(it.appPackage).first().second
            }
        }

        AppDatabase
            .getDatabase(this@AppUsageGathererService)
            .appSessionDao()
            .updateSessions(openSessions)
    }

    private fun processUsageStats(startTime: Long, endTime: Long) {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val appsSessions: HashMap<String, MutableList<Pair<Long, Long?>>> =
            processAppsUsageSessions(manager, startTime, endTime)
        val usageStatsList = mutableListOf<Pair<String, Long>>()
        var totalPerApp = 0L
        var totalTime = 0L

        val dbAppSessionList = mutableListOf<AppSession>()
        appsSessions.forEach { session ->
            val pkg = session.key
            session.value.forEach {
                totalPerApp += if (it.second == null) {
                    endTime - it.first
                } else {
                    it.second!! - it.first
                }
                dbAppSessionList.add(AppSession(0, pkg, it.first, it.second))
            }
            if (session.value.size > 0) {
                usageStatsList.add(Pair(getAppName(this, session.key), totalPerApp))
                totalTime += totalPerApp
                totalPerApp = 0L
            }
        }

        // Add the data to the database
        AppDatabase
            .getDatabase(this@AppUsageGathererService)
            .appSessionDao()
            .insertAll(dbAppSessionList)
    }

    private fun processAppsUsageSessions(manager: UsageStatsManager, startTime: Long, endTime: Long)
            : HashMap<String, MutableList<Pair<Long, Long?>>> {
        val usageEvents: UsageEvents = manager.queryEvents(startTime, endTime)
        val appSessions = hashMapOf<String, MutableList<Pair<Long, Long?>>>()
        val currentAppTimestamps = hashMapOf<String, Long?>()

        while (usageEvents.hasNextEvent()) {
            val currentUsageEvent: UsageEvents.Event = UsageEvents.Event()
            usageEvents.getNextEvent(currentUsageEvent)
            processEventStats(currentUsageEvent, appSessions, currentAppTimestamps)
        }

        currentAppTimestamps.forEach {
            if (it.value != null) {
                if (!appSessions.containsKey(it.key)) {
                    appSessions[it.key] = mutableListOf()
                }
                appSessions.getValue(it.key).add(Pair(it.value!!, null))
                /*val hms = getHMS(Calendar.getInstance().timeInMillis - it.value!!)
                Log.d(
                    Const.LOG_TAG,
                    "APP: ${com.meicm.cas.digitalwellbeing.util.getAppName(it.key)} used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(it.value!!)} TO ${epochToString(Calendar.getInstance().timeInMillis)}"
                )*/
            }
        }

//        logAppSessions(appSessions)
        return appSessions
    }

    private fun processEventStats(
        usageEvent: UsageEvents.Event, appSessions: HashMap<String, MutableList<Pair<Long, Long?>>>,
        currentAppTimestamps: HashMap<String, Long?>
    ) {

        if (!currentAppTimestamps.containsKey(usageEvent.packageName)) {
            currentAppTimestamps[usageEvent.packageName] = null
        }

        //activity moved to the foreground
        if (usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
            currentAppTimestamps[usageEvent.packageName] = usageEvent.timeStamp
            return
        }

        //An activity moved to the background or
        //an activity becomes invisible on the UI
        if ((usageEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED) ||
            usageEvent.eventType == UsageEvents.Event.ACTIVITY_STOPPED
        ) {
            if (currentAppTimestamps.getValue(usageEvent.packageName) == null) {
                return
            }
            if (!appSessions.containsKey(usageEvent.packageName)) {
                appSessions[usageEvent.packageName] = mutableListOf()
            }
            appSessions.getValue(usageEvent.packageName).add(
                Pair(
                    currentAppTimestamps.getValue(usageEvent.packageName)!!,
                    usageEvent.timeStamp
                )
            )
            currentAppTimestamps[usageEvent.packageName] = null
        }
    }

    private fun gatherAppCategories() {
        Log.d(Const.LOG_TAG, "[AppUsageGathererService] Starting to gather app categories...")
        CoroutineScope(Dispatchers.IO + CoroutineName("GatherAppCategories")).launch {
            isGatheringCategories = true

            val elapsedStart = System.nanoTime()
            val installedPackages = getInstalledPackages(this@AppUsageGathererService)
            val googlePlayService: GooglePlayService = GooglePlayService.create()

            val newAppCategories: MutableList<AppCategory> = mutableListOf()
            val updatedAppCategories: MutableList<AppCategory> = mutableListOf()
            for (pkg in installedPackages) {
                try {
                    val appCategory = getAppCategory(pkg.packageName)

                    if (/* doesn't exist */ appCategory == null) {
                        val element: GooglePlayCategory? = googlePlayService.getAppPage(pkg.packageName)
                        newAppCategories.add(AppCategory(pkg.packageName, element?.category))
                    } else {
                        if (appCategory.category != null) continue
                        val element: GooglePlayCategory? = googlePlayService.getAppPage(pkg.packageName)
                        appCategory.category = element?.category
                        updatedAppCategories.add(appCategory)
                    }
                } catch (ex: Exception) {
                    if (/* is system app */ pkg.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                    val existingAppCategory = getAppCategory(pkg.packageName)
                    if (/* doesn't exist */ existingAppCategory == null) {
                        newAppCategories.add(AppCategory(pkg.packageName, null))
                        Log.d(
                            Const.LOG_TAG,
                            "[AppUsageGathererService] No play store page available for app ${pkg.packageName}"
                        )
                    }
                }
            }

            if (newAppCategories.size > 0) {
                createNewAppCategories(newAppCategories)
                Log.d(Const.LOG_TAG, "[AppUsageGathererService] Gathered ${newAppCategories.size} new categories")
            }
            if (updatedAppCategories.size > 0) {
                updateAppCategories(updatedAppCategories)
                Log.d(Const.LOG_TAG, "[AppUsageGathererService] Updated ${newAppCategories.size} new categories")
            }
            val elapsedEnd = System.nanoTime()
            Log.d(Const.LOG_TAG, "[AppUsageGathererService] Category gathering elapsed time " +
                    "${(elapsedEnd - elapsedStart) / 1_000_000_000} seconds")

            isGatheringCategories = false
        }
    }

    private fun getAppCategory(packageName: String): AppCategory? {
        return AppDatabase
            .getDatabase(this)
            .appCategoryDao()
            .getAppCategory(packageName)
    }

    private fun createNewAppCategories(appCategory: List<AppCategory>) {
        AppDatabase
            .getDatabase(this)
            .appCategoryDao()
            .insert(appCategory)
    }

    private fun updateAppCategories(appCategories: List<AppCategory>) {
        AppDatabase
            .getDatabase(this)
            .appCategoryDao()
            .update(appCategories)
    }

    private fun loadUnlockEntries() {
        Log.d(Const.LOG_TAG, "[AppUsageGathererService] Starting to gather device unlocks...")

        val cal = Calendar.getInstance()
        cal.setStartOfDay()
        cal.set(Calendar.DAY_OF_YEAR, -7)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO + CoroutineName("GatherScreenUnlocks")).launch {
            isGatheringUnlocks = true

            val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val lastUnlockTimestamp =
                AppDatabase
                    .getDatabase(this@AppUsageGathererService)
                    .unlockDao()
                    .getLastUnlock()?.startTimestamp

            val startTimeStamp =
                if (lastUnlockTimestamp != null) {
                    lastUnlockTimestamp + 1
                } else {
                    startTime
                }

            val usageEvents: UsageEvents = manager.queryEvents(startTimeStamp, endTime)
            val listUnlocks = mutableListOf<Unlock>()
            var previousUnlock: Unlock? = null

            while (usageEvents.hasNextEvent()) {
                val event: UsageEvents.Event = UsageEvents.Event()
                usageEvents.getNextEvent(event)

                if (event.timeStamp < startTime || event.timeStamp > endTime) continue

                if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                    previousUnlock = Unlock(0, event.timeStamp, null)
                } else if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    if (previousUnlock == null) continue
                    if (previousUnlock.startTimestamp == null) continue
                    //if the diff between end and start timestamp is lower than 1s
                    if (event.timeStamp - previousUnlock.startTimestamp!! < 1000) continue

                    previousUnlock.endTimestamp = event.timeStamp
                    listUnlocks.add(previousUnlock)
                    previousUnlock = null
                }
            }
            // add the remaining unlock (current one) to the list
            if (previousUnlock != null) listUnlocks.add(previousUnlock)

            AppDatabase
                .getDatabase(this@AppUsageGathererService)
                .unlockDao()
                .insertAll(listUnlocks)

            isGatheringUnlocks = false
        }
    }

    private fun getHMS(timeInMillis: Long): Triple<Long, Long, Long> {
        val seconds: Long = (timeInMillis / 1000) % 60
        val minutes: Long = (timeInMillis / (1000 * 60)) % 60
        val hours: Long = (timeInMillis / (1000 * 60 * 60))

        return Triple(hours, minutes, seconds)
    }

    private fun epochToString(epoch: Long): String {
        val date = Calendar.getInstance()
        date.timeInMillis = epoch
        return "${date.get(Calendar.DAY_OF_MONTH)}/${date.get(Calendar.MONTH) + 1}/${date.get(
            Calendar.YEAR
        )} " +
                "${date.get(Calendar.HOUR_OF_DAY)}h ${date.get(Calendar.MINUTE)}m ${date.get(
                    Calendar.SECOND
                )}s"
    }

    private fun logAppSessions(appSessions: HashMap<String, MutableList<Pair<Long, Long?>>>) {
        var totalPerApp = 0L
        var total = 0L
        var diff = 0L
        appSessions.forEach {sessions ->
            if (sessions.value.size > 0) {
                Log.d(Const.LOG_TAG, "App: ${getAppName(this, sessions.key)}")
                Log.d(Const.LOG_TAG, "")
            }
            sessions.value.forEach {
                if (it.second == null) {
                    diff = Calendar.getInstance().timeInMillis - it.first
                    val hms = getHMS(diff)
                    Log.d(
                        Const.LOG_TAG,
                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(
                            it.first
                        )} TO ${epochToString(
                            Calendar.getInstance().timeInMillis
                        )}"
                    )
                } else {
                    diff = it.second!! - it.first
                    val hms = getHMS(diff)
                    Log.d(
                        Const.LOG_TAG,
                        "Session used for: ${hms.first}h ${hms.second}min ${hms.third}s FROM ${epochToString(
                            it.first
                        )} TO ${epochToString(it.second!!)}"
                    )
                }
                totalPerApp += diff

            }
            if (sessions.value.size > 0) {
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