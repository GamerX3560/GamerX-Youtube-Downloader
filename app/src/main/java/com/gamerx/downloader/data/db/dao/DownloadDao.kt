package com.gamerx.downloader.data.db.dao

import androidx.room.*
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'Active' ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'Queued' ORDER BY createdAt ASC")
    fun getQueuedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'Completed' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'Cancelled' ORDER BY createdAt DESC")
    fun getCancelledDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'Error' ORDER BY createdAt DESC")
    fun getErroredDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'Paused' ORDER BY createdAt DESC")
    fun getPausedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<DownloadEntity?>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    fun getCountByStatus(status: DownloadStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'Active'")
    fun getActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadEntity>): List<Long>

    @Update
    suspend fun update(item: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET progress = :progress, progressText = :progressText WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, progressText: String)

    @Query("UPDATE downloads SET progress = :progress, progressText = :progressText, speed = :speed, eta = :eta WHERE id = :id")
    suspend fun updateProgressWithSpeed(id: Long, progress: Int, progressText: String, speed: String, eta: String)

    @Query("UPDATE downloads SET progress = :progress, progressText = :progressText, speed = :speed, eta = :eta, stage = :stage WHERE id = :id")
    suspend fun updateProgressFull(id: Long, progress: Int, progressText: String, speed: String, eta: String, stage: String)

    @Query("UPDATE downloads SET status = 'Completed', completedAt = :time, filePath = :path WHERE id = :id")
    suspend fun markCompleted(id: Long, time: Long = System.currentTimeMillis(), path: String?)

    @Query("UPDATE downloads SET status = 'Error', errorMessage = :error WHERE id = :id")
    suspend fun markError(id: Long, error: String)

    @Query("UPDATE downloads SET status = 'Cancelled' WHERE id = :id")
    suspend fun markCancelled(id: Long)

    @Delete
    suspend fun delete(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT * FROM downloads WHERE status IN ('Queued', 'Paused') ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextQueued(): DownloadEntity?
}
