package com.example.videogenerator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Create Image: references and characters, a prompt, and Wan 2.7 Pro settings.
 * The server sends prompt-only requests to text-to-image and anything with
 * reference images to the edit model.
 */
@Composable
fun ImageScreen(vm: GeneratorViewModel, modifier: Modifier = Modifier) {
    val state by vm.imageState.collectAsState()
    val prompt by vm.imagePrompt.collectAsState()
    val context = LocalContext.current
    val ws = Workspace.IMAGE
    val hasReferences = state.references.isNotEmpty()

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReferenceAndCharacterSections(vm, ws, state, prompt, noun = "image")

        Section("Prompt", "Describe the image: subject, setting, composition, style and lighting.") {
            TaggedTextField(
                value = state.scene,
                onValueChange = { v -> vm.edit(ws) { it.copy(scene = v) } },
                tags = prompt.tagMap.keys.toList(),
                placeholder = "e.g. @Mara reading in a sunlit café, 35mm photo, shallow depth of field",
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Image settings") {
            Text(
                if (hasReferences) "Model: Wan 2.7 Pro Edit (uses your reference images)"
                else "Model: Wan 2.7 Pro text-to-image (prompt only)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Label("Aspect ratio")
            ChipRow(IMAGE_ASPECTS, state.imageAspect, { it }) { a -> vm.edit(ws) { it.copy(imageAspect = a) } }
            Label("Resolution")
            if (hasReferences) {
                Text("2K (images made from references are always 2K)", style = MaterialTheme.typography.bodySmall)
            } else {
                ChipRow(listOf("2k", "4k"), state.imageResolution, { it.uppercase() }) { r -> vm.edit(ws) { it.copy(imageResolution = r) } }
            }
            OutlinedTextField(
                value = state.seed,
                onValueChange = { v -> vm.edit(ws) { it.copy(seed = v.filter(Char::isDigit)) } },
                label = { Text("Seed (optional, for repeatable results)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Content") {
            SwitchRow("NSFW / explicit content", state.nsfw) { v -> vm.edit(ws) { it.copy(nsfw = v, adultsConfirmed = false) } }
            if (state.nsfw) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Anything involving minors is always blocked, in text and in images; uploaded images are " +
                                "age-checked by the server first. SpicyAPI image links expire after ~20 minutes, so save " +
                                "images you want to keep.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.clickable { vm.edit(ws) { it.copy(adultsConfirmed = !it.adultsConfirmed) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(state.adultsConfirmed, onCheckedChange = { v -> vm.edit(ws) { it.copy(adultsConfirmed = v) } })
                            Text(
                                "Everyone depicted is an adult (18+), and any real person shown has consented to this use of their likeness.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        PromptPreview(prompt.copy(warnings = prompt.warnings.filterNot { it.startsWith("Add a scene") }), context)

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = vm::generateImage,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("Uploading…")
            } else {
                Text("Generate image · ≈ ${formatUsd(IMAGE_PRICE_USD)}", maxLines = 1)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
