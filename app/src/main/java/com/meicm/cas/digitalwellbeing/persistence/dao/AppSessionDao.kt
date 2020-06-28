package com.meicm.cas.digitalwellbeing.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSession
import com.meicm.cas.digitalwellbeing.persistence.entity.AppSessionWithCategory

@Dao
interface AppSessionDao {
    @Query("SELECT * FROM AppSessions")
    fun getAll(): LiveData<List<AppSession>>

    @Query("SELECT * FROM AppSessions WHERE start_timestamp >= :startTime AND (end_timestamp <= :endTime OR end_timestamp IS NULL)")
    fun getSessionByRange(startTime: Long, endTime: Long): List<AppSession>

    @Query("SELECT * FROM AppSessions WHERE package_name = :packageName AND start_timestamp >= :startTime AND (end_timestamp <= :endTime OR end_timestamp IS NULL)")
    fun getSessionByRange(packageName: String, startTime: Long, endTime: Long): List<AppSession>

    @Query("SELECT * FROM AppSessions INNER JOIN Categories ON AppSessions.package_name = Categories.package WHERE AppSessions.start_timestamp >= :startTime AND AppSessions.end_timestamp <= :endTime")
    fun getSessionWithCategory(startTime: Long, endTime: Long): List<AppSessionWithCategory>

    @Query("SELECT * FROM AppSessions WHERE end_timestamp IS NULL ORDER BY start_timestamp")
    fun getOpenSessions(): List<AppSession>

    @Query("SELECT * FROM AppSessions ORDER BY start_timestamp DESC LIMIT 1")
    fun getLastSession(): AppSession?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(session: AppSession)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(sessions: List<AppSession>)

    @Update
    fun updateSessions(sessions: List<AppSession>)

    @Delete
    fun delete(session: AppSession)
}