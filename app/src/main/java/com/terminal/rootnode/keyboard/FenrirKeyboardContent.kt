package com.terminal.rootnode.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminal.rootnode.ui.theme.NordFrost1
import com.terminal.rootnode.ui.theme.NordFrost2
import com.terminal.rootnode.ui.theme.NordGreen
import com.terminal.rootnode.ui.theme.NordNight2
import com.terminal.rootnode.ui.theme.NordPurple
import com.terminal.rootnode.ui.theme.NordSnow0
import com.terminal.rootnode.ui.theme.NordYellow

// ─────────────────────────────────────────────────────────────────────────────
// FenrirKeyboardContent
//
// The assembler composable that builds the full keyboard layout from the
// individual row constants and key components.
//
// This is intentionally NOT the public API entrypoint — see FenrirKeyboard.kt
// for that. Splitting them allows this to be tested in isolation and allows
// the public-facing composable to stay minimal.
// ─────────────────────────────────────────────────────────────────────────────

/** Background of the entire keyboard panel. */
private val KEYBOARD_BACKGROUND = Color(0xFF13141F)

@Composable
internal fun FenrirKeyboardContent(
    controller: KeyboardController,
    modifier: Modifier = Modifier,
) {
    val shift = controller.shiftState
    val layer = controller.layer

    // Stable lambda reference — "dispatch" never changes identity for controller
    val dispatch = remember(controller) { controller::dispatch }
    val backspace = remember(controller) { controller::backspace }

    // Dynamic label resolver for ALPHA rows (applies shift casing)
    val alphaLabel: (KeyDefinition) -> String = remember(shift) {
        { key -> if (shift.isActive) key.label.uppercase() else key.label }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KEYBOARD_BACKGROUND)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {

        // ── Row 0: Power / special symbols (always visible) ──────────────────
        KeyRow(
            keys             = POWER_ROW,
            onDispatch       = dispatch,
            keyHeight        = 35.dp,
            defaultTextColor = NordGreen,
            fontSize         = 13.sp,
        )

        // ── Terminal extras row (Esc, Home, arrows, End) ─────────────────────
        KeyRow(
            keys       = TERMINAL_ROW,
            onDispatch = dispatch,
            keyHeight  = 35.dp,
            fontSize   = 13.sp,
        )

        // ── Main layer ────────────────────────────────────────────────────────
        when (layer) {

            KeyboardLayer.ALPHA -> {
                // Row 1: QWERTY top
                KeyRow(ALPHA_ROW1, dispatch, alphaLabel)
                // Row 2: ASDFGHJKL
                KeyRow(ALPHA_ROW2, dispatch, alphaLabel)
                // Row 3: Shift + ZXCVBNM + Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FenrirShiftKey(
                        shiftState = shift,
                        modifier   = Modifier.weight(1.5f),
                        onDispatch = dispatch,
                    )
                    ALPHA_ROW3_INNER.forEach { key ->
                        FenrirKey(
                            label     = if (shift.isActive) key.label.uppercase() else key.label,
                            modifier  = Modifier.weight(1f),
                            a11yLabel = key.a11yLabel,
                            onTap     = { dispatch(key.action) },
                        )
                    }
                    FenrirRepeatKey(
                        label       = "⌫",
                        modifier    = Modifier.weight(1.5f),
                        accentColor = NordFrost2,
                        a11yLabel   = "Backspace",
                        onAction    = backspace,
                    )
                }
            }

            KeyboardLayer.NUMERIC -> {
                // Row 1: 1234567890
                KeyRow(NUM_ROW1, dispatch)
                // Row 2: - _ = + ( ) [ ] ;
                KeyRow(NUM_ROW2, dispatch)
                // Row 3: { } ' " , ? ! + Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NUM_ROW3_INNER.forEach { key ->
                        FenrirKey(
                            label    = key.label,
                            modifier = Modifier.weight(1f),
                            a11yLabel = key.a11yLabel,
                            onTap    = { dispatch(key.action) },
                        )
                    }
                    FenrirRepeatKey(
                        label       = "⌫",
                        modifier    = Modifier.weight(1.5f),
                        accentColor = NordFrost2,
                        a11yLabel   = "Backspace",
                        onAction    = backspace,
                    )
                }
            }
        }

        // ── Bottom action bar (always visible) ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FenrirKey(
                label       = "⇥ Tab",
                modifier    = Modifier.weight(1.5f),
                accentColor = NordFrost1,
                fontSize    = 13.sp,
                a11yLabel   = "Tab",
                onTap       = { dispatch(KeyAction.Tab) },
            )
            FenrirKey(
                label       = if (layer == KeyboardLayer.NUMERIC) LAYER_TOGGLE_LABEL_NUMERIC
                              else LAYER_TOGGLE_LABEL_ALPHA,
                modifier    = Modifier.weight(1.3f),
                accentColor = NordPurple,
                fontSize    = 13.sp,
                a11yLabel   = "Switch keyboard layer",
                onTap       = { dispatch(KeyAction.LayerToggle) },
            )
            FenrirKey(
                label     = "space",
                modifier  = Modifier.weight(3f),
                textColor = NordSnow0.copy(alpha = 0.45f),
                fontSize  = 13.sp,
                a11yLabel = "Space",
                onTap     = { dispatch(KeyAction.Space) },
            )
            FenrirKey(
                label       = "↑",
                modifier    = Modifier.weight(1f),
                accentColor = NordYellow,
                fontSize    = 14.sp,
                a11yLabel   = "History up",
                onTap       = { dispatch(KeyAction.HistoryUp) },
            )
            FenrirKey(
                label       = "↓",
                modifier    = Modifier.weight(1f),
                accentColor = NordYellow,
                fontSize    = 14.sp,
                a11yLabel   = "History down",
                onTap       = { dispatch(KeyAction.HistoryDown) },
            )
            FenrirKey(
                label       = "↵",
                modifier    = Modifier.weight(1.5f),
                accentColor = NordGreen,
                background  = NordNight2,
                fontSize    = 14.sp,
                a11yLabel   = "Enter",
                onTap       = { dispatch(KeyAction.Enter) },
            )
        }
    }
}
