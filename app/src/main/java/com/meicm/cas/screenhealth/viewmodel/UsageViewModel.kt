package com.meicm.cas.screenhealth.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.meicm.cas.screenhealth.persistence.entity.Unlock
import com.meicm.cas.screenhealth.persistence.AppDatabase
import com.meicm.cas.screenhealth.persistence.DataRepository
import com.meicm.cas.screenhealth.persistence.entity.AppCategory
import com.meicm.cas.screenhealth.persistence.entity.AppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DataRepository
    val allUnlocks: LiveData<List<Unlock>>
    val appCategories: LiveData<List<AppCategory>>
    val appSessions: LiveData<List<AppSession>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DataRepository(
            db.unlockDao(),
            db.appCategoryDao(),
            db.appSessionDao()
        )
        allUnlocks = repository.allUnlocks
        appCategories = repository.allAppCategories
        appSessions = repository.allAppSessions
    }

    fun getUnlocks(startTime: Long, endTime: Long, callback: (List<Unlock>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val unlocks = repository.getUnlocks(startTime, endTime)
            callback(unlocks)
        }
    }

    fun getAppSessions(startTime: Long, endTime: Long, callback: (HashMap<String, MutableList<AppSession>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            callback(repository.getAppSessions(startTime, endTime))
        }
    }

    fun getAppSessions(packageName: String, startTime: Long, endTime: Long, callback: (List<AppSession>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            callback(repository.getAppSessions(packageName, startTime, endTime))
        }
    }

    fun getAppCategory(packageName: String, callback: (String?) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        callback(repository.getAppCategory(packageName))
    }

    fun categorizeApplications(appPackages: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        repository.categorizeApplications(appPackages)
    }

    fun insertUnlock(vararg unlock: Unlock) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertUnlocks(unlock.toList())
    }

    fun insertUnlocks(unlocks: List<Unlock>) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertUnlocks(unlocks)
    }

    fun insertUnlocksIfEmpty(unlocks: List<Unlock>) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertUnlockIfEmpty(unlocks)
    }
}