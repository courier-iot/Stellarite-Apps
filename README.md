# Stellarite Apps

**A small, personal app launcher and installer for Android.**

Stellarite Apps puts your apps in one place. It lists the apps published as GitHub releases, installs them, keeps them up to date, and opens a door into big open-source catalogs like F-Droid. It was made mainly as the launcher for The Courier's own apps, and works just as well for yours.

Two looks to choose from: **Stellarite Cyber** (graphite, cyan and crimson) and **Brassworks Steampunk** (leather, brass and copper).

- Android 8.0 or newer
- No account needed. No ads. No tracking.
- Free and open source (MIT license)

---

## Get it

1. Open the **Releases** page of this repo and download the latest `.apk`.
2. Tap the file to install it. Android asks you to allow installs from your browser or file manager. Say yes.
3. Open **Stellarite Apps**. Under **CORE**, **Deploy Clearance** should say GRANTED. If it says ALLOW, tap it and allow installs from Stellarite Apps. That lets the app install other apps for you.

Once it's installed, it updates itself: when a new version is released, an UPDATE tile appears on the Catalog page.

---

## The easy stuff

### Catalog: your apps, one tap away
The **CATALOG** tab lists every app from the built-in "Made by The Courier" archive. Each tile shows the app's name and state. Tap **INSTALL** to download and install it. Tap a tile for the full page with release notes and the app's README.

- **INSTALL** installs the app. **UPDATE** appears when a newer version is released. **OPEN** launches it.
- **ALL / UPDATES / INSTALLED** filters the list, and search finds apps by name.
- Greyed tiles are apps still in development that have no download yet.
- After a download finishes, that app's button locks for 30 seconds (it shows WAIT 24s and counts down) so a double tap can't start it twice.

### Updates
Open the app and any available updates are on the Catalog page. Turn on **Auto-Patch** (CORE > GENERAL) and apps update by themselves in the background. Prefer to stay in charge? Leave it off and you get a notification when updates exist. Nothing installs unless you say so.

### Archives: F-Droid and more
The **ARCHIVES** tab opens big open-source catalogs. F-Droid is built in. Open one to browse everything A to Z, search, filter by what fits your phone, and tick the apps you want to add to your catalog.

### Suggested
Under your apps, **Suggested** offers the official F-Droid app, in case you'd rather use their store directly.

### Already have the apps?
Stellarite Apps finds apps already on your phone and matches them to the catalog, so they show as installed and get updates. See **Device Scan** under CORE > GENERAL.

### Control Core
The **CORE** tab holds every setting, in four tabs:

| Tab | What's in it |
| --- | --- |
| **GENERAL** | Visor Skin (theme), Auto-Patch, Device Scan, Deploy Clearance, Payload Cache |
| **OPERATOR** | Uplink (account and Access Key), Sources, Backup Vault, Commit & Sync |
| **NETLINK** | Wi-Fi only downloads, screenshots on Wi-Fi, update alerts, GitHub limit bar, link status |
| **CREDITS** | Who made it, the F-Droid notice, libraries used |

Switches apply immediately. Only the Operator Handle and Access Key need **COMMIT & SYNC**.

---

## Advanced

Everything below is optional. You don't need any of it to use the app.

### Sources
Besides the built-in archive, **CORE > OPERATOR > Sources** lets you paste a link:

- `github.com/owner` adds every qualifying repo of an account or organization.
- `github.com/owner/repo` adds a single repo.
- **Stars** adds your starred repos (the first 100; needs an Access Key).
- An F-Droid-style repo address (F-Droid, IzzyOnDroid and others) adds an archive. Only the apps you tick join your catalog.

Sources can be switched off or removed. They are included in Backup Vault snapshots and covered by Auto-Patch.

### Operator Handle and Access Key (Uplink)
- Blank handle and no key: the built-in catalog.
- Another GitHub handle: that account's public releases.
- An Access Key (a GitHub token): your own repos, including private ones.

GitHub limits anonymous requests to 60 per hour per internet connection (IP address), shared with every other app on the same Wi-Fi or mobile network. A key gives you your own 5,000 per hour. The key is only ever sent to `api.github.com`.

**HOW TO GET AN ACCESS KEY** (folds open under the key field) walks through making a fine-grained token on GitHub: Settings > Developer settings > Personal access tokens > Fine-grained tokens > Generate new token. "Public repositories" is enough for the higher limit; private repos and Backup Vault need "All repositories" with Contents read and write. OPEN GITHUB goes straight to the token page.

### Saved catalog and the GitHub limit
Each sync costs about one request per repo, plus one. To save requests, the last good catalog is kept on the phone:
- Opening the store shows the saved list at once. It only asks GitHub again when the list is more than 15 minutes old, even if Android closed the store in the background. Pull it fresh any time with the sync button or SYNC NOW.
- Auto-Patch's background check saves its list too, so the next open can reuse it.
- If a sync fails (no connection, or the limit is used up), the saved list stays on screen with SAVED LIST and the time it was synced. Installs and updates from it still work, because APK downloads don't count against the limit.
- When the limit is used up, the error says when it resets.

### How apps get into the catalog
A repo appears when it is not a fork, is not archived, and its **latest release** has an `.apk` file attached. Pre-releases and drafts are skipped. If a release has several APKs, one named `universal` or `release` is preferred.

Tag releases with the app's version (`v1.2.0` or `1.2.0` both work). Update detection compares the installed version with the latest tag, ignoring a leading `v` and trailing `.0`.

Repos that look like Android projects (Kotlin or Java, tagged `android` or `stellarite`, or with no language detected yet) but have no APK release show as greyed "In development" tiles, only when no search or filter is active.

### Auto-Patch
- **ON:** every module patches itself, except those you untick in the **Patch Roster**.
- **OFF:** the Patch Roster opens so you can tick the few modules that should still patch themselves.

A background job runs about every 6 hours. Installs go through Android's PackageInstaller, so on Android 12 and newer, updates to apps this store originally installed can finish without a prompt. Where Android still asks (older versions or a first install), you get a notification to tap. Auto-Patch asks for notification permission on Android 13 and newer. Background updates wait for Wi-Fi when "Wi-Fi only downloads" is on.

### NETLINK: downloads and alerts
- **Wi-Fi only downloads** (on by default): installers and archive indexes wait for an unmetered connection. A held download shows WAITING FOR WI-FI with a **DOWNLOAD ANYWAY** button, and starts by itself when Wi-Fi returns.
- **Screenshots on Wi-Fi only** (on by default): F-Droid screenshots are separate image downloads. Switch it off to always load them.
- **Update alerts** (on by default): with Auto-Patch off, one notification lists the updates, once per set of updates rather than at every check.
- **GitHub limit** shows how much of the hourly limit is used, as a percentage bar (cyan, amber from 75%, red at 100%), with the reset time. Every GitHub answer updates it; CHECK LIMIT asks GitHub directly, which is free. Without a key the count covers your whole connection, so it can rise while the store is closed. The catalog shows a LIMIT chip once 75% is used.
- **Link status** shows your connection type and whether an Access Key is set. SYNC NOW reloads the catalog.

### Device Scan
Apps installed before Stellarite Apps (or by another installer) normally aren't recognised, because Stellarite Apps learns an app's package name when it installs it. **Device Scan** closes that gap without downloading anything:

- It compares each catalog repo's name with the names and package ids of the non-system apps on your phone. For example, "CyberTxTs" matches an installed app called "CyberTxTs" or "Cyber TxTs", or a package ending in `cybertxts`.
- Ambiguous matches are skipped rather than guessed.
- **Detect installed apps** (on by default) runs this each time the catalog loads. **Scan now** runs it once and reports what it found.
- If an app's name differs a lot from its repo name, the scan can miss it. Installing it once through Stellarite Apps links it for good.

Stellarite Apps recognises itself the same way, and hides its own tile unless an update is waiting.

### Backup Vault
Records which modules (and versions) you have installed, plus your skin and Patch Roster, so you can restore them on another device. It never stores your Access Key or any app data.

- **File target:** SNAPSHOT NOW saves `stellarite-snapshot.json` through the system file picker (Drive, Syncthing, USB, NAS, anywhere). RESTORE opens a file you pick. No account or server is involved.
- **GitHub target:** snapshots go to `snapshot.json` in a private repo named `stellarite-backup` in the account that owns your Access Key. Every save is a commit, so you get history for free. Auto-snapshot saves after installs, removals, and skin or roster changes.
- Each device only rewrites its own section, so two devices never overwrite each other. Restore lets you pick which device's snapshot to use.
- **Restore** shows each module as MISSING, INSTALLED or "HAS vX" and ticks what needs restoring. "As recorded" installs the exact recorded release (found by its tag). "Latest" installs the newest release. Android still asks you to confirm each install, and Deploy Clearance must be granted first.

Access Key permissions for the GitHub target: it strictly needs **Contents read and write on the `stellarite-backup` repo**.

- Safest: create an empty private repo named `stellarite-backup` yourself (tick "Add a README"), then use a fine-grained token limited to that repo with Contents read and write. If the same token also lists your repos for the catalog, it needs read access to those too.
- Easiest: a classic token with the `repo` scope also lets the app create the repo on the first snapshot.
- If the app can't create the repo, it says so and asks you to create it by hand. Treat creation as a convenience, not something to rely on.

### Archives in depth
Opening an archive gives the whole index A to Z with sticky letters and a letter rail, search, app icons, and filters: fits this device (CPU and Android version), hide anti-features, category, updated, and sort. Tick apps and tap ADD to put them in your catalog.

On first open, or when the daily cache expires, the index is downloaded once (you're asked first on mobile data). F-Droid app pages show the description, categories, anti-features, screenshots, version history and license, all from the verified index.

**Verification.** The index is checked against the repository's signed `entry.jar` before it is used. F-Droid's signing key is built into the app. For other repos, the key is remembered the first time and a later change is refused. A repo without a signature file still works but is labelled UNSIGNED. Every downloaded APK is checked against the SHA-256 in the index. If a check fails, nothing is installed and you get a clear message.

### Suggested in depth
The F-Droid card gets its build from F-Droid's own signed index (the same checks as Archives), then hands it to Android's installer. The home page never downloads the large index by surprise: until it has been synced once, the card shows INSTALL without a version, and on mobile data you are asked before the download. If F-Droid is already on your phone, it moves out of Suggested into your module list, shows as installed, and offers UPDATE when F-Droid's public API lists a newer version. The update itself still comes from the verified index.

### How the Access Key is stored
The key is encrypted with an AES-GCM key held in the Android Keystore and saved in its own file, which is excluded from Android backups and device transfers. A key saved in plain text by an earlier version is encrypted the first time it is read. If the Keystore key is ever lost (for example after a restore to a new phone), the app simply asks for the key again.

### Module detail and README
Under the release notes, the detail screen pulls the repo's README live from GitHub and renders it (headings, lists, code, quotes, tables, links). Images and raw HTML are skipped, relative links point at the repo, and long READMEs are trimmed with an "Open full README on GitHub" button.

### Known limits
- Android never installs an older version over a newer one, so "as recorded" restores and F-Droid modules can't be downgraded.
- Updates only install over apps signed with the same key. Switching signing keys means uninstalling once, which clears that app's data.
- Index signature checking uses Android's JAR verifier. If a repo signs with an algorithm Android refuses, the sync fails rather than skipping the check.
- Device Scan matches by name, so unusual names can be missed.
- Only modules installed through Stellarite Apps are recorded in Backup Vault snapshots.
- Stars are capped at the first 100.
- Archive icons load from the repository over the network (via Coil). App icons in the catalog are generic glyphs, since GitHub releases don't carry artwork.
- Fonts are system fonts (monospace and sans for Cyber, serif for Steampunk).

---

## About other people's repositories

Stellarite Apps was made mainly as a launcher for its maker's own apps. It is not an app store replacement.

The F-Droid app repository belongs to the F-Droid project, and IzzyOnDroid's belongs to its maintainer. Stellarite Apps does not host, copy or change their apps. It only gives you a way into their public indexes from one place and downloads the APKs from their servers, checking signatures and checksums as described above. It is independent software and is not made by, affiliated with or endorsed by F-Droid or IzzyOnDroid. If you would rather use their official app, get F-Droid from https://f-droid.org.

Stellarite Apps can install any APK from any source you add. It does not review third-party apps, so only add sources you trust. The software is provided as is, without warranty (see LICENSE).

---

Made by The Courier · Released under the MIT License
