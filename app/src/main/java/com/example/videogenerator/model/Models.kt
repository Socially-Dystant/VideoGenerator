package com.example.videogenerator.model

import kotlinx.serialization.Serializable

enum class Resolution(val apiValue: String, val usdPerSecond: Double) {
    P480("480p", 0.064),
    P720("720p", 0.13),
    P1080("1080p", 0.26),
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

data class Shot(
    val id: Long,
    val type: ShotType = ShotType.NONE,
    val description: String = "",
    /** Optional fixed length; shots without one share the remaining time. */
    val seconds: Int? = null,
)

data class ReferenceImage(
    val id: Long,
    val uri: String,
    /** Name used as @tag in the scene/shot text. */
    val tag: String,
    /** Optional note telling the model what this image shows. */
    val note: String = "",
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
) {
    val isTerminal: Boolean get() = status in TERMINAL_STATUSES
}

val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "expired")

data class AppSettings(
    val serverUrl: String = "",
    val appToken: String = "",
    val tagStyle: TagStyle = TagStyle.WAN,
)
