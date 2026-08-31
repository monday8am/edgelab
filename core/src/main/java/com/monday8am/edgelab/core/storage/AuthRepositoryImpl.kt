package com.monday8am.edgelab.core.storage

import android.content.Context
import com.monday8am.edgelab.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AuthRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope,
) : AuthRepository {

    private val dataStore =
        androidx.datastore.core.DataStoreFactory.create(
            serializer = AuthTokenSerializer.factory(context),
            produceFile = { context.filesDir.resolve("auth_token.pb") },
        )

    override val authToken: StateFlow<String?> =
        dataStore.data.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    override suspend fun saveToken(token: String) {
        dataStore.updateData { token }
    }

    override suspend fun clearToken() {
        dataStore.updateData { null }
    }
}
