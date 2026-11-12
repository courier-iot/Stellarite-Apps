package com.stellarite.apps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellarite.apps.data.RateInfo
import com.stellarite.apps.data.RateMeter
import com.stellarite.apps.ui.theme.SA
import kotlinx.coroutines.delay

/** The current time, ticking every [stepMs], so readings fade to "reset" without a new check. */
@Composable
private fun rememberNow(stepMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stepMs) {
        while (true) {
            delay(stepMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private data class Usage(val used: Int, val limit: Int, val percent: Int, val reset: Boolean)

private fun usageOf(r: RateInfo, now: Long): Usage {
    val reset = now >= r.resetAt
    val used = if (reset) 0 else (r.limit - r.remaining).coerceIn(0, r.limit)
    return Usage(used, r.limit, if (r.limit > 0) used * 100 / r.limit else 0, reset)
}

private fun colorFor(percent: Int): Color = when {
    percent >= 100 -> SA.Alert
    percent >= 75 -> SA.Update
    else -> SA.Accent
}

/** Usage bar for Core > Netlink: how much of GitHub's hourly limit is used, with a free CHECK. */
@Composable
fun RateBar(rate: RateInfo?, checking: Boolean, hasKey: Boolean, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val u = rate?.let { usageOf(it, now) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mono("GITHUB LIMIT", Modifier.weight(1f), spacing = 1.3.sp)
            Mono(
                if (u == null) "NOT CHECKED" else "${u.percent}% · ${u.used} / ${u.limit} USED",
                color = if (u == null) SA.Muted else colorFor(u.percent),
                spacing = 1.sp, weight = FontWeight.Medium, maxLines = 1,
            )
        }
        val fill = (u?.percent ?: 0) / 100f
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(SA.Void, SA.controlShape)
                .border(1.dp, SA.Line2, SA.controlShape)
                .semantics { contentDescription = "GitHub limit ${u?.percent ?: 0} percent used" },
        ) {
            if (fill > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill.coerceIn(0.03f, 1f))
                        .background(colorFor(u?.percent ?: 0), SA.controlShape),
                )
            }
        }
        Mono(
            when {
                rate == null -> "TAP CHECK TO READ IT FROM GITHUB"
                u!!.reset -> "LIMIT HAS RESET · LAST CHECKED ${RateMeter.clockTime(rate.checkedAt).uppercase()}"
                else -> "RESETS ${RateMeter.clockTime(rate.resetAt).uppercase()} · CHECKED ${RateMeter.clockTime(rate.checkedAt).uppercase()}"
            },
            spacing = 0.8.sp,
        )
        SaButton(if (checking) "CHECKING…" else "CHECK LIMIT", onCheck, Modifier.fillMaxWidth(), BtnStyle.OutlineLight, enabled = !checking)
        Text(
            if (hasKey) "Your Access Key gives you your own allowance of ${rate?.limit ?: 5000} requests per hour."
            else "Without an Access Key, GitHub allows 60 requests per hour for your whole connection, so other apps " +
                "on the same Wi-Fi or mobile network count too. Checking is free. Each sync uses about one request per app.",
            color = SA.Muted, fontSize = 13.sp, lineHeight = 18.sp,
        )
    }
}

/** Small warning on the catalog once three quarters of the limit is used. Shows nothing below that. */
@Composable
fun RateChip(rate: RateInfo?) {
    val now = rememberNow()
    val u = rate?.let { usageOf(it, now) } ?: return
    if (u.reset || u.percent < 75) return
    StatusChip("LIMIT ${u.percent}%", colorFor(u.percent))
}
