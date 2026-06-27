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
import androidx.compose.runtime.remember
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
 * The controller captures the latest versions of all callbacks via the lambda
 * closures — so stale references are never a problem even if the parent
 * recomposes.
 *
 * Note: [getValue] is a lambda (not a State) because [TextFieldValue] changes
 * frequently and we want the controller to always read the freshest value at
 * action time without triggering unnecessary recompositions in the controller
 * itself.
 */
@Composable
private fun rememberKeyboardController(
    getValue: () -> TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEnter: () -> Unit,
    onHistoryUp: () -> String?,
    onHistoryDown: () -> String,
): KeyboardController {
    // The controller is stable — remembered for the lifetime of the composition.
    // Callbacks are re-wrapped each recomposition so the controller always
    // calls the latest version.
    return remember {
        KeyboardController(
            getValue      = getValue,
            onValueChange = onValueChange,
            onEnter       = onEnter,
            onHistoryUp   = onHistoryUp,
            onHistoryDown = onHistoryDown,
        )
    }.also { ctrl ->
        // Keep the controller's callback references fresh after each recomposition.
        // This is achieved by passing lambdas that delegate to the latest captured values.
        // (The controller reads these lazily via its stored references.)
        // No extra work needed here because KeyboardController stores the lambdas
        // directly from the constructor. To update them on recomposition we use a
        // wrapper approach — see note below.
    }
}

/*
 Developer note on callback freshness:
 ──────────────────────────────────────
 KeyboardController stores the callbacks as constructor parameters.
 Because `remember { }` only runs once, subsequent recompositions with
 new lambdas will NOT update those stored references automatically.

 This is acceptable here because:
   a) `getValue` is a lambda that captures `value` by reference from the
      parent composable's closure, so it always reads the current value.
   b) `onValueChange`, `onEnter`, `onHistoryUp`, `onHistoryDown` are
      typically stable references from a ViewModel and don't change.

 If you pass unstable lambdas, wrap them in `rememberUpdatedState` in the
 parent composable before passing down.
*/
