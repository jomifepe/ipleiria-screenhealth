package com.meicm.cas.screenhealth.persistence

import androidx.lifecycle.LiveData
import com.meicm.cas.screenhealth.persistence.dao.AppCategoryDao
import com.meicm.cas.screenhealth.persistence.dao.AppSessionDao
import com.meicm.cas.screenhealth.persistence.entity.Unlock
import com.meicm.cas.screenhealth.persistence.dao.UnlockDao
import com.meicm.cas.screenhealth.persistence.entity.AppCategory
import com.meicm.cas.screenhealth.persistence.entity.AppSession
import com.meicm.cas.screenhealth.remote.GooglePlayCategory
import com.meicm.cas.screenhealth.remote.GooglePlayService
import com.meicm.cas.screenhealth.util.compareTimestampsDateEqual
import java.lang.Exception

class DataRepository(
    private val unlocksDao: UnlockDao,
    private val appCategoryDao: AppCategoryDao,
    private val appSessionDao: AppSessionDao
) {
    private val googlePlayService: GooglePlayService = GooglePlayService.create()
    val allUnlocks: LiveData<List<Unlock>> = unlocksDao.getAll()
    val allAppCategories: LiveData<List<AppCategory>> = appCategoryDao.getAll()
    val allAppSessions: LiveData<List<AppSession>> = appSessionDao.getAll()

    suspend fun categorizeApplications(appPackages: List<String>) {
        val categories: List<String>
        for (pkg in appPackages) {
            try {
                val element: GooglePlayCategory? = googlePlayService.getAppPage(pkg)
                val appCategory = AppCategory(pkg, element?.category)
                if (appCategoryDao.getAppCategory(pkg) == null) {
                    appCategoryDao.insert(appCategory)
                }
            } catch (ex: Exception) {
                appCategoryDao.insert(AppCategory(pkg, null))
//                Log.d(Const.LOG_TAG, "Failed to get category for app $pkg, ${ex.message}")
            }
        }
    }

    fun getAppSessions(packageName: String, startTime: Long, endTime: Long): List<AppSession> {
        val sessions = appSessionDao.getSessionByRange(packageName, startTime, endTime).toMutableList()
        sessions.forEach { if (it.endTimestamp == null) it.endTimestamp = System.currentTimeMillis() }
        return sessions
    }

    fun getAppSessions(
        startTime: Long,
        endTime: Long
    ): HashMap<String, MutableList<AppSession>> {

        var sessions = appSessionDao.getSessionByRange(startTime, endTime)
        if (!compareTimestampsDateEqual(startTime, System.currentTimeMillis())) {
            sessions = sessions.filter { it.endTimestamp != null }
        }

        val packageSessions = HashMap<String, MutableList<AppSession>>()

        val currentTimeMillis = System.currentTimeMillis()
        for (session in sessions) {
            if (!packageSessions.containsKey(session.appPackage)) {
                packageSessions[session.appPackage] = mutableListOf()
            }
            if (session.endTimestamp == null) {
                session.endTimestamp = currentTimeMillis
            }
            packageSessions[session.appPackage]?.add(session)
        }

        return packageSessions
    }

    fun getUnlocks(startTime: Long, endTime: Long): List<Unlock> {
        val unlocks = unlocksDao.getUnlocks(startTime, endTime)
        if (!compareTimestampsDateEqual(startTime, System.currentTimeMillis())) {
            return unlocks.filter { it.endTimestamp != null }
        }
        return unlocks
    }

    fun insertUnlock(unlock: Unlock) {
        unlocksDao.insert(unlock)
    }

    fun insertUnlocks(unlocks: List<Unlock>) {
        unlocksDao.insertAll(unlocks)
    }

    fun insertUnlockIfEmpty(unlocks: List<Unlock>) {
        if (unlocksDao.getLastUnlock() != null) return
        unlocksDao.insertAll(unlocks)
    }

    fun getAppCategory(packageName: String): String? {
        return appCategoryDao.getCategory(packageName)
    }
}