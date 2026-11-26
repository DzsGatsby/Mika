package com.wangzan101.mika.calls.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class CallDialNumberEditTest {
    @Test fun keyInsertsAtCursorAndReplacesReversedSelection() {
        assertEquals(TextFieldValue("12934", TextRange(3)), replaceNumberSelection(TextFieldValue("1234", TextRange(2)), "9"))
        assertEquals(TextFieldValue("194", TextRange(2)), replaceNumberSelection(TextFieldValue("1234", TextRange(3, 1)), "9"))
    }

    @Test fun backspaceDeletesTheSelectionOrTheCodePointBeforeTheCursor() {
        assertEquals(TextFieldValue("14", TextRange(1)), deleteNumberSelection(TextFieldValue("1234", TextRange(3, 1))))
        assertEquals(TextFieldValue("13", TextRange(1)), deleteNumberSelection(TextFieldValue("123", TextRange(2))))
        assertEquals(TextFieldValue("12", TextRange(1)), deleteNumberSelection(TextFieldValue("1😀2", TextRange(3))))
        assertEquals(TextFieldValue("12", TextRange(0)), deleteNumberSelection(TextFieldValue("12", TextRange(0))))
    }

    @Test fun draftFieldsRoundTripThroughClearAndRestoreWithoutSavingExtraFields() {
        val draft = CallTaskDraft()
        val prior = listOf("123", "goal", "question", "context", "constraint", "sim")
        draft.restoreFields(prior)
        draft.clear()
        assertEquals(List(6) { "" }, draft.snapshotFields())
        draft.restoreFields(prior)
        assertEquals(prior, draft.snapshotFields())
    }
}
