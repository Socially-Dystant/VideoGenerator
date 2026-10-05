package com.example.videogenerator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Text field with @tag chips underneath. A tapped chip is inserted at the
 * cursor (replacing any selection) and the cursor lands directly after it.
 */
@Composable
fun TaggedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    tags: List<String>,
    placeholder: String,
    minLines: Int,
    modifier: Modifier = Modifier,
) {
    // Local TextFieldValue keeps the cursor/selection; the ViewModel only stores the text.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Only adopt [value] when it changes from outside this field; a ViewModel update
    // lagging a frame behind typing must not reset the text or the cursor.
    var lastValue by remember { mutableStateOf(value) }
    if (value != lastValue) {
        lastValue = value
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    val focus = remember { FocusRequester() }

    Column(modifier) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != value) onValueChange(it.text)
            },
            placeholder = { Text(placeholder) },
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        TagChips(tags) { tag ->
            field = insertTag(field, tag)
            onValueChange(field.text)
            focus.requestFocus()
        }
    }
}

/**
 * Puts [tag] in place of the current selection, adding a space before it when it
 * would otherwise touch the previous word and after it when it would touch the
 * next one. The cursor ends immediately after the tag itself.
 */
fun insertTag(field: TextFieldValue, tag: String): TextFieldValue {
    val text = field.text
    val start = field.selection.min.coerceIn(0, text.length)
    val end = field.selection.max.coerceIn(0, text.length)
    val before = text.substring(0, start)
    val after = text.substring(end)
    val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val trail = if (after.isNotEmpty() && (after.first().isLetterOrDigit() || after.first() == '@')) " " else ""
    val cursor = before.length + lead.length + tag.length
    return TextFieldValue(before + lead + tag + trail + after, TextRange(cursor))
}
