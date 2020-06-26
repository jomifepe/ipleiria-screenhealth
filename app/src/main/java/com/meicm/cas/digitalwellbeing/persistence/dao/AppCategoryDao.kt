package com.meicm.cas.digitalwellbeing.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory

@Dao
interface AppCategoryDao {
    @Query("SELECT * FROM Categories")
    fun getAll(): LiveData<List<AppCategory>>

    @Query("SELECT * FROM Categories WHERE package = :packageName")
    fun getAppCategory(packageName: String): AppCategory?

    @Query("SELECT category FROM Categories WHERE package = :packageName")
    fun getCategory(packageName: String): String?

    @Insert
    fun insert(appCategory: AppCategory)

    @Insert
    fun insert(appCategory: List<AppCategory>)

    @Update
    fun update(appCategory: AppCategory)

    @Update
    fun update(appCategories: List<AppCategory>)

    @Delete
    fun delete(appCategory: AppCategory)
}