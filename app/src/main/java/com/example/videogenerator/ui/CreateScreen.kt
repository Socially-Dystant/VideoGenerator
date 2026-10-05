package com.example.videogenerator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.videogenerator.model.AspectRatio
import com.example.videogenerator.model.Character
import com.example.videogenerator.model.DURATIONS
import com.example.videogenerator.model.Resolution
import com.example.videogenerator.model.Shot
import com.example.videogenerator.model.ShotType
import com.example.videogenerator.prompt.BuiltPrompt
import com.example.videogenerator.prompt.PromptBuilder
import java.util.Locale

@Composable
fun CreateScreen(vm: GeneratorViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val prompt by vm.prompt.collectAsState()
    val context = LocalContext.current

    val pickStart = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setStartFrame(uri)
    }
    val pickRefs = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) vm.addReferences(uris)
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val tagNames = prompt.tagMap.keys.toList()
    var editingCharacter by remember { mutableStateOf<Character?>(null) }

    val characterImages = buildList {
        state.startFrame?.let { add(CharacterImageOption(Character.START_FRAME_IMAGE_ID, it, "@start")) }
        state.references.forEach { add(CharacterImageOption(it.id, it.uri, "@${it.tag}")) }
    }

    if (state.offerCharacter) {
        AlertDialog(
            onDismissRequest = vm::dismissCharacterOffer,
            icon = { Icon(Icons.Default.Person, null) },
            title = { Text("Create a character?") },
            text = {
                Text(
                    "Turn the person in these images into a named character. The prompt will lock their appearance " +
                        "and clothing so they look the same in every shot.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissCharacterOffer()
                    editingCharacter = vm.newCharacter()
                }) { Text("Create character") }
            },
            dismissButton = { TextButton(onClick = vm::dismissCharacterOffer) { Text("Not now") } },
        )
    }

    editingCharacter?.let { character ->
        CharacterEditorDialog(
            initial = character,
            isNew = state.characters.none { it.id == character.id },
            images = characterImages,
            onSave = vm::saveCharacter,
            onDismiss = { editingCharacter = null },
        )
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Start frame -------------------------------------------------------------
        Section("Start frame", "Optional. The video opens on this image. Tag: @${PromptBuilder.START_FRAME_TAG}") {
            if (state.startFrame == null) {
                OutlinedButton(onClick = { pickStart.launch(imagesOnly) }) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Choose start image")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumbnail(state.startFrame!!, Modifier.size(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("@start → ${prompt.tagMap["@start"]}", style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = { pickStart.launch(imagesOnly) }) { Text("Replace") }
                            TextButton(onClick = { vm.setStartFrame(null) }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        // --- Reference images ----------------------------------------------------------
        Section(
            "Reference images (${state.references.size}/${state.maxReferences})",
            "Characters, objects, outfits or locations to keep consistent. Mention them in your text with their @tag.",
        ) {
            state.references.forEach { ref ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                        Thumbnail(ref.uri, Modifier.size(80.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = ref.tag,
                                onValueChange = { v -> vm.updateReference(ref.id) { it.copy(tag = v.filter { c -> c.isLetterOrDigit() || c == '_' }) } },
                                label = { Text("Tag") },
                                prefix = { Text("@") },
                                supportingText = { prompt.tagMap["@${ref.tag.lowercase()}"]?.let { Text("→ $it") } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = ref.note,
                                onValueChange = { v -> vm.updateReference(ref.id) { it.copy(note = v) } },
                                label = { Text("What is this? (optional)") },
                                placeholder = { Text("e.g. the woman in the red coat") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(onClick = { vm.removeReference(ref.id) }) { Icon(Icons.Default.Close, "Remove reference") }
                    }
                }
            }
            if (state.references.size < state.maxReferences) {
                OutlinedButton(onClick = { pickRefs.launch(imagesOnly) }) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add reference images")
                }
            }
        }

        // --- Characters ---------------------------------------------------------------------
        Section(
            "Characters",
            "Lock a person's look and outfit across every shot. Refer to them as @Name in the scene and shots.",
        ) {
            state.characters.forEach { character ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        characterImages.firstOrNull { it.id in character.imageIds }?.let { Thumbnail(it.uri, Modifier.size(56.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("@${character.name}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                prompt.tagMap["@${character.tag}"].orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                listOf(character.top, character.bottom, character.footwear).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { editingCharacter = character }) { Icon(Icons.Default.Edit, "Edit character") }
                        IconButton(onClick = { vm.removeCharacter(character.id) }) { Icon(Icons.Default.Delete, "Delete character") }
                    }
                }
            }
            OutlinedButton(
                onClick = { editingCharacter = vm.newCharacter() },
                enabled = characterImages.isNotEmpty(),
            ) {
                Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text("Create character")
            }
            if (characterImages.isEmpty()) {
                Text("Add a start frame or reference images first.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- Scene ---------------------------------------------------------------------------
        Section("Scene description", "Setting, characters, mood, lighting, style.") {
            OutlinedTextField(
                value = state.scene,
                onValueChange = { v -> vm.edit { it.copy(scene = v) } },
                placeholder = { Text("e.g. A rainy neon-lit street at night. @ref1 waits under an umbrella…") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            TagChips(tagNames) { tag -> vm.edit { it.copy(scene = appendTag(it.scene, tag)) } }
        }

        // --- Shots ------------------------------------------------------------------------------
        Section("Shots", "Describe each shot in order. Leave length empty to split the remaining time evenly.") {
            state.shots.forEachIndexed { index, shot ->
                ShotCard(
                    index = index,
                    shot = shot,
                    total = state.shots.size,
                    tagNames = tagNames,
                    onChange = { transform -> vm.updateShot(shot.id, transform) },
                    onMove = { vm.moveShot(shot.id, it) },
                    onDelete = { vm.removeShot(shot.id) },
                )
            }
            OutlinedButton(onClick = vm::addShot) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add shot")
            }
        }

        // --- Output settings --------------------------------------------------------------------
        Section("Video settings") {
            Label("Resolution")
            ChipRow(Resolution.entries, state.resolution, { it.apiValue }) { r -> vm.edit { it.copy(resolution = r) } }
            Label("Duration")
            ChipRow(DURATIONS, state.duration, { "${it}s" }) { d -> vm.edit { it.copy(duration = d) } }
            Label("Aspect ratio")
            ChipRow(AspectRatio.entries, state.aspectRatio, { it.label }) { a -> vm.edit { it.copy(aspectRatio = a) } }
            SwitchRow("Generate audio", state.generateAudio) { v -> vm.edit { it.copy(generateAudio = v) } }
            OutlinedTextField(
                value = state.seed,
                onValueChange = { v -> vm.edit { it.copy(seed = v.filter(Char::isDigit)) } },
                label = { Text("Seed (optional, for repeatable results)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // --- Content ---------------------------------------------------------------------------------
        Section("Content") {
            SwitchRow("NSFW / explicit content", state.nsfw) { v -> vm.edit { it.copy(nsfw = v, adultsConfirmed = false) } }
            if (state.nsfw) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "NSFW requests are sent to SpicyAPI (Wan 3.0 Prime). Anything involving minors is " +
                                "always blocked — in text and in images; uploaded images are age-checked by the server " +
                                "first. SpicyAPI video links expire after ~20 minutes, so save videos you want to keep.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.clickable { vm.edit { it.copy(adultsConfirmed = !it.adultsConfirmed) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(state.adultsConfirmed, onCheckedChange = { v -> vm.edit { it.copy(adultsConfirmed = v) } })
                            Text(
                                "Everyone depicted is an adult (18+), and any real person shown has consented to this use of their likeness.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        // --- Prompt preview ----------------------------------------------------------------------------
        PromptPreview(prompt, context)

        // --- Generate --------------------------------------------------------------------------------------
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = vm::generate,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("Uploading…")
            } else {
                Text("Generate · ${state.resolution.apiValue} · ${state.duration}s · ≈ ${formatUsd(state.estimatedCostUsd)}")
            }
        }
        Text(
            "Cost estimate uses the listed Wan 3.0 Prime rates (Ofox, or SpicyAPI for NSFW); the final charge is shown in History.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShotCard(
    index: Int,
    shot: Shot,
    total: Int,
    tagNames: List<String>,
    onChange: ((Shot) -> Shot) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var typeMenu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shot ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                IconButton(onClick = { onMove(1) }, enabled = index < total - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete shot") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Framing: ${shot.type.label}")
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ShotType.entries.forEach { type ->
                            DropdownMenuItem(text = { Text(type.label) }, onClick = { onChange { it.copy(type = type) }; typeMenu = false })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = shot.seconds?.toString().orEmpty(),
                    onValueChange = { v -> onChange { it.copy(seconds = v.filter(Char::isDigit).take(2).toIntOrNull()) } },
                    label = { Text("Secs") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(88.dp),
                )
            }
            OutlinedTextField(
                value = shot.description,
                onValueChange = { v -> onChange { it.copy(description = v) } },
                placeholder = { Text("Action, camera movement, dialogue…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            TagChips(tagNames) { tag -> onChange { it.copy(description = appendTag(it.description, tag)) } }
        }
    }
}

@Composable
private fun PromptPreview(prompt: BuiltPrompt, context: Context) {
    Section("Prompt preview", "Exactly what is sent to Ofox. @tags are replaced with the model's image labels.") {
        if (prompt.tagMap.isNotEmpty()) {
            Text(
                prompt.tagMap.entries.joinToString("   ") { "${it.key} → ${it.value}" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    highlightTags(prompt.text, prompt.tagMap.values.toSet(), MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
        prompt.warnings.forEach { warning ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${prompt.text.length} characters", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("prompt", prompt.text))
                Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        }
    }
}

private fun highlightTags(text: String, labels: Set<String>, color: androidx.compose.ui.graphics.Color): AnnotatedString {
    val imageLabels = labels.filter { it != "the first frame" }
    if (imageLabels.isEmpty()) return AnnotatedString(text)
    val regex = Regex(imageLabels.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) + "(?!\\d)" })
    return buildAnnotatedString {
        var last = 0
        regex.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(m.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

private fun appendTag(text: String, tag: String): String =
    if (text.isEmpty() || text.endsWith(" ") || text.endsWith("\n")) "$text$tag " else "$text $tag "

fun formatUsd(amount: Double): String = String.format(Locale.US, "$%.2f", amount)

@Composable
fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<String>, onInsert: (String) -> Unit) {
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            OutlinedButton(
                onClick = { onInsert(tag) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) { Text(tag, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun Thumbnail(uri: String, modifier: Modifier) {
    AsyncImage(
        model = Uri.parse(uri),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    )
}
