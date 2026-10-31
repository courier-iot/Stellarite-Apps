# Stellarite Apps

A personal app store for Android. It lists the modules (apps) published in a GitHub account's releases, shows each repo's README, then downloads and installs the APKs. Debug/test build only, no Play Store or release signing.

> This code has not been compiled yet. Expect a few small fixes on the first Gradle sync.

## Run it

1. Open `StellariteApps/` in Android Studio (Koala or newer). It will sync Gradle and create the wrapper scripts.
2. `DEFAULT_OWNER` in `app/src/main/java/com/stellarite/apps/Config.kt` is set to `courier-iot`. This is the built-in catalog every user sees first; change it there if the account ever moves.
3. Run the `app` configuration on a device or emulator (Android 8.0+).
4. The first time you install something, Android asks you to allow installs from Stellarite Apps. Control Core shows the clearance state.

## What shows up in the catalog

A repo appears when it is not a fork or archived, and its **latest release** has an `.apk` asset. Pre-releases and drafts are skipped. If a release has several APKs, one named `universal` or `release` is preferred.

Tag your releases with the app's `versionName` (`v1.2.0` or `1.2.0` both work). Update detection compares the installed `versionName` against the latest tag. The status line reads `SYNCED · up-to-date/total MODULES`.

## Sources

Besides the built-in **Home Archive** (shown as "Made by The Courier"), Control Core > Sources lets you paste a link:

- `github.com/owner` adds every qualifying repo of an account or org.
- `github.com/owner/repo` adds one repo.
- **Stars** adds your starred repos (first 100; needs an Access Key).
- An F-Droid-style repo address (F-Droid, IzzyOnDroid, others) opens that archive (see Archives): only the apps you tick join the catalog.

Sources can be switched off or removed, are included in Backup Vault snapshots, and are covered by Auto-Patch. F-Droid downloads are verified against the SHA-256 in the index, and your Access Key is only ever sent to api.github.com.

## Archives

The ARCHIVES tab lists large open-source repositories. F-Droid is built in; any F-Droid-style source you add also appears. Opening one gives the whole index A to Z with sticky letters and a letter rail, search, app icons, and filters: fits this device (CPU and Android version), hide anti-features, category, updated, and sort. Tick apps and tap ADD to put them in your catalog.

On first open (or when the daily cache expires) the index is downloaded once. On mobile data you are asked first. The index is checked against the repository's signed `entry.jar` before use: F-Droid's signing key is built into the app; for other repos the key is remembered the first time and a change is refused. A repo with no signature file is usable but shown as UNSIGNED.

## Module detail and README

Under the release notes, the detail screen pulls the repo's README live from GitHub and renders it (headings, lists, code, quotes, tables, links). Images and raw HTML are skipped, relative links point at the repo, and long READMEs are trimmed with an "Open full README on GitHub" button.

## Control Core (settings)

- **Uplink**: Operator Handle (GitHub username) and Access Key (token). Blank handle, no key = the built-in catalog. Another handle = that account's public releases. A token = the owner's own repos, including private ones. Unauthenticated requests are limited to 60/hour per device; a token raises that. The token stays in the app's private storage.
- **Visor Skin**: Stellarite Cyber (graphite, cyan, crimson alerts) or Brassworks Steampunk (leather, brass, copper, serif type).
- **Auto-Patch Protocol**: master switch.
  - ON: every module patches itself except those you untick in the Patch Roster.
  - OFF: the Patch Roster opens so you can tick the few modules that should still patch themselves.
- **Deployment Core**: Deploy Clearance (install permission) and Payload Cache purge.
- **Commit & Sync** saves the Uplink fields and reloads the catalog.

## Backup Vault

Control Core has a **Backup Vault** panel that records which modules (and versions) you have installed, plus your skin and Patch Roster, so you can restore them on another device. It never stores your Access Key or any app data.

- **File target:** SNAPSHOT NOW saves `stellarite-snapshot.json` through the system file picker (Drive, Syncthing, USB, NAS, anywhere). RESTORE opens a file you pick. No account or server involved.
- **GitHub target:** snapshots go to `snapshot.json` in a private repo named `stellarite-backup` in the account that owns your Access Key. Every save is a commit, so you get history for free. Auto-Snapshot saves after installs, removals, skin and roster changes.
- Each device only rewrites its own section of the file, so two devices never overwrite each other. Restore lets you pick which device's snapshot to use.
- **Restore** shows each module as MISSING, INSTALLED or "HAS vX" and ticks what needs restoring. "As recorded" installs the exact recorded release (found by its tag); "Latest" installs the newest release. Android still asks you to confirm each install, and Deploy Clearance must be granted first.

### Access Key permissions for GitHub backup

The one thing the GitHub target strictly needs is **Contents read and write on the `stellarite-backup` repo**.

- Safest: create an empty private repo named `stellarite-backup` yourself (tick "Add a README"), then use a fine-grained token limited to that repo with Contents read and write. (A token that also lists your repos for the catalog needs read access to those too.)
- Easiest: a classic token with the `repo` scope also lets the app create the repo for you on the first snapshot.
- If the app can't create the repo with your key, it says so and tells you to create it by hand. I could not confirm from GitHub's docs whether fine-grained tokens are allowed to create personal repos, so treat creation as a convenience, not something to rely on.

Known limits: Android will not install an older version over a newer one, so "as recorded" can't downgrade a module. Only modules installed through Stellarite Apps are recorded.

## Auto-Patch details

A background job runs about every 6 hours on a network connection. Installs go through Android's PackageInstaller, so on Android 12+ updates to apps this store originally installed can complete without a prompt. Where Android still requires confirmation (older versions, or a first install), you get a notification to tap. Enabling Auto-Patch asks for notification permission on Android 13+.

## Known limits

- An app is only recognised as installed after it has been installed through Stellarite Apps once (the package name is read from the downloaded APK).
- Updates only install over apps signed with the same key.
- F-Droid modules cannot be downgraded. Index signature checking uses Android's JAR verifier; if a repo signs with an algorithm Android refuses, the sync fails rather than skipping the check.
- Icons in Archives load from the repository over the network (via Coil).
- Stars are capped at the first 100.
- App icons are generic glyphs, since GitHub releases don't carry artwork.
- Fonts are system fonts (monospace/sans for Cyber, serif for Steampunk); IBM Plex can be bundled later.

## Layout

- `data/` GitHub client, models, installer, local prefs, snapshot model and vault (backup)
- `patch/` Auto-Patch worker, install result receiver, notifications
- `ui/` view model, Compose screens (Catalog, Detail, Settings, Patch Roster sheet), Markdown renderer, theme and shared components
- `MainActivity.kt` navigation between Catalog, Detail and Control Core
