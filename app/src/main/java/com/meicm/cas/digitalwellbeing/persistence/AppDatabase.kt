package com.meicm.cas.digitalwellbeing.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.meicm.cas.digitalwellbeing.persistence.dao.AppCategoryDao
import com.meicm.cas.digitalwellbeing.persistence.dao.SnoozeDao
import com.meicm.cas.digitalwellbeing.persistence.entity.Unlock
import com.meicm.cas.digitalwellbeing.persistence.dao.UnlockDao
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import com.meicm.cas.digitalwellbeing.persistence.entity.Snooze
import com.meicm.cas.digitalwellbeing.util.Const

@Database(entities = arrayOf(
    Unlock::class,
    AppCategory::class,
    Snooze::class
), version = 1, exportSchema = false)

abstract class AppDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Const.DATABASE_NAME
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }

    abstract fun unlockDao(): UnlockDao
    abstract fun appCategoryDao(): AppCategoryDao
    abstract fun snoozeDao(): SnoozeDao
}