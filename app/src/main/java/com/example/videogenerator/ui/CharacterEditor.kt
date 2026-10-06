package com.example.videogenerator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.videogenerator.model.Character

/** An image the user can attach to a character: the start frame or a reference image. */
data class CharacterImageOption(val id: Long, val uri: String, val label: String)

/**
 * Guided character creation: 1) pick the images, 2) name it, 3) describe the
 * clothing precisely, plus optional distinguishing features.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterEditorDialog(
    initial: Character,
    isNew: Boolean,
    images: List<CharacterImageOption>,
    /** Second argument: also save to the character library. */
    onSave: (Character, Boolean) -> String?,
    /** When set, a "Save & add another" button saves and starts a fresh character. */
    onCreateAnother: (() -> Character)? = null,
    onDismiss: () -> Unit,
) {
    var c by remember(initial.id) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf(0) }
    // Characters that came from the library stay in sync with it by default.
    var saveToLibrary by remember(initial.id) { mutableStateOf(initial.savedId != null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(if (isNew) "Create a character" else "Edit ${initial.name}", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "The prompt will tell the model to reproduce this person exactly as in the chosen images and " +
                            "to keep their clothing identical in every shot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Step(1, "Which images show this character?")
                    Text(
                        "Pick every upload that shows this person. Several angles (face close-up, full body) give the closest match.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (images.isEmpty()) {
                        Text("Add reference images or a start frame first.", color = MaterialTheme.colorScheme.error)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        images.forEach { img ->
                            val selected = img.id in c.imageIds
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            c = c.copy(imageIds = if (selected) c.imageIds - img.id else c.imageIds + img.id)
                                        },
                                ) {
                                    Thumbnail(img.uri, Modifier.size(96.dp))
                                    if (selected) {
                                        Icon(
                                            Icons.Default.CheckCircle, "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                        )
                                    }
                                }
                                Text(img.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Step(2, "Name the character")
                    OutlinedTextField(
                        value = c.name,
                        onValueChange = { v -> c = c.copy(name = v.filter { it.isLetterOrDigit() || it == '_' }) },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Mara") },
                        supportingText = { Text("Use @${c.name.ifEmpty { "Name" }} in the scene and shots to refer to them.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Step(3, "Describe the clothing as precisely as possible")
                    Text(
                        "Be specific about colour (exact shade), material, fit, length, pattern, logos/text, closures and how " +
                            "each item is worn. Whatever you write here is locked for the whole video.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ClothingField("Top / outerwear", c.top, "e.g. cropped black leather biker jacket, silver zips, worn open over a fitted white ribbed cotton tank top") {
                        c = c.copy(top = it)
                    }
                    ClothingField("Bottoms", c.bottom, "e.g. high-waisted light-wash straight-leg jeans, frayed hem at the ankle, rolled once") {
                        c = c.copy(bottom = it)
                    }
                    ClothingField("Footwear", c.footwear, "e.g. white low-top canvas sneakers with white laces and a gum sole") {
                        c = c.copy(footwear = it)
                    }
                    ClothingField("Accessories & jewellery", c.accessories, "e.g. thin gold hoop earrings, black leather belt with square silver buckle, round tortoiseshell sunglasses on head") {
                        c = c.copy(accessories = it)
                    }
                    ClothingField("Other clothing details", c.otherClothing, "e.g. jacket sleeves pushed to the elbows; tank top tucked in at the front only") {
                        c = c.copy(otherClothing = it)
                    }

                    Text("Optional: distinguishing features", style = MaterialTheme.typography.titleSmall)
                    ClothingField("Features that must never change", c.features, "e.g. small black rose tattoo on left wrist, freckles across the nose, silver nose stud, red lipstick") {
                        c = c.copy(features = it)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { saveToLibrary = !saveToLibrary }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(saveToLibrary, onCheckedChange = { saveToLibrary = it })
                    Text(
                        if (initial.savedId != null) "Update the saved copy in my character library"
                        else "Save to my character library (keeps the images and descriptions)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (savedCount > 0 && error == null) {
                    Text(
                        "$savedCount character${if (savedCount == 1) "" else "s"} added. Fill in the next one, or Cancel when done.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (isNew && onCreateAnother != null) {
                        TextButton(onClick = {
                            error = onSave(c, saveToLibrary)
                            if (error == null) {
                                savedCount++
                                c = onCreateAnother()
                            }
                        }) { Text("Save & add another") }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        error = onSave(c, saveToLibrary)
                        if (error == null) onDismiss()
                    }) { Text(if (isNew) "Create character" else "Save") }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedCard(shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
            Text("$n", Modifier.padding(horizontal = 10.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ClothingField(label: String, value: String, example: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(example, style = MaterialTheme.typography.bodySmall) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}
