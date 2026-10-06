package com.example.videogenerator.prompt

import com.example.videogenerator.model.Character
import com.example.videogenerator.model.ReferenceImage
import com.example.videogenerator.model.Shot
import com.example.videogenerator.model.ShotType
import com.example.videogenerator.model.TagStyle

data class PromptInput(
    val scene: String,
    val shots: List<Shot>,
    val references: List<ReferenceImage>,
    val hasStartFrame: Boolean,
    val durationSeconds: Int,
    val instructions: List<String>,
    val nsfw: Boolean,
    val tagStyle: TagStyle,
    val characters: List<Character> = emptyList(),
)

data class BuiltPrompt(
    val text: String,
    /** Every @tag the user can type, mapped to what it becomes in [text]. */
    val tagMap: Map<String, String>,
    /** @tags found in the text that don't match an image. */
    val unknownTags: Set<String>,
    val warnings: List<String>,
    /** Character tag → the image labels that show them. */
    val characterImages: Map<String, List<String>> = emptyMap(),
)

object PromptBuilder {
    const val START_FRAME_TAG = "start"
    private val TAG_REGEX = Regex("@([A-Za-z0-9_]+)")

    /**
     * Ofox sends either a first frame (frame_images) or references (input_references),
     * never both. With references present the start frame is sent as reference #1,
     * so numbering here must match the server's ordering in server/src/ofox.js.
     */
    fun tagMap(references: List<ReferenceImage>, hasStartFrame: Boolean, style: TagStyle): Map<String, String> {
        val map = linkedMapOf<String, String>()
        if (references.isEmpty()) {
            if (hasStartFrame) map[START_FRAME_TAG] = "the first frame"
            return map
        }
        var n = 1
        if (hasStartFrame) map[START_FRAME_TAG] = imageLabel(n++, style)
        references.forEach { map[it.tag.lowercase()] = imageLabel(n++, style) }
        return map
    }

    fun imageLabel(n: Int, style: TagStyle) = when (style) {
        TagStyle.WAN -> "Image $n"
        TagStyle.AT -> "@Image$n"
    }

    /** Image labels for a character's chosen images, in upload order. */
    fun characterImageLabels(
        character: Character,
        references: List<ReferenceImage>,
        imageTags: Map<String, String>,
    ): List<String> {
        val labels = mutableListOf<String>()
        if (Character.START_FRAME_IMAGE_ID in character.imageIds) imageTags[START_FRAME_TAG]?.let(labels::add)
        references.filter { it.id in character.imageIds }.forEach { ref -> imageTags[ref.tag.lowercase()]?.let(labels::add) }
        return labels
    }

    /**
     * One compact line per character: who they are, an identity lock, and the
     * outfit that must not change. Kept short on purpose; long, repetitive
     * instructions dilute what the model pays attention to.
     */
    fun characterLine(character: Character, labels: List<String>, resolve: (String) -> String): String = buildString {
        append(character.name)
        if (labels.isNotEmpty()) {
            append(" (${labels.joinToString(", ")}): identical face, hair, skin tone and body to ")
            append(if (labels.size == 1) labels[0] else "these images")
            append(".")
        } else {
            append(":")
        }
        resolve(character.features).trimEnd('.').takeIf { it.isNotEmpty() }?.let { append(" Always visible: $it.") }
        val outfit = listOf(character.top, character.bottom, character.footwear, character.accessories, character.otherClothing)
            .map { resolve(it).trimEnd('.') }
            .filter { it.isNotEmpty() }
        if (outfit.isNotEmpty()) append(" Wears, unchanged throughout: ${outfit.joinToString("; ")}.")
    }

    /**
     * Continuity line that opens every shot. Shot 1 is anchored to the start
     * frame; later shots carry over from the end of the previous shot.
     */
    fun continuityLine(shotIndex: Int, startLabel: String?): String? = when {
        shotIndex == 0 && startLabel != null ->
            "Starts exactly on $startLabel; every character keeps the face, hair, body and clothes shown in $startLabel."
        shotIndex == 0 -> null
        else -> "Every character keeps the face, hair, body and clothes from the end of shot $shotIndex."
    }

    fun build(input: PromptInput): BuiltPrompt {
        val imageTags = tagMap(input.references, input.hasStartFrame, input.tagStyle)
        val characters = input.characters.filter { it.name.isNotBlank() }
        val characterImages = characters.associate { it.tag to characterImageLabels(it, input.references, imageTags) }
        // @Name becomes just the name; the Characters section already ties it to its images.
        val tags = LinkedHashMap(imageTags).apply { characters.forEach { put(it.tag, it.name) } }
        val unknown = mutableSetOf<String>()
        val warnings = mutableListOf<String>()

        fun resolve(text: String) = TAG_REGEX.replace(text.trim()) { m ->
            tags[m.groupValues[1].lowercase()] ?: run {
                unknown += m.groupValues[1]
                m.value
            }
        }

        val sections = mutableListOf<String>()

        input.instructions.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let {
            sections += it.joinToString("\n")
        }

        val startLabel = if (input.hasStartFrame) imageTags[START_FRAME_TAG] else null

        // References: one "Image N = …" line each.
        val referenceLines = mutableListOf<String>()
        if (startLabel != null && input.references.isNotEmpty()) referenceLines += "$startLabel = start frame."
        input.references.forEach { ref ->
            val label = imageTags.getValue(ref.tag.lowercase())
            val note = resolve(ref.note).trimEnd('.')
            val owners = characters.filter { ref.id in it.imageIds }.map { it.name }
            referenceLines += "$label = " + when {
                note.isNotEmpty() -> note
                owners.isNotEmpty() -> owners.joinToString(" and ")
                DEFAULT_TAG.matches(ref.tag) -> "reference image"
                else -> ref.tag
            } + "."
        }
        if (referenceLines.isNotEmpty()) sections += "References:\n" + referenceLines.joinToString("\n")

        if (characters.isNotEmpty()) {
            sections += "Characters:\n" + characters.joinToString("\n") {
                characterLine(it, characterImages.getValue(it.tag), ::resolve)
            }
        }

        val shots = input.shots.filter { it.description.isNotBlank() || it.type != ShotType.NONE }
        val scene = resolve(input.scene)
        when {
            scene.isNotEmpty() && shots.isEmpty() ->
                sections += "Scene: " + listOfNotNull(continuityLine(0, startLabel), scene).joinToString(" ")
            scene.isNotEmpty() -> sections += "Scene: $scene"
            shots.isEmpty() && startLabel != null -> sections += "Scene: ${continuityLine(0, startLabel)}"
        }

        if (shots.isNotEmpty()) {
            val timings = shotTimings(shots.map { it.seconds }, input.durationSeconds)
            if (shots.sumOf { it.seconds ?: 0 } > input.durationSeconds) {
                warnings += "Shot lengths add up to more than ${input.durationSeconds}s."
            }
            sections += "Shots:\n" + shots.mapIndexed { i, shot ->
                val (start, end) = timings[i]
                val framing = if (shot.type != ShotType.NONE) ", ${shot.type.label.lowercase()}" else ""
                val body = listOfNotNull(continuityLine(i, startLabel), resolve(shot.description).ifEmpty { "Continue the action." })
                "${i + 1} (${start}–${end}s$framing): ${body.joinToString(" ")}"
            }.joinToString("\n")
        }

        sections += if (input.nsfw) Safety.NSFW_DIRECTIVE else Safety.SFW_DIRECTIVE

        if (input.scene.isBlank() && shots.isEmpty()) warnings += "Add a scene description or at least one shot."
        if (unknown.isNotEmpty()) {
            warnings += "Unknown tag(s): " + unknown.joinToString { "@$it" } + ". They are sent as plain text."
        }
        return BuiltPrompt(
            text = sections.joinToString("\n\n"),
            tagMap = tags.mapKeys { "@${it.key}" },
            unknownTags = unknown,
            warnings = warnings,
            characterImages = characterImages,
        )
    }

    /** Tags the app gives new uploads ("ref1"…); they say nothing about the image. */
    private val DEFAULT_TAG = Regex("ref\\d+", RegexOption.IGNORE_CASE)

    /**
     * Assigns start/end seconds to each shot. Fixed lengths are honoured; the
     * rest of the clip is split evenly between shots without one.
     */
    fun shotTimings(fixed: List<Int?>, total: Int): List<Pair<Int, Int>> {
        val flexibleCount = fixed.count { it == null }
        val remaining = (total - fixed.sumOf { it ?: 0 }).coerceAtLeast(0)
        val base = if (flexibleCount > 0) remaining / flexibleCount else 0
        var extra = if (flexibleCount > 0) remaining % flexibleCount else 0
        var cursor = 0
        return fixed.map { len ->
            val length = len ?: (base + if (extra-- > 0) 1 else 0)
            val start = cursor
            cursor += length
            start to cursor
        }
    }
}
