package com.meicm.cas.digitalwellbeing.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "Snoozes")
data class Snooze(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "timestamp") var timestamp: Long
) {
    constructor(timestamp: Long): this(0, timestamp) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        this.timestamp = cal.timeInMillis
    }
}