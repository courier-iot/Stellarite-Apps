package com.stellarite.apps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * A short lock on an app's action button right after a download finishes, so repeated taps can't
 * start the same install twice. It ends early as soon as the install is confirmed on the device.
 * Held in memory only: it never needs to survive a restart.
 */
object Cooldown {
    const val SECONDS = 30

    private val until = mutableStateMapOf<String, Long>()

    fun start(key: String) {
        until[key] = System.currentTimeMillis() + SECONDS * 1000L
    }

    /** Lifts the lock, e.g. once the new version is confirmed installed. */
    fun clear(key: String) {
        until.remove(key)
    }

    fun active(key: String): Boolean = (until[key] ?: 0L) > System.currentTimeMillis()

    /** Seconds left for [key]; 0 when free. Recomposes as the countdown ticks. */
    @Composable
    fun left(key: String): Int {
        val end = until[key] ?: return 0
        var now by remember(end) { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(end) {
            while (true) {
                now = System.currentTimeMillis()
                if (now >= end) break
                delay(500)
            }
        }
        return ((end - now + 999) / 1000).toInt().coerceAtLeast(0)
    }
}
