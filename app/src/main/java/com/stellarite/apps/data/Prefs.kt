package com.stellarite.apps.data

import android.content.Context
import androidx.core.content.edit
import com.stellarite.apps.Config
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local settings and the repo -> package-name map learned from downloaded APKs. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("stellarite", Context.MODE_PRIVATE)

    /** Custom GitHub username; blank means "use the built-in Stellarite catalog". */
    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit { putString("username", v.trim().removePrefix("@")) }

    /** Personal access token; when set, the token owner's own repos are shown. */
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit {
            putString("token", v.trim())
            remove("backupOwner") // the owner is re-learned from the new key
        }

    /** The account to browse when no token is set. */
    val effectiveUser: String get() = username.ifBlank { Config.DEFAULT_OWNER }

    /** Saved theme key ("cyber" or "steam"). */
    var theme: String
        get() = sp.getString("theme", "cyber") ?: "cyber"
        set(v) = sp.edit { putString("theme", v) }

    /**
     * Auto-Patch master switch.
     * ON: every module patches itself except those in [patchExcluded].
     * OFF: only modules in [patchIncluded] patch themselves.
     */
    var autoPatchOn: Boolean
        get() = sp.getBoolean("autoPatchOn", false)
        set(v) = sp.edit { putBoolean("autoPatchOn", v) }

    var patchExcluded: Set<String>
        get() = sp.getStringSet("patchExcluded", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet("patchExcluded", v) }

    var patchIncluded: Set<String>
        get() = sp.getStringSet("patchIncluded", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet("patchIncluded", v) }

    fun shouldAutoPatch(repoFullName: String): Boolean =
        if (autoPatchOn) repoFullName !in patchExcluded else repoFullName in patchIncluded

    /** True when the background updater has any work to do. */
    val autoPatchActive: Boolean get() = autoPatchOn || patchIncluded.isNotEmpty()

    // ---- Extra sources ------------------------------------------------------------------------

    private val sourcesJson = Json { ignoreUnknownKeys = true }

    /** Extra places to get modules from, in the order the user added them. */
    var sources: List<Source>
        get() = runCatching {
            sourcesJson.decodeFromString<List<Source>>(sp.getString("sources", "[]") ?: "[]")
        }.getOrDefault(emptyList())
        set(v) = sp.edit { putString("sources", sourcesJson.encodeToString(v)) }

    // ---- Backup Vault -------------------------------------------------------------------------

    /** "github" or "file". */
    var backupTarget: String
        get() = sp.getString("backupTarget", "github") ?: "github"
        set(v) = sp.edit { putString("backupTarget", v) }

    var autoSnapshot: Boolean
        get() = sp.getBoolean("autoSnapshot", false)
        set(v) = sp.edit { putBoolean("autoSnapshot", v) }

    /** Login of the Access Key's owner; the backup repo lives in this account. */
    var backupOwner: String
        get() = sp.getString("backupOwner", "") ?: ""
        set(v) = sp.edit { putString("backupOwner", v) }

    var lastSnapshotAt: Long
        get() = sp.getLong("lastSnapshotAt", 0L)
        set(v) = sp.edit { putLong("lastSnapshotAt", v) }

    var lastSnapshotCount: Int
        get() = sp.getInt("lastSnapshotCount", 0)
        set(v) = sp.edit { putInt("lastSnapshotCount", v) }

    /** Fingerprint of the last snapshot that was saved, so Auto-Snapshot only fires on changes. */
    var lastSnapshotHash: Int
        get() = sp.getInt("lastSnapshotHash", 0)
        set(v) = sp.edit { putInt("lastSnapshotHash", v) }

    /** Fingerprint of the last automatic attempt, so a failing upload is not retried in a loop. */
    var lastAttemptHash: Int
        get() = sp.getInt("lastAttemptHash", 0)
        set(v) = sp.edit { putInt("lastAttemptHash", v) }

    /** Random id that names this device's section inside a shared snapshot. */
    val deviceId: String
        get() = sp.getString("deviceId", null)
            ?: java.util.UUID.randomUUID().toString().also { id -> sp.edit { putString("deviceId", id) } }

    /** Every repo whose package name we have learned: repo full name -> package. */
    fun knownModules(): Map<String, String> =
        sp.all.entries
            .filter { it.key.startsWith("pkg:") && it.value is String }
            .associate { it.key.removePrefix("pkg:") to (it.value as String) }

    fun packageFor(repoFullName: String): String? = sp.getString("pkg:$repoFullName", null)

    fun setPackage(repoFullName: String, pkg: String) =
        sp.edit { putString("pkg:$repoFullName", pkg) }

    // ---- Repo signing keys (remembered the first time a non-pinned repo is used) --------------

    fun repoKey(url: String): String? = sp.getString("repoKey:$url", null)

    fun setRepoKey(url: String, fingerprint: String) = sp.edit { putString("repoKey:$url", fingerprint) }
}
