package com.gamerx.downloader.data.db

import androidx.room.TypeConverter
import com.gamerx.downloader.data.model.DownloadStatus
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class Converters {

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        try { DownloadStatus.valueOf(value) } catch (e: Exception) { DownloadStatus.Queued }

    @TypeConverter
    fun fromDownloadType(value: DownloadType): String = value.name

    @TypeConverter
    fun toDownloadType(value: String): DownloadType =
        try { DownloadType.valueOf(value) } catch (e: Exception) { DownloadType.Video }

    @TypeConverter
    fun fromFormatInfo(value: FormatInfo): String = json.encodeToString(value)

    @TypeConverter
    fun toFormatInfo(value: String): FormatInfo =
        try { json.decodeFromString<FormatInfo>(value) } catch (e: Exception) { FormatInfo() }

    @TypeConverter
    fun fromFormatList(value: List<FormatInfo>): String = json.encodeToString(value)

    @TypeConverter
    fun toFormatList(value: String): List<FormatInfo> =
        try { json.decodeFromString<List<FormatInfo>>(value) } catch (e: Exception) { emptyList() }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        try { json.decodeFromString<List<String>>(value) } catch (e: Exception) { emptyList() }
}
