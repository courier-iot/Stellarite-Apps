package com.stellarite.apps.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Response
import java.text.DateFormat
import java.util.Date

/** GitHub's hourly API allowance as last reported to this device. */
data class RateInfo(
    val limit: Int,
    val remaining: Int,
    /** Epoch millis when the allowance refills. */
    val resetAt: Long,
    /** Epoch millis of the reading. */
    val checkedAt: Long,
) {
    /** True once the reset time has passed, so the reading no longer applies. */
    val expired: Boolean get() = System.currentTimeMillis() >= resetAt

    val used: Int get() = if (expired) 0 else (limit - remaining).coerceIn(0, limit)

    /** Share of the hourly allowance used, 0..100. */
    val percent: Int get() = if (limit <= 0) 0 else used * 100 / limit

    val exhausted: Boolean get() = !expired && remaining <= 0
}

/**
 * Keeps track of GitHub's rate limit from the X-RateLimit headers of every API answer. GitHub counts
 * per internet connection (IP address) without an Access Key, so other apps on the same connection
 * use the same allowance; the reading is only exact at the moment it was taken.
 */
object RateMeter {
    private val _state = MutableStateFlow<RateInfo?>(null)
    val state: StateFlow<RateInfo?> = _state
    val current: RateInfo? get() = _state.value

    @Volatile private var sp: SharedPreferences? = null

    /** Loads the last saved reading. Safe to call more than once (UI and background worker). */
    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("stellarite_rate", Context.MODE_PRIVATE)
        sp = p
        if (_state.value == null && p.contains("limit")) {
            _state.value = RateInfo(
                p.getInt("limit", 60), p.getInt("remaining", 60),
                p.getLong("resetAt", 0L), p.getLong("checkedAt", 0L),
            )
        }
    }

    /** Called for every response from api.github.com. Answers without the headers are ignored. */
    fun record(resp: Response) {
        val limit = resp.header("X-RateLimit-Limit")?.toIntOrNull() ?: return
        val remaining = resp.header("X-RateLimit-Remaining")?.toIntOrNull() ?: return
        val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull() ?: return
        // Only the "core" allowance matters here; search and GraphQL have their own.
        val resource = resp.header("X-RateLimit-Resource")
        if (resource != null && resource != "core") return
        set(RateInfo(limit, remaining, reset * 1000L, System.currentTimeMillis()))
    }

    /** [exact] readings (from the rate_limit check) replace whatever was known, e.g. after a network change. */
    @Synchronized
    fun set(info: RateInfo, exact: Boolean = false) {
        val old = _state.value
        // Answers to parallel requests can arrive out of order: within one window keep the lowest count.
        if (!exact && old != null && !old.expired && old.limit == info.limit && old.resetAt == info.resetAt &&
            info.remaining > old.remaining
        ) return
        _state.value = info
        sp?.edit {
            putInt("limit", info.limit)
            putInt("remaining", info.remaining)
            putLong("resetAt", info.resetAt)
            putLong("checkedAt", info.checkedAt)
        }
    }

    fun clockTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
}
