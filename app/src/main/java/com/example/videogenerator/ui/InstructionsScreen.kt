package com.example.videogenerator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.videogenerator.model.Instruction

@Composable
fun InstructionsScreen(vm: GeneratorViewModel, modifier: Modifier = Modifier) {
    val instructions by vm.instructions.collectAsState()
    var editing by remember { mutableStateOf<Instruction?>(null) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Permanent instructions are added to the top of every prompt sent to Ofox. " +
                    "Switch one off to skip it without deleting it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(instructions, key = { it.id }) { ins ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ins.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
                        Text(ins.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = ins.enabled, onCheckedChange = { vm.saveInstruction(ins.copy(enabled = it)) })
                    IconButton(onClick = { editing = ins }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { vm.deleteInstruction(ins.id) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
        item {
            Button(onClick = { editing = Instruction(id = NEW_ID, title = "", text = "") }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add instruction")
            }
        }
    }

    editing?.let { current ->
        var title by remember(current.id) { mutableStateOf(current.title) }
        var text by remember(current.id) { mutableStateOf(current.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (current.id == NEW_ID) "New instruction" else "Edit instruction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text("Instruction") },
                        placeholder = { Text("e.g. Cinematic 35mm film look, shallow depth of field, natural skin texture.") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    vm.saveInstruction(current.copy(title = title.trim(), text = text.trim()))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

private const val NEW_ID = -1L
