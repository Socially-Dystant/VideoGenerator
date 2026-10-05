package com.example.videogenerator.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Pulls the final frame out of a generated video so it can start the next one. */
class FrameExtractor(private val context: Context) {
    private val frames = File(context.filesDir, "frames")
    private val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()

    /**
     * Returns a JPEG of the frame [offsetMs] before the end of the video at [url].
     * The very last frame is often motion-blurred or a fade, so a small offset helps.
     */
    suspend fun lastFrame(url: String, name: String, offsetMs: Long = 100): File = withContext(Dispatchers.IO) {
        // Streaming lets the retriever fetch only the end of the file; some CDNs
        // don't allow that, so fall back to downloading the whole video.
        val bitmap = runCatching { grab(offsetMs) { setDataSource(url, emptyMap()) } }.getOrNull()
            ?: downloadThenGrab(url, offsetMs)
        frames.mkdirs()
        val out = File(frames, "$name-${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        prune()
        out
    }

    private fun downloadThenGrab(url: String, offsetMs: Long): Bitmap {
        val tmp = File.createTempFile("video", ".mp4", context.cacheDir)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Couldn't download the video (HTTP ${res.code}).")
                tmp.outputStream().use { out -> res.body!!.byteStream().copyTo(out) }
            }
            return grab(offsetMs) { setDataSource(tmp.absolutePath) }
        } finally {
            tmp.delete()
        }
    }

    private fun grab(offsetMs: Long, source: MediaMetadataRetriever.() -> Unit): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.source()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IOException("Couldn't read the video's length.")
            val atUs = (durationMs - offsetMs).coerceAtLeast(0) * 1000
            return retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                ?: throw IOException("Couldn't read the last frame.")
        } finally {
            retriever.release()
        }
    }

    /** Keeps the newest frames; older ones are no longer needed once used as a start frame. */
    private fun prune(keep: Int = 20) {
        frames.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
    }
}
