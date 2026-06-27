package com.terminal.rootnode.keyboard

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Shift State Machine
// ─────────────────────────────────────────────────────────────────────────────

/** Three-state shift machine for terminal keyboard. */
enum class ShiftState {
    /** Shift is off — lowercase input. */
    OFF,

    /** One-shot: next character typed will be uppercase, then reverts to OFF. */
    ONE_SHOT,

    /** Caps lock: stays uppercase until explicitly toggled off. */
    CAPS_LOCK;

    /**
     * Transition on a single tap of the Shift key.
     * OFF -> ONE_SHOT -> CAPS_LOCK -> OFF
     */
    fun onTap(): ShiftState = when (this) {
        OFF       -> ONE_SHOT
        ONE_SHOT  -> CAPS_LOCK
        CAPS_LOCK -> OFF
    }

    /**
     * Transition on long-press of the Shift key.
     * Always activates CAPS_LOCK, or toggles off if already locked.
     */
    fun onLongPress(): ShiftState = when (this) {
        CAPS_LOCK -> OFF
        else      -> CAPS_LOCK
    }

    /**
     * Called after a printable character is typed.
     * ONE_SHOT reverts to OFF; CAPS_LOCK stays on.
     */
    fun afterChar(): ShiftState = if (this == ONE_SHOT) OFF else this

    /** True when input should be uppercased. */
    val isActive: Boolean get() = this != OFF
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard Layer
// ─────────────────────────────────────────────────────────────────────────────

/** Which key layer is currently displayed. */
enum class KeyboardLayer { ALPHA, NUMERIC }

// ─────────────────────────────────────────────────────────────────────────────
// Key Action
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy of all possible key actions.
 * Adding a new action is as simple as adding a new subclass here.
 */
sealed class KeyAction {

    /** Insert a character/string, respecting shift state. */
    data class Insert(
        val lower: String,
        /** Defaults to the uppercase version of [lower]. */
        val upper: String = lower.uppercase(),
    ) : KeyAction()

    /** Special whitespace character: space. */
    object Space : KeyAction()

    /** Horizontal tab character for auto-complete. */
    object Tab : KeyAction()

    /** Delete the character/selection to the left of the cursor. */
    object Backspace : KeyAction()

    /** Submit the current command. */
    object Enter : KeyAction()

    /** Navigate to older command history entry. */
    object HistoryUp : KeyAction()

    /** Navigate to newer command history entry (or empty). */
    object HistoryDown : KeyAction()

    /** Toggle shift state via the state machine (single tap). */
    object ShiftTap : KeyAction()

    /** Force CAPS_LOCK (or off if already locked) — long press. */
    object ShiftLongPress : KeyAction()

    /** Switch between ALPHA and NUMERIC layers. */
    object LayerToggle : KeyAction()

    /** Send an Escape sequence. */
    object Escape : KeyAction()

    /** Move text cursor left by one grapheme. */
    object CursorLeft : KeyAction()

    /** Move text cursor right by one grapheme. */
    object CursorRight : KeyAction()

    /** Move cursor to beginning of line. */
    object Home : KeyAction()

    /** Move cursor to end of line. */
    object End : KeyAction()
}

// ─────────────────────────────────────────────────────────────────────────────
// Key Definition
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Describes a single key in the layout grid.
 *
 * @param label        Visible text on the key.
 * @param action       What action fires on tap.
 * @param weight       Horizontal layout weight within a row (default 1f).
 * @param accentColor  Optional override for the label text color.
 * @param isRepeat     Whether this key auto-repeats when held.
 * @param a11yLabel    Accessibility content description (defaults to [label]).
 */
data class KeyDefinition(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val accentColor: Color? = null,
    val isRepeat: Boolean = false,
    val a11yLabel: String = label,
)
