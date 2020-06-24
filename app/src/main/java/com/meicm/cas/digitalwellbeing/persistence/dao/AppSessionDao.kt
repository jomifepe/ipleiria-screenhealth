package com.meicm.cas.digitalwellbeing.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock

@Dao
interface AppSessionDao {
    @Query("SELECT * FROM AppSessions")
    fun getAll(): LiveData<List<AppSession>>

    @Query("SELECT * FROM AppSessions WHERE start_timestamp >= :startTime AND (end_timestamp <= :endTime OR end_timestamp IS NULL)")
    fun getSessionByRange(startTime: Long, endTime: Long): LiveData<List<AppSession>>

    @Insert
    fun insert(session: AppSession)

    @Insert
    fun insertAll(sessions: List<AppSession>)

    @Delete
    fun delete(session: AppSession)
}