package com.monday8am.edgelab.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monday8am.edgelab.data.testing.LocalTestDataSource
import com.monday8am.edgelab.data.testing.TestCaseDefinition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class DataStoreTestDataSource(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : LocalTestDataSource {

    private val testsKey = stringPreferencesKey("test_definitions")

    override suspend fun getTests(): List<TestCaseDefinition>? {
        val jsonString = dataStore.data.map { preferences -> preferences[testsKey] }.first()

        return if (jsonString != null) {
            try {
                json.decodeFromString<List<TestCaseDefinition>>(jsonString)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    override suspend fun saveTests(tests: List<TestCaseDefinition>) {
        val jsonString = json.encodeToString(tests)
        dataStore.edit { preferences -> preferences[testsKey] = jsonString }
    }
}
