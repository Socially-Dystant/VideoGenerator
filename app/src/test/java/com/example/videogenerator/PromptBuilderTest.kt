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
        assertTrue(p.text.contains("Scene: Night street. Image 2 walks to Image 3."))
        assertTrue(p.text.contains("Image 2: a woman in a red coat."))
    }

    @Test
    fun referencesStartAtImage1WithoutStartFrame() {
        val p = PromptBuilder.build(input(start = false, style = TagStyle.AT))
        assertEquals("@Image1", p.tagMap["@anna"])
        assertNull(p.tagMap["@start"])
    }

    @Test
    fun shotsGetTimings() {
        val p = PromptBuilder.build(input())
        assertTrue(p.text.contains("Shot 1 (0s–4s, wide shot): Image 2 crosses the road"))
        assertTrue(p.text.contains("Shot 2 (4s–7s, close-up)"))
        assertTrue(p.text.contains("Shot 3 (7s–10s): Image 3 drives away"))
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
    fun characterTagResolvesToItsImages() {
        val p = PromptBuilder.build(input().copy(characters = listOf(mara), scene = "@Mara walks to @car."))
        assertEquals("Mara (Image 1, Image 2)", p.tagMap["@mara"])
        assertTrue(p.text.contains("Scene: Mara (Image 1, Image 2) walks to Image 3."))
        assertTrue(p.unknownTags.isEmpty())
    }

    @Test
    fun characterBlockLocksIdentityAndWardrobe() {
        val p = PromptBuilder.build(input().copy(characters = listOf(mara), scene = "@Mara walks."))
        println(p.text)
        assertTrue(p.text.contains("Character Mara (Image 1, Image 2):"))
        assertTrue(p.text.contains("Mara is the exact person shown in Image 1 and Image 2; all of these images show the same person."))
        assertTrue(p.text.contains("- Top / outerwear: cropped black leather biker jacket over a white ribbed tank top."))
        assertTrue(p.text.contains("- Footwear: white canvas sneakers."))
        assertTrue(p.text.contains("Distinguishing features that must always be visible and unchanged: rose tattoo on left wrist."))
        assertTrue(p.text.contains("Do not add, remove, swap, recolour or restyle any item"))
        // Character section comes before the scene so the model knows who @Mara is first.
        assertTrue(p.text.indexOf("Character Mara") < p.text.indexOf("Scene:"))
    }
}
