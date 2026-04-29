package com.monday8am.edgelab.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val client =
        OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    override suspend fun doWork(): Result {
        return try {
            val url =
                inputData.getString(KEY_URL)
                    ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "URL not provided"))
            val destinationPath =
                inputData.getString(KEY_DESTINATION_PATH)
                    ?: return Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to "Destination path not provided")
                    )

            setForeground(createForegroundInfo(0))

            val destinationFile = File(destinationPath)
            destinationFile.parentFile?.mkdirs()

            downloadFile(url, destinationFile)
            setProgress(workDataOf(KEY_PROGRESS to 100f))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.withTag("DownloadWorker").e(e) { "Download failed" }
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun downloadFile(url: String, destFile: File) {
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }
        inputData.getString(KEY_AUTH_TOKEN)?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) return
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
                        setForeground(createForegroundInfo(progress.toInt()))
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
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var bytesCopied: Long = alreadyCopied
        var lastUpdateProgress = -1f
        var lastUpdateTime = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
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

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Downloading model")
                .setContentText(if (progress < 100) "$progress%" else "Download complete")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, progress == 0)
                .setOngoing(progress < 100)
                .setOnlyAlertOnce(true)
                .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureNotificationChannel() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Model Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { description = "Shows progress while downloading AI models" }
                .also { manager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val KEY_URL = "KEY_URL"
        const val KEY_DESTINATION_PATH = "KEY_DESTINATION_PATH"
        const val KEY_PROGRESS = "KEY_PROGRESS"
        const val KEY_ERROR_MESSAGE = "KEY_ERROR_MESSAGE"
        const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"

        private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BUFFER_SIZE = 64 * 1024
    }
}
