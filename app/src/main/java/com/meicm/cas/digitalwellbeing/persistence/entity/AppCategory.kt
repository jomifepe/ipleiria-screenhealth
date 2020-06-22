package com.meicm.cas.digitalwellbeing.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Categories")
class AppCategory (
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "package_name") var appPackage: String,
    @ColumnInfo(name = "category") var category: String?
)