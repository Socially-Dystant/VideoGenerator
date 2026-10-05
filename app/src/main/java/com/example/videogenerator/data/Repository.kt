package com.example.videogenerator.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.videogenerator.model.AppSettings
import com.example.videogenerator.model.Instruction
import com.example.videogenerator.model.Job
import com.example.videogenerator.model.TagStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "videogenerator")

/** Persists settings, permanent instructions and job history. */
class Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val APP_TOKEN = stringPreferencesKey("app_token")
        val TAG_STYLE = stringPreferencesKey("tag_style")
        val INSTRUCTIONS = stringPreferencesKey("instructions")
        val JOBS = stringPreferencesKey("jobs")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.SERVER_URL].orEmpty(),
            appToken = p[Keys.APP_TOKEN].orEmpty(),
            tagStyle = p[Keys.TAG_STYLE]?.let { runCatching { TagStyle.valueOf(it) }.getOrNull() } ?: TagStyle.WAN,
        )
    }

    val instructions: Flow<List<Instruction>> = context.dataStore.data.map { decodeList(it, Keys.INSTRUCTIONS, Instruction.serializer()) }

    val jobs: Flow<List<Job>> = context.dataStore.data.map { decodeList(it, Keys.JOBS, Job.serializer()) }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit {
            it[Keys.SERVER_URL] = settings.serverUrl.trim().trimEnd('/')
            it[Keys.APP_TOKEN] = settings.appToken.trim()
            it[Keys.TAG_STYLE] = settings.tagStyle.name
        }
    }

    suspend fun updateInstructions(transform: (List<Instruction>) -> List<Instruction>) {
        context.dataStore.edit {
            val current = decodeList(it, Keys.INSTRUCTIONS, Instruction.serializer())
            it[Keys.INSTRUCTIONS] = json.encodeToString(ListSerializer(Instruction.serializer()), transform(current))
        }
    }

    suspend fun updateJobs(transform: (List<Job>) -> List<Job>) {
        context.dataStore.edit {
            val current = decodeList(it, Keys.JOBS, Job.serializer())
            it[Keys.JOBS] = json.encodeToString(ListSerializer(Job.serializer()), transform(current).take(MAX_JOBS))
        }
    }

    private fun <T> decodeList(prefs: Preferences, key: Preferences.Key<String>, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        prefs[key]?.let { runCatching { json.decodeFromString(ListSerializer(serializer), it) }.getOrNull() } ?: emptyList()

    private companion object {
        const val MAX_JOBS = 100
    }
}
