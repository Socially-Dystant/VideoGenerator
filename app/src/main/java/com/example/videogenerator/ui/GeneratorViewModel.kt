package com.example.videogenerator.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videogenerator.data.ApiClient
import com.example.videogenerator.data.GenerateRequest
import com.example.videogenerator.data.Repository
import com.example.videogenerator.data.TaskResponse
import com.example.videogenerator.model.AppSettings
import com.example.videogenerator.model.AspectRatio
import com.example.videogenerator.model.Instruction
import com.example.videogenerator.model.Job
import com.example.videogenerator.model.ReferenceImage
import com.example.videogenerator.model.Resolution
import com.example.videogenerator.model.Shot
import com.example.videogenerator.prompt.BuiltPrompt
import com.example.videogenerator.prompt.PromptBuilder
import com.example.videogenerator.prompt.PromptInput
import com.example.videogenerator.prompt.Safety
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CreateState(
    val scene: String = "",
    val shots: List<Shot> = listOf(Shot(id = 1)),
    val references: List<ReferenceImage> = emptyList(),
    val startFrame: String? = null,
    val resolution: Resolution = Resolution.P720,
    val duration: Int = 10,
    val aspectRatio: AspectRatio = AspectRatio.ADAPTIVE,
    val generateAudio: Boolean = true,
    val seed: String = "",
    val nsfw: Boolean = false,
    val adultsConfirmed: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
) {
    /** Ofox allows 9 input references; a start frame sent alongside them takes one slot. */
    val maxReferences: Int get() = if (startFrame != null) 8 else 9
    val estimatedCostUsd: Double get() = (if (nsfw) resolution.nsfwUsdPerSecond else resolution.usdPerSecond) * duration
}

class GeneratorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val api = ApiClient(app)
    private var nextId = System.currentTimeMillis()
    private var pollJob: CoroutineJob? = null

    private val _state = MutableStateFlow(CreateState())
    val state: StateFlow<CreateState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val instructions: StateFlow<List<Instruction>> = repo.instructions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val jobs: StateFlow<List<Job>> = repo.jobs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prompt: StateFlow<BuiltPrompt> = combine(_state, instructions, settings) { s, ins, cfg ->
        PromptBuilder.build(
            PromptInput(
                scene = s.scene,
                shots = s.shots,
                references = s.references,
                hasStartFrame = s.startFrame != null,
                durationSeconds = s.duration,
                instructions = ins.filter { it.enabled }.map { it.text },
                nsfw = s.nsfw,
                tagStyle = cfg.tagStyle,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PromptBuilder.build(emptyInput()))

    init {
        viewModelScope.launch {
            if (repo.jobs.first().any { !it.isTerminal }) startPolling()
        }
    }

    fun edit(transform: (CreateState) -> CreateState) = _state.update { transform(it).copy(message = null) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // --- Images ---------------------------------------------------------------

    fun addReferences(uris: List<Uri>) = edit { s ->
        val free = s.maxReferences - s.references.size
        val used = s.references.map { it.tag.lowercase() }.toMutableSet()
        val added = uris.take(free).map { uri ->
            var n = s.references.size + 1
            while ("ref$n" in used) n++
            used += "ref$n"
            ReferenceImage(id = nextId++, uri = uri.toString(), tag = "ref$n")
        }
        val next = s.copy(references = s.references + added)
        if (uris.size > free) next.copy(message = "Only ${s.maxReferences} reference images are allowed.") else next
    }

    fun updateReference(id: Long, transform: (ReferenceImage) -> ReferenceImage) = edit { s ->
        s.copy(references = s.references.map { if (it.id == id) transform(it) else it })
    }

    fun removeReference(id: Long) = edit { s -> s.copy(references = s.references.filterNot { it.id == id }) }

    fun setStartFrame(uri: Uri?) = edit { s ->
        val next = s.copy(startFrame = uri?.toString())
        if (next.references.size > next.maxReferences) {
            next.copy(references = next.references.take(next.maxReferences), message = "Removed one reference to make room for the start frame.")
        } else next
    }

    // --- Shots ----------------------------------------------------------------

    fun addShot() = edit { it.copy(shots = it.shots + Shot(id = nextId++)) }

    fun updateShot(id: Long, transform: (Shot) -> Shot) = edit { s ->
        s.copy(shots = s.shots.map { if (it.id == id) transform(it) else it })
    }

    fun removeShot(id: Long) = edit { s -> s.copy(shots = s.shots.filterNot { it.id == id }) }

    fun moveShot(id: Long, delta: Int) = edit { s ->
        val i = s.shots.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in s.shots.indices) s else s.copy(shots = s.shots.toMutableList().apply { add(j, removeAt(i)) })
    }

    // --- Instructions & settings --------------------------------------------------

    fun saveInstruction(instruction: Instruction) = viewModelScope.launch {
        repo.updateInstructions { list ->
            if (list.any { it.id == instruction.id }) list.map { if (it.id == instruction.id) instruction else it }
            else list + instruction.copy(id = nextId++)
        }
    }

    fun deleteInstruction(id: Long) = viewModelScope.launch { repo.updateInstructions { list -> list.filterNot { it.id == id } } }

    fun saveSettings(settings: AppSettings) = viewModelScope.launch { repo.saveSettings(settings) }

    // --- Generation ---------------------------------------------------------------

    /** Returns an error message if the current input can't be submitted. */
    fun validate(): String? {
        val s = _state.value
        val p = prompt.value
        val cfg = settings.value
        if (cfg.serverUrl.isBlank() || cfg.appToken.isBlank()) return "Set the server URL and app token in Settings first."
        if (s.scene.isBlank() && s.shots.all { it.description.isBlank() }) return "Describe the scene or at least one shot."
        val tags = s.references.map { it.tag.lowercase() }
        if (tags.any { !it.matches(Regex("[a-z0-9_]+")) }) return "Reference tags may only contain letters, numbers and _."
        if (tags.size != tags.toSet().size || "start" in tags) return "Each reference needs a unique tag (\"start\" is reserved)."
        if (s.seed.isNotBlank() && s.seed.toLongOrNull() == null) return "Seed must be a whole number."
        if (s.nsfw) {
            if (!s.adultsConfirmed) return "Confirm that everyone depicted is a consenting adult (18+)."
            Safety.findMinorReference(p.text)?.let {
                return "NSFW prompts can't reference minors (found \"$it\"). Remove it or turn NSFW off."
            }
        }
        return null
    }

    fun generate() {
        validate()?.let { msg -> _state.update { it.copy(message = msg) }; return }
        val s = _state.value
        val cfg = settings.value
        val promptText = prompt.value.text
        _state.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            try {
                val task = api.submit(
                    baseUrl = cfg.serverUrl,
                    token = cfg.appToken,
                    request = GenerateRequest(
                        prompt = promptText,
                        nsfw = s.nsfw,
                        adultsConfirmed = s.nsfw && s.adultsConfirmed,
                        resolution = s.resolution.apiValue,
                        duration = s.duration,
                        aspectRatio = s.aspectRatio.apiValue,
                        generateAudio = s.generateAudio,
                        seed = s.seed.toLongOrNull(),
                    ),
                    startFrame = s.startFrame?.let(Uri::parse),
                    references = s.references.map { Uri.parse(it.uri) },
                )
                val job = Job(
                    id = task.id,
                    createdAt = System.currentTimeMillis(),
                    prompt = promptText,
                    resolution = s.resolution.apiValue,
                    duration = s.duration,
                    status = task.status.ifEmpty { "pending" },
                )
                repo.updateJobs { listOf(job) + it }
                _state.update { it.copy(submitting = false, message = "Submitted! Track progress in History.") }
                startPolling()
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, message = e.message ?: "Submission failed.") }
            }
        }
    }

    fun cancel(job: Job) = viewModelScope.launch {
        val cfg = settings.value
        runCatching { api.cancel(cfg.serverUrl, cfg.appToken, job.id) }
            .onSuccess { applyTask(job.id, TaskResponse(id = job.id, status = "cancelled")) }
            .onFailure { e -> _state.update { it.copy(message = e.message) } }
    }

    /** Re-polls the job so the link is current (SpicyAPI links expire after ~20 minutes). */
    suspend fun freshVideoUrl(job: Job): String? {
        val cfg = settings.value
        return runCatching { api.status(cfg.serverUrl, cfg.appToken, job.id) }
            .onSuccess { applyTask(job.id, it) }
            .getOrNull()?.videoUrl ?: job.videoUrl.takeUnless { job.isSpicy }
    }

    fun removeJob(id: String) = viewModelScope.launch { repo.updateJobs { list -> list.filterNot { it.id == id } } }

    fun refreshJobs() = startPolling()

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val pending = repo.jobs.first().filterNot { it.isTerminal }
                if (pending.isEmpty()) break
                val cfg = settings.value
                pending.forEach { job ->
                    runCatching { api.status(cfg.serverUrl, cfg.appToken, job.id) }
                        .onSuccess { applyTask(job.id, it) }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun applyTask(id: String, task: TaskResponse) = repo.updateJobs { list ->
        list.map {
            if (it.id != id) it else it.copy(
                status = task.status.ifEmpty { it.status },
                videoUrl = task.videoUrl ?: it.videoUrl,
                error = task.error ?: it.error,
                costUsd = task.costUsd ?: it.costUsd,
            )
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L

        fun emptyInput() = PromptInput("", emptyList(), emptyList(), false, 10, emptyList(), false, com.example.videogenerator.model.TagStyle.WAN)
    }
}
