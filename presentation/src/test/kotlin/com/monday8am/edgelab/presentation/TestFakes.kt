package com.monday8am.edgelab.presentation

import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

internal class FakeLocalInferenceEngine : LocalInferenceEngine {
    var initializeCalled = false

    override suspend fun initialize(
        modelConfig: ModelConfiguration,
        modelPath: String,
    ): Result<Unit> {
        initializeCalled = true
        return Result.success(Unit)
    }

    override fun initializeAsFlow(
        modelConfig: ModelConfiguration,
        modelPath: String,
    ): Flow<LocalInferenceEngine> {
        initializeCalled = true
        return flowOf(this)
    }

    override fun setToolsAndResetConversation(tools: List<Any>): Result<Unit> = Result.success(Unit)

    override suspend fun prompt(prompt: String): Result<String> = Result.success("Test response")

    override fun promptStreaming(prompt: String) = flowOf("Hi!")

    var closeSessionCalled = false

    override fun closeSession(): Result<Unit> {
        closeSessionCalled = true
        return Result.success(Unit)
    }
}

internal class FakeModelDownloadManager(
    private val progressSteps: List<Float> = emptyList(),
    private val shouldFail: Boolean = false,
    private val shouldReject: Boolean = false,
    private val modelsStatusFlow: MutableStateFlow<Map<String, ModelDownloadManager.Status>> =
        MutableStateFlow(emptyMap()),
) : ModelDownloadManager {

    var deleteModelCallCount = 0
        private set

    var lastDeletedBundleFilename: String? = null
        private set

    override suspend fun downloadModel(downloadUrl: String, bundleFilename: String): Boolean {
        if (shouldReject) return false
        if (shouldFail) {
            modelsStatusFlow.update {
                it +
                    (bundleFilename to
                        ModelDownloadManager.Status.Failed("Simulated download failure"))
            }
            return true
        }

        progressSteps.forEach { progress ->
            modelsStatusFlow.update {
                it + (bundleFilename to ModelDownloadManager.Status.InProgress(progress))
            }
        }
        modelsStatusFlow.update {
            it +
                (bundleFilename to
                    ModelDownloadManager.Status.Completed(File("/fake/path/$bundleFilename")))
        }
        return true
    }

    override val modelsStatus: Flow<Map<String, ModelDownloadManager.Status>>
        get() = modelsStatusFlow

    fun setDownloadedFilenames(filenames: Set<String>) {
        modelsStatusFlow.update { current ->
            val updated = current.toMutableMap()
            filenames.forEach { filename ->
                updated[filename] =
                    ModelDownloadManager.Status.Completed(File("/fake/path/$filename"))
            }
            updated
        }
    }

    override fun cancelDownload(bundleFilename: String) {}

    override fun getModelPath(bundleFilename: String): String = "/fake/path/$bundleFilename"

    override suspend fun deleteModel(bundleFilename: String): Boolean {
        deleteModelCallCount++
        lastDeletedBundleFilename = bundleFilename
        modelsStatusFlow.update { it - bundleFilename }
        return true
    }

    override fun dispose() {}
}
