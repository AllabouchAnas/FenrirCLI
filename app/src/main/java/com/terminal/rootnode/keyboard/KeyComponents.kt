package com.terminal.rootnode.keyboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminal.rootnode.ui.theme.NordFrost1
import com.terminal.rootnode.ui.theme.NordFrost3
import com.terminal.rootnode.ui.theme.NordNight1
import com.terminal.rootnode.ui.theme.NordNight3
import com.terminal.rootnode.ui.theme.NordSnow0
import com.terminal.rootnode.ui.theme.TerminalFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Key Composables
//
// This file contains three focused composables:
//   - FenrirKey       — standard tap key
//   - FenrirRepeatKey — backspace key with auto-repeat
//   - FenrirShiftKey  — shift key with state machine visualization
//
// All three share the same visual DNA but have distinct interaction models.
// They deliberately avoid sharing a single monolithic implementation so that
// each can be reasoned about independently.
// ─────────────────────────────────────────────────────────────────────────────

// ── Shared visual constants ───────────────────────────────────────────────────

private val KEY_SHAPE          = RoundedCornerShape(7.dp)
private val DEFAULT_KEY_HEIGHT = 38.dp
private val BORDER_WIDTH       = 0.5.dp
private val BORDER_IDLE        = NordNight3.copy(alpha = 0.7f)

// ── Auto-repeat timings ───────────────────────────────────────────────────────

/** Delay before backspace starts repeating (ms). */
private const val REPEAT_INITIAL_DELAY_MS = 400L

/** Interval between repeated backspace events (ms). */
private const val REPEAT_INTERVAL_MS = 45L

// ─────────────────────────────────────────────────────────────────────────────
// FenrirKey — standard tap key
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single keyboard key that fires [onTap] on press-down.
 *
 * Interaction model:
 *  - Action fires immediately on pointer DOWN (not on up) for low latency.
 *  - The pressed state is animated and corrects itself even if the pointer
 *    is cancelled or stolen by a parent scroll container.
 *  - Touch events are fully consumed so parent gesture detectors cannot
 *    intercept them.
 *
 * Accessibility:
 *  - Exposes role = Button and a content description.
 *  - Reflects the pressed state in semantics.
 */
@Composable
internal fun FenrirKey(
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = NordSnow0,
    accentColor: Color? = null,
    background: Color = NordNight1,
    fontSize: TextUnit = 14.sp,
    height: Dp = DEFAULT_KEY_HEIGHT,
    a11yLabel: String = label,
    onTap: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    // Capture stable references to lambdas — prevents stale captures after
    // recomposition without restarting the pointer loop.
    val currentOnTap by rememberUpdatedState(onTap)
    val haptic = LocalHapticFeedback.current

    // Background color animation
    val bg by animateColorAsState(
        targetValue = if (pressed) NordNight3 else background,
        animationSpec = tween(durationMillis = 60),
        label = "FenrirKey_bg",
    )

    // Subtle scale-down on press to add tactile feel
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "FenrirKey_scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .scale(scale)
            .clip(KEY_SHAPE)
            .background(bg)
            .border(BORDER_WIDTH, BORDER_IDLE, KEY_SHAPE)
            .semantics {
                role = Role.Button
                contentDescription = a11yLabel
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Wait for any finger-down, even if already consumed above us
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true

                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnTap()

                        // Wait for release, consume everything in between
                        try {
                            waitForRelease(down.id)
                        } finally {
                            pressed = false
                        }
                    }
                }
            },
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = TerminalFont,
                fontSize    = fontSize,
                color       = accentColor ?: textColor,
                textAlign   = TextAlign.Center,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FenrirRepeatKey — auto-repeating key (backspace)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A key that fires [onAction] immediately on press, then repeats it
 * at [REPEAT_INTERVAL_MS] after an initial [REPEAT_INITIAL_DELAY_MS] hold.
 *
 * The repeat coroutine is scoped to the Compose scope and is cancelled
 * cleanly when the pointer is released, preventing coroutine leaks.
 */
@Composable
internal fun FenrirRepeatKey(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = NordFrost1,
    height: Dp = DEFAULT_KEY_HEIGHT,
    a11yLabel: String = label,
    onAction: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    val currentOnAction by rememberUpdatedState(onAction)
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()

    val bg by animateColorAsState(
        targetValue  = if (pressed) NordNight3 else NordNight1,
        animationSpec = tween(60),
        label        = "RepeatKey_bg",
    )

    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "RepeatKey_scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .scale(scale)
            .clip(KEY_SHAPE)
            .background(bg)
            .border(BORDER_WIDTH, BORDER_IDLE, KEY_SHAPE)
            .semantics {
                role = Role.Button
                contentDescription = a11yLabel
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true

                        // Fire immediately on press
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnAction()

                        // Start auto-repeat coroutine
                        val repeatJob = scope.launch {
                            delay(REPEAT_INITIAL_DELAY_MS)
                            while (true) {
                                currentOnAction()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                delay(REPEAT_INTERVAL_MS)
                            }
                        }

                        try {
                            waitForRelease(down.id)
                        } finally {
                            // Always cancel — even if pointer was stolen or coroutine cancelled
                            repeatJob.cancel()
                            pressed = false
                        }
                    }
                }
            },
    ) {
        Text(
            text  = label,
            style = TextStyle(
                fontFamily = TerminalFont,
                fontSize   = 14.sp,
                color      = accentColor,
                textAlign  = TextAlign.Center,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FenrirShiftKey — shift key with state machine visualization
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The Shift key.
 *
 * Visualizes [shiftState] with animated background and border.
 * Dispatches [KeyAction.ShiftTap] on tap and [KeyAction.ShiftLongPress] on hold.
 *
 * Long-press detection uses [LONG_PRESS_TIMEOUT_MS] timeout:
 *   - Released before timeout → tap
 *   - Still pressed at timeout → long press; waits for release, then returns
 */
private const val LONG_PRESS_TIMEOUT_MS = 400L

@Composable
internal fun FenrirShiftKey(
    shiftState: ShiftState,
    modifier: Modifier = Modifier,
    height: Dp = DEFAULT_KEY_HEIGHT,
    onDispatch: (KeyAction) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    val currentDispatch by rememberUpdatedState(onDispatch)
    val haptic = LocalHapticFeedback.current

    // Background: reflects shift state
    val bg by animateColorAsState(
        targetValue = when {
            shiftState == ShiftState.CAPS_LOCK -> NordFrost3.copy(alpha = 0.30f)
            shiftState == ShiftState.ONE_SHOT  -> NordNight3.copy(alpha = 0.85f)
            pressed                            -> NordNight3
            else                               -> NordNight1
        },
        animationSpec = tween(80),
        label         = "ShiftKey_bg",
    )

    // Border: highlighted when shift is active
    val borderColor by animateColorAsState(
        targetValue = if (shiftState.isActive) NordFrost1.copy(alpha = 0.9f) else BORDER_IDLE,
        animationSpec = tween(80),
        label         = "ShiftKey_border",
    )
    val borderWidth = if (shiftState.isActive) 1.dp else BORDER_WIDTH

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .clip(KEY_SHAPE)
            .background(bg)
            .border(borderWidth, borderColor, KEY_SHAPE)
            .semantics {
                role = Role.Button
                contentDescription = when (shiftState) {
                    ShiftState.OFF       -> "Shift"
                    ShiftState.ONE_SHOT  -> "Shift active"
                    ShiftState.CAPS_LOCK -> "Caps Lock"
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true

                        try {
                            val isLongPress = detectLongPress(down, LONG_PRESS_TIMEOUT_MS)
                            when (isLongPress) {
                                true -> {
                                    // Long press detected
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentDispatch(KeyAction.ShiftLongPress)
                                    // Wait for finger to lift before resetting pressed
                                    waitForRelease(down.id)
                                }
                                false -> {
                                    // Normal tap
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentDispatch(KeyAction.ShiftTap)
                                }
                                null -> {
                                    // Pointer was cancelled — do nothing
                                }
                            }
                        } finally {
                            pressed = false
                        }
                    }
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Icon switches between ⇧ (shift) and ⇪ (caps lock)
            Text(
                text  = if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧",
                style = TextStyle(
                    fontFamily = TerminalFont,
                    fontSize   = 14.sp,
                    color      = if (shiftState.isActive) NordFrost1 else NordSnow0,
                    textAlign  = TextAlign.Center,
                ),
            )
            // Indicator dot for CAPS_LOCK only
            if (shiftState == ShiftState.CAPS_LOCK) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(NordFrost1),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KeyRow helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a horizontal row of [FenrirKey]s from a list of [KeyDefinition]s.
 *
 * [labelOf] allows the caller to override the displayed label dynamically
 * (e.g., applying uppercase when shift is active).
 */
@Composable
internal fun KeyRow(
    keys: List<KeyDefinition>,
    onDispatch: (KeyAction) -> Unit,
    labelOf: (KeyDefinition) -> String = { it.label },
    keyHeight: Dp = DEFAULT_KEY_HEIGHT,
    defaultTextColor: Color = NordSnow0,
    fontSize: TextUnit = 14.sp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            FenrirKey(
                label      = labelOf(key),
                modifier   = Modifier.weight(key.weight),
                textColor  = defaultTextColor,
                accentColor = key.accentColor,
                fontSize   = fontSize,
                height     = keyHeight,
                a11yLabel  = key.a11yLabel,
                onTap      = { onDispatch(key.action) },
            )
        }
    }
}
