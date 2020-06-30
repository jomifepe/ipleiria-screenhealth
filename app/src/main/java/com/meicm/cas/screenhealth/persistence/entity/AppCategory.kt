package com.meicm.cas.screenhealth.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Categories")
data class AppCategory (
    @PrimaryKey @ColumnInfo(name = "package") var appPackage: String,
    @ColumnInfo(name = "category") var category: String?
)