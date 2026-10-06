package com.example.videogenerator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import com.example.videogenerator.prompt.BuiltPrompt

/**
 * Reference images and Characters sections, shared by Create Video and Create
 * Image. Everything acts on the given [ws] so the two screens stay independent.
 */
@Composable
fun ReferenceAndCharacterSections(
    vm: GeneratorViewModel,
    ws: Workspace,
    state: CreateState,
    prompt: BuiltPrompt,
    /** "shot" for video, "image" for images, used in help text. */
    noun: String,
) {
    val pickRefs = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) vm.addReferences(uris, ws)
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    var editingCharacter by remember { mutableStateOf<Character?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    val savedCharacters by vm.savedCharacters.collectAsState()
    val libraryMessage by vm.libraryMessage.collectAsState()

    val characterImages = buildList {
        state.startFrame?.let { add(CharacterImageOption(Character.START_FRAME_IMAGE_ID, it, "@start")) }
        state.references.forEach { add(CharacterImageOption(it.id, it.uri, "@${it.tag}")) }
    }

    if (state.offerCharacter) {
        AlertDialog(
            onDismissRequest = { vm.dismissCharacterOffer(ws) },
            icon = { Icon(Icons.Default.Person, null) },
            title = { Text("Create a character?") },
            text = {
                Text(
                    "Turn the person in these images into a named character. The prompt will lock their appearance " +
                        "and clothing so they look the same in every $noun.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissCharacterOffer(ws)
                    editingCharacter = vm.newCharacter()
                }) { Text("Create character") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissCharacterOffer(ws) }) { Text("Not now") } },
        )
    }

    editingCharacter?.let { character ->
        CharacterEditorDialog(
            initial = character,
            isNew = state.characters.none { it.id == character.id },
            images = characterImages,
            onSave = { c, toLibrary -> vm.saveCharacter(c, toLibrary, ws) },
            onCreateAnother = vm::newCharacter,
            onDismiss = { editingCharacter = null },
        )
    }

    if (showLibrary) {
        SavedCharactersDialog(
            saved = savedCharacters,
            alreadyAdded = state.characters.mapNotNull { it.savedId }.toSet(),
            onAdd = { vm.loadSavedCharacters(it, ws) },
            onDelete = { vm.deleteSavedCharacter(it) },
            onDismiss = { showLibrary = false },
        )
    }

    Section(
        "Reference images (${state.references.size}/${state.maxReferences})",
        "People, objects, outfits or places to match. Mention them in your text with their @tag.",
    ) {
        state.references.forEach { ref ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                    Thumbnail(ref.uri, Modifier.size(80.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = ref.tag,
                            onValueChange = { v -> vm.updateReference(ref.id, ws) { it.copy(tag = v.filter { c -> c.isLetterOrDigit() || c == '_' }) } },
                            label = { Text("Tag") },
                            prefix = { Text("@") },
                            supportingText = { prompt.tagMap["@${ref.tag.lowercase()}"]?.let { Text("→ $it") } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = ref.note,
                            onValueChange = { v -> vm.updateReference(ref.id, ws) { it.copy(note = v) } },
                            label = { Text("What is this? (optional)") },
                            placeholder = { Text("e.g. the woman in the red coat") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(onClick = { vm.removeReference(ref.id, ws) }) { Icon(Icons.Default.Close, "Remove reference") }
                }
            }
        }
        if (state.references.size < state.maxReferences) {
            OutlinedButton(onClick = { pickRefs.launch(imagesOnly) }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add reference images")
            }
        }
    }

    Section(
        "Characters",
        "Lock a person's look and outfit in every $noun. Refer to them as @Name in your text.",
    ) {
        state.characters.forEach { character ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    characterImages.firstOrNull { it.id in character.imageIds }?.let { Thumbnail(it.uri, Modifier.size(56.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("@${character.name}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            prompt.characterImages[character.tag].orEmpty().joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            listOf(character.top, character.bottom, character.footwear).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.saveToLibrary(character, ws) }) {
                        if (character.savedId != null) {
                            Icon(Icons.Default.Favorite, "Update saved character", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.FavoriteBorder, "Save character to library")
                        }
                    }
                    IconButton(onClick = { editingCharacter = character }) { Icon(Icons.Default.Edit, "Edit character") }
                    IconButton(onClick = { vm.removeCharacter(character.id, ws) }) { Icon(Icons.Default.Delete, "Delete character") }
                }
            }
        }
        libraryMessage?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::dismissLibraryMessage) { Text("OK") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { editingCharacter = vm.newCharacter() },
                enabled = characterImages.isNotEmpty(),
            ) {
                Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text("Create", maxLines = 1)
            }
            OutlinedButton(onClick = { showLibrary = true }) {
                Icon(Icons.Default.Favorite, null); Spacer(Modifier.width(6.dp))
                Text("Saved (${savedCharacters.size})", maxLines = 1)
            }
        }
        if (characterImages.isEmpty()) {
            Text(
                "Add reference images to create a character, or add one from Saved.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
