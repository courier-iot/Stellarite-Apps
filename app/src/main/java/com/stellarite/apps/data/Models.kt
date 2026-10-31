package com.stellarite.apps.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GhAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    /** API URL; needed to download assets from private repos with a token. */
    val url: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhRepo(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("stargazers_count") val stars: Int = 0,
)

enum class AppState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

/** A repo that has a latest release containing an APK, plus local install status. */
data class StoreApp(
    val repo: GhRepo,
    val release: GhRelease,
    val apk: GhAsset,
    val packageName: String?,
    val installedVersion: String?,
    val state: AppState,
)

/** State of a repo's README as fetched from GitHub. */
sealed interface ReadmeState {
    data object Loading : ReadmeState
    data class Loaded(val text: String) : ReadmeState
    data object Missing : ReadmeState
    data class Failed(val message: String) : ReadmeState
}

/** Transient per-app download status shown on cards. */
sealed interface Transfer {
    data class Downloading(val progress: Float) : Transfer
    data object Installing : Transfer
    data class Failed(val message: String) : Transfer
}
