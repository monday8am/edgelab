package com.monday8am.edgelab.presentation.playground

import com.monday8am.edgelab.agent.playground.PlaygroundBackend
import com.monday8am.edgelab.agent.playground.TurnResult
import com.monday8am.edgelab.agent.playground.TurnToolCall
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.model.ModelRepository
import com.monday8am.edgelab.data.model.RepositoryState
import com.monday8am.edgelab.data.playground.Probe
import com.monday8am.edgelab.data.playground.ProbeRepository
import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * Fake [PlaygroundBackend] for the Playground ViewModel tests — stands in for both the cloud and
 * local legs, since the ViewModel only ever sees this interface.
 *
 * The real backends' behaviour is covered in `:agent`: the cloud tool loop in
 * `CloudPlaygroundBackendTest`, litert-lm handler recording in `LocalPlaygroundBackendTest`.
 */
internal class FakePlaygroundBackend : PlaygroundBackend {
    var initializeCallCount = 0
        private set

    var runCallCount = 0
        private set

    var closeCallCount = 0
        private set

    /** Probes handed to the most recent [run]. */
    var lastProbes: List<Probe> = emptyList()
        private set

    var text: String = "Sure — here is the answer."
    var toolCalls: List<TurnToolCall> = emptyList()
    var initializeShouldFail: Boolean = false
    var runShouldFail: Boolean = false

    override suspend fun initialize(): Result<Unit> {
        initializeCallCount++
        return if (initializeShouldFail) Result.failure(RuntimeException("init boom"))
        else Result.success(Unit)
    }

    override suspend fun run(prompt: String, probes: List<Probe>): Result<TurnResult> {
        runCallCount++
        lastProbes = probes
        return if (runShouldFail) Result.failure(RuntimeException("prompt boom"))
        else Result.success(TurnResult(text = text, toolCalls = toolCalls))
    }

    override fun close() {
        closeCallCount++
    }
}

/** Records which targets the ViewModel asked for, and hands back one backend per target. */
internal class RecordingBackendFactory(
    private val backend: FakePlaygroundBackend = FakePlaygroundBackend()
) : PlaygroundBackendFactory {
    val requestedTargets = mutableListOf<PlaygroundTarget>()

    override fun create(target: PlaygroundTarget): PlaygroundBackend {
        requestedTargets += target
        return backend
    }

    fun backend(): FakePlaygroundBackend = backend
}

internal class FakeProbeRepository(initialProbes: List<Probe> = emptyList()) : ProbeRepository {
    private val flow = MutableStateFlow(initialProbes)

    override fun getProbesAsFlow(): Flow<List<Probe>> = flow

    fun setProbes(probes: List<Probe>) {
        flow.value = probes
    }
}

internal class FakeModelRepository(repositoryModels: List<ModelConfiguration>) : ModelRepository {
    private val storedModels: List<ModelConfiguration> = repositoryModels

    override val models: StateFlow<List<ModelConfiguration>>
        get() = MutableStateFlow(storedModels)

    override val loadingState: StateFlow<RepositoryState>
        get() = MutableStateFlow(RepositoryState.Success(storedModels))

    override fun refreshModels() {}

    override fun findById(modelId: String): ModelConfiguration? = storedModels.find {
        it.modelId == modelId
    }

    override fun getAllModels(): List<ModelConfiguration> = storedModels

    override fun getByFamily(family: String): List<ModelConfiguration> = storedModels.filter {
        it.modelFamily.equals(family, ignoreCase = true)
    }

    override fun updateModel(
        modelId: String,
        updater: (ModelConfiguration) -> ModelConfiguration,
    ) {}
}

internal class FakeModelDownloadManagerForPlayground(
    private val downloadedFilenames: Set<String> = emptySet()
) : ModelDownloadManager {
    private val statusFlow: MutableStateFlow<Map<String, ModelDownloadManager.Status>> =
        MutableStateFlow(
            downloadedFilenames.associateWith {
                ModelDownloadManager.Status.Completed(File("/fake/it"))
            }
        )

    override val modelsStatus: Flow<Map<String, ModelDownloadManager.Status>>
        get() = statusFlow

    override fun getModelPath(bundleFilename: String): String = "/fake/$bundleFilename"

    override suspend fun downloadModel(downloadUrl: String, bundleFilename: String): Boolean = true

    override fun cancelDownload(bundleFilename: String) {}

    override suspend fun deleteModel(bundleFilename: String): Boolean = true

    override fun dispose() {}
}

/** Test helper: builds a [Probe] with the given name/description and an empty object schema. */
internal fun testProbe(
    name: String,
    description: String = "test probe",
    mockResponse: String = "{\"ok\":true}",
): Probe =
    Probe(
        toolSpec =
            ToolSpecification(
                function =
                    FunctionSpec(
                        name = name,
                        description = description,
                        parameters = JsonObject(emptyMap()),
                    )
            ),
        mockResponse = mockResponse,
    )

internal val TEST_MODEL: ModelConfiguration = ModelCatalog.GEMMA3_1B
