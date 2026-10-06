package com.example.videogenerator.model

import kotlinx.serialization.Serializable

/** Listed per-second rates: Ofox for SFW requests, SpicyAPI for NSFW ones. */
enum class Resolution(val apiValue: String, val usdPerSecond: Double, val nsfwUsdPerSecond: Double) {
    P480("480p", 0.064, 0.0612),
    P720("720p", 0.13, 0.126),
    P1080("1080p", 0.26, 0.252),
}

val DURATIONS = listOf(5, 10, 15, 20, 25, 30)

enum class AspectRatio(val apiValue: String, val label: String) {
    ADAPTIVE("adaptive", "Auto"),
    LANDSCAPE("16:9", "16:9"),
    PORTRAIT("9:16", "9:16"),
    SQUARE("1:1", "1:1"),
    STANDARD("4:3", "4:3"),
    STANDARD_PORTRAIT("3:4", "3:4"),
}

enum class ShotType(val label: String) {
    NONE("Any"),
    ESTABLISHING("Establishing shot"),
    WIDE("Wide shot"),
    MEDIUM("Medium shot"),
    CLOSE_UP("Close-up"),
    EXTREME_CLOSE_UP("Extreme close-up"),
    OVER_THE_SHOULDER("Over-the-shoulder"),
    POV("POV shot"),
    TRACKING("Tracking shot"),
    AERIAL("Aerial shot"),
}

/** How resolved @tags are written into the prompt sent to Ofox. */
enum class TagStyle(val label: String) {
    /** Wan 3.0's documented syntax: "Image 1", "Image 2" … */
    WAN("Image 1"),
    AT("@Image1"),
}

@Serializable
data class Shot(
    val id: Long,
    val type: ShotType = ShotType.NONE,
    val description: String = "",
    /** Optional fixed length; shots without one share the remaining time. */
    val seconds: Int? = null,
)

@Serializable
data class ReferenceImage(
    val id: Long,
    val uri: String,
    /** Name used as @tag in the scene/shot text. */
    val tag: String,
    /** Optional note telling the model what this image shows. */
    val note: String = "",
)

/**
 * A named person built from one or more of the uploaded images, with a wardrobe
 * that must stay identical across every shot.
 */
@Serializable
data class Character(
    val id: Long,
    val name: String = "",
    /** [ReferenceImage.id]s, or [START_FRAME_IMAGE_ID] for the start frame. */
    val imageIds: List<Long> = emptyList(),
    val top: String = "",
    val bottom: String = "",
    val footwear: String = "",
    val accessories: String = "",
    val otherClothing: String = "",
    /** Tattoos, scars, piercings, makeup, glasses… anything that must not change. */
    val features: String = "",
    /** Set when this character came from, or was saved to, the character library. */
    val savedId: Long? = null,
) {
    val tag: String get() = name.lowercase()
    val hasClothing: Boolean get() = listOf(top, bottom, footwear, accessories, otherClothing).any { it.isNotBlank() }

    companion object {
        const val START_FRAME_IMAGE_ID = -1L
    }
}

/** A character kept in the library, with its own copies of the images. */
@Serializable
data class SavedCharacter(
    val id: Long,
    val name: String,
    /** Absolute paths of the image copies in app storage. */
    val imagePaths: List<String>,
    val top: String = "",
    val bottom: String = "",
    val footwear: String = "",
    val accessories: String = "",
    val otherClothing: String = "",
    val features: String = "",
    val savedAt: Long = 0,
)

@Serializable
data class Instruction(
    val id: Long,
    val title: String,
    val text: String,
    val enabled: Boolean = true,
)

@Serializable
data class Job(
    val id: String,
    val createdAt: Long,
    val prompt: String,
    val resolution: String,
    val duration: Int,
    val status: String,
    val videoUrl: String? = null,
    val error: String? = null,
    val costUsd: Double? = null,
    val provider: String? = null,
    val model: String? = null,
    val completedAt: Long? = null,
    val billedSeconds: Double? = null,
    /** "video" or "image". */
    val kind: String = "video",
) {
    val isImage: Boolean get() = kind == "image"

    /** Empty for jobs imported by Refresh that weren't created from this app. */
    val hasPrompt: Boolean get() = prompt.isNotBlank()
    val providerLabel: String get() = if (isSpicy || provider == "spicy") "SpicyAPI" else "Ofox"

    val isTerminal: Boolean get() = status in TERMINAL_STATUSES

    /** NSFW jobs run on SpicyAPI: they can't be cancelled and their video links expire after ~20 minutes. */
    val isSpicy: Boolean get() = id.startsWith("spicy.")
}

val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "expired")

data class AppSettings(
    val serverUrl: String = "",
    val appToken: String = "",
    val tagStyle: TagStyle = TagStyle.WAN,
)
