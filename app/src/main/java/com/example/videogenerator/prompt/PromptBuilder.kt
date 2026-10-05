package com.example.videogenerator.prompt

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
)

data class BuiltPrompt(
    val text: String,
    /** Every @tag the user can type, mapped to what it becomes in [text]. */
    val tagMap: Map<String, String>,
    /** @tags found in the text that don't match an image. */
    val unknownTags: Set<String>,
    val warnings: List<String>,
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

    fun build(input: PromptInput): BuiltPrompt {
        val tags = tagMap(input.references, input.hasStartFrame, input.tagStyle)
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

        val imageLines = mutableListOf<String>()
        if (input.hasStartFrame) {
            imageLines += if (input.references.isEmpty()) {
                "Animate from the provided first frame, keeping its composition, characters and lighting."
            } else {
                val label = tags.getValue(START_FRAME_TAG)
                "$label is the opening frame: the video must start exactly on $label as shown, then continue from it."
            }
        }
        input.references.forEach { ref ->
            val label = tags.getValue(ref.tag.lowercase())
            val note = resolve(ref.note).trimEnd('.')
            imageLines += if (note.isNotEmpty()) "$label: $note." else
                "$label: reference image; keep its subject's identity and appearance consistent."
        }
        if (imageLines.isNotEmpty()) sections += imageLines.joinToString("\n")

        resolve(input.scene).takeIf { it.isNotEmpty() }?.let { sections += "Scene: $it" }

        val shots = input.shots.filter { it.description.isNotBlank() || it.type != ShotType.NONE }
        if (shots.isNotEmpty()) {
            val timings = shotTimings(shots.map { it.seconds }, input.durationSeconds)
            if (shots.sumOf { it.seconds ?: 0 } > input.durationSeconds) {
                warnings += "Shot lengths add up to more than ${input.durationSeconds}s."
            }
            sections += buildString {
                append("Shots:")
                shots.forEachIndexed { i, shot ->
                    val (start, end) = timings[i]
                    append("\nShot ${i + 1} (${start}s–${end}s")
                    if (shot.type != ShotType.NONE) append(", ${shot.type.label.lowercase()}")
                    append("): ")
                    append(resolve(shot.description).ifEmpty { "continue the action." })
                }
            }
        }

        sections += if (input.nsfw) Safety.NSFW_DIRECTIVE else Safety.SFW_DIRECTIVE

        if (input.scene.isBlank() && shots.isEmpty()) warnings += "Add a scene description or at least one shot."
        val text = sections.joinToString("\n\n")
        if (unknown.isNotEmpty()) {
            warnings += "Unknown tag(s): " + unknown.joinToString { "@$it" } + ". They are sent as plain text."
        }
        return BuiltPrompt(text, tags.mapKeys { "@${it.key}" }, unknown, warnings)
    }

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
