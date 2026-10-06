package com.example.videogenerator.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.videogenerator.model.Character
import com.example.videogenerator.model.SavedCharacter
import java.io.File

/** Being created or edited on this screen: the character plus every photo shown in the editor. */
private data class Draft(val character: Character, val images: List<CharacterImageOption>)

/**
 * The character library on its own: create characters from your photos, edit
 * or delete them. They can then be added to any image or video from "Saved".
 */
@Composable
fun CharactersScreen(vm: GeneratorViewModel, modifier: Modifier = Modifier) {
    val saved by vm.savedCharacters.collectAsState()
    val message by vm.libraryMessage.collectAsState()
    var draft by remember { mutableStateOf<Draft?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedCharacter?>(null) }
    // Editor images need ids that can't clash with the start frame's -1.
    var nextImageId by remember { mutableStateOf(-100L) }

    fun options(uris: List<Uri>): List<CharacterImageOption> = uris.mapIndexed { i, uri ->
        CharacterImageOption(nextImageId - i, uri.toString(), "Photo")
    }.also { nextImageId -= uris.size }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val current = draft
        draft = if (current == null) {
            val images = options(uris)
            Draft(vm.newCharacter().copy(imageIds = images.map { it.id }), images)
        } else {
            current.copy(images = (current.images + options(uris)).take(9))
        }
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    draft?.let { d ->
        CharacterEditorDialog(
            initial = d.character,
            isNew = d.character.savedId == null,
            images = d.images.mapIndexed { i, img -> img.copy(label = "Photo ${i + 1}") },
            onSave = { c, _ -> vm.saveLibraryCharacter(c, d.images) },
            showLibraryOption = false,
            onAddImages = { pickPhotos.launch(imagesOnly) },
            onDismiss = { draft = null },
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("This removes the saved character and its photo copies from the app.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSavedCharacter(c)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Characters keep a person's look and outfit identical across images and videos. Create them here, " +
                    "then add them from \"Saved\" on the Create Image or Create Video screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(onClick = { pickPhotos.launch(imagesOnly) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text("New character from photos")
            }
        }
        message?.let { msg ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::dismissLibraryMessage) { Text("OK") }
                }
            }
        }
        if (saved.isEmpty()) {
            item { Text("No characters yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(saved, key = { it.id }) { c ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("@${c.name}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(c.top, c.bottom, c.footwear, c.accessories).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            val images = c.imagePaths.map { Uri.fromFile(File(it)) }.let(::options)
                            draft = Draft(
                                Character(
                                    id = vm.newCharacter().id,
                                    name = c.name,
                                    imageIds = images.map { it.id },
                                    top = c.top,
                                    bottom = c.bottom,
                                    footwear = c.footwear,
                                    accessories = c.accessories,
                                    otherClothing = c.otherClothing,
                                    features = c.features,
                                    savedId = c.id,
                                ),
                                images,
                            )
                        }) { Icon(Icons.Default.Edit, "Edit ${c.name}") }
                        IconButton(onClick = { confirmDelete = c }) { Icon(Icons.Default.Delete, "Delete ${c.name}") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(c.imagePaths) { path -> Thumbnail(Uri.fromFile(File(path)).toString(), Modifier.size(64.dp)) }
                    }
                }
            }
        }
    }
}
