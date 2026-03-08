package com.monday8am.edgelab.agent.jvm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool

private val gemma3n = "/Users/anton/Downloads/gemma3-1b-it-int4.litertlm"
private val qwen3 = "/Users/anton/Downloads/qwen3_0.6b_q8_ekv4096.litertlm"
private val hammer2p1 = "/Users/anton/Downloads/hammer2.1_0.5b_q8_ekv4096.litertlm"

suspend fun main() {
    Engine.setNativeMinLogSeverity(LogSeverity.INFO) // hide log for TUI app

    val engineConfig =
        EngineConfig(
            backend = Backend.CPU(),
            maxNumTokens = 4096,
            modelPath = hammer2p1, // gemma3n
        )

    Engine(engineConfig).use { engine ->
        engine.initialize()

        val conversationConfig =
            ConversationConfig(
                systemInstruction =
                    Contents.of(
                        "You are Qwen, created by Alibaba Cloud. You are a helpful assistant."
                    ),
                tools =
                    listOf(NativeLocationTools()), // Native LiteRT-LM tools with @Tool annotations
                samplerConfig = SamplerConfig(topK = 40, topP = 0.85, temperature = 0.2),
            )

        engine.createConversation(conversationConfig = conversationConfig).use { conversation ->
            while (true) {
                print("\n>>> ")
                conversation.sendMessageAsync(Message.of(readln())).collect { print(it) }
            }
        }
    }
}

private class NativeLocationTools {
    @Tool(
        description =
            "No arguments required. Get the user's current location in latitude and longitude format"
    )
    fun get_location(): String {
        val result = """{"latitude": 40.4168, "longitude": -3.7038}"""
        return result
    }
}
