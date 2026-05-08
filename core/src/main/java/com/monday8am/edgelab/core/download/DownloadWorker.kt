package com.monday8am.edgelab.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val bundleFilename by lazy { inputData.getString(KEY_BUNDLE_FILENAME) ?: "" }

    private val notificationId by lazy { deriveNotificationId(bundleFilename) }

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val contentPendingIntent by lazy {
        applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?.let {
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
    }

    private val cancelPendingIntent by lazy {
        val intent = Intent(applicationContext, CancelDownloadReceiver::class.java).apply {
            action = CancelDownloadReceiver.ACTION
            putExtra(CancelDownloadReceiver.EXTRA_BUNDLE_FILENAME, bundleFilename)
            putExtra(CancelDownloadReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        PendingIntent.getBroadcast(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val logger = Logger.withTag("DownloadWorker")

    override suspend fun doWork(): Result {
        val url =
            inputData.getString(KEY_URL)
                ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "URL not provided"))
        val destinationPath =
            inputData.getString(KEY_DESTINATION_PATH)
                ?: return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Destination path not provided")
                )

        return try {
            ensureNotificationChannel()
            setForeground(createForegroundInfo())

            val destinationFile = File(destinationPath)
            destinationFile.parentFile?.mkdirs()

            downloadFile(url, destinationFile)
            setProgress(workDataOf(KEY_PROGRESS to 100f))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Download failed" }
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun downloadFile(url: String, destFile: File) = withContext(Dispatchers.IO) {
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }
        inputData.getString(KEY_AUTH_TOKEN)?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) return@withContext
            if (!response.isSuccessful) {
                val errorCode = response.header("X-Error-Code")
                val errorMessage = response.header("X-Error-Message")
                val detail =
                    when {
                        errorCode == "GatedRepo" ->
                            "Access restricted. Please visit the model page to accept the license agreement."
                        !errorMessage.isNullOrBlank() -> errorMessage
                        else -> "HTTP error ${response.code}"
                    }
                throw IOException(detail)
            }

            val body = response.body
            val isResuming = response.code == 206
            val contentLen = body.contentLength()
            val totalBytes =
                if (isResuming) contentLen + existingBytes
                else contentLen.takeIf { it > 0 } ?: -1L

            body.byteStream().use { input ->
                FileOutputStream(destFile, isResuming).use { output ->
                    copyStreamWithProgress(input, output, totalBytes, existingBytes) { progress ->
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                        notificationManager.notify(notificationId, createNotification(progress.toInt()))
                    }
                }
            }
        }
    }

    private suspend fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        alreadyCopied: Long,
        onProgress: suspend (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var bytesCopied: Long = alreadyCopied
        var lastUpdateProgress = -1f
        var lastUpdateTime = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            if (isStopped) {
                throw CancellationException("Download cancelled")
            }
            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead

            if (totalBytes > 0) {
                val currentTime = System.currentTimeMillis()
                val progress = (bytesCopied * 100).toFloat() / totalBytes.toFloat()

                if (progress - lastUpdateProgress >= 1f || currentTime - lastUpdateTime >= 500L) {
                    onProgress(progress)
                    lastUpdateProgress = progress
                    lastUpdateTime = currentTime
                }
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            notificationId,
            createNotification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createNotification(progress: Int): Notification {
        val isDownloading = progress < 100
        return NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading model")
            .setContentText(if (isDownloading) "$progress%" else "Download complete")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(isDownloading)
            .setOnlyAlertOnce(true)
            .apply {
                if (isDownloading) {
                    setSilent(true)
                    setPriority(NotificationCompat.PRIORITY_LOW)
                    setForegroundServiceBehavior(
                        NotificationCompat.FOREGROUND_SERVICE_DEFERRED,
                    )
                }
                contentPendingIntent?.let { setContentIntent(it) }
                if (isDownloading) {
                    addAction(
                        android.R.drawable.ic_delete,
                        applicationContext.getString(android.R.string.cancel),
                        cancelPendingIntent,
                    )
                }
            }
            .build()
    }

    private fun ensureNotificationChannel() {
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Model Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { description = "Shows progress while downloading AI models" }
                .also { notificationManager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val KEY_URL = "KEY_URL"
        const val KEY_DESTINATION_PATH = "KEY_DESTINATION_PATH"
        const val KEY_PROGRESS = "KEY_PROGRESS"
        const val KEY_ERROR_MESSAGE = "KEY_ERROR_MESSAGE"
        const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"
        const val KEY_BUNDLE_FILENAME = "KEY_BUNDLE_FILENAME"

        private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private const val BASE_NOTIFICATION_ID = 1001
        private const val BUFFER_SIZE = 64 * 1024
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        fun getUniqueWorkName(bundleFilename: String): String = "model-download-$bundleFilename"

        private fun deriveNotificationId(bundleFilename: String): Int {
            return BASE_NOTIFICATION_ID + bundleFilename.hashCode()
        }
    }
}
