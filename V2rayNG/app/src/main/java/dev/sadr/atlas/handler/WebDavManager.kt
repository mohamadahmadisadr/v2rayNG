package dev.sadr.atlas.handler

import dev.sadr.atlas.AppConfig
import dev.sadr.atlas.dto.entities.WebDavConfig
import dev.sadr.atlas.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

object WebDavManager {
    private var cfg: WebDavConfig? = null
    private var client: OkHttpClient? = null

    /**
     * Initialize the WebDAV manager with a configuration and build an OkHttp client.
     *
     * @param config WebDavConfig containing baseUrl, credentials, remoteBasePath and timeoutSeconds.
     */
    fun init(config: WebDavConfig) {
        cfg = config
        client = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Auto-initialize from the WebDAV configuration stored in MMKV.
     */
    fun autoInit() {
        MmkvManager.decodeWebDavConfig()?.let { init(it) }
    }

    /**
     * Upload a local file to a remote file name under the configured remoteBasePath.
     * The provided `remoteFileName` should be a file name (e.g. "backup_ng.zip").
     * The method will attempt to create parent directories via MKCOL before PUT.
     *
     * @param localFile File to upload.
     * @param remoteFileName Remote file name relative to configured remoteBasePath.
     * @return true if upload succeeded (HTTP 2xx), false otherwise.
     */
    suspend fun uploadFile(localFile: File, remoteFileName: String): Boolean = withContext(Dispatchers.IO) {
        val remote = buildRemoteUrl(remoteFileName)
        try {
            val cl = client ?: return@withContext false

            // Ensure parent directories exist
            val dirPath = remote.substringBeforeLast('/')
            if (dirPath != remote) {
                ensureRemoteDirs(dirPath)
            }

            // Determine content type based on file extension
            val mediaType = when (localFile.extension.lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                else -> "application/octet-stream"
            }.toMediaTypeOrNull()

            val requestBuilder = Request.Builder()
                .url(remote)
                .put(localFile.asRequestBody(mediaType))

            cfg?.let {
                if (it.username != null && it.password != null) {
                    requestBuilder.header("Authorization", Credentials.basic(it.username, it.password))
                }
            }

            val response = cl.newCall(requestBuilder.build()).execute()
            LogUtil.d(AppConfig.TAG, "WebDAV upload: $remote -> ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV upload failed: $remote", e)
            false
        }
    }

    /**
     * Download a remote file to a local destination.
     *
     * @param remoteFileName Remote file name relative to configured remoteBasePath.
     * @param destFile Local destination file.
     * @return true if download succeeded, false otherwise.
     */
    suspend fun downloadFile(remoteFileName: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val remote = buildRemoteUrl(remoteFileName)
        try {
            val cl = client ?: return@withContext false

            val requestBuilder = Request.Builder()
                .url(remote)
                .get()

            cfg?.let {
                if (it.username != null && it.password != null) {
                    requestBuilder.header("Authorization", Credentials.basic(it.username, it.password))
                }
            }

            val response = cl.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                LogUtil.d(AppConfig.TAG, "WebDAV download failed: ${response.code}")
                false
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WebDAV download error: $remote", e)
            false
        }
    }

    private fun buildRemoteUrl(fileName: String): String {
        val base = cfg?.baseUrl?.trimEnd('/') ?: ""
        val path = cfg?.remoteBasePath?.trim('/') ?: ""
        return if (path.isEmpty()) "$base/$fileName" else "$base/$path/$fileName"
    }

    private fun ensureRemoteDirs(dirUrl: String) {
        // Simple sequential MKCOL for each path segment
        // In a real app, you'd want to check existence (PROPFIND) first.
        val base = cfg?.baseUrl?.trimEnd('/') ?: ""
        val relativePath = dirUrl.removePrefix(base).trim('/')
        if (relativePath.isEmpty()) return

        val segments = relativePath.split('/')
        var currentPath = base
        segments.forEach { segment ->
            currentPath += "/$segment"
            mkCol(currentPath)
        }
    }

    private fun mkCol(url: String) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .method("MKCOL", null)

            cfg?.let {
                if (it.username != null && it.password != null) {
                    requestBuilder.header("Authorization", Credentials.basic(it.username, it.password))
                }
            }

            client?.newCall(requestBuilder.build())?.execute()?.close()
        } catch (_: Exception) {}
    }
}
