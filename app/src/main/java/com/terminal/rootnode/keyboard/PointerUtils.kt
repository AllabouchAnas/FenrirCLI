package com.terminal.rootnode.keyboard

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.withTimeoutOrNull

// ─────────────────────────────────────────────────────────────────────────────
// Pointer Utilities
//
// Low-level pointer helpers that give precise, race-condition-free control
// over press/release detection within an AwaitPointerEventScope.
//
// Design decisions:
//  - All helpers consume events they handle to prevent parent scroll
//    containers from intercepting key presses.
//  - "waitForUpOrCancel" returns null on cancel, so callers can decide
//    whether to treat a cancel as a release.
//  - Long-press detection is built with withTimeoutOrNull so it integrates
//    cleanly with coroutine cancellation.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Waits for the pointer with [pointerId] to be released.
 *
 * Consumes all move and up events for this pointer.
 * Returns `true` when the pointer is released normally.
 * Returns `false` if the pointer is cancelled or removed from the event list.
 *
 * This is safe across recompositions because the scope itself persists.
 */
internal suspend fun AwaitPointerEventScope.waitForRelease(pointerId: PointerId): Boolean {
    while (true) {
        val event  = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId }

        // Pointer disappeared (e.g. gesture stolen by parent, or multitouch)
        if (change == null) return false

        change.consume()

        // Pointer lifted
        if (!change.pressed) return true
    }
}

/**
 * Awaits a long-press on [down] by waiting [longPressTimeoutMs] for a release.
 *
 * @return `true`  if the pointer was held for [longPressTimeoutMs] without
 *                 being released (long press detected).
 *         `false` if the pointer was released before the timeout (normal tap).
 *         `null`  if the pointer was cancelled/stolen.
 */
internal suspend fun AwaitPointerEventScope.detectLongPress(
    down: PointerInputChange,
    longPressTimeoutMs: Long = 400L,
): Boolean? {
    // withTimeoutOrNull returns null on timeout, or the block result if it finishes first
    val released = withTimeoutOrNull(longPressTimeoutMs) {
        waitForRelease(down.id)
    }
    return when {
        released == null -> true   // timeout fired = long press
        released         -> false  // released before timeout = tap
        else             -> null   // cancelled
    }
}
