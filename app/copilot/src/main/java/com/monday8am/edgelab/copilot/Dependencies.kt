package com.monday8am.edgelab.copilot

import android.content.Context
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.core.di.CoreDependencies
import com.monday8am.edgelab.core.oauth.HuggingFaceOAuthManager
import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.data.model.ModelRepository
import com.monday8am.edgelab.data.model.ModelRepositoryImpl
import com.monday8am.edgelab.data.route.RouteRepository
import com.monday8am.edgelab.data.route.SegmentRepository
import com.monday8am.edgelab.data.route.WeatherRepository
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object Dependencies {
    lateinit var appContext: Context

    val applicationScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // OAuth manager for HuggingFace authentication
    val oAuthManager: HuggingFaceOAuthManager by lazy {
        CoreDependencies.createOAuthManager(
            context = appContext,
            clientId = BuildConfig.HF_CLIENT_ID,
            redirectScheme = "copilot",
            activityClass = MainActivity::class.java,
        )
    }

    // Reuse core infrastructure
    val modelDownloadManager: ModelDownloadManager by lazy {
        CoreDependencies.createDownloadManager(appContext, authRepository)
    }

    val authRepository: AuthRepository by lazy {
        CoreDependencies.createAuthRepository(appContext, applicationScope)
    }

    val inferenceEngine: LocalInferenceEngine by lazy {
        CoreDependencies.createInferenceEngine()
    }

    val routeRepository: RouteRepository by lazy {
        CoreDependencies.createRouteRepository(appContext)
    }

    val segmentRepository: SegmentRepository by lazy {
        CoreDependencies.createSegmentRepository(appContext)
    }

    val weatherRepository: WeatherRepository by lazy {
        CoreDependencies.createWeatherRepository(appContext)
    }

    // For onboarding, we need a simple model repository
    // We'll use a minimal catalog for copilot (just the models we need)
    val modelRepository: ModelRepository by lazy {
        // For now, use all models from the catalog
        // TODO: Create CopilotModelCatalogProvider with only FunctionGemma 2B + Gemma 2 550M
        ModelRepositoryImpl(
            object : com.monday8am.edgelab.data.model.ModelCatalogProvider {
                override fun getModels() = kotlinx.coroutines.flow.flowOf(
                    com.monday8am.edgelab.data.model.ModelCatalog.ALL_MODELS
                )
            }
        )
    }
}
