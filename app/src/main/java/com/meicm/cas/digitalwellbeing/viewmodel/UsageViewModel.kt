package com.meicm.cas.digitalwellbeing.viewmodel

import android.app.Application
import android.database.Observable
import androidx.databinding.ObservableMap
import androidx.lifecycle.*
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.DataRepository
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsageViewModel(application: Application): AndroidViewModel(application) {
    private val repository: DataRepository
    val allUnlocks: LiveData<List<Unlock>>
    val appCategories: LiveData<List<AppCategory>>

//    private val appSessionRange = MutableLiveData<Pair<Long, Long>>()
//    val appSessions: LiveData<HashMap<String, MutableList<AppSession>>> = Transformations.switchMap(appSessionRange) {
//            range -> repository.getAppSession(range.first, range.second)
//    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DataRepository(
            db.unlockDao(),
            db.appCategoryDao(),
            db.appSessionDao()
        )
        allUnlocks = repository.allUnlocks
        appCategories = repository.allAppCategories
    }

//    fun getAppSession(startTime: Long, endTime: Long) = viewModelScope.launch(Dispatchers.IO) {
//        repository.getAppSession(startTime, endTime)
//    }

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