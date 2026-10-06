package com.example.videogenerator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class TaskResponse(
    val id: String = "",
    val status: String = "",
    val videoUrl: String? = null,
    /** Why [videoUrl] is missing on a finished job (still preparing, expired, deleted). */
    val videoNote: String? = null,
    val error: String? = null,
    val costUsd: Double? = null,
    val billedSeconds: Double? = null,
    /** "ofox" or "spicy". */
    val provider: String? = null,
    val model: String? = null,
    val prompt: String? = null,
    val resolution: String? = null,
    val duration: Double? = null,
    /** ISO-8601. */
    val createdAt: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class RecentResponse(
    val spicy: List<TaskResponse> = emptyList(),
    val ofox: List<TaskResponse> = emptyList(),
    /** False when Ofox has no list endpoint and only known job ids can be refreshed. */
    val ofoxListed: Boolean = false,
    val notes: List<String> = emptyList(),
)

@Serializable
private data class ErrorResponse(val error: String? = null)

@Serializable
data class GenerateRequest(
    val prompt: String,
    val nsfw: Boolean,
    val adultsConfirmed: Boolean,
    val resolution: String,
    val duration: Int,
    val aspectRatio: String,
    val generateAudio: Boolean,
    val seed: Long? = null,
)

@Serializable
data class EraseResult(
    val id: String,
    val erased: Boolean = false,
    /** The provider keeps the video; only the app's History entry can go. */
    val localOnly: Boolean = false,
    val message: String? = null,
)

@Serializable
data class EraseResponse(val results: List<EraseResult> = emptyList())

@Serializable
data class EraseAllSpicyResponse(
    val purged: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
)

@Serializable
private data class EraseRequest(val ids: List<String>)

/** One provider's balance in USD, or why it couldn't be read. */
@Serializable
data class ProviderBalance(
    val available: Double? = null,
    /** SpicyAPI only: reserved by jobs still running. */
    val held: Double? = null,
    /** Ofox only: lifetime spend. */
    val used: Double? = null,
    val total: Double? = null,
    val currency: String = "USD",
    val error: String? = null,
)

@Serializable
data class Balances(val ofox: ProviderBalance = ProviderBalance(), val spicy: ProviderBalance = ProviderBalance())

class ApiException(message: String) : IOException(message)

/** Talks to our Render server, which holds the Ofox key. */
class ApiClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // NSFW submits run the age check and upload every image to SpicyAPI before the
        // job exists; a short timeout made the app drop jobs that were actually created.
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun submit(
        baseUrl: String,
        token: String,
        request: GenerateRequest,
        startFrame: Uri?,
        references: List<Uri>,
    ): TaskResponse = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("payload", json.encodeToString(GenerateRequest.serializer(), request))
        startFrame?.let { body.addFormDataPart("start_frame", "start.jpg", compress(it).toRequestBody(JPEG)) }
        references.forEachIndexed { i, uri ->
            body.addFormDataPart("references", "ref$i.jpg", compress(uri).toRequestBody(JPEG))
        }
        execute(Request.Builder().url("$baseUrl/api/videos").post(body.build()), token)
    }

    suspend fun status(baseUrl: String, token: String, id: String): TaskResponse = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$baseUrl/api/videos/$id").get(), token)
    }

    suspend fun recent(baseUrl: String, token: String, limit: Int): RecentResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/videos/recent?limit=$limit")
            .header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
                throw ApiException(msg ?: httpError(res.code, text))
            }
            json.decodeFromString(RecentResponse.serializer(), text)
        }
    }

    suspend fun erase(baseUrl: String, token: String, ids: List<String>): EraseResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(EraseRequest.serializer(), EraseRequest(ids)).toRequestBody(JSON)
        decode(Request.Builder().url("$baseUrl/api/videos/erase").post(body), token, EraseResponse.serializer())
    }

    suspend fun eraseAllSpicy(baseUrl: String, token: String): EraseAllSpicyResponse = withContext(Dispatchers.IO) {
        decode(
            Request.Builder().url("$baseUrl/api/videos/erase-all-spicy").post(ByteArray(0).toRequestBody(JSON)),
            token,
            EraseAllSpicyResponse.serializer(),
        )
    }

    suspend fun balances(baseUrl: String, token: String): Balances = withContext(Dispatchers.IO) {
        decode(Request.Builder().url("$baseUrl/api/balance").get(), token, Balances.serializer())
    }

    private fun <T> decode(builder: Request.Builder, token: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val request = builder.header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
                throw ApiException(msg ?: httpError(res.code, text))
            }
            return json.decodeFromString(serializer, text)
        }
    }

    suspend fun cancel(baseUrl: String, token: String, id: String) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$baseUrl/api/videos/$id").delete(), token)
    }

    private fun execute(builder: Request.Builder, token: String): TaskResponse {
        val request = builder.header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
                throw ApiException(msg ?: httpError(res.code, text))
            }
            return runCatching { json.decodeFromString(TaskResponse.serializer(), text) }.getOrElse { TaskResponse() }
        }
    }

    /** Downscales to at most 2048px on the long side and re-encodes as JPEG (also strips EXIF). */
    private fun compress(uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }

    /** Express answers unknown routes with "Cannot GET …": the server predates this app version. */
    private fun httpError(code: Int, body: String): String =
        if (code == 404 && body.contains("Cannot ")) {
            "The server is out of date. Push the latest code to GitHub so Render redeploys it."
        } else {
            "Server returned HTTP $code"
        }

    private companion object {
        const val MAX_SIDE = 2048
        val JPEG = "image/jpeg".toMediaType()
        val JSON = "application/json".toMediaType()
    }
}
