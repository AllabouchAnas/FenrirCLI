package com.terminal.rootnode.keyboard

// ─────────────────────────────────────────────────────────────────────────────
// Keyboard Layout Definitions
//
// All key rows are defined as immutable top-level constants.
// The only "dynamic" labelling (shift, layer toggle label) is resolved
// at render time in the composables — NOT stored here.
// ─────────────────────────────────────────────────────────────────────────────

import com.terminal.rootnode.ui.theme.NordFrost1
import com.terminal.rootnode.ui.theme.NordFrost2
import com.terminal.rootnode.ui.theme.NordFrost3
import com.terminal.rootnode.ui.theme.NordGreen
import com.terminal.rootnode.ui.theme.NordPurple
import com.terminal.rootnode.ui.theme.NordYellow

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Create a plain insert key from a single string. */
private fun ch(s: String, weight: Float = 1f) =
    KeyDefinition(label = s, action = KeyAction.Insert(s), weight = weight, a11yLabel = s)

/** Create a plain insert key from a Char. */
private fun ch(c: Char, weight: Float = 1f) = ch(c.toString(), weight)

// ─────────────────────────────────────────────────────────────────────────────
// Power row  (always visible at top)
// |  &  /  ~  .  ..  *  >  <  #  `
// ─────────────────────────────────────────────────────────────────────────────

val POWER_ROW: List<KeyDefinition> = listOf(
    KeyDefinition("|",  KeyAction.Insert("|"),  accentColor = NordGreen,  a11yLabel = "pipe"),
    KeyDefinition("&",  KeyAction.Insert("&"),  accentColor = NordGreen,  a11yLabel = "ampersand"),
    KeyDefinition("/",  KeyAction.Insert("/"),  accentColor = NordGreen,  a11yLabel = "slash"),
    KeyDefinition("~",  KeyAction.Insert("~"),  accentColor = NordGreen,  a11yLabel = "tilde"),
    KeyDefinition(".",  KeyAction.Insert("."),  accentColor = NordGreen,  a11yLabel = "dot"),
    KeyDefinition("..", KeyAction.Insert(".."), accentColor = NordGreen,  a11yLabel = "double dot", weight = 1.3f),
    KeyDefinition("*",  KeyAction.Insert("*"),  accentColor = NordGreen,  a11yLabel = "asterisk"),
    KeyDefinition(">",  KeyAction.Insert(">"),  accentColor = NordGreen,  a11yLabel = "greater than"),
    KeyDefinition("<",  KeyAction.Insert("<"),  accentColor = NordGreen,  a11yLabel = "less than"),
    KeyDefinition("#",  KeyAction.Insert("#"),  accentColor = NordGreen,  a11yLabel = "hash"),
    KeyDefinition("`",  KeyAction.Insert("`"),  accentColor = NordGreen,  a11yLabel = "backtick"),
)

// ─────────────────────────────────────────────────────────────────────────────
// ALPHA layer
// ─────────────────────────────────────────────────────────────────────────────

val ALPHA_ROW1: List<KeyDefinition> = "qwertyuiop".map { ch(it) }
val ALPHA_ROW2: List<KeyDefinition> = "asdfghjkl".map { ch(it) }

/** Middle letters in alpha row 3 — shift and backspace are added by the composable. */
val ALPHA_ROW3_INNER: List<KeyDefinition> = "zxcvbnm".map { ch(it) }

// ─────────────────────────────────────────────────────────────────────────────
// NUMERIC / SYMBOL layer
// ─────────────────────────────────────────────────────────────────────────────

val NUM_ROW1: List<KeyDefinition> = "1234567890".map { ch(it) }

val NUM_ROW2: List<KeyDefinition> = listOf("-", "_", "=", "+", "(", ")", "[", "]", ";").map { ch(it) }

/** Inner symbols in numeric row 3 — backspace is added by the composable. */
val NUM_ROW3_INNER: List<KeyDefinition> = listOf("{", "}", "'", "\"", ",", "?", "!").map { ch(it) }

// ─────────────────────────────────────────────────────────────────────────────
// Terminal extra row (Esc, arrows, Home/End)
// ─────────────────────────────────────────────────────────────────────────────

val TERMINAL_ROW: List<KeyDefinition> = listOf(
    KeyDefinition("ESC",  KeyAction.Escape,      accentColor = NordPurple, weight = 1.2f, a11yLabel = "Escape"),
    KeyDefinition("⌂",    KeyAction.Home,         accentColor = NordFrost3, a11yLabel = "Home"),
    KeyDefinition("←",    KeyAction.CursorLeft,   accentColor = NordFrost1, a11yLabel = "cursor left"),
    KeyDefinition("→",    KeyAction.CursorRight,  accentColor = NordFrost1, a11yLabel = "cursor right"),
    KeyDefinition("⌦",    KeyAction.End,           accentColor = NordFrost3, a11yLabel = "End"),
)

// ─────────────────────────────────────────────────────────────────────────────
// Bottom action bar (always visible)
// ─────────────────────────────────────────────────────────────────────────────

/** Placeholder: layer label is computed dynamically in FenrirKeyboard. */
const val LAYER_TOGGLE_LABEL_ALPHA   = "123"
const val LAYER_TOGGLE_LABEL_NUMERIC = "ABC"
