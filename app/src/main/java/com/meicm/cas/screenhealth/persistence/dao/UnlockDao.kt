package com.meicm.cas.screenhealth.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.screenhealth.persistence.entity.Unlock

@Dao
interface UnlockDao {
    @Query("SELECT * FROM Unlocks")
    fun getAll(): LiveData<List<Unlock>>

    @Query("SELECT * FROM Unlocks WHERE start_timestamp >= :startTime AND (end_timestamp <= :endTime OR end_timestamp IS NULL)")
    fun getUnlocks(startTime: Long, endTime: Long): List<Unlock>

    @Insert
    fun insert(unlock: Unlock)

    @Insert
    fun insertAll(unlocks: List<Unlock>)

    @Delete
    fun delete(unlock: Unlock)

    @Query("UPDATE Unlocks SET end_timestamp = :timestamp WHERE id = (SELECT MAX(id) FROM Unlocks WHERE start_timestamp IS NOT NULL)")
    fun updateLastUnlockEndTimestamp(timestamp: Long)

    @Query("SELECT * FROM Unlocks ORDER BY id DESC LIMIT 1")
    fun getLastUnlock(): Unlock?
}