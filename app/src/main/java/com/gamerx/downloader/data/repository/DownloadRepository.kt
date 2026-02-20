package com.gamerx.downloader.data.repository

import com.gamerx.downloader.data.db.dao.DownloadDao
import com.gamerx.downloader.data.db.dao.HistoryDao
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.db.entity.HistoryEntity
import com.gamerx.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
) {
    fun getActiveDownloads(): Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()
    fun getQueuedDownloads(): Flow<List<DownloadEntity>> = downloadDao.getQueuedDownloads()
    fun getCompletedDownloads(): Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()
    fun getCancelledDownloads(): Flow<List<DownloadEntity>> = downloadDao.getCancelledDownloads()
    fun getErroredDownloads(): Flow<List<DownloadEntity>> = downloadDao.getErroredDownloads()
    fun getActiveCount(): Flow<Int> = downloadDao.getActiveCount()

    suspend fun getById(id: Long): DownloadEntity? = downloadDao.getById(id)
    fun getByIdFlow(id: Long): Flow<DownloadEntity?> = downloadDao.getByIdFlow(id)

    suspend fun insert(item: DownloadEntity): Long = downloadDao.insert(item)
    suspend fun insertAll(items: List<DownloadEntity>): List<Long> = downloadDao.insertAll(items)
    suspend fun update(item: DownloadEntity) = downloadDao.update(item)
    suspend fun updateStatus(id: Long, status: DownloadStatus) = downloadDao.updateStatus(id, status)
    suspend fun updateProgress(id: Long, progress: Int, text: String) = downloadDao.updateProgress(id, progress, text)
    suspend fun updateProgressWithSpeed(id: Long, progress: Int, text: String, speed: String, eta: String) = downloadDao.updateProgressWithSpeed(id, progress, text, speed, eta)
    suspend fun updateProgressFull(id: Long, progress: Int, text: String, speed: String, eta: String, stage: String) = downloadDao.updateProgressFull(id, progress, text, speed, eta, stage)
    suspend fun markCompleted(id: Long, path: String?) = downloadDao.markCompleted(path = path, id = id)
    suspend fun markError(id: Long, error: String) = downloadDao.markError(id, error)
    suspend fun markCancelled(id: Long) = downloadDao.markCancelled(id)
    suspend fun deleteById(id: Long) = downloadDao.deleteById(id)
    suspend fun deleteByStatus(status: DownloadStatus) = downloadDao.deleteByStatus(status)
    suspend fun getNextQueued(): DownloadEntity? = downloadDao.getNextQueued()

    // Move a completed download to history
    suspend fun moveToHistory(download: DownloadEntity) {
        val historyItem = HistoryEntity(
            url = download.url,
            title = download.title,
            author = download.author,
            thumbnail = download.thumbnail,
            duration = download.duration,
            type = download.type,
            format = download.format,
            filePath = download.filePath ?: "",
            website = download.website,
        )
        historyDao.insert(historyItem)
    }

    // History
    fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getAll()
    fun searchHistory(query: String): Flow<List<HistoryEntity>> = historyDao.search(query)
    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)
    suspend fun clearHistory() = historyDao.deleteAll()
}
