package com.monday8am.edgelab.presentation.modelselector

import java.io.File
import kotlinx.coroutines.flow.Flow

interface ModelDownloadManager {
    sealed interface Status {
        data object NotStarted : Status

        data object Pending : Status

        data class InProgress(val progress: Float?) : Status // progress typically 0-100

        data class Completed(val file: File) : Status

        data class Failed(val message: String) : Status

        data object Cancelled : Status
    }

    /**
     * Flow of all model statuses keyed by bundle filename. Combines disk state with active
     * WorkManager jobs.
     */
    val modelsStatus: Flow<Map<String, Status>>

    /** Returns the absolute path for a model bundle. */
    fun getModelPath(bundleFilename: String): String

    /**
     * Starts downloading a model. Status updates come through [modelsStatus]. If the model already
     * exists on disk, returns immediately.
     */
    /** Returns false if the concurrent download limit has been reached. */
    suspend fun downloadModel(modelId: String, downloadUrl: String, bundleFilename: String): Boolean

    /** Cancels all active downloads. */
    fun cancelDownload(modelId: String)

    /**
     * Deletes a downloaded model from disk.
     *
     * @return true if deletion succeeded or file didn't exist
     */
    suspend fun deleteModel(bundleFilename: String): Boolean

    /** Releases resources. Should be called when the manager is no longer needed. */
    fun dispose()
}
