package com.meicm.cas.digitalwellbeing.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.persistence.AppDatabase
import com.meicm.cas.digitalwellbeing.DataRepository
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UsageViewModel(application: Application): AndroidViewModel(application) {
    private val repository: DataRepository
    val allUnlocks: LiveData<List<Unlock>>
    val appCategories: LiveData<List<AppCategory>>

    init {
        val unlocksDao = AppDatabase.getDatabase(application).unlockDao()
        val appCategoryDao = AppDatabase.getDatabase(application).appCategoryDao()
        repository = DataRepository(unlocksDao, appCategoryDao)
        allUnlocks = repository.allUnlocks
        appCategories = repository.allAppCategories
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