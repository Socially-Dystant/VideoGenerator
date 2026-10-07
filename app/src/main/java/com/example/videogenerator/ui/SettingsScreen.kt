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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import com.example.videogenerator.data.ProviderBalance
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
    val balances by vm.balances.collectAsState()
    val loadingBalances by vm.loadingBalances.collectAsState()
    LaunchedEffect(saved.serverUrl, saved.appToken) { vm.loadBalances() }

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
        Section("Balances", "Remaining credit on each account, in US dollars.") {
            when {
                saved.serverUrl.isBlank() || saved.appToken.isBlank() ->
                    Text("Save the server URL and app token to see balances.", style = MaterialTheme.typography.bodySmall)
                balances == null && loadingBalances -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> balances?.fold(
                    onSuccess = {
                        BalanceRow("Ofox (NSFW off)", it.ofox, OFOX_TOP_UP_URL, "Opens the Ofox console; choose Billing to add funds.")
                        BalanceRow("SpicyAPI (NSFW on, images)", it.spicy, SPICY_TOP_UP_URL, null)
                    },
                    onFailure = {
                        Text("Couldn't reach the server: ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    },
                )
            }
            OutlinedButton(onClick = vm::loadBalances, enabled = !loadingBalances) {
                if (loadingBalances) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Refresh balances")
            }
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

@Composable
private fun BalanceRow(label: String, balance: ProviderBalance, topUpUrl: String, topUpHint: String?) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uriHandler.openUri(topUpUrl) }, contentPadding = PaddingValues(0.dp)) { Text("Top up ↗") }
            topUpHint?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (balance.error != null) {
                Text(balance.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    balance.available?.let { formatUsd(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = if ((balance.available ?: 0.0) < 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                listOfNotNull(
                    balance.held?.takeIf { it > 0 }?.let { "${formatUsd(it)} held by running jobs" },
                    balance.used?.let { "${formatUsd(it)} spent in total" },
                ).forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

/** Ofox doesn't publish a direct billing link; Billing is a page inside its console. */
private const val OFOX_TOP_UP_URL = "https://app.ofox.ai"
private const val SPICY_TOP_UP_URL = "https://spicyapi.ai/console/billing"
