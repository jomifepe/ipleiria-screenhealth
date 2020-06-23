package com.meicm.cas.digitalwellbeing.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory

@Dao
interface AppCategoryDao {
    @Query("SELECT * FROM Categories")
    fun getAll(): LiveData<List<AppCategory>>

    @Query("SELECT * FROM Categories WHERE package_name = :packageName")
    fun getAppCategory(packageName: String): AppCategory?

    @Query("SELECT category FROM Categories WHERE package_name = :packageName")
    fun getCategory(packageName: String): String?

    @Insert
    fun insert(appCategory: AppCategory)

    @Update
    fun update(appCategory: AppCategory)

    @Delete
    fun delete(appCategory: AppCategory)
}