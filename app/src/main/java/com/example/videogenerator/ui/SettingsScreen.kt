package com.example.videogenerator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.videogenerator.model.TagStyle

@Composable
fun SettingsScreen(vm: GeneratorViewModel, modifier: Modifier = Modifier) {
    val saved by vm.settings.collectAsState()
    var serverUrl by remember(saved) { mutableStateOf(saved.serverUrl) }
    var token by remember(saved) { mutableStateOf(saved.appToken) }
    var tagStyle by remember(saved) { mutableStateOf(saved.tagStyle) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Server", "Your Render web service. The Ofox API key lives there, never on this phone.") {
            OutlinedTextField(
                serverUrl, { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://videogenerator-server.onrender.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                token, { token = it },
                label = { Text("App token (APP_TOKEN on Render)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Section("Tag format", "How @tags are written in the prompt sent to Ofox. Wan 3.0 documents \"Image 1\".") {
            Row {
                TagStyle.entries.forEach { style ->
                    FilterChip(selected = tagStyle == style, onClick = { tagStyle = style }, label = { Text(style.label) })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Button(onClick = {
            val url = serverUrl.trim()
            message = if (url.isNotEmpty() && !url.startsWith("https://")) {
                "Server URL must start with https://"
            } else {
                vm.saveSettings(saved.copy(serverUrl = url, appToken = token, tagStyle = tagStyle))
                "Saved."
            }
        }) { Text("Save") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
