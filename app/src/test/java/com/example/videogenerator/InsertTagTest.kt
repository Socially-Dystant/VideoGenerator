package com.example.videogenerator

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.videogenerator.ui.insertTag
import org.junit.Assert.assertEquals
import org.junit.Test

class InsertTagTest {
    private fun at(text: String, cursor: Int) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun insertsAtCursorAndPlacesCursorAfterTag() {
        val r = insertTag(at("Mara walks to the car", 11), "@anna")
        assertEquals("Mara walks @anna to the car", r.text)
        assertEquals(TextRange(16), r.selection)
    }

    @Test
    fun addsSpacesWhenTouchingWords() {
        val r = insertTag(at("walkswith", 5), "@anna")
        assertEquals("walks @anna with", r.text)
        assertEquals(TextRange(11), r.selection)
    }

    @Test
    fun replacesSelection() {
        val r = insertTag(TextFieldValue("Hello NAME, hi", TextRange(6, 10)), "@mara")
        assertEquals("Hello @mara, hi", r.text)
        assertEquals(TextRange(11), r.selection)
    }

    @Test
    fun insertsIntoEmptyText() {
        val r = insertTag(at("", 0), "@start")
        assertEquals("@start", r.text)
        assertEquals(TextRange(6), r.selection)
    }
}
