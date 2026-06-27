package com.terminal.rootnode

/**
 * FenrirKeyboard.kt
 *
 * Public entry point for the FenrirCLI custom keyboard.
 *
 * This file intentionally contains only the public composable and the
 * [rememberKeyboardController] helper. All internal implementation lives
 * in the `keyboard` sub-package to keep each concern cleanly separated.
 *
 * Sub-package files:
 *   KeyModels.kt              — Data models (KeyAction, KeyDefinition, ShiftState, KeyboardLayer)
 *   KeyboardLayouts.kt        — Immutable row definitions
 *   EditingUtils.kt           — Grapheme-safe TextFieldValue extension functions
 *   PointerUtils.kt           — Low-level pointer input primitives
 *   KeyboardController.kt     — Business logic (dispatch, shift state, layer state)
 *   KeyComponents.kt          — FenrirKey / FenrirRepeatKey / FenrirShiftKey / KeyRow
 *   FenrirKeyboardContent.kt  — Layout assembler
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

import com.terminal.rootnode.keyboard.FenrirKeyboardContent
import com.terminal.rootnode.keyboard.KeyboardController

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The FenrirCLI custom terminal keyboard.
 *
 * Renders a full on-screen keyboard optimised for terminal input, with:
 *  - Power/special symbols row
 *  - Terminal extras row (Esc, Home, arrows, End)
 *  - QWERTY/NUMERIC layer toggle
 *  - Three-state shift machine (OFF / ONE_SHOT / CAPS_LOCK)
 *  - Grapheme-cluster-aware backspace with auto-repeat
 *  - Command history navigation
 *  - Proper accessibility semantics on every key
 *
 * @param value         The current [TextFieldValue] of the terminal input field.
 * @param onValueChange Called whenever a key modifies the text or cursor.
 * @param onEnter       Called when the user presses the Enter key.
 * @param onHistoryUp   Called for ↑ history; return the older command, or null if at the beginning.
 * @param onHistoryDown Called for ↓ history; return the newer command (or empty string).
 * @param modifier      Optional layout modifier applied to the keyboard container.
 */
@Composable
fun FenrirKeyboard(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEnter: () -> Unit,
    onHistoryUp: () -> String?,
    onHistoryDown: () -> String,
    modifier: Modifier = Modifier,
) {
    val controller = rememberKeyboardController(
        getValue      = { value },
        onValueChange = onValueChange,
        onEnter       = onEnter,
        onHistoryUp   = onHistoryUp,
        onHistoryDown = onHistoryDown,
    )

    FenrirKeyboardContent(
        controller = controller,
        modifier   = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates and remembers a [KeyboardController] across recompositions.
 *
 * All callbacks are wrapped in [rememberUpdatedState] so the controller always
 * invokes the LATEST version of each lambda — even after recomposition.
 *
 * Without this, `getValue` would permanently capture the TextFieldValue from
 * the FIRST composition, causing every insertion to overwrite from position 0.
 */
@Composable
private fun rememberKeyboardController(
    getValue: () -> TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEnter: () -> Unit,
    onHistoryUp: () -> String?,
    onHistoryDown: () -> String,
): KeyboardController {
    // rememberUpdatedState wraps each param in a State<T> that always holds
    // the latest value. The lambdas below capture the State objects (stable),
    // so reading from them at call-time is always fresh.
    val latestGetValue      by rememberUpdatedState(getValue)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnEnter       by rememberUpdatedState(onEnter)
    val latestOnHistoryUp   by rememberUpdatedState(onHistoryUp)
    val latestOnHistoryDown by rememberUpdatedState(onHistoryDown)

    return remember {
        KeyboardController(
            getValue      = { latestGetValue() },
            onValueChange = { latestOnValueChange(it) },
            onEnter       = { latestOnEnter() },
            onHistoryUp   = { latestOnHistoryUp() },
            onHistoryDown = { latestOnHistoryDown() },
        )
    }
}

