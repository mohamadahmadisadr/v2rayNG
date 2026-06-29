package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            AppConfig.APP_RELEASE_API_URL
        }

        try {
            val content = HttpUtil.getUrlContent(UrlContentRequest(url))
            if (content == null) {
                return@withContext CheckUpdateResult(false, "Network error")
            }

            val releases = if (includePreRelease) {
                JsonUtil.fromJsonSafe(content, Array<GitHubRelease>::class.java)?.toList()
            } else {
                JsonUtil.fromJsonSafe(content, GitHubRelease::class.java)?.let { listOf(it) }
            }

            if (releases == null || releases.isEmpty()) {
                return@withContext CheckUpdateResult(false, "No release found")
            }

            val latest = releases[0]
            val latestVersion = latest.tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (isNewerVersion(latestVersion, currentVersion)) {
                val apkAsset = latest.assets.find { asset ->
                    asset.name.contains(getArchTag()) && asset.name.endsWith(".apk")
                } ?: latest.assets.find { it.name.endsWith(".apk") }

                return@withContext CheckUpdateResult(
                    true,
                    latestVersion,
                    latest.publishedAt, // Using publishedAt as a fallback for html_url if missing
                    apkAsset?.browserDownloadUrl,
                    latest.body
                )
            } else {
                return@withContext CheckUpdateResult(false, "Already up to date")
            }

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Update check failed", e)
            return@withContext CheckUpdateResult(false, e.message ?: "Unknown error")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        // Simple version comparison logic
        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    private fun getArchTag(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("armeabi") -> "armeabi-v7a"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> ""
        }
    }
}
