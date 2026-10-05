package com.example.videogenerator.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps saved characters' images in app-private storage. Photo-picker URIs are
 * only readable for a while, so the library holds its own copies.
 */
class CharacterLibrary(private val context: Context) {
    private val root = File(context.filesDir, "characters")

    /** Copies [uris] into the folder for [savedId], replacing anything there. Returns the new file paths. */
    suspend fun storeImages(savedId: Long, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val dir = File(root, savedId.toString())
        // Write to a temp folder first: re-saving a loaded character reads from the folder being replaced.
        val tmp = File(root, "$savedId.tmp").apply { deleteRecursively(); mkdirs() }
        uris.mapIndexed { i, uri ->
            val file = File(tmp, "${i + 1}.img")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Couldn't read image ${i + 1}." }
                file.outputStream().use { input.copyTo(it) }
            }
        }
        dir.deleteRecursively()
        check(tmp.renameTo(dir)) { "Couldn't save the character's images." }
        dir.listFiles().orEmpty().sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }.map { it.absolutePath }
    }

    suspend fun delete(savedId: Long) = withContext(Dispatchers.IO) {
        File(root, savedId.toString()).deleteRecursively()
    }
}
