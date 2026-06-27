package com.terminal.rootnode.keyboard

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

// ─────────────────────────────────────────────────────────────────────────────
// KeyboardController
//
// Central business logic controller for the keyboard.
// Owns the shift and layer states and exposes the single [dispatch] entry point.
//
// All text mutation happens through the editing utilities in EditingUtils.kt.
// This class holds NO Compose state for text — it delegates that to the caller
// via [onValueChange], keeping it compatible with the public FenrirKeyboard API.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Controls keyboard state (shift, layer) and dispatches [KeyAction]s to
 * produce updated [TextFieldValue]s.
 *
 * This class is annotated [Stable] so Compose can skip recompositions when
 * nothing relevant has changed.
 */
@Stable
class KeyboardController(
    private val getValue: () -> TextFieldValue,
    private val onValueChange: (TextFieldValue) -> Unit,
    private val onEnter: () -> Unit,
    private val onHistoryUp: () -> String?,
    private val onHistoryDown: () -> String,
) {
    // ── Observed state ────────────────────────────────────────────────────────

    /** Current shift state. Observed by Compose. */
    var shiftState: ShiftState by mutableStateOf(ShiftState.OFF)
        private set

    /** Currently active keyboard layer. Observed by Compose. */
    var layer: KeyboardLayer by mutableStateOf(KeyboardLayer.ALPHA)
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main dispatch function. Handles all [KeyAction]s from any key source.
     *
     * This is the single point of truth for state transitions and text edits.
     */
    fun dispatch(action: KeyAction) {
        when (action) {
            // ── Printable characters ──────────────────────────────────────────
            is KeyAction.Insert -> {
                val char = if (shiftState.isActive) action.upper else action.lower
                onValueChange(getValue().insertText(char))
                shiftState = shiftState.afterChar()
            }

            KeyAction.Space -> {
                onValueChange(getValue().insertText(" "))
                shiftState = shiftState.afterChar()
            }

            KeyAction.Tab -> {
                onValueChange(getValue().insertText("\t"))
            }

            // ── Escape ────────────────────────────────────────────────────────
            KeyAction.Escape -> {
                // In a terminal context, Esc clears the current input line
                onValueChange(getValue().insertText("\u001B"))
            }

            // ── Destructive ───────────────────────────────────────────────────
            KeyAction.Backspace -> {
                onValueChange(getValue().deleteBeforeCursor())
            }

            // ── Navigation ────────────────────────────────────────────────────
            KeyAction.Enter -> onEnter()

            KeyAction.HistoryUp -> {
                val cmd = onHistoryUp() ?: return
                onValueChange(TextFieldValue(cmd).moveToEnd())
            }

            KeyAction.HistoryDown -> {
                val cmd = onHistoryDown()
                onValueChange(TextFieldValue(cmd).moveToEnd())
            }

            KeyAction.CursorLeft  -> onValueChange(getValue().moveCursorLeft())
            KeyAction.CursorRight -> onValueChange(getValue().moveCursorRight())
            KeyAction.Home        -> onValueChange(getValue().moveToStart())
            KeyAction.End         -> onValueChange(getValue().moveToEnd())

            // ── Shift ─────────────────────────────────────────────────────────
            KeyAction.ShiftTap       -> shiftState = shiftState.onTap()
            KeyAction.ShiftLongPress -> shiftState = shiftState.onLongPress()

            // ── Layer ─────────────────────────────────────────────────────────
            KeyAction.LayerToggle -> {
                layer = if (layer == KeyboardLayer.ALPHA) KeyboardLayer.NUMERIC
                        else KeyboardLayer.ALPHA
            }
        }
    }

    /** Convenience wrapper — dispatch [KeyAction.Backspace]. */
    fun backspace() = dispatch(KeyAction.Backspace)
}
