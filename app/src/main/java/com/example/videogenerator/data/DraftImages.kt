package com.example.videogenerator.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * App-private copies of photos picked for the create screens. Photo-picker
 * URIs stop working once the app process ends, so a draft that should survive
 * being closed has to point at its own copies.
 */
class DraftImages(private val context: Context) {
    private val dir = File(context.filesDir, "drafts")

    /** Copies anything outside app storage; files the app already owns are used as they are. */
    suspend fun keep(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        dir.mkdirs()
        uris.map { uri ->
            if (isAppFile(uri)) return@map uri
            val file = File(dir, "${UUID.randomUUID()}.img")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "couldn't read the photo" }
                file.outputStream().use { input.copyTo(it) }
            }
            Uri.fromFile(file)
        }
    }

    fun exists(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        return parsed.scheme != "file" || File(requireNotNull(parsed.path)).exists()
    }

    /** Deletes draft copies that no create screen references any more. */
    suspend fun prune(inUse: Set<String>) = withContext(Dispatchers.IO) {
        val keep = inUse.mapNotNull { Uri.parse(it).path }.toSet()
        dir.listFiles().orEmpty().filterNot { it.absolutePath in keep }.forEach { it.delete() }
    }

    private fun isAppFile(uri: Uri) =
        uri.scheme == "file" && uri.path?.startsWith(context.filesDir.absolutePath) == true
}
