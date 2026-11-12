package com.stellarite.apps.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class SavedApp(val repo: GhRepo, val release: GhRelease, val apk: GhAsset)

@Serializable
internal data class SavedCatalog(
    val savedAt: Long,
    /** Which settings produced this list; a list made for other settings is never shown. */
    val key: String,
    val apps: List<SavedApp>,
    val homeCount: Int,
    val counts: Map<String, Int> = emptyMap(),
    val pending: List<GhRepo> = emptyList(),
)

/** A catalog read from the saved copy, with the time it was synced. */
data class SavedResult(val result: CatalogResult, val savedAt: Long)

/**
 * The last successful catalog, kept on the device. Reopening the store shows it straight away instead of
 * asking GitHub again, and it stays on screen when GitHub can't be reached or the hourly limit is used up.
 * Install state is not saved; it is always read fresh from the device.
 */
object CatalogCache {
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(ctx: Context) = File(ctx.filesDir, "catalog.json")

    /** What the list depends on. The Access Key itself is never written, only whether one is set. */
    fun keyFor(prefs: Prefs): String =
        prefs.effectiveUser.lowercase() + "|" + prefs.token.isNotBlank() + "|" +
            prefs.sources.filter { it.enabled }.joinToString(",") { it.id + "=" + it.value + ":" + it.picks.joinToString("+") }

    fun save(ctx: Context, prefs: Prefs, result: CatalogResult, at: Long = System.currentTimeMillis()) {
        val saved = SavedCatalog(
            savedAt = at,
            key = keyFor(prefs),
            apps = result.apps.map { SavedApp(it.repo, it.release, it.apk) },
            homeCount = result.homeCount,
            counts = result.counts,
            pending = result.pending,
        )
        try {
            val f = file(ctx)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(saved))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (_: Exception) {
            // A missing saved copy only means the next open asks GitHub again.
        }
    }

    /** The saved catalog for the current settings, or null if there is none. */
    fun load(ctx: Context, prefs: Prefs): SavedResult? = try {
        val saved = json.decodeFromString<SavedCatalog>(file(ctx).readText())
        if (saved.key != keyFor(prefs)) null
        else SavedResult(
            CatalogResult(
                apps = saved.apps.map { StoreApp(it.repo, it.release, it.apk, null, null, AppState.NOT_INSTALLED) },
                homeCount = saved.homeCount,
                counts = saved.counts,
                errors = emptyMap(),
                pending = saved.pending,
            ),
            saved.savedAt,
        )
    } catch (_: Exception) {
        null
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
