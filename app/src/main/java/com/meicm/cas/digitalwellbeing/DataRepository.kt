package com.meicm.cas.digitalwellbeing

import android.util.Log
import androidx.lifecycle.LiveData
import com.meicm.cas.digitalwellbeing.persistence.dao.AppCategoryDao
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.persistence.dao.UnlockDao
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.remote.GooglePlayCategory
import com.meicm.cas.digitalwellbeing.remote.GooglePlayService
import com.meicm.cas.digitalwellbeing.util.Const
import java.lang.Exception

class DataRepository(
    private val unlocksDao: UnlockDao,
    private val appCategoryDao: AppCategoryDao
) {
    private val googlePlayService: GooglePlayService = GooglePlayService.create()
    val allUnlocks: LiveData<List<Unlock>> = unlocksDao.getAll()
    val allAppCategories: LiveData<List<AppCategory>> = appCategoryDao.getAll()

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
                Log.d(Const.LOG_TAG, "Failed to get category for app $pkg, ${ex.message}")
            }
        }
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