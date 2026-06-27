package com.terminal.rootnode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminal.rootnode.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Shift state machine ───────────────────────────────────────────────────────
private enum class ShiftState { OFF, ONE_SHOT, CAPS_LOCK }

// ── Key data model ────────────────────────────────────────────────────────────
private sealed class KeyAction {
    data class Char(val lower: String, val upper: String = lower.uppercase()) : KeyAction()
    object Backspace : KeyAction()
    object Enter : KeyAction()
    object Tab : KeyAction()
    object HistoryUp : KeyAction()
    object HistoryDown : KeyAction()
    object ShiftToggle : KeyAction()
    object LayerToggle : KeyAction()
    object Space : KeyAction()
}

private data class KeyDef(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val accentColor: Color? = null
)

// ── Layout rows ───────────────────────────────────────────────────────────────

private val POWER_ROW = listOf("|", "&", "/", "~", ".", "..", "*", ">", "<", "#", "`").map {
    KeyDef(it, KeyAction.Char(it))
}

private val QWERTY_ROW1 = "qwertyuiop".map { KeyDef(it.toString(), KeyAction.Char(it.toString())) }
private val QWERTY_ROW2 = "asdfghjkl".map { KeyDef(it.toString(), KeyAction.Char(it.toString())) }
private val QWERTY_ROW3_INNER = "zxcvbnm".map { KeyDef(it.toString(), KeyAction.Char(it.toString())) }

private val NUM_ROW1 = "1234567890".map { KeyDef(it.toString(), KeyAction.Char(it.toString())) }
private val NUM_ROW2 = listOf("-", "_", "=", "+", "(", ")", "[", "]", ";").map { KeyDef(it, KeyAction.Char(it)) }
private val NUM_ROW3_INNER = listOf("{", "}", "'", "\"", ",", "?", "!").map { KeyDef(it, KeyAction.Char(it)) }

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun FenrirKeyboard(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEnter: () -> Unit,
    onHistoryUp: () -> String?,
    onHistoryDown: () -> String,
    modifier: Modifier = Modifier
) {
    var shiftState by remember { mutableStateOf(ShiftState.OFF) }
    var isNumLayer by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    fun insert(text: String) {
        val sel = value.selection
        val cur = value.text
        val newText = cur.substring(0, sel.start) + text + cur.substring(sel.end)
        val newCursor = sel.start + text.length
        onValueChange(TextFieldValue(newText, selection = TextRange(newCursor)))
    }

    fun backspace() {
        val sel = value.selection
        val cur = value.text
        if (sel.start != sel.end) {
            val newText = cur.substring(0, sel.start) + cur.substring(sel.end)
            onValueChange(TextFieldValue(newText, selection = TextRange(sel.start)))
        } else if (sel.start > 0) {
            val newText = cur.substring(0, sel.start - 1) + cur.substring(sel.start)
            onValueChange(TextFieldValue(newText, selection = TextRange(sel.start - 1)))
        }
    }

    fun handleAction(action: KeyAction) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (action) {
            is KeyAction.Char -> {
                val toInsert = if (shiftState != ShiftState.OFF) action.upper else action.lower
                insert(toInsert)
                if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
            }
            KeyAction.Space -> {
                insert(" ")
                if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
            }
            KeyAction.Backspace -> backspace()
            KeyAction.Enter -> onEnter()
            KeyAction.Tab -> insert("\t")
            KeyAction.HistoryUp -> {
                val cmd = onHistoryUp() ?: return
                onValueChange(TextFieldValue(cmd, selection = TextRange(cmd.length)))
            }
            KeyAction.HistoryDown -> {
                val cmd = onHistoryDown()
                onValueChange(TextFieldValue(cmd, selection = TextRange(cmd.length)))
            }
            KeyAction.ShiftToggle -> {
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.ONE_SHOT
                    ShiftState.ONE_SHOT -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
            }
            KeyAction.LayerToggle -> isNumLayer = !isNumLayer
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF13141F))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 0: Power symbols
        KeyRow(
            keys = POWER_ROW,
            onAction = ::handleAction,
            labelOf = { it.label },
            keyHeight = 32.dp,
            textColor = NordGreen,
            fontSize = 12.sp
        )

        if (!isNumLayer) {
            // QWERTY rows
            KeyRow(QWERTY_ROW1, ::handleAction, { if (shiftState != ShiftState.OFF) it.label.uppercase() else it.label })
            KeyRow(QWERTY_ROW2, ::handleAction, { if (shiftState != ShiftState.OFF) it.label.uppercase() else it.label })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FenrirShiftKey(
                    shiftState = shiftState,
                    onTap = { handleAction(KeyAction.ShiftToggle) },
                    onLongPress = { shiftState = ShiftState.CAPS_LOCK },
                    modifier = Modifier.weight(1.5f)
                )
                QWERTY_ROW3_INNER.forEach { key ->
                    FenrirKey(
                        label = if (shiftState != ShiftState.OFF) key.label.uppercase() else key.label,
                        modifier = Modifier.weight(1f),
                        onTap = { handleAction(key.action) }
                    )
                }
                FenrirRepeatKey(
                    label = "⌫",
                    modifier = Modifier.weight(1.5f),
                    accentColor = NordFrost2,
                    onAction = ::backspace
                )
            }
        } else {
            // Numeric / symbol rows
            KeyRow(NUM_ROW1, ::handleAction, { it.label })
            KeyRow(NUM_ROW2, ::handleAction, { it.label })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NUM_ROW3_INNER.forEach { key ->
                    FenrirKey(
                        label = key.label,
                        modifier = Modifier.weight(1f),
                        onTap = { handleAction(key.action) }
                    )
                }
                FenrirRepeatKey(
                    label = "⌫",
                    modifier = Modifier.weight(1.5f),
                    accentColor = NordFrost2,
                    onAction = ::backspace
                )
            }
        }

        // Bottom action bar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FenrirKey("⇥ Tab",  Modifier.weight(1.5f), accentColor = NordFrost1)  { handleAction(KeyAction.Tab) }
            FenrirKey(if (isNumLayer) "ABC" else "123", Modifier.weight(1.3f), accentColor = NordPurple) { handleAction(KeyAction.LayerToggle) }
            FenrirKey("space",  Modifier.weight(3f),   textColor  = NordSnow0.copy(alpha = 0.45f)) { handleAction(KeyAction.Space) }
            FenrirKey("↑",      Modifier.weight(1f),   accentColor = NordYellow)  { handleAction(KeyAction.HistoryUp) }
            FenrirKey("↓",      Modifier.weight(1f),   accentColor = NordYellow)  { handleAction(KeyAction.HistoryDown) }
            FenrirKey("↵",      Modifier.weight(1.5f), accentColor = NordGreen, background = NordNight2) { handleAction(KeyAction.Enter) }
        }
    }
}

// ── Private building blocks ───────────────────────────────────────────────────

@Composable
private fun KeyRow(
    keys: List<KeyDef>,
    onAction: (KeyAction) -> Unit,
    labelOf: (KeyDef) -> String,
    keyHeight: Dp = 38.dp,
    textColor: Color = NordSnow0,
    fontSize: TextUnit = 14.sp
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            FenrirKey(
                label = labelOf(key),
                modifier = Modifier.weight(key.weight).height(keyHeight),
                textColor = key.accentColor ?: textColor,
                fontSize = fontSize,
                onTap = { onAction(key.action) }
            )
        }
    }
}

@Composable
private fun FenrirKey(
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = NordSnow0,
    accentColor: Color? = null,
    background: Color = NordNight1,
    fontSize: TextUnit = 14.sp,
    onTap: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) NordNight3 else background,
        animationSpec = tween(60),
        label = "keyBg"
    )
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, NordNight3.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        try {
                            currentOnTap()
                            customWaitForUp(down.id)
                        } finally {
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = TerminalFont,
                fontSize = fontSize,
                color = accentColor ?: textColor,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun FenrirRepeatKey(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = NordFrost2,
    onAction: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (pressed) NordNight3 else NordNight1, tween(60), "repBg")
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnAction by rememberUpdatedState(onAction)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, NordNight3.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnAction()
                        val job = scope.launch {
                            delay(380)
                            while (true) {
                                currentOnAction()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                delay(48)
                            }
                        }
                        try {
                            customWaitForUp(down.id)
                        } finally {
                            job.cancel()
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontFamily = TerminalFont, fontSize = 14.sp, color = accentColor, textAlign = TextAlign.Center))
    }
}

@Composable
private fun FenrirShiftKey(
    shiftState: ShiftState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            shiftState == ShiftState.CAPS_LOCK -> NordFrost3.copy(alpha = 0.35f)
            shiftState == ShiftState.ONE_SHOT  -> NordNight3
            pressed                            -> NordNight3
            else                               -> NordNight1
        },
        tween(80), "shiftBg"
    )
    val haptic = LocalHapticFeedback.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(
                width = if (shiftState != ShiftState.OFF) 1.dp else 0.5.dp,
                color = if (shiftState != ShiftState.OFF) NordFrost1.copy(alpha = 0.8f)
                        else NordNight3.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        try {
                            val success = withTimeoutOrNull(400) {
                                customWaitForUp(down.id)
                            }
                            
                            if (success == null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnLongPress()
                                customWaitForUp(down.id)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTap()
                            }
                        } finally {
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧",
                style = TextStyle(
                    fontFamily = TerminalFont, fontSize = 14.sp,
                    color = if (shiftState != ShiftState.OFF) NordFrost1 else NordSnow0,
                    textAlign = TextAlign.Center
                )
            )
            if (shiftState == ShiftState.CAPS_LOCK) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(NordFrost1))
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.customWaitForUp(pointerId: PointerId): Boolean {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
        if (!change.pressed) {
            change.consume()
            return true
        }
        change.consume()
    }
}
