package com.stellarite.apps.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stellarite.apps.data.AppState
import com.stellarite.apps.data.DeviceSnapshot
import com.stellarite.apps.data.GhRepo
import com.stellarite.apps.data.GitHubClient
import com.stellarite.apps.data.InstallResult
import com.stellarite.apps.data.Installer
import com.stellarite.apps.data.ModStatus
import com.stellarite.apps.data.Prefs
import com.stellarite.apps.data.ReadmeState
import com.stellarite.apps.data.SnapModule
import com.stellarite.apps.data.Snapshot
import com.stellarite.apps.data.StoreApp
import com.stellarite.apps.data.Transfer
import com.stellarite.apps.data.Vault
import com.stellarite.apps.data.statusOf
import com.stellarite.apps.patch.AutoPatch
import com.stellarite.apps.ui.theme.SA
import com.stellarite.apps.ui.theme.ThemeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Filter { ALL, UPDATES, INSTALLED }

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val apps: List<StoreApp> = emptyList(),
    val source: String = "",
    val transfers: Map<String, Transfer> = emptyMap(),
    val readmes: Map<String, ReadmeState> = emptyMap(),
    val filter: Filter = Filter.ALL,
    val query: String = "",
    val message: String? = null,
    val autoPatch: Boolean = false,
    val excluded: Set<String> = emptySet(),
    val included: Set<String> = emptySet(),
    val vault: VaultUi = VaultUi(),
    val restore: RestoreUi? = null,
) {
    /** Modules that are installed and on the latest release. */
    val upToDate: Int get() = apps.count { it.state == AppState.INSTALLED }

    /** Modules currently set to patch themselves, given the Auto-Patch mode. */
    fun armed(): Set<String> {
        val all = apps.map { it.repo.fullName }.toSet()
        return if (autoPatch) all - excluded else all intersect included
    }
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val client = GitHubClient()
    private val ctx: Context get() = getApplication<Application>()
    private val jobs = mutableMapOf<String, Job>()

    private val _state = MutableStateFlow(
        UiState(
            source = sourceLabel(),
            autoPatch = prefs.autoPatchOn,
            excluded = prefs.patchExcluded,
            included = prefs.patchIncluded,
            vault = VaultUi(
                target = prefs.backupTarget,
                autoSnapshot = prefs.autoSnapshot,
                hasKey = prefs.token.isNotBlank(),
                owner = prefs.backupOwner.ifBlank { null },
                lastAt = prefs.lastSnapshotAt,
                lastCount = prefs.lastSnapshotCount,
            ),
        )
    )
    val state: StateFlow<UiState> = _state

    private val vaultRepo = Vault(client, prefs)

    val username: String get() = prefs.username
    val token: String get() = prefs.token

    init {
        SA.theme = ThemeId.fromKey(prefs.theme)
        AutoPatch.sync(ctx, prefs)
        refresh()
    }

    private fun sourceLabel(): String =
        if (prefs.token.isNotBlank()) "your GitHub" else "@${prefs.effectiveUser}"

    private fun tokenOrNull(): String? = prefs.token.ifBlank { null }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, source = sourceLabel()) }
            try {
                val found = client.fetchApps(prefs.effectiveUser, tokenOrNull())
                val apps = found
                    .map { (repo, release, apk) ->
                        withLocalState(StoreApp(repo, release, apk, null, null, AppState.NOT_INSTALLED))
                    }
                    .sortedWith(
                        compareBy<StoreApp> { it.state != AppState.UPDATE_AVAILABLE }
                            .thenBy { it.repo.name.lowercase() }
                    )
                _state.update { it.copy(loading = false, apps = apps, readmes = emptyMap()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not reach GitHub") }
            }
        }
    }

    /** Re-reads what is installed on the device (call when returning from the installer). */
    fun recheck() {
        _state.update { s -> s.copy(apps = s.apps.map { withLocalState(it) }) }
        refreshRestoreStatus()
        maybeAutoSnapshot()
    }

    private fun withLocalState(a: StoreApp): StoreApp {
        val pkg = prefs.packageFor(a.repo.fullName)
        val installed = Installer.installedVersion(ctx, pkg)
        val st = when {
            installed == null -> AppState.NOT_INSTALLED
            Installer.sameVersion(installed, a.release.tagName) -> AppState.INSTALLED
            else -> AppState.UPDATE_AVAILABLE
        }
        return a.copy(packageName = pkg, installedVersion = installed, state = st)
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---- Theme --------------------------------------------------------------------------------

    fun setTheme(t: ThemeId) {
        prefs.theme = t.key
        SA.theme = t
        maybeAutoSnapshot()
    }

    // ---- Auto-Patch ---------------------------------------------------------------------------

    fun setAutoPatch(on: Boolean) {
        prefs.autoPatchOn = on
        _state.update { it.copy(autoPatch = on) }
        AutoPatch.sync(ctx, prefs)
        maybeAutoSnapshot()
    }

    /**
     * Saves the Patch Roster. [armed] is the set of modules that should patch themselves.
     * ON: everything not armed becomes an exception. OFF: the armed set is the opt-in list.
     */
    fun saveRoster(armed: Set<String>) {
        val all = _state.value.apps.map { it.repo.fullName }.toSet()
        if (prefs.autoPatchOn) {
            prefs.patchExcluded = (prefs.patchExcluded - all) + (all - armed)
        } else {
            prefs.patchIncluded = (prefs.patchIncluded - all) + (armed intersect all)
        }
        _state.update {
            it.copy(excluded = prefs.patchExcluded, included = prefs.patchIncluded)
        }
        AutoPatch.sync(ctx, prefs)
        maybeAutoSnapshot()
    }

    // ---- README -------------------------------------------------------------------------------

    fun loadReadme(app: StoreApp, force: Boolean = false) {
        val key = app.repo.fullName
        val current = _state.value.readmes[key]
        if (!force && (current is ReadmeState.Loaded || current == ReadmeState.Loading || current == ReadmeState.Missing)) return
        _state.update { it.copy(readmes = it.readmes + (key to ReadmeState.Loading)) }
        viewModelScope.launch {
            val result = try {
                val text = client.fetchReadme(key, tokenOrNull())
                if (text == null) ReadmeState.Missing else ReadmeState.Loaded(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReadmeState.Failed(e.message ?: "Could not load the README")
            }
            _state.update { it.copy(readmes = it.readmes + (key to result)) }
        }
    }

    // ---- Install ------------------------------------------------------------------------------

    private fun setTransfer(key: String, t: Transfer?) = _state.update { s ->
        s.copy(transfers = if (t == null) s.transfers - key else s.transfers + (key to t))
    }

    fun install(app: StoreApp) {
        val key = app.repo.fullName
        if (jobs[key]?.isActive == true) return
        val file = File(ctx.cacheDir, "apks/${key.replace('/', '_')}-${app.release.tagName}.apk")
        jobs[key] = viewModelScope.launch {
            setTransfer(key, Transfer.Downloading(0f))
            try {
                client.download(app.apk, tokenOrNull(), file) { p ->
                    setTransfer(key, Transfer.Downloading(p))
                }
                setTransfer(key, Transfer.Installing)
                val pkg = Installer.packageNameOf(ctx, file)
                    ?: error("Downloaded file is not a valid APK")
                prefs.setPackage(key, pkg)
                val result = Installer.install(ctx, file, app.repo.name)
                setTransfer(key, null)
                if (result == InstallResult.NEEDS_PERMISSION) {
                    file.delete()
                    _state.update {
                        it.copy(message = "Grant deploy clearance, then tap Install again.")
                    }
                }
                recheck()
            } catch (e: CancellationException) {
                file.delete()
                setTransfer(key, null)
                throw e
            } catch (e: Exception) {
                file.delete()
                setTransfer(key, Transfer.Failed(e.message ?: "Transfer failed"))
            }
        }
    }

    fun cancel(app: StoreApp) {
        jobs[app.repo.fullName]?.cancel()
    }

    fun clearTransfer(app: StoreApp) = setTransfer(app.repo.fullName, null)

    fun clearCache() {
        File(ctx.cacheDir, "apks").deleteRecursively()
        _state.update { it.copy(message = "Payload cache purged.") }
    }

    fun saveSettings(username: String, token: String) {
        prefs.username = username
        prefs.token = token
        _state.update {
            it.copy(vault = it.vault.copy(hasKey = prefs.token.isNotBlank(), owner = null, note = null))
        }
        refresh()
        AutoPatch.sync(ctx, prefs)
    }

    fun reportMessage(text: String) = _state.update { it.copy(message = text) }

    // ---- Backup Vault: snapshots --------------------------------------------------------------

    private fun updateVault(f: VaultUi.() -> VaultUi) = _state.update { it.copy(vault = it.vault.f()) }

    /** What this device would record right now. Modules come from every repo installed through the store. */
    private fun localSnapshot(): DeviceSnapshot {
        val modules = prefs.knownModules().mapNotNull { (repo, pkg) ->
            Installer.installedVersion(ctx, pkg)?.let { SnapModule(repo, pkg, it) }
        }.sortedBy { it.repo }
        return DeviceSnapshot(
            name = Build.MODEL ?: "Android device",
            savedAt = System.currentTimeMillis(),
            modules = modules,
            theme = prefs.theme,
            autoPatchOn = prefs.autoPatchOn,
            excluded = prefs.patchExcluded.sorted(),
            included = prefs.patchIncluded.sorted(),
        )
    }

    fun setBackupTarget(target: String) {
        prefs.backupTarget = target
        updateVault { copy(target = target, note = null) }
        maybeAutoSnapshot()
    }

    fun setAutoSnapshot(on: Boolean) {
        prefs.autoSnapshot = on
        updateVault { copy(autoSnapshot = on) }
        if (on) maybeAutoSnapshot()
    }

    /** GitHub target only; the file target goes through the system file picker in the activity. */
    fun snapshotNow() = pushSnapshot(manual = true)

    private fun pushSnapshot(manual: Boolean) {
        if (_state.value.vault.busy) return
        val snap = localSnapshot()
        viewModelScope.launch {
            updateVault { copy(busy = true, note = null) }
            try {
                vaultRepo.push(snap)
                recordSnapshot(snap)
                updateVault { copy(busy = false, owner = prefs.backupOwner.ifBlank { null }) }
                if (manual) reportMessage("Snapshot saved.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateVault { copy(busy = false, note = e.message ?: "Snapshot failed") }
            }
        }
    }

    private fun recordSnapshot(snap: DeviceSnapshot) {
        prefs.lastSnapshotAt = snap.savedAt
        prefs.lastSnapshotCount = snap.modules.size
        prefs.lastSnapshotHash = snap.fingerprint()
        updateVault { copy(lastAt = snap.savedAt, lastCount = snap.modules.size, note = null) }
    }

    /** Saves a snapshot after installs, removals, skin or roster changes, when Auto-Snapshot is on. */
    private fun maybeAutoSnapshot() {
        if (!prefs.autoSnapshot || prefs.backupTarget != "github" || prefs.token.isBlank()) return
        if (_state.value.vault.busy) return
        val fp = localSnapshot().fingerprint()
        if (fp == prefs.lastSnapshotHash || fp == prefs.lastAttemptHash) return
        prefs.lastAttemptHash = fp
        pushSnapshot(manual = false)
    }

    /** File target: the text the activity writes to the file the user picked. */
    fun snapshotText(): String = Snapshot().withDevice(prefs.deviceId, localSnapshot()).toText()

    fun fileSnapshotSaved() {
        val snap = localSnapshot()
        recordSnapshot(snap)
        reportMessage("Snapshot saved to file.")
    }

    // ---- Backup Vault: restore ----------------------------------------------------------------

    private fun updateRestore(f: (RestoreUi) -> RestoreUi) =
        _state.update { s -> s.restore?.let { s.copy(restore = f(it)) } ?: s }

    private fun installedNow(modules: List<SnapModule>): Map<String, String?> =
        modules.associate { it.repo to Installer.installedVersion(ctx, it.pkg ?: prefs.packageFor(it.repo)) }

    private fun refreshRestoreStatus() = updateRestore { r ->
        val dev = r.snapshot?.devices?.get(r.deviceId) ?: return@updateRestore r
        r.copy(installed = installedNow(dev.modules))
    }

    private fun defaultSelection(dev: DeviceSnapshot, installed: Map<String, String?>): Set<String> =
        dev.modules.filter { statusOf(it, installed[it.repo]) != ModStatus.INSTALLED }.map { it.repo }.toSet()

    fun openRestoreFromGithub() {
        _state.update { it.copy(restore = RestoreUi(loading = true)) }
        viewModelScope.launch {
            try {
                val snap = vaultRepo.pull()
                val owner = prefs.backupOwner
                showRestore(snap, "GITHUB · $owner")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(restore = RestoreUi(error = e.message ?: "Could not read the snapshot")) }
            }
        }
    }

    fun openRestoreFromText(text: String) {
        try {
            showRestore(Snapshot.parse(text), "FILE")
        } catch (e: Exception) {
            _state.update { it.copy(restore = RestoreUi(error = e.message ?: "Could not read that file")) }
        }
    }

    private fun showRestore(snap: Snapshot, source: String) {
        // Prefer the newest snapshot from another device; fall back to this device's own.
        val pick = snap.devices.entries.filter { it.key != prefs.deviceId }.maxByOrNull { it.value.savedAt }
            ?: snap.devices.entries.firstOrNull()
        if (pick == null) {
            _state.update { it.copy(restore = RestoreUi(error = "That snapshot is empty.")) }
            return
        }
        val installed = installedNow(pick.value.modules)
        _state.update {
            it.copy(
                restore = RestoreUi(
                    source = source,
                    snapshot = snap,
                    deviceId = pick.key,
                    installed = installed,
                    selected = defaultSelection(pick.value, installed),
                )
            )
        }
    }

    fun closeRestore() = _state.update { it.copy(restore = null) }

    fun selectRestoreDevice(id: String) = updateRestore { r ->
        val dev = r.snapshot?.devices?.get(id) ?: return@updateRestore r
        val installed = installedNow(dev.modules)
        r.copy(deviceId = id, installed = installed, selected = defaultSelection(dev, installed))
    }

    fun setRestoreMode(mode: RestoreMode) = updateRestore { it.copy(mode = mode) }

    fun toggleRestoreModule(repo: String) = updateRestore {
        it.copy(selected = if (repo in it.selected) it.selected - repo else it.selected + repo)
    }

    fun setRestoreSettings(on: Boolean) = updateRestore { it.copy(includeSettings = on) }

    private fun applySettings(dev: DeviceSnapshot) {
        prefs.theme = dev.theme
        SA.theme = ThemeId.fromKey(dev.theme)
        prefs.autoPatchOn = dev.autoPatchOn
        prefs.patchExcluded = dev.excluded.toSet()
        prefs.patchIncluded = dev.included.toSet()
        _state.update {
            it.copy(autoPatch = dev.autoPatchOn, excluded = prefs.patchExcluded, included = prefs.patchIncluded)
        }
        AutoPatch.sync(ctx, prefs)
    }

    /** Installs the ticked modules one after another; Android still confirms each install. */
    fun runRestore() {
        val r = _state.value.restore ?: return
        if (r.progress != null) return
        val dev = r.snapshot?.devices?.get(r.deviceId) ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            updateRestore { it.copy(done = "Grant Deploy Clearance in Control Core first, then restore.") }
            return
        }
        val chosen = dev.modules.filter { it.repo in r.selected }
        viewModelScope.launch {
            // Teach the store about modules that are already on this device.
            dev.modules.forEach { m ->
                if (m.pkg != null && Installer.installedVersion(ctx, m.pkg) != null) prefs.setPackage(m.repo, m.pkg)
            }
            if (r.includeSettings) applySettings(dev)
            val problems = mutableListOf<String>()
            for ((i, m) in chosen.withIndex()) {
                updateRestore { it.copy(progress = "Restoring ${i + 1} of ${chosen.size} · ${m.name}", done = null) }
                try {
                    val resolved = client.resolveModule(
                        m.repo,
                        if (r.mode == RestoreMode.RECORDED) m.version else null,
                        tokenOrNull(),
                    )
                    if (resolved == null) {
                        problems += "${m.name}: no APK release found"
                        continue
                    }
                    val (release, apk) = resolved
                    val repo = GhRepo(
                        name = m.name,
                        fullName = m.repo,
                        htmlUrl = "https://github.com/${m.repo}",
                    )
                    val app = StoreApp(repo, release, apk, m.pkg, null, AppState.NOT_INSTALLED)
                    val before = Installer.installedVersion(ctx, m.pkg ?: prefs.packageFor(m.repo))
                    install(app)
                    jobs[m.repo]?.join()
                    val failed = _state.value.transfers[m.repo] as? Transfer.Failed
                    if (failed != null) {
                        problems += "${m.name}: ${failed.message}"
                        setTransfer(m.repo, null)
                        continue
                    }
                    awaitInstallChange(prefs.packageFor(m.repo), before)
                    recheck()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    problems += "${m.name}: ${e.message ?: "failed"}"
                }
            }
            recheck()
            updateRestore {
                it.copy(
                    progress = null,
                    done = if (problems.isEmpty()) "Restore complete." else "Finished with problems: " + problems.joinToString("; "),
                )
            }
        }
    }

    /** Waits (up to a minute) for the user to confirm the system install prompt. */
    private suspend fun awaitInstallChange(pkg: String?, before: String?) {
        if (pkg == null) return
        repeat(60) {
            if (Installer.installedVersion(ctx, pkg) != before) return
            delay(1000)
        }
    }
}
