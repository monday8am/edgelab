package com.monday8am.edgelab.data.testing

import co.touchlab.kermit.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class TestRepositoryImpl(
    private val remoteUrl: String,
    private val localTestDataSource: LocalTestDataSource,
    private val bundledRepository: TestRepository = AssetsTestRepository(),
    private val json: Json,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TestRepository {

    private val logger = Logger.withTag("TestRepositoryImpl")

    override suspend fun getTests(): List<TestCaseDefinition> {
        return localTestDataSource.getTests()
            ?: fetchNetworkTests().getOrElse { bundledRepository.getTests() }
    }

    override fun getTestsAsFlow(): Flow<List<TestCaseDefinition>> = flow {
        // 1. Emit cached tests immediately if available
        val cachedTests = localTestDataSource.getTests()
        cachedTests?.takeIf { it.isNotEmpty() }?.let { emit(it) }

        // 2. Fetch and emit fresh tests from network
        fetchNetworkTests()
            .onSuccess { networkTests -> saveAndEmitIfChanged(networkTests, cachedTests) }
            .onFailure { error ->
                logger.e(error) { "Failed to refresh tests from remote URL" }
                handleFallback(cachedTests)
            }
    }
        .flowOn(dispatcher)

    private suspend fun FlowCollector<List<TestCaseDefinition>>.saveAndEmitIfChanged(
        networkTests: List<TestCaseDefinition>,
        cachedTests: List<TestCaseDefinition>?,
    ) {
        when {
            networkTests.isEmpty() -> {
                logger.w { "Network fetch returned empty test list" }
                handleFallback(cachedTests)
            }
            networkTests != cachedTests -> {
                logger.d { "Successfully loaded ${networkTests.size} tests from remote" }
                localTestDataSource.saveTests(networkTests)
                emit(networkTests)
            }
            else -> {
                logger.d { "Remote tests identical to cached tests, skipping duplicate emission" }
            }
        }
    }

    private suspend fun FlowCollector<List<TestCaseDefinition>>.handleFallback(
        cachedTests: List<TestCaseDefinition>?
    ) {
        if (cachedTests.isNullOrEmpty()) {
            logger.w { "No cached tests available, using bundled fallback" }
            emit(bundledRepository.getTests())
        }
    }

    private suspend fun fetchNetworkTests(): Result<List<TestCaseDefinition>> =
        try {
            Result.success(fetchRemoteTests())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun fetchRemoteTests(): List<TestCaseDefinition> {
        val request = Request.Builder().url(remoteUrl).build()
        return executeRequest(request) { response ->
            val body = response.body.string()
            json.decodeFromString<TestSuiteDefinition>(body).tests
        }
    }

    private suspend fun <T> executeRequest(request: Request, parser: (Response) -> T): T {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) return
                        response.use {
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("Unexpected code $response")
                                )
                                return
                            }
                            try {
                                continuation.resume(parser(response))
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            )
        }
    }
}
