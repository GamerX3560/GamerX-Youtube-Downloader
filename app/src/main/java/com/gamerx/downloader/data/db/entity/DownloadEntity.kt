package com.gamerx.downloader.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gamerx.downloader.data.model.DownloadStatus
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val author: String = "",
    val thumbnail: String = "",
    val duration: Long = 0L,
    val type: DownloadType = DownloadType.Video,
    val format: FormatInfo = FormatInfo(),
    @ColumnInfo(defaultValue = "mp4")
    val container: String = "mp4",
    val allFormats: List<FormatInfo> = emptyList(),
    val downloadPath: String = "",
    val website: String = "",
    val downloadSize: String = "",
    val status: DownloadStatus = DownloadStatus.Queued,
    @ColumnInfo(defaultValue = "0")
    val progress: Int = 0,
    val progressText: String = "",
    @ColumnInfo(defaultValue = "")
    val speed: String = "",
    @ColumnInfo(defaultValue = "")
    val eta: String = "",
    @ColumnInfo(defaultValue = "")
    val extraCommands: String = "",
    @ColumnInfo(defaultValue = "")
    val customFileName: String = "",
    val saveThumbnail: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val incognito: Boolean = false,
    val playlistUrl: String? = null,
    val playlistIndex: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val scheduledAt: Long = 0L,
    val filePath: String? = null,
    @ColumnInfo(defaultValue = "")
    val stage: String = "",
)
