package com.monday8am.edgelab.core.inference

import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.data.model.HardwareBackend
import com.monday8am.edgelab.data.model.ModelConfiguration
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private data class LlmModelInstance(
    val engine: Engine,
    var conversation: Conversation,
    val modelConfig: ModelConfiguration,
)

/** LiteRT-LM implementation with native tool calling support. */
class LiteRTLmInferenceEngineImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalInferenceEngine {
    private var currentInstance: LlmModelInstance? = null

    override suspend fun initialize(
        modelConfig: ModelConfiguration,
        modelPath: String,
    ): Result<Unit> =
        withContext(dispatcher) {
            if (currentInstance != null) {
                return@withContext Result.success(Unit)
            }

            try {
                if (!File(modelPath).exists()) {
                    throw IllegalStateException("Model file not found at path: $modelPath")
                }

                val engineConfig =
                    EngineConfig(
                        modelPath = modelPath,
                        backend =
                            if (modelConfig.hardwareAcceleration == HardwareBackend.GPU_SUPPORTED)
                                Backend.GPU()
                            else Backend.CPU(),
                        visionBackend = null, // Text-only inference
                        audioBackend = null, // Text-only inference
                        maxNumTokens = modelConfig.contextLength,
                    )

                val engine = Engine(engineConfig)
                engine.initialize()

                // Configure conversation with tools for native tool calling
                val conversationConfig =
                    ConversationConfig(
                        systemInstruction = Contents.of("You are a helpful assistant."),
                        samplerConfig =
                            SamplerConfig(
                                topK = modelConfig.defaultTopK,
                                topP = modelConfig.defaultTopP.toDouble(),
                                temperature = modelConfig.defaultTemperature.toDouble(),
                            ),
                    )
                val conversation = engine.createConversation(conversationConfig)

                currentInstance =
                    LlmModelInstance(
                        engine = engine,
                        conversation = conversation,
                        modelConfig = modelConfig,
                    )

                Result.success(Unit)
            } catch (e: Throwable) {
                val message = e.message ?: ""
                if (message.contains("Failed to look up signature input tensor")) {
                    Result.failure(
                        IllegalStateException(
                            "Model incompatible: The model signature does not match the expected format. " +
                                "This may be due to an outdated model conversion or an incompatible model format. " +
                                "Please try a different model or check for updates.",
                            e,
                        )
                    )
                } else {
                    Result.failure(e)
                }
            }
        }

    override suspend fun prompt(prompt: String): Result<String> {
        val instance =
            currentInstance
                ?: return Result.failure(
                    IllegalStateException("Inference engine is not initialized.")
                )

        return withContext(dispatcher) {
            runCatching {
                if (prompt.isBlank()) {
                    throw IllegalArgumentException("Prompt cannot be blank")
                }

                Logger.i("LocalInferenceEngine") {
                    "▶️ Starting inference (prompt: ${prompt.length} chars)"
                }
                val startTime = System.currentTimeMillis()

                val userMessage = Contents.of(prompt)
                val response = instance.conversation.sendMessageWithCallback(userMessage)

                val duration = System.currentTimeMillis() - startTime
                val tokensApprox = response.length / 4 // Rough estimate: 1 token ≈ 4 chars
                val tokensPerSec = if (duration > 0) (tokensApprox * 1000.0 / duration) else 0.0

                Logger.i("LocalInferenceEngine") {
                    "✅ Inference complete: ${duration}ms | " +
                        "Response: ${response.length} chars (~$tokensApprox tokens) | " +
                        "Speed: %.2f tokens/sec".format(tokensPerSec)
                }

                response
            }
        }
    }

    override fun promptStreaming(prompt: String): Flow<String> {
        val instance =
            currentInstance
                ?: run {
                    Logger.e("LocalInferenceEngine") { "Inference instance is not available." }
                    return emptyFlow() // Return an empty flow if there's no instance.
                }
        val userMessage = Contents.of(prompt)
        var startTime = 0L

        return instance.conversation
            .sendMessageAsync(userMessage)
            .map { message ->
                message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            }
            .filter { it.isNotEmpty() }
            .onStart {
                startTime = System.currentTimeMillis()
                Logger.i("LocalInferenceEngine") { "Streaming inference started." }
            }
            .onCompletion {
                val duration = System.currentTimeMillis() - startTime
                Logger.i("LocalInferenceEngine") { "✅ Streaming inference complete: ${duration}ms" }
            }
            .flowOn(dispatcher)
    }

    override fun initializeAsFlow(
        modelConfig: ModelConfiguration,
        modelPath: String,
    ): Flow<LocalInferenceEngine> = flow {
        initialize(modelConfig = modelConfig, modelPath = modelPath)
            .onSuccess { emit(this@LiteRTLmInferenceEngineImpl) }
            .onFailure { throw it }
    }

    override fun setToolsAndResetConversation(tools: List<Any>): Result<Unit> {
        val instance =
            currentInstance
                ?: return Result.failure(IllegalStateException("Engine not initialized"))
        return runCatching {
            instance.conversation.close()
            val conversationConfig =
                ConversationConfig(
                    systemInstruction = Contents.of("You are a helpful assistant."),
                    tools = tools, // Use provided test-specific tools
                    samplerConfig =
                        SamplerConfig(
                            topK = instance.modelConfig.defaultTopK,
                            topP = instance.modelConfig.defaultTopP.toDouble(),
                            temperature = instance.modelConfig.defaultTemperature.toDouble(),
                        ),
                )
            Logger.i("LocalInferenceEngine") { "\uD83D\uDCAC Reset conversation with ${tools.size} tools" }
            instance.conversation = instance.engine.createConversation(conversationConfig)
        }
    }

    override fun closeSession(): Result<Unit> {
        val instance = currentInstance ?: return Result.success(Unit) // Nothing to close
        return runCatching {
            currentInstance = null
            instance.conversation.close()
            instance.engine.close()
        }
    }
}

@OptIn(InternalCoroutinesApi::class)
private suspend fun Conversation.sendMessageWithCallback(content: Contents): String =
    suspendCancellableCoroutine { continuation ->
        val resultBuilder = StringBuilder()

        val callback =
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    // Extract text content from message and append it
                    message.contents.contents.filterIsInstance<Content.Text>().forEach { content ->
                        resultBuilder.append(content.text)
                    }
                }

                override fun onDone() {
                    continuation.resume(resultBuilder.toString())
                }

                override fun onError(throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            }

        this.sendMessageAsync(content, callback)

        // Handle coroutine cancellation.
        // LiteRT-LM's Conversation API does not currently offer a direct way to interrupt an
        // ongoing
        // inference task. The coroutine will suspend until the task completes or errors out.
        // Cleanup, like closing the conversation, is handled by the closeSession() method.
        continuation.invokeOnCancellation {
            Logger.i(
                "sendMessageAsync:Coroutine was cancelled, but underlying inference may continue."
            )
        }
    }
