package com.stellarite.apps.data

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Finds apps that are already installed on this device and ties them to catalog entries, so something
 * installed before Stellarite Apps (or by another installer) shows as installed and gets its updates.
 * Nothing is downloaded: it compares the repo name with each installed app's name and package id.
 */
object DeviceScan {
    private class Installed(val pkg: String, val label: String)

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Returns the names of the catalog apps that were newly recognised. */
    fun run(ctx: Context, prefs: Prefs, apps: List<StoreApp>): List<String> {
        val pm = ctx.packageManager
        val known = prefs.knownModules().values.toSet()
        val installed = pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName && it.packageName !in known }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .map { Installed(it.packageName, runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.packageName)) }
        if (installed.isEmpty()) return emptyList()

        val taken = mutableSetOf<String>()
        val found = mutableListOf<String>()
        for (app in apps) {
            val key = app.repo.fullName
            if (key.contains(':') || prefs.packageFor(key) != null) continue
            val n = norm(app.repo.name)
            if (n.length < 3) continue
            val exact = installed.firstOrNull { it.pkg !in taken && norm(it.label) == n }
            val hit = exact
                ?: installed.filter { it.pkg !in taken && (norm(it.pkg.substringAfterLast('.')) == n || norm(it.pkg).endsWith(n)) }
                    .singleOrNull()
                ?: continue
            taken += hit.pkg
            prefs.setPackage(key, hit.pkg)
            found += app.repo.name
        }
        return found
    }
}
