package com.meicm.cas.screenhealth.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Unlocks")
data class Unlock(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "start_timestamp") var startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp") var endTimestamp: Long?
)