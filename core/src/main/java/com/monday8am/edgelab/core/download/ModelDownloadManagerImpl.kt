package com.monday8am.edgelab.core.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ModelDownloadManagerImpl(
    context: android.content.Context,
    private val authRepository: AuthRepository,
    private val wifiOnly: Boolean = false,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"
    private val downloadMutex = Mutex()

    private val downloadedFilenames = MutableStateFlow(scanDiskFiles())

    override val modelsStatus: Flow<Map<String, ModelDownloadManager.Status>> =
        combine(downloadedFilenames, workManager.getWorkInfosByTagFlow(WORK_TAG)) {
                downloaded,
                workInfos ->
                buildStatusMap(downloaded, workInfos)
            }
            .flowOn(dispatcher)

    init {
        scope.launch {
            workManager.getWorkInfosByTagFlow(WORK_TAG).collect { workInfos ->
                if (workInfos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    refreshDiskState()
                }
            }
        }
    }

    private fun buildStatusMap(
        downloadedFiles: Set<String>,
        workInfos: List<WorkInfo>,
    ): Map<String, ModelDownloadManager.Status> {
        val statusMap = mutableMapOf<String, ModelDownloadManager.Status>()

        downloadedFiles.forEach { filename ->
            statusMap[filename] =
                ModelDownloadManager.Status.Completed(File(getModelPath(filename)))
        }

        workInfos.forEach { info ->
            val filename = info.extractBundleFilename() ?: return@forEach
            if (!info.state.isFinished) {
                statusMap[filename] = info.toStatus()
            }
        }

        return statusMap
    }

    private fun scanDiskFiles(): Set<String> =
        File(modelDestinationPath).listFiles()?.map { it.name }?.toSet() ?: emptySet()

    private fun refreshDiskState() {
        downloadedFilenames.value = scanDiskFiles()
    }

    override fun getModelPath(bundleFilename: String) = "$modelDestinationPath$bundleFilename"

    override suspend fun deleteModel(bundleFilename: String): Boolean =
        withContext(dispatcher) {
            val modelFile = File(getModelPath(bundleFilename))
            val deleted = if (modelFile.exists()) modelFile.delete() else true
            if (deleted) refreshDiskState()
            deleted
        }

    override suspend fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ): Boolean =
        withContext(dispatcher) {
            if (File(getModelPath(bundleFilename)).exists()) return@withContext true
            if (findRunningWork(DownloadWorker.getUniqueWorkName(modelId)) != null) return@withContext true

            downloadMutex.withLock {
                val activeCount =
                    workManager.getWorkInfosByTag(WORK_TAG).get().count { !it.state.isFinished }
                if (activeCount >= MAX_CONCURRENT_DOWNLOADS) return@withContext false

                val token = authRepository.authToken.value
                val workRequest =
                    createDownloadWorkRequest(modelId, downloadUrl, File(getModelPath(bundleFilename)), token)
                workManager.enqueueUniqueWork(DownloadWorker.getUniqueWorkName(modelId), ExistingWorkPolicy.KEEP, workRequest)
            }
            true
        }

    private fun createDownloadWorkRequest(
        modelId: String,
        downloadUrl: String,
        destinationFile: File,
        token: String?,
    ): OneTimeWorkRequest {
        val constraints =
            if (wifiOnly) {
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            } else {
                Constraints.NONE
            }

        return OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to downloadUrl,
                    DownloadWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                    DownloadWorker.KEY_AUTH_TOKEN to token,
                    DownloadWorker.KEY_MODEL_ID to modelId,
                )
            )
            .addTag(WORK_TAG)
            .addTag("$MODEL_ID_PREFIX$modelId")
            .addTag("$BUNDLE_FILENAME_PREFIX${destinationFile.name}")
            .build()
    }

    private suspend fun findRunningWork(workName: String): WorkInfo? =
        withContext(dispatcher) {
            workManager.getWorkInfosForUniqueWork(workName).get().firstOrNull {
                !it.state.isFinished
            }
        }

    override fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork(DownloadWorker.getUniqueWorkName(modelId))
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        private const val WORK_TAG = "model-download"
        private const val MODEL_ID_PREFIX = "model-id:"
        private const val BUNDLE_FILENAME_PREFIX = "bundle-filename:"
        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }
}

private fun WorkInfo.extractBundleFilename(): String? =
    tags.firstOrNull { it.startsWith("bundle-filename:") }?.removePrefix("bundle-filename:")

private fun WorkInfo.toStatus(): ModelDownloadManager.Status =
    when (state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED -> ModelDownloadManager.Status.Pending

        WorkInfo.State.RUNNING -> {
            val progress = progress.getFloat(DownloadWorker.KEY_PROGRESS, 0f)
            ModelDownloadManager.Status.InProgress(progress.coerceIn(0f, 100f))
        }

        WorkInfo.State.SUCCEEDED -> ModelDownloadManager.Status.Completed(File(""))

        WorkInfo.State.FAILED -> {
            val errorMessage =
                outputData.getString(DownloadWorker.KEY_ERROR_MESSAGE)
                    ?: "Download failed due to an unknown error."
            ModelDownloadManager.Status.Failed(errorMessage)
        }

        WorkInfo.State.CANCELLED -> ModelDownloadManager.Status.Cancelled
    }
