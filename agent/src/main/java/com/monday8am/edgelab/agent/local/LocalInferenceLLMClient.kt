package com.monday8am.edgelab.agent.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/** Bridge LLMClient between Koog's agent framework and local native inference. */
class LocalInferenceLLMClient(
    private val promptExecutor: suspend (String) -> String?,
    private val streamPromptExecutor: ((String) -> Flow<String>)? = null,
) : LLMClient {
    private val logger = Logger.withTag("LiteRTLLMClient")

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val lastUserMessage =
            if (
                prompt.messages.first() is Message.System &&
                    prompt.messages.last() is Message.User &&
                    prompt.messages.size == 2
            ) {
                prompt.messages.joinToString { "${it.content}\n" }
            } else {
                prompt.messages.filterIsInstance<Message.User>().lastOrNull()?.content
                    ?: throw IllegalArgumentException("No user message in prompt")
            }

        logger.d { "Executing prompt with Local Inference:\n$lastUserMessage" }

        val response =
            promptExecutor(lastUserMessage)
                ?: throw IllegalStateException("Local Inference returned null response")

        // Return as Koog message
        return listOf(Message.Assistant(content = response, metaInfo = ResponseMetaInfo.Empty))
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        val executor =
            streamPromptExecutor
                ?: throw UnsupportedOperationException(
                    "Local Inference client streaming is not configured. " +
                        "Provide streamPromptExecutor in constructor."
                )

        val lastUserMessage =
            if (
                prompt.messages.first() is Message.System &&
                    prompt.messages.last() is Message.User &&
                    prompt.messages.size == 2
            ) {
                prompt.messages.joinToString { "${it.content}\n" }
            } else {
                prompt.messages.filterIsInstance<Message.User>().lastOrNull()?.content
                    ?: throw IllegalArgumentException("No user message in prompt")
            }

        logger.d { "Executing streaming prompt with Local Inference:\n$lastUserMessage" }

        return executor(lastUserMessage)
            .onEach { logger.d { it } }
            .map<String, StreamFrame> { chunk -> StreamFrame.TextDelta(text = chunk) }
            .onCompletion { error ->
                logger.d { "Streaming ended: $error" }
                if (error == null) {
                    emit(StreamFrame.End(finishReason = "stop"))
                }
            }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException(
            "Local Inference client does not support moderation. " +
                "Implement content filtering in your application layer if needed."
        )

    override fun llmProvider(): LLMProvider =
        object : LLMProvider("local-inference", "Local Inference") {}

    override fun close() {
        // No resources to clean up - client is stateless
        // Actual LiteRT-LM resources (Engine/Conversation) are managed by LocalInferenceEngine
    }
}
