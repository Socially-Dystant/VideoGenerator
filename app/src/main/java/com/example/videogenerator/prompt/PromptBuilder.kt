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

    /** "Mara (Image 2, Image 3)": what @mara becomes in the prompt. */
    fun characterReference(name: String, labels: List<String>) =
        if (labels.isEmpty()) name else "$name (${labels.joinToString(", ")})"

    private fun joinLabels(labels: List<String>) = when (labels.size) {
        0 -> ""
        1 -> labels[0]
        else -> labels.dropLast(1).joinToString(", ") + " and " + labels.last()
    }

    /**
     * Identity lock plus wardrobe continuity. The wording is deliberately
     * exhaustive: Wan follows explicit, enumerated constraints far better than
     * a general "keep them consistent".
     */
    fun characterBlock(character: Character, labels: List<String>, resolve: (String) -> String): String = buildString {
        val name = character.name
        val images = joinLabels(labels)
        val plural = labels.size > 1
        append("Character $name")
        if (labels.isNotEmpty()) append(" (${labels.joinToString(", ")})")
        append(":\n")
        if (labels.isNotEmpty()) {
            append("$name is the exact person shown in $images")
            append(if (plural) "; all of these images show the same person.\n" else ".\n")
            append(
                "Reproduce $name's appearance exactly as in ${if (plural) "those images" else images}: identical face and " +
                    "facial structure, eye shape and colour, eyebrows, nose, lips, jawline, skin tone and skin texture, " +
                    "hairstyle, hair colour, hair length and hairline, body type, height and proportions. " +
                    "Do not alter, beautify, age, de-age, slim or stylise $name, and do not blend in features from anyone else. " +
                    "$name must be instantly recognisable as the same person in every shot, from every angle and distance, " +
                    "and in any lighting.\n",
            )
        }
        resolve(character.features).trimEnd('.').takeIf { it.isNotEmpty() }?.let {
            append("Distinguishing features that must always be visible and unchanged: $it.\n")
        }
        if (character.hasClothing) {
            append("$name's wardrobe, identical in every shot from the first frame to the last:\n")
            listOf(
                "Top / outerwear" to character.top,
                "Bottoms" to character.bottom,
                "Footwear" to character.footwear,
                "Accessories" to character.accessories,
                "Other clothing details" to character.otherClothing,
            ).forEach { (label, value) ->
                resolve(value).trimEnd('.').takeIf { it.isNotEmpty() }?.let { append("- $label: $it.\n") }
            }
            append(
                "Keep every garment's colour, shade, material, texture, pattern, print, logo, fit, length, layering, " +
                    "buttons, zips and how it is worn exactly the same throughout the video. Do not add, remove, swap, " +
                    "recolour or restyle any item, and keep the clothing continuous between shots.",
            )
            if (labels.isNotEmpty()) append(" If the clothing in the reference images differs, dress $name as described here.")
        }
    }.trimEnd()

    fun build(input: PromptInput): BuiltPrompt {
        val imageTags = tagMap(input.references, input.hasStartFrame, input.tagStyle)
        val characterLabels = input.characters.associateWith { characterImageLabels(it, input.references, imageTags) }
        val tags = LinkedHashMap(imageTags).apply {
            characterLabels.forEach { (c, labels) -> if (c.tag.isNotEmpty()) put(c.tag, characterReference(c.name, labels)) }
        }
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
                val label = imageTags.getValue(START_FRAME_TAG)
                "$label is the opening frame: the video must start exactly on $label as shown, then continue from it."
            }
        }
        input.references.forEach { ref ->
            val label = imageTags.getValue(ref.tag.lowercase())
            val note = resolve(ref.note).trimEnd('.')
            val owners = input.characters.filter { ref.id in it.imageIds && it.name.isNotBlank() }.map { it.name }
            imageLines += when {
                note.isNotEmpty() -> "$label: $note."
                owners.isNotEmpty() -> "$label: ${owners.joinToString(" and ")}."
                else -> "$label: reference image; keep its subject's identity and appearance consistent."
            }
        }
        if (imageLines.isNotEmpty()) sections += imageLines.joinToString("\n")

        characterLabels.forEach { (character, labels) ->
            if (character.name.isNotBlank()) sections += characterBlock(character, labels, ::resolve)
        }

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
