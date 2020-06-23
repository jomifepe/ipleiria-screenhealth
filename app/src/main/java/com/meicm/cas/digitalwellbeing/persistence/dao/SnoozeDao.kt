package com.meicm.cas.digitalwellbeing.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.digitalwellbeing.persistence.entity.Snooze
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock

@Dao
interface SnoozeDao {
    @Query("SELECT * FROM Snoozes")
    fun getAll(): LiveData<List<Snooze>>

    @Insert
    fun insert(snooze: Snooze)

    @Insert
    fun insertAll(snoozes: List<Snooze>)

    @Delete
    fun delete(snooze: Snooze)

    @Query("SELECT * FROM Snoozes WHERE timestamp = :timestamp")
    fun getSnooze(timestamp: Long): Snooze?
}