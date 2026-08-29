package com.monday8am.edgelab.presentation.playground

import com.monday8am.edgelab.agent.playground.PlaygroundBackend
import com.monday8am.edgelab.agent.playground.TurnResult
import com.monday8am.edgelab.agent.playground.TurnToolCall
import com.monday8am.edgelab.data.model.ModelCatalog
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.data.model.ModelRepository
import com.monday8am.edgelab.data.model.RepositoryState
import com.monday8am.edgelab.data.playground.ProbeRepository
import com.monday8am.edgelab.data.testing.FunctionSpec
import com.monday8am.edgelab.data.testing.ToolSpecification
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

internal class FakePlaygroundBackend : PlaygroundBackend {
    var initializeCallCount = 0
        private set

    var runCallCount = 0
        private set

    var closeCallCount = 0
        private set

    var lastTools: List<ToolSpecification> = emptyList()
        private set

    var lastMockResponses: Map<String, String> = emptyMap()
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

    override suspend fun run(
        prompt: String,
        tools: List<ToolSpecification>,
        mockResponses: Map<String, String>,
    ): Result<TurnResult> {
        runCallCount++
        lastTools = tools
        lastMockResponses = mockResponses
        return if (runShouldFail) Result.failure(RuntimeException("prompt boom"))
        else Result.success(TurnResult(text = text, toolCalls = toolCalls))
    }

    override fun close() {
        closeCallCount++
    }
}

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

internal class FakeProbeRepository(
    initialTools: List<ToolSpecification> = emptyList(),
    initialMockResponses: Map<String, String> = emptyMap(),
) : ProbeRepository {
    private val toolsFlow = MutableStateFlow(initialTools)
    private val mocksFlow = MutableStateFlow(initialMockResponses)

    override fun getToolsAsFlow(): Flow<List<ToolSpecification>> = toolsFlow

    override fun getMockResponsesAsFlow(): Flow<Map<String, String>> = mocksFlow

    fun setProbes(tools: List<ToolSpecification>, mockResponses: Map<String, String>) {
        toolsFlow.value = tools
        mocksFlow.value = mockResponses
    }
}

internal class FakeModelRepository(private val repositoryModels: List<ModelConfiguration>) :
    ModelRepository {
    private val modelsFlow = MutableStateFlow<List<ModelConfiguration>>(emptyList())

    override val models: StateFlow<List<ModelConfiguration>> = modelsFlow.asStateFlow()

    override val loadingState: StateFlow<RepositoryState> =
        MutableStateFlow(RepositoryState.Success(repositoryModels))

    var refreshCallCount = 0
        private set

    /** Mirrors [com.monday8am.edgelab.data.model.ModelRepositoryImpl]: empty until refreshed. */
    override fun refreshModels() {
        refreshCallCount++
        modelsFlow.value = repositoryModels
    }

    override fun findById(modelId: String): ModelConfiguration? =
        modelsFlow.value.find { it.modelId == modelId }

    override fun getAllModels(): List<ModelConfiguration> = modelsFlow.value

    override fun getByFamily(family: String): List<ModelConfiguration> =
        modelsFlow.value.filter { it.modelFamily.equals(family, ignoreCase = true) }

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

internal fun testTool(name: String, description: String = "test probe"): ToolSpecification =
    ToolSpecification(
        function =
            FunctionSpec(
                name = name,
                description = description,
                parameters = JsonObject(emptyMap()),
            )
    )

internal val TEST_MODEL: ModelConfiguration = ModelCatalog.GEMMA3_1B
