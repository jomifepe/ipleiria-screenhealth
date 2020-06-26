package com.meicm.cas.digitalwellbeing.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Categories")
data class AppCategory (
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "cid") val id: Int,
    @ColumnInfo(name = "package") var appPackage: String,
    @ColumnInfo(name = "category") var category: String?
)