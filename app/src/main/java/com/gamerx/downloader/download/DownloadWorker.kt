package com.gamerx.downloader.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gamerx.downloader.GamerXApp
import com.gamerx.downloader.MainActivity
import com.gamerx.downloader.R
import com.gamerx.downloader.data.model.DownloadStatus
import com.gamerx.downloader.data.repository.DownloadRepository
import com.gamerx.downloader.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val ytDlpManager: YtDlpManager,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val NOTIFICATION_ID_BASE = 1000
        const val NOTIFICATION_COMPLETED_BASE = 2000
        const val NOTIFICATION_ERROR_BASE = 3000

        // Weighted progress: video 0-70%, audio 70-90%, merge/post 90-100%
        private const val WEIGHT_VIDEO = 70
        private const val WEIGHT_AUDIO = 20
        private const val WEIGHT_POST = 10

        fun createWorkRequest(downloadId: Long): OneTimeWorkRequest {
            val data = workDataOf("download_id" to downloadId)
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .addTag("download_$downloadId")
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
        }

        fun createWorkRequest(downloadId: Long, delayMillis: Long): OneTimeWorkRequest {
            val data = workDataOf("download_id" to downloadId)
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .addTag("download_$downloadId")
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong("download_id", -1)
        if (downloadId == -1L) return Result.failure()

        val item = repository.getById(downloadId) ?: return Result.failure()
        val notifId = downloadId.toInt()

        // Set as active
        repository.updateStatus(downloadId, DownloadStatus.Active)

        // Show foreground notification
        setForeground(createForegroundInfo(item.title, 0, notifId))

        // Track current stage and weighted progress
        var currentStage = "Starting"
        var trackIndex = 0              // 0 = first track (video), 1 = second track (audio)
        var maxReportedProgress = 0     // monotonically increasing

        return try {
            // Read settings
            val cookieFile = settingsRepository.cookieFilePath.first().ifBlank { null }
            val sbEnabled = settingsRepository.sponsorBlockEnabled.first()
            val sbCategories = if (sbEnabled) settingsRepository.sponsorBlockCategories.first() else null

            val result = ytDlpManager.download(item, cookieFile, sbCategories) { rawProgress, text, line ->
                if (line != null) {
                    val prevStage = currentStage
                    currentStage = detectStage(line, currentStage)

                    // Detect track switch: "[download] Destination:" means a new file/track
                    if (line.contains("[download] Destination:") && prevStage.contains("Downloading")) {
                        trackIndex++
                    }
                }

                // Compute weighted overall progress
                val weightedProgress = when (currentStage) {
                    "Fetching info" -> 0
                    "Downloading video", "Downloading" -> {
                        // First track: 0–70%
                        (rawProgress * WEIGHT_VIDEO / 100).coerceIn(0, WEIGHT_VIDEO)
                    }
                    "Downloading audio" -> {
                        // Second track: 70–90%
                        WEIGHT_VIDEO + (rawProgress * WEIGHT_AUDIO / 100).coerceIn(0, WEIGHT_AUDIO)
                    }
                    "Merging" -> WEIGHT_VIDEO + WEIGHT_AUDIO + 2  // 92%
                    "Post-processing" -> WEIGHT_VIDEO + WEIGHT_AUDIO + 5  // 95%
                    "Saving" -> 98
                    else -> {
                        // If we haven't classified the stage, use raw with track offset
                        when (trackIndex) {
                            0 -> (rawProgress * WEIGHT_VIDEO / 100).coerceIn(0, WEIGHT_VIDEO)
                            1 -> WEIGHT_VIDEO + (rawProgress * WEIGHT_AUDIO / 100).coerceIn(0, WEIGHT_AUDIO)
                            else -> (WEIGHT_VIDEO + WEIGHT_AUDIO + rawProgress * WEIGHT_POST / 100).coerceIn(0, 100)
                        }
                    }
                }

                // Enforce monotonically increasing progress
                val smoothProgress = maxOf(weightedProgress, maxReportedProgress).coerceIn(0, 100)
                maxReportedProgress = smoothProgress

                // Parse speed and ETA
                val speed = parseSpeed(text)
                val eta = parseEta(text)

                // Update DB
                kotlinx.coroutines.runBlocking {
                    repository.updateProgressFull(downloadId, smoothProgress, text, speed, eta, currentStage)
                }

                // Build notification text
                val notifText = buildString {
                    if (currentStage.isNotBlank() && currentStage != "Starting") {
                        append(currentStage)
                        append(" • ")
                    }
                    if (speed.isNotBlank()) {
                        append(speed)
                        append(" • ")
                    }
                    append("$smoothProgress%")
                }
                updateNotification(item.title, smoothProgress, notifText, notifId)
            }

            result.fold(
                onSuccess = { filePath ->
                    repository.markCompleted(downloadId, filePath)
                    repository.moveToHistory(item.copy(
                        status = DownloadStatus.Completed,
                        filePath = filePath,
                    ))
                    cancelForegroundNotification(notifId)
                    showCompletedNotification(item.title, notifId)
                    Result.success()
                },
                onFailure = { error ->
                    val msg = error.message ?: "Unknown error"
                    Log.e(TAG, "Download failed for ${item.title}: $msg", error)
                    repository.markError(downloadId, msg)
                    cancelForegroundNotification(notifId)
                    showErrorNotification(item.title, msg, notifId)
                    Result.failure(workDataOf("error" to msg))
                }
            )
        } catch (e: Exception) {
            if (isStopped) {
                repository.markCancelled(downloadId)
                cancelForegroundNotification(notifId)
                Result.failure()
            } else {
                val msg = e.message ?: "Unknown error"
                Log.e(TAG, "Download exception for ${item.title}: $msg", e)
                repository.markError(downloadId, msg)
                cancelForegroundNotification(notifId)
                showErrorNotification(item.title, msg, notifId)
                Result.failure(workDataOf("error" to msg))
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Downloading...", 0, 0)
    }

    /**
     * Detect the current download stage from a yt-dlp output line.
     */
    private fun detectStage(line: String, fallback: String): String = when {
        line.contains("[info]") -> "Fetching info"
        line.contains("[download] Destination:") && (line.contains(".f") || line.contains("video", true)) -> "Downloading video"
        line.contains("[download] Destination:") && line.contains("audio", true) -> "Downloading audio"
        line.contains("[download] Destination:") -> "Downloading"
        line.contains("[download]") && line.contains("%") -> fallback  // keep current stage
        line.contains("[Merger]") || line.contains("[Merge") -> "Merging"
        line.contains("[FixupM3u8]") || line.contains("[ExtractAudio]") || line.contains("[EmbedThumbnail]") -> "Post-processing"
        line.contains("[Metadata]") || line.contains("[EmbedSubtitle]") -> "Post-processing"
        line.contains("[MoveFiles]") || line.contains("has already been downloaded") -> "Saving"
        line.contains("[ffmpeg]") -> "Post-processing"
        else -> fallback
    }

    private fun createForegroundInfo(title: String, progress: Int, id: Int): ForegroundInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(getId())

        val notification = NotificationCompat.Builder(context, GamerXApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("Starting download...")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_error, "Cancel", cancelIntent)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID_BASE + id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_BASE + id, notification)
        }
    }

    private fun updateNotification(title: String, progress: Int, progressText: String, id: Int) {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(getId())
        val notification = NotificationCompat.Builder(context, GamerXApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(progressText)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .addAction(R.drawable.ic_error, "Cancel", cancelIntent)
            .setSilent(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + id, notification)
    }

    private fun cancelForegroundNotification(id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_BASE)
        manager.cancel(NOTIFICATION_ID_BASE + id)
    }

    private fun showCompletedNotification(title: String, id: Int) {
        val notification = NotificationCompat.Builder(context, GamerXApp.CHANNEL_COMPLETED)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_COMPLETED_BASE + id, notification)
    }

    private fun showErrorNotification(title: String, error: String, id: Int) {
        val notification = NotificationCompat.Builder(context, GamerXApp.CHANNEL_COMPLETED)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$error"))
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ERROR_BASE + id, notification)
    }

    private fun parseSpeed(text: String): String {
        val speedRegex = Regex("at\\s+([\\d.]+\\s*[KMG]?i?B/s)")
        return speedRegex.find(text)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun parseEta(text: String): String {
        val etaRegex = Regex("ETA\\s+([\\d:]+[smh]?)")
        return etaRegex.find(text)?.groupValues?.getOrNull(1) ?: ""
    }
}
