package com.terminal.rootnode.keyboard

import android.text.TextUtils
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.BreakIterator

// ─────────────────────────────────────────────────────────────────────────────
// Text Editing Utilities
//
// All editing operations are pure functions that take and return TextFieldValue.
// They never mutate state — the caller decides what to do with the result.
//
// Key safety properties:
//  - Grapheme-cluster-aware deletion (handles emoji, surrogates, combining chars)
//  - Selection-aware all operations
//  - Cursor bounds are always clamped
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Insert [text] at the current cursor position, replacing any selection.
 *
 * Preserves the cursor immediately after the inserted text.
 */
fun TextFieldValue.insertText(text: String): TextFieldValue {
    val start  = selection.min
    val end    = selection.max
    val before = this.text.substring(0, start)
    val after  = this.text.substring(end)
    val newText = before + text + after
    return TextFieldValue(newText, selection = TextRange(start + text.length))
}

/**
 * Delete one grapheme cluster to the left of the cursor, or delete the
 * selected region if a selection is active.
 *
 * Uses [BreakIterator] to correctly handle:
 * - Multi-codepoint emoji (e.g. 👨‍👩‍👧‍👦)
 * - Surrogate pairs
 * - Combining diacritics (e.g. é as e + combining accent)
 *
 * Returns the same value unchanged if there is nothing to delete.
 */
fun TextFieldValue.deleteBeforeCursor(): TextFieldValue {
    val start = selection.min
    val end   = selection.max

    // If there is a selection, delete the whole selected region
    if (start != end) {
        val newText = text.substring(0, start) + text.substring(end)
        return TextFieldValue(newText, selection = TextRange(start))
    }

    // Nothing to delete
    if (start == 0) return this

    // Walk back one grapheme cluster using BreakIterator
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    iterator.following(start)
    val clusterStart = iterator.previous() // position of current cluster
    val deleteFrom   = iterator.previous() // start of cluster before cursor

    // Clamp to 0 in case iterator returns DONE
    val safeDeleteFrom = if (deleteFrom == BreakIterator.DONE) 0 else deleteFrom

    val newText = text.substring(0, safeDeleteFrom) + text.substring(clusterStart)
    return TextFieldValue(newText, selection = TextRange(safeDeleteFrom))
}

/**
 * Move the cursor one grapheme cluster to the left.
 * If a selection is active, collapses to the start of the selection.
 */
fun TextFieldValue.moveCursorLeft(): TextFieldValue {
    val pos = selection.min
    if (pos == 0) return TextFieldValue(text, selection = TextRange(0))

    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    iterator.following(pos)
    iterator.previous()
    val prev = iterator.previous()
    val newPos = if (prev == BreakIterator.DONE) 0 else prev

    return TextFieldValue(text, selection = TextRange(newPos))
}

/**
 * Move the cursor one grapheme cluster to the right.
 * If a selection is active, collapses to the end of the selection.
 */
fun TextFieldValue.moveCursorRight(): TextFieldValue {
    val pos = selection.max
    if (pos >= text.length) return TextFieldValue(text, selection = TextRange(text.length))

    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val next = iterator.following(pos)
    val newPos = if (next == BreakIterator.DONE) text.length else next

    return TextFieldValue(text, selection = TextRange(newPos))
}

/** Move cursor to position 0 (beginning of field). */
fun TextFieldValue.moveToStart(): TextFieldValue =
    TextFieldValue(text, selection = TextRange(0))

/** Move cursor to the end of the text. */
fun TextFieldValue.moveToEnd(): TextFieldValue =
    TextFieldValue(text, selection = TextRange(text.length))
