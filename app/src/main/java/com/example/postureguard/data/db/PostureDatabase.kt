package com.example.postureguard.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.postureguard.data.dao.CalibrationDao
import com.example.postureguard.data.dao.PostureEventDao
import com.example.postureguard.data.dao.SessionDao
import com.example.postureguard.data.dao.SessionSplitDao
import com.example.postureguard.data.entity.CalibrationEntity
import com.example.postureguard.data.entity.PostureEventEntity
import com.example.postureguard.data.entity.SessionDayEntity
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.data.entity.SessionHourEntity

@Database(
    entities = [
        CalibrationEntity::class,
        SessionEntity::class,
        SessionDayEntity::class,
        SessionHourEntity::class,
        PostureEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PostureDatabase : RoomDatabase() {
    abstract fun calibrationDao(): CalibrationDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionSplitDao(): SessionSplitDao
    abstract fun postureEventDao(): PostureEventDao

    companion object {
        @Volatile
        private var instance: PostureDatabase? = null

        fun get(context: Context): PostureDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PostureDatabase::class.java,
                "posture_guard.db",
            ).build().also { instance = it }
        }
    }
}
