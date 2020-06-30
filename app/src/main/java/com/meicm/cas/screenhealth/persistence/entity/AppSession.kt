package com.meicm.cas.screenhealth.persistence.entity

import androidx.room.*

@Entity(tableName = "AppSessions")
data class AppSession (
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "package_name") var appPackage: String,
    @ColumnInfo(name = "start_timestamp") var startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp") var endTimestamp: Long?
)