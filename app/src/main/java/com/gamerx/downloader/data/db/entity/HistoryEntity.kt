package com.gamerx.downloader.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val author: String = "",
    val thumbnail: String = "",
    val duration: Long = 0L,
    val type: DownloadType = DownloadType.Video,
    val format: FormatInfo = FormatInfo(),
    val filePath: String = "",
    val fileSize: Long = 0L,
    val website: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val command: String = "",
)
