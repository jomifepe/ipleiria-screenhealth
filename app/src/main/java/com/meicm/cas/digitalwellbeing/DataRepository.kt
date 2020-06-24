package com.meicm.cas.digitalwellbeing

import android.se.omapi.Session
import android.util.Log
import androidx.lifecycle.LiveData
import com.meicm.cas.digitalwellbeing.persistence.dao.AppCategoryDao
import com.meicm.cas.digitalwellbeing.persistence.dao.AppSessionDao
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.persistence.dao.UnlockDao
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.remote.GooglePlayCategory
import com.meicm.cas.digitalwellbeing.remote.GooglePlayService
import com.meicm.cas.digitalwellbeing.util.Const
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
                val appCategory = AppCategory(0, pkg, element?.category)
                if (appCategoryDao.getAppCategory(pkg) == null) {
                    appCategoryDao.insert(appCategory)
                }
            } catch (ex: Exception) {
                appCategoryDao.insert(AppCategory(0, pkg, null))
//                Log.d(Const.LOG_TAG, "Failed to get category for app $pkg, ${ex.message}")
            }
        }
    }

    suspend fun getAppSessions(startTime: Long, endTime: Long): HashMap<String, MutableList<AppSession>> {
        val sessions = appSessionDao.getSessionByRange(startTime, endTime)

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

    suspend fun insertUnlock(unlock: Unlock) {
        unlocksDao.insert(unlock)
    }

    suspend fun insertUnlocks(unlocks: List<Unlock>) {
        unlocksDao.insertAll(unlocks)
    }

    suspend fun insertUnlockIfEmpty(unlocks: List<Unlock>) {
        if (unlocksDao.getLastUnlock() != null) return
        unlocksDao.insertAll(unlocks)
    }
}