package com.example.videogenerator.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.videogenerator.model.SavedCharacter
import java.io.File

/** Tick any number of saved characters to add to this video, or delete them from the library. */
@Composable
fun SavedCharactersDialog(
    saved: List<SavedCharacter>,
    /** Characters already in this video, which can't be added twice. */
    alreadyAdded: Set<Long>,
    onAdd: (List<SavedCharacter>) -> String?,
    onDelete: (SavedCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf<SavedCharacter?>(null) }
    val chosen = saved.filter { it.id in selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved characters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved.isEmpty()) Text("No saved characters yet. Save one from its card or the character editor.")
                else Text("Tick every character you want in this video.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(saved, key = { it.id }) { character ->
                        val inVideo = character.id in alreadyAdded
                        val isSelected = character.id in selected
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .clickable(enabled = !inVideo) {
                                        selected = if (isSelected) selected - character.id else selected + character.id
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected || inVideo,
                                    enabled = !inVideo,
                                    onCheckedChange = {
                                        selected = if (it) selected + character.id else selected - character.id
                                    },
                                )
                                character.imagePaths.firstOrNull()?.let {
                                    Thumbnail(Uri.fromFile(File(it)).toString(), Modifier.size(52.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@${character.name}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (inVideo) "Already in this video"
                                        else "${character.imagePaths.size} image${if (character.imagePaths.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        listOf(character.top, character.bottom, character.footwear).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { confirmDelete = character }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                error = onAdd(chosen)
                if (error == null) onDismiss()
            }) { Text(if (chosen.size > 1) "Add ${chosen.size} characters" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    confirmDelete?.let { character ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${character.name}?") },
            text = { Text("This removes the saved character and its image copies from the app. Characters already in this video stay.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(character)
                    selected = selected - character.id
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
