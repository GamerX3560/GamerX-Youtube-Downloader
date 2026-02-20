package com.gamerx.downloader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gamerx.downloader.data.db.dao.DownloadDao
import com.gamerx.downloader.data.db.dao.HistoryDao
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.db.entity.HistoryEntity

@Database(
    entities = [
        DownloadEntity::class,
        HistoryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
}
