package com.example.videogenerator.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videogenerator.model.Job
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: GeneratorViewModel, modifier: Modifier = Modifier) {
    val jobs by vm.jobs.collectAsState()
    val context = LocalContext.current

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                Text("${jobs.size} generations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::refreshJobs) { Text("Refresh") }
            }
        }
        if (jobs.isEmpty()) {
            item { Text("Nothing yet. Generated videos show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(jobs, key = { it.id }) { job -> JobCard(job, vm, context) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(job: Job, vm: GeneratorViewModel, context: Context) {
    var playUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Fetch a current link first; SpicyAPI links expire after ~20 minutes.
    fun withUrl(action: (String) -> Unit) = scope.launch {
        vm.freshVideoUrl(job)?.let(action)
            ?: Toast.makeText(context, "Couldn't get the video link. Try Refresh.", Toast.LENGTH_SHORT).show()
    }
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                AssistChip(onClick = {}, label = { Text(job.status + if (job.isSpicy) " · NSFW" else "") })
                Text(
                    "  ${job.resolution} · ${job.duration}s" + (job.costUsd?.let { " · ${formatUsd(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(top = 14.dp),
                )
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(job.createdAt)),
                style = MaterialTheme.typography.labelSmall,
            )
            if (!job.isTerminal) LinearProgressIndicator(Modifier.fillMaxWidth())
            job.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(
                job.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            playUrl?.let { url ->
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            setVideoURI(Uri.parse(url))
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            // Wraps onto a second line instead of pushing buttons off-screen.
            FlowRow {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "Full prompt") }
                if (job.videoUrl != null) {
                    TextButton(onClick = { if (playUrl != null) playUrl = null else withUrl { playUrl = it } }) {
                        Text(if (playUrl != null) "Hide" else "Play")
                    }
                    TextButton(onClick = { withUrl { download(context, job, it) } }) { Text("Save") }
                    TextButton(onClick = {
                        withUrl { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    }) { Text("Open") }
                }
                if (!job.isTerminal && !job.isSpicy) TextButton(onClick = { vm.cancel(job) }) { Text("Cancel") }
                else TextButton(onClick = { vm.removeJob(job.id) }) { Text("Remove") }
            }
        }
    }
}

private fun download(context: Context, job: Job, url: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("Generated video")
        .setMimeType("video/mp4")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "videogen-${job.id.takeLast(8)}.mp4")
    context.getSystemService(DownloadManager::class.java).enqueue(request)
    Toast.makeText(context, "Saving to Movies…", Toast.LENGTH_SHORT).show()
}
