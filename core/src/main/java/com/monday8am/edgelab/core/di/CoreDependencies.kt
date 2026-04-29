package com.monday8am.edgelab.core.di

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.monday8am.edgelab.agent.core.LocalInferenceEngine
import com.monday8am.edgelab.core.download.ModelDownloadManagerImpl
import com.monday8am.edgelab.core.inference.LiteRTLmInferenceEngineImpl
import com.monday8am.edgelab.core.oauth.HuggingFaceOAuthManager
import com.monday8am.edgelab.core.storage.AuthRepositoryImpl
import com.monday8am.edgelab.core.storage.DataStoreAuthorRepository
import com.monday8am.edgelab.core.route.AssetRouteRepository
import com.monday8am.edgelab.core.route.AssetSegmentRepository
import com.monday8am.edgelab.core.route.AssetWeatherRepository
import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.data.auth.AuthorRepository
import com.monday8am.edgelab.data.huggingface.HuggingFaceApiClient
import com.monday8am.edgelab.data.route.RouteRepository
import com.monday8am.edgelab.data.route.SegmentRepository
import com.monday8am.edgelab.data.route.WeatherRepository
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope

/**
 * Factory object for creating core Android infrastructure services.
 * Provides implementations for inference, download, OAuth, and storage layers.
 */
object CoreDependencies {

    /**
     * Creates a LiteRT-LM inference engine instance.
     */
    fun createInferenceEngine(): LocalInferenceEngine {
        return LiteRTLmInferenceEngineImpl()
    }

    /**
     * Creates a model download manager instance.
     */
    fun createDownloadManager(
        context: Context,
        authRepository: AuthRepository,
        wifiOnly: Boolean = false,
    ): ModelDownloadManager = ModelDownloadManagerImpl(context, authRepository, wifiOnly)

    /**
     * Creates an OAuth manager instance.
     *
     * @param context Android context
     * @param clientId HuggingFace OAuth client ID
     * @param redirectScheme App-specific redirect scheme (e.g., "edgelab", "copilot")
     * @param activityClass The Activity class to return to after OAuth flow
     */
    fun createOAuthManager(
        context: Context,
        clientId: String,
        redirectScheme: String,
        activityClass: Class<out Activity>
    ): HuggingFaceOAuthManager {
        return HuggingFaceOAuthManager(context, clientId, redirectScheme, activityClass)
    }

    /**
     * Creates an auth repository instance.
     */
    fun createAuthRepository(
        context: Context,
        applicationScope: CoroutineScope
    ): AuthRepository {
        return AuthRepositoryImpl(context, applicationScope)
    }

    /**
     * Creates a route repository that loads from bundled assets.
     */
    fun createRouteRepository(context: Context): RouteRepository {
        return AssetRouteRepository(context)
    }

    /**
     * Creates a segment repository that loads from bundled assets.
     */
    fun createSegmentRepository(context: Context): SegmentRepository {
        return AssetSegmentRepository(context)
    }

    /**
     * Creates a weather repository that loads from bundled assets.
     */
    fun createWeatherRepository(context: Context): WeatherRepository {
        return AssetWeatherRepository(context)
    }

    /**
     * Creates an author repository instance.
     */
    fun createAuthorRepository(
        dataStore: DataStore<Preferences>,
        apiClient: HuggingFaceApiClient,
        scope: CoroutineScope,
    ): AuthorRepository {
        return DataStoreAuthorRepository(dataStore, apiClient, scope)
    }
}
