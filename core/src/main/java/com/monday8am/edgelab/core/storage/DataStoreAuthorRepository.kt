package com.monday8am.edgelab.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monday8am.edgelab.data.auth.AuthorInfo
import com.monday8am.edgelab.data.auth.AuthorRepository
import com.monday8am.edgelab.data.huggingface.HuggingFaceApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class DataStoreAuthorRepository(
    private val dataStore: DataStore<Preferences>,
    private val apiClient: HuggingFaceApiClient,
    private val scope: CoroutineScope,
) : AuthorRepository {

    private val authorsKey = stringPreferencesKey("hf_author_list")

    private val _authors = MutableStateFlow(listOf(DEFAULT_AUTHOR))
    override val authors: StateFlow<List<AuthorInfo>> = _authors

    init {
        scope.launch {
            val stored = loadFromDataStore()
            if (stored != null) {
                _authors.value = ensureDefaultPresent(stored)
            }
        }
    }

    override suspend fun addAuthor(name: String): Result<AuthorInfo> {
        val trimmedName = name.trim()
        if (_authors.value.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return Result.success(
                _authors.value.first { it.name.equals(trimmedName, ignoreCase = true) }
            )
        }

        val authorInfo =
            apiClient.fetchAuthorInfo(trimmedName)
                ?: return Result.failure(Exception("Author not found"))

        val updated = _authors.value + authorInfo
        _authors.value = updated
        persistToDataStore(updated)
        return Result.success(authorInfo)
    }

    override suspend fun removeAuthor(name: String) {
        val author = _authors.value.find { it.name.equals(name, ignoreCase = true) } ?: return
        if (author.isDefault) return

        val updated = _authors.value.filter { !it.name.equals(name, ignoreCase = true) }
        _authors.value = updated
        persistToDataStore(updated)
    }

    private suspend fun loadFromDataStore(): List<AuthorInfo>? {
        val jsonString = dataStore.data.map { preferences -> preferences[authorsKey] }.first()

        return if (jsonString != null) {
            try {
                Json.decodeFromString<List<AuthorInfo>>(jsonString)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private suspend fun persistToDataStore(authors: List<AuthorInfo>) {
        val jsonString = Json.encodeToString<List<AuthorInfo>>(authors)
        dataStore.edit { preferences -> preferences[authorsKey] = jsonString }
    }

    private fun ensureDefaultPresent(authors: List<AuthorInfo>): List<AuthorInfo> {
        return if (authors.none { it.isDefault }) {
            listOf(DEFAULT_AUTHOR) + authors
        } else {
            authors
        }
    }

    companion object {
        val DEFAULT_AUTHOR = AuthorInfo(name = "litert-community", isDefault = true)
    }
}
