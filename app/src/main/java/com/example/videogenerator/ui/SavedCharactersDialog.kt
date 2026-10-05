package com.example.videogenerator.ui

import android.net.Uri
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

/** Pick a saved character to add to this video, or delete one from the library. */
@Composable
fun SavedCharactersDialog(
    saved: List<SavedCharacter>,
    onLoad: (SavedCharacter) -> String?,
    onDelete: (SavedCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedCharacter?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved characters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved.isEmpty()) Text("No saved characters yet. Save one from its card or the character editor.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(saved, key = { it.id }) { character ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                character.imagePaths.firstOrNull()?.let {
                                    Thumbnail(Uri.fromFile(File(it)).toString(), Modifier.size(56.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@${character.name}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${character.imagePaths.size} image${if (character.imagePaths.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        listOf(character.top, character.bottom, character.footwear).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    TextButton(onClick = {
                                        error = onLoad(character)
                                        if (error == null) onDismiss()
                                    }) { Text("Add") }
                                    TextButton(onClick = { confirmDelete = character }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    confirmDelete?.let { character ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${character.name}?") },
            text = { Text("This removes the saved character and its image copies from the app. Characters already in this video stay.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(character)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
