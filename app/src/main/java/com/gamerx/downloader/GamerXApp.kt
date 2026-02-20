package com.gamerx.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject

@HiltAndroidApp
class GamerXApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initYtDlp()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed downloads"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(completedChannel)
        }
    }

    private fun initYtDlp() {
        applicationScope.launch {
            try {
                YoutubeDL.getInstance().init(this@GamerXApp)
                FFmpeg.getInstance().init(this@GamerXApp)
                Log.d(TAG, "yt-dlp + FFmpeg initialized")

                // Auto-update yt-dlp (at most once per day)
                val prefs = this@GamerXApp.getSharedPreferences("gamerx_prefs", MODE_PRIVATE)
                val lastUpdate = prefs.getLong("last_ytdlp_update", 0L)
                val oneDayMs: Long = 24L * 60L * 60L * 1000L
                if (System.currentTimeMillis() - lastUpdate > oneDayMs) {
                    try {
                        val status = YoutubeDL.getInstance().updateYoutubeDL(
                            this@GamerXApp,
                            YoutubeDL.UpdateChannel.STABLE
                        )
                        Log.d(TAG, "yt-dlp update result: $status")
                        prefs.edit().putLong("last_ytdlp_update", System.currentTimeMillis()).apply()
                    } catch (e: Exception) {
                        Log.w(TAG, "yt-dlp auto-update failed (non-fatal): ${e.message}")
                    }
                }
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "yt-dlp init failed", e)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "GamerXApp"
        const val CHANNEL_DOWNLOADS = "gx_downloads"
        const val CHANNEL_COMPLETED = "gx_completed"
        lateinit var instance: GamerXApp
            private set
    }
}
