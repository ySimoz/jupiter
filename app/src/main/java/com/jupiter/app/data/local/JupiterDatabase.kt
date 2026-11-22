package com.jupiter.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MediaHistoryEntity::class, NetworkStreamEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class JupiterDatabase : RoomDatabase() {
    abstract fun mediaHistoryDao(): MediaHistoryDao
    abstract fun networkStreamDao(): NetworkStreamDao
}
