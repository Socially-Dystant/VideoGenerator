package com.example.videogenerator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.videogenerator.model.Job

/**
 * Bottom bar shown while any video or image is still processing. Tapping it
 * opens History. Neither provider reports percent complete, so it's indeterminate.
 */
@Composable
fun ProcessingBar(jobs: List<Job>, onClick: () -> Unit) {
    val running = jobs.filterNot { it.isTerminal }
    if (running.isEmpty()) return
    val videos = running.count { !it.isImage }
    val images = running.count { it.isImage }
    val summary = listOfNotNull(
        videos.takeIf { it > 0 }?.let { "$it video${if (it == 1) "" else "s"}" },
        images.takeIf { it > 0 }?.let { "$it image${if (it == 1) "" else "s"}" },
    ).joinToString(" and ")

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open History", onClick = onClick),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Processing $summary…", style = MaterialTheme.typography.titleSmall)
                    Text("Tap to see progress in History", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
    }
}
