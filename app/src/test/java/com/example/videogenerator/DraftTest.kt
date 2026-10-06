package com.example.videogenerator

import com.example.videogenerator.model.AspectRatio
import com.example.videogenerator.model.Character
import com.example.videogenerator.model.ReferenceImage
import com.example.videogenerator.model.Resolution
import com.example.videogenerator.model.Shot
import com.example.videogenerator.model.ShotType
import com.example.videogenerator.ui.CreateState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DraftTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyInputSurvivesSaveAndRestore() {
        val state = CreateState(
            scene = "Night street",
            shots = listOf(Shot(1, ShotType.WIDE, "@mara walks", seconds = 4), Shot(2, description = "close on her face")),
            references = listOf(ReferenceImage(10, "file:///data/drafts/a.img", "mara1", "front view")),
            startFrame = "file:///data/frames/f.jpg",
            characters = listOf(Character(20, "Mara", listOf(10, Character.START_FRAME_IMAGE_ID), top = "red coat", savedId = 5)),
            resolution = Resolution.P1080,
            duration = 25,
            aspectRatio = AspectRatio.PORTRAIT,
            generateAudio = false,
            imageResolution = "4k",
            imageAspect = "3:2",
            seed = "42",
            nsfw = true,
            adultsConfirmed = true,
            submitting = true,
            message = "Submitted!",
        )
        val restored = json.decodeFromString(CreateState.serializer(), json.encodeToString(CreateState.serializer(), state))
        assertEquals(state.copy(submitting = false, message = null), restored)
        // Momentary UI state is not restored.
        assertFalse(restored.submitting)
        assertNull(restored.message)
    }
}
