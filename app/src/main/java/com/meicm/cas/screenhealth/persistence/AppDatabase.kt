package com.meicm.cas.screenhealth.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.meicm.cas.screenhealth.persistence.dao.AppCategoryDao
import com.meicm.cas.screenhealth.persistence.dao.AppSessionDao
import com.meicm.cas.screenhealth.persistence.entity.Unlock
import com.meicm.cas.screenhealth.persistence.dao.UnlockDao
import com.meicm.cas.screenhealth.persistence.entity.AppCategory
import com.meicm.cas.screenhealth.persistence.entity.AppSession
import com.meicm.cas.screenhealth.util.Const

@Database(
    entities = [Unlock::class, AppCategory::class, AppSession::class],
    version = 1,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
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
    abstract fun appSessionDao(): AppSessionDao
}