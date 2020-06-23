package com.meicm.cas.digitalwellbeing.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock

@Dao
interface UnlockDao {
    @Query("SELECT * FROM Unlocks")
    fun getAll(): LiveData<List<Unlock>>

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