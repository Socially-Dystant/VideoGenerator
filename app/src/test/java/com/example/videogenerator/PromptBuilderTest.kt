package com.example.videogenerator

import com.example.videogenerator.model.Character
import com.example.videogenerator.model.ReferenceImage
import com.example.videogenerator.model.Shot
import com.example.videogenerator.model.ShotType
import com.example.videogenerator.model.TagStyle
import com.example.videogenerator.prompt.PromptBuilder
import com.example.videogenerator.prompt.PromptInput
import com.example.videogenerator.prompt.Safety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val anna = ReferenceImage(1, "content://a", "anna", "a woman in a red coat")
    private val car = ReferenceImage(2, "content://b", "car")

    private fun input(
        refs: List<ReferenceImage> = listOf(anna, car),
        start: Boolean = true,
        style: TagStyle = TagStyle.WAN,
    ) = PromptInput(
        scene = "Night street. @anna walks to @car.",
        shots = listOf(
            Shot(1, ShotType.WIDE, "@anna crosses the road", seconds = 4),
            Shot(2, ShotType.CLOSE_UP, "@anna opens the door of @car"),
            Shot(3, description = "@car drives away"),
        ),
        references = refs,
        hasStartFrame = start,
        durationSeconds = 10,
        instructions = listOf("Cinematic film look."),
        nsfw = false,
        tagStyle = style,
    )

    @Test
    fun startFrameTakesImage1WhenCombinedWithReferences() {
        val p = PromptBuilder.build(input())
        assertEquals("Image 1", p.tagMap["@start"])
        assertEquals("Image 2", p.tagMap["@anna"])
        assertEquals("Image 3", p.tagMap["@car"])
        assertTrue(p.text.contains("References:\nImage 1 = start frame.\nImage 2 = a woman in a red coat.\nImage 3 = car."))
        assertTrue(p.text.contains("Scene: Night street. Image 2 walks to Image 3."))
    }

    @Test
    fun referencesStartAtImage1WithoutStartFrame() {
        val p = PromptBuilder.build(input(start = false, style = TagStyle.AT))
        assertEquals("@Image1", p.tagMap["@anna"])
        assertNull(p.tagMap["@start"])
    }

    @Test
    fun everyShotOpensWithContinuityAndShot1UsesTheStartFrame() {
        val p = PromptBuilder.build(input())
        assertTrue(p.text.contains(
            "1 (0–4s, wide shot): Starts exactly on Image 1; every character keeps the face, hair, body and clothes " +
                "shown in Image 1. Image 2 crosses the road",
        ))
        assertTrue(p.text.contains(
            "2 (4–7s, close-up): Every character keeps the face, hair, body and clothes from the end of shot 1. " +
                "Image 2 opens the door of Image 3",
        ))
        assertTrue(p.text.contains("3 (7–10s): Every character keeps the face, hair, body and clothes from the end of shot 2."))
    }

    @Test
    fun noStartFrameMeansNoOpeningLine() {
        val p = PromptBuilder.build(input(start = false))
        assertTrue(p.text.contains("1 (0–4s, wide shot): Image1 crosses the road".replace("Image1", "Image 1")))
    }

    @Test
    fun instructionsFirstAndContentRuleLast() {
        val p = PromptBuilder.build(input())
        assertTrue(p.text.startsWith("Cinematic film look."))
        assertTrue(p.text.endsWith(Safety.SFW_DIRECTIVE))
    }

    @Test
    fun unknownTagsAreReported() {
        val p = PromptBuilder.build(input().copy(scene = "@bob waves"))
        assertEquals(setOf("bob"), p.unknownTags)
    }

    @Test
    fun minorReferencesAreDetected() {
        assertNotNull(Safety.findMinorReference("a 16 year old"))
        assertNotNull(Safety.findMinorReference("High-school uniform"))
        assertNull(Safety.findMinorReference("a 30 year old man, personal season"))
    }

    private val mara = Character(
        id = 10,
        name = "Mara",
        imageIds = listOf(Character.START_FRAME_IMAGE_ID, 1),
        top = "cropped black leather biker jacket over a white ribbed tank top",
        bottom = "light-wash straight-leg jeans",
        footwear = "white canvas sneakers",
        features = "rose tattoo on left wrist",
    )

    @Test
    fun characterTagBecomesItsNameAndIsDefinedOnce() {
        val p = PromptBuilder.build(input().copy(characters = listOf(mara), scene = "@Mara walks to @car."))
        assertEquals("Mara", p.tagMap["@mara"])
        assertEquals(listOf("Image 1", "Image 2"), p.characterImages["mara"])
        assertTrue(p.text.contains("Scene: Mara walks to Image 3."))
        assertTrue(p.unknownTags.isEmpty())
    }

    @Test
    fun characterLineIsCompact() {
        val p = PromptBuilder.build(input().copy(characters = listOf(mara), scene = "@Mara walks."))
        println(p.text)
        assertTrue(p.text.contains(
            "Characters:\nMara (Image 1, Image 2): identical face, hair, skin tone and body to these images. " +
                "Always visible: rose tattoo on left wrist. Wears, unchanged throughout: cropped black leather biker jacket " +
                "over a white ribbed tank top; light-wash straight-leg jeans; white canvas sneakers.",
        ))
        assertTrue(p.text.indexOf("Characters:") < p.text.indexOf("Scene:"))
    }

    @Test
    fun multipleCharactersEachGetALine() {
        val ben = Character(id = 11, name = "Ben", imageIds = listOf(2), top = "grey hoodie")
        val p = PromptBuilder.build(input().copy(characters = listOf(mara, ben)))
        assertTrue(p.text.contains("\nBen (Image 3): identical face, hair, skin tone and body to Image 3. Wears, unchanged throughout: grey hoodie."))
        assertTrue(p.text.contains("Image 3 = Ben."))
    }
}
