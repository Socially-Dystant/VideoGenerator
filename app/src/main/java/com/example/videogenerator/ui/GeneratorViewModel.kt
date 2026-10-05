package com.example.videogenerator.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videogenerator.data.ApiClient
import com.example.videogenerator.data.CharacterLibrary
import com.example.videogenerator.data.FrameExtractor
import com.example.videogenerator.data.GenerateRequest
import com.example.videogenerator.data.Repository
import com.example.videogenerator.data.TaskResponse
import com.example.videogenerator.model.AppSettings
import com.example.videogenerator.model.AspectRatio
import com.example.videogenerator.model.Character
import com.example.videogenerator.model.Instruction
import com.example.videogenerator.model.Job
import com.example.videogenerator.model.ReferenceImage
import com.example.videogenerator.model.Resolution
import com.example.videogenerator.model.SavedCharacter
import com.example.videogenerator.model.Shot
import com.example.videogenerator.prompt.BuiltPrompt
import com.example.videogenerator.prompt.PromptBuilder
import com.example.videogenerator.prompt.PromptInput
import com.example.videogenerator.prompt.Safety
import java.io.File
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
    val characters: List<Character> = emptyList(),
    /** Set after the first images are added, to offer creating a character. */
    val offerCharacter: Boolean = false,
    val characterOffered: Boolean = false,
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
    private val library = CharacterLibrary(app)
    private val frameExtractor = FrameExtractor(app)
    private var nextId = System.currentTimeMillis()
    private var pollJob: CoroutineJob? = null

    private val _state = MutableStateFlow(CreateState())
    val state: StateFlow<CreateState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val instructions: StateFlow<List<Instruction>> = repo.instructions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val jobs: StateFlow<List<Job>> = repo.jobs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedCharacters: StateFlow<List<SavedCharacter>> =
        repo.savedCharacters.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Feedback for library actions, shown in the Characters section. */
    private val _libraryMessage = MutableStateFlow<String?>(null)
    val libraryMessage: StateFlow<String?> = _libraryMessage.asStateFlow()

    val prompt: StateFlow<BuiltPrompt> = combine(_state, instructions, settings) { s, ins, cfg ->
        PromptBuilder.build(
            PromptInput(
                scene = s.scene,
                shots = s.shots,
                references = s.references,
                hasStartFrame = s.startFrame != null,
                characters = s.characters,
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
        val next = s.copy(references = s.references + added).offerCharacterIfNew()
        if (uris.size > free) next.copy(message = "Only ${s.maxReferences} reference images are allowed.") else next
    }

    fun updateReference(id: Long, transform: (ReferenceImage) -> ReferenceImage) = edit { s ->
        s.copy(references = s.references.map { if (it.id == id) transform(it) else it })
    }

    fun removeReference(id: Long) = edit { s ->
        s.copy(references = s.references.filterNot { it.id == id }).withoutCharacterImage(id)
    }

    fun setStartFrame(uri: Uri?) = edit { s ->
        val next = s.copy(startFrame = uri?.toString()).let {
            if (uri == null) it.withoutCharacterImage(Character.START_FRAME_IMAGE_ID) else it.offerCharacterIfNew()
        }
        if (next.references.size > next.maxReferences) {
            next.copy(references = next.references.take(next.maxReferences), message = "Removed one reference to make room for the start frame.")
        } else next
    }

    // --- Characters -----------------------------------------------------------------

    fun dismissCharacterOffer() = edit { it.copy(offerCharacter = false) }

    fun newCharacter() = Character(id = nextId++)

    /** Returns an error message, or null once the character is saved. */
    fun saveCharacter(character: Character, toLibrary: Boolean = false): String? {
        val s = _state.value
        val name = character.name.trim()
        val taken = s.references.map { it.tag.lowercase() } + PromptBuilder.START_FRAME_TAG +
            s.characters.filter { it.id != character.id }.map { it.tag }
        val error = when {
            character.imageIds.isEmpty() -> "Choose at least one image of this character."
            name.isEmpty() -> "Give the character a name."
            !name.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) -> "Use one word for the name: letters, numbers and _ (it becomes @${name.filter { it.isLetterOrDigit() || it == '_' }})."
            name.lowercase() in taken -> "\"$name\" is already used by another image or character."
            !character.hasClothing -> "Describe the clothing so it stays the same in every shot."
            else -> null
        }
        if (error != null) return error
        val saved = character.copy(name = name)
        edit { st ->
            val exists = st.characters.any { it.id == saved.id }
            st.copy(
                characters = if (exists) st.characters.map { if (it.id == saved.id) saved else it } else st.characters + saved,
                offerCharacter = false,
            )
        }
        if (toLibrary) saveToLibrary(saved)
        return null
    }

    // --- Character library -----------------------------------------------------------

    fun dismissLibraryMessage() {
        _libraryMessage.value = null
    }

    /** Saves (or updates) [character] in the library with copies of its images and all descriptions. */
    fun saveToLibrary(character: Character) = viewModelScope.launch {
        val s = _state.value
        val uris = buildList {
            if (Character.START_FRAME_IMAGE_ID in character.imageIds) s.startFrame?.let { add(Uri.parse(it)) }
            s.references.filter { it.id in character.imageIds }.forEach { add(Uri.parse(it.uri)) }
        }
        if (uris.isEmpty()) {
            _libraryMessage.value = "${character.name} has no images to save."
            return@launch
        }
        val savedId = character.savedId ?: nextId++
        try {
            val paths = library.storeImages(savedId, uris)
            val saved = SavedCharacter(
                id = savedId,
                name = character.name,
                imagePaths = paths,
                top = character.top,
                bottom = character.bottom,
                footwear = character.footwear,
                accessories = character.accessories,
                otherClothing = character.otherClothing,
                features = character.features,
                savedAt = System.currentTimeMillis(),
            )
            repo.updateSavedCharacters { list ->
                if (list.any { it.id == savedId }) list.map { if (it.id == savedId) saved else it } else list + saved
            }
            _state.update { st ->
                st.copy(characters = st.characters.map { if (it.id == character.id) it.copy(savedId = savedId) else it })
            }
            _libraryMessage.value = "Saved ${character.name} (${paths.size} image${if (paths.size == 1) "" else "s"}) to your library."
        } catch (e: Exception) {
            _libraryMessage.value = "Couldn't save ${character.name}: ${e.message}"
        }
    }

    /** Adds a saved character to this video: its images become references and the character is recreated. */
    fun loadSavedCharacter(saved: SavedCharacter): String? {
        val s = _state.value
        if (s.characters.any { it.savedId == saved.id }) return "${saved.name} is already in this video."
        val files = saved.imagePaths.map(::File).filter { it.exists() }
        if (files.isEmpty()) return "${saved.name}'s images are missing. Delete and save the character again."
        val free = s.maxReferences - s.references.size
        if (files.size > free) {
            return "${saved.name} needs ${files.size} reference slots but only $free are free. Remove some reference images first."
        }
        val tag = saved.name.lowercase()
        val taken = (s.references.map { it.tag.lowercase() } + PromptBuilder.START_FRAME_TAG + s.characters.map { it.tag }).toMutableSet()
        if (tag in taken) return "Something called @${saved.name} is already in this video. Rename or remove it first."
        taken += tag
        val refs = files.map { file ->
            var n = 1
            while ("$tag$n" in taken) n++
            taken += "$tag$n"
            ReferenceImage(id = nextId++, uri = Uri.fromFile(file).toString(), tag = "$tag$n")
        }
        val character = Character(
            id = nextId++,
            name = saved.name,
            imageIds = refs.map { it.id },
            top = saved.top,
            bottom = saved.bottom,
            footwear = saved.footwear,
            accessories = saved.accessories,
            otherClothing = saved.otherClothing,
            features = saved.features,
            savedId = saved.id,
        )
        edit { st ->
            st.copy(
                references = st.references + refs,
                characters = st.characters + character,
                characterOffered = true,
                offerCharacter = false,
            )
        }
        _libraryMessage.value = "Added ${saved.name} with ${refs.size} image${if (refs.size == 1) "" else "s"}."
        return null
    }

    fun deleteSavedCharacter(saved: SavedCharacter) = viewModelScope.launch {
        library.delete(saved.id)
        repo.updateSavedCharacters { list -> list.filterNot { it.id == saved.id } }
        _state.update { st ->
            st.copy(characters = st.characters.map { if (it.savedId == saved.id) it.copy(savedId = null) else it })
        }
        _libraryMessage.value = "Deleted ${saved.name} from your library."
    }

    fun removeCharacter(id: Long) = edit { s -> s.copy(characters = s.characters.filterNot { it.id == id }) }

    private fun CreateState.offerCharacterIfNew() =
        if (characterOffered || characters.isNotEmpty()) this else copy(offerCharacter = true, characterOffered = true)

    private fun CreateState.withoutCharacterImage(imageId: Long) =
        copy(characters = characters.map { c -> c.copy(imageIds = c.imageIds - imageId) })

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
        s.characters.firstOrNull { it.imageIds.isEmpty() }?.let {
            return "${it.name} has no images left. Edit the character and choose at least one."
        }
        s.characters.firstOrNull { it.tag in tags }?.let {
            return "@${it.tag} is used by both a reference image and a character. Rename one of them."
        }
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
                    provider = task.provider,
                    model = task.model,
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

    private val _historyMessage = MutableStateFlow<String?>(null)
    val historyMessage: StateFlow<String?> = _historyMessage.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * Pulls the latest [RECENT_LIMIT] videos from SpicyAPI and Ofox, adds any the
     * app hasn't seen, and updates status, links and metadata on the rest. Ofox
     * doesn't document a job list, so when it has none the newest Ofox jobs
     * already in History are re-checked one by one instead.
     */
    fun refreshJobs() {
        if (_refreshing.value) return
        val cfg = settings.value
        if (cfg.serverUrl.isBlank() || cfg.appToken.isBlank()) {
            _historyMessage.value = "Set the server URL and app token in Settings first."
            return
        }
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val recent = api.recent(cfg.serverUrl, cfg.appToken, RECENT_LIMIT)
                val ofoxTasks = if (recent.ofoxListed) recent.ofox else {
                    repo.jobs.first().filterNot { it.isSpicy }.sortedByDescending { it.createdAt }.take(RECENT_LIMIT)
                        .mapNotNull { job -> runCatching { api.status(cfg.serverUrl, cfg.appToken, job.id) }.getOrNull() }
                }
                var added = 0
                repo.updateJobs { list ->
                    val byId = LinkedHashMap(list.associateBy { it.id })
                    (recent.spicy + ofoxTasks).forEach { task ->
                        val existing = byId[task.id]
                        if (existing == null) added++
                        byId[task.id] = mergeTask(existing, task)
                    }
                    byId.values.sortedByDescending { it.createdAt }
                }
                _historyMessage.value = buildString {
                    append("Updated ${recent.spicy.size} from SpicyAPI and ${ofoxTasks.size} from Ofox")
                    if (added > 0) append(" ($added new)")
                    append(".")
                    recent.notes.forEach { append("\n").append(it) }
                }
                startPolling()
            } catch (e: Exception) {
                _historyMessage.value = e.message ?: "Refresh failed."
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Id of the job whose last frame is being extracted, if any. */
    private val _extractingFrame = MutableStateFlow<String?>(null)
    val extractingFrame: StateFlow<String?> = _extractingFrame.asStateFlow()

    /**
     * Uses the last frame of [job]'s video as the start frame of the next video.
     * The current scene, shots and characters are kept so the story can continue.
     */
    fun continueFromLastFrame(job: Job, onReady: () -> Unit) {
        if (_extractingFrame.value != null) return
        _extractingFrame.value = job.id
        viewModelScope.launch {
            try {
                val url = freshVideoUrl(job) ?: throw IllegalStateException("Couldn't get the video link. Try Refresh.")
                val frame = frameExtractor.lastFrame(url, name = job.id.takeLast(12).replace(Regex("[^A-Za-z0-9_-]"), "_"))
                setStartFrame(Uri.fromFile(frame))
                _state.update { it.copy(message = "Start frame set from the last frame of that video. Describe what happens next.") }
                onReady()
            } catch (e: Exception) {
                _historyMessage.value = "Couldn't use the last frame: ${e.message}"
            } finally {
                _extractingFrame.value = null
            }
        }
    }

    private val _erasing = MutableStateFlow(false)
    val erasing: StateFlow<Boolean> = _erasing.asStateFlow()

    /**
     * Erases [toErase] from the providers where possible: SpicyAPI purges finished
     * videos; Ofox can only cancel unfinished ones, so finished Ofox videos are just
     * removed from History. Anything a provider refuses stays in History.
     */
    fun eraseJobs(toErase: List<Job>) = runErase {
        val cfg = settings.value
        val results = api.erase(cfg.serverUrl, cfg.appToken, toErase.map { it.id }).results
        val removable = results.filter { it.erased || it.localOnly }.map { it.id }.toSet()
        repo.updateJobs { list -> list.filterNot { it.id in removable } }
        val failures = results.filterNot { it.erased || it.localOnly }
        val spicyDeleted = results.count { it.erased && it.id.startsWith("spicy.") }
        val ofoxRemoved = results.count { it.id in removable && !it.id.startsWith("spicy.") }
        buildString {
            if (spicyDeleted > 0) append("Deleted $spicyDeleted from SpicyAPI. ")
            if (ofoxRemoved > 0) append("Removed $ofoxRemoved Ofox video${if (ofoxRemoved == 1) "" else "s"} from History (Ofox keeps finished videos). ")
            failures.forEach { append("\n${it.message ?: "Couldn't erase ${it.id}."}") }
        }.trim()
    }

    /**
     * Purges every finished SpicyAPI video for this model (including ones not in
     * History) and erases every Ofox job in History.
     */
    fun eraseAll() = runErase {
        val cfg = settings.value
        val spicy = api.eraseAllSpicy(cfg.serverUrl, cfg.appToken)
        val ofoxIds = repo.jobs.first().filterNot { it.isSpicy }.map { it.id }
        val ofoxResults = if (ofoxIds.isEmpty()) emptyList() else api.erase(cfg.serverUrl, cfg.appToken, ofoxIds).results
        val keep = (spicy.skipped + spicy.failed).toSet() +
            ofoxResults.filterNot { it.erased || it.localOnly }.map { it.id }
        repo.updateJobs { list -> list.filter { it.id in keep || (it.isSpicy && !it.isTerminal) } }
        buildString {
            append("Deleted ${spicy.purged.size} video${if (spicy.purged.size == 1) "" else "s"} from SpicyAPI")
            append(" and removed ${ofoxResults.count { it.erased || it.localOnly }} Ofox entr${if (ofoxResults.size == 1) "y" else "ies"} from History.")
            if (spicy.skipped.isNotEmpty()) append("\n${spicy.skipped.size} SpicyAPI job(s) still running were skipped; erase them once they finish.")
            if (spicy.failed.isNotEmpty()) append("\n${spicy.failed.size} SpicyAPI job(s) could not be deleted.")
        }
    }

    private fun runErase(block: suspend () -> String) {
        if (_erasing.value) return
        val cfg = settings.value
        if (cfg.serverUrl.isBlank() || cfg.appToken.isBlank()) {
            _historyMessage.value = "Set the server URL and app token in Settings first."
            return
        }
        _erasing.value = true
        viewModelScope.launch {
            _historyMessage.value = try {
                block()
            } catch (e: Exception) {
                e.message ?: "Erase failed."
            } finally {
                _erasing.value = false
            }
        }
    }

    fun dismissHistoryMessage() {
        _historyMessage.value = null
    }

    /** Creates or updates a History entry from a provider task, keeping what the app already knows. */
    private fun mergeTask(existing: Job?, task: TaskResponse): Job {
        val base = existing ?: Job(
            id = task.id,
            createdAt = parseInstant(task.createdAt) ?: System.currentTimeMillis(),
            prompt = task.prompt.orEmpty(),
            resolution = task.resolution.orEmpty(),
            duration = task.duration?.toInt() ?: 0,
            status = task.status,
        )
        return base.copy(
            status = task.status.ifEmpty { base.status },
            videoUrl = task.videoUrl ?: base.videoUrl,
            error = task.error ?: base.error,
            costUsd = task.costUsd ?: base.costUsd,
            billedSeconds = task.billedSeconds ?: base.billedSeconds,
            provider = task.provider ?: base.provider,
            model = task.model ?: base.model,
            completedAt = parseInstant(task.completedAt) ?: base.completedAt,
            prompt = base.prompt.ifBlank { task.prompt.orEmpty() },
            resolution = base.resolution.ifBlank { task.resolution.orEmpty() },
            duration = if (base.duration > 0) base.duration else task.duration?.toInt() ?: 0,
        )
    }

    private fun parseInstant(iso: String?): Long? =
        iso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

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
            if (it.id != id) it else mergeTask(it, task)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val RECENT_LIMIT = 5

        fun emptyInput() = PromptInput("", emptyList(), emptyList(), false, 10, emptyList(), false, com.example.videogenerator.model.TagStyle.WAN)
    }
}
