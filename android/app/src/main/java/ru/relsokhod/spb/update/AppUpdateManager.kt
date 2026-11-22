package ru.relsokhod.spb.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val buildNumber: Int? = null
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        val a = buildNumber ?: 0
        val b = other.buildNumber ?: 0
        return a.compareTo(b)
    }
}

data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256Hex: String?
)

data class AvailableRelease(
    val tagName: String,
    val releaseName: String,
    val notesSummary: String,
    val htmlUrl: String,
    val publishedAtIso: String,
    val parsedVersion: AppVersion?,
    val apkAsset: ReleaseAssetInfo
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: AvailableRelease) : UpdateCheckResult
    data class UpToDate(val latestTag: String?) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progress: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState

    data class ReadyToInstall(
        val apkFile: File,
        val release: AvailableRelease
    ) : UpdateDownloadState

    data class Failed(val message: String) : UpdateDownloadState
}

object AppUpdateLogic {
    private val SEMVER_REGEX = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
    private val BUILD_REGEX = Regex("""(?:^|[-._])build\.(\d+)""", RegexOption.IGNORE_CASE)
    private val REPO_REGEX = Regex("""^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$""")
    private val SHA256_REGEX = Regex("""^(?:sha256:)?([a-fA-F0-9]{64})$""")

    fun isValidRepoSlug(repoSlug: String): Boolean = REPO_REGEX.matches(repoSlug.trim())

    fun isAllowedGitHubUrl(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl.trim())
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase(Locale.US) ?: return false
            host == "github.com" ||
                host == "api.github.com" ||
                host.endsWith(".github.com") ||
                host.endsWith(".githubusercontent.com")
        } catch (_: Exception) {
            false
        }
    }

    fun normalizeSha256(digest: String?): String? {
        if (digest.isNullOrBlank()) return null
        val match = SHA256_REGEX.matchEntire(digest.trim()) ?: return null
        return match.groupValues[1].lowercase(Locale.US)
    }

    fun parseVersion(raw: String, fallbackBuildNumber: Int = 0): AppVersion? {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        val semverMatch = SEMVER_REGEX.find(cleaned) ?: return null
        val major = semverMatch.groupValues[1].toIntOrNull() ?: return null
        val minor = semverMatch.groupValues[2].toIntOrNull() ?: return null
        val patch = semverMatch.groupValues[3].toIntOrNull() ?: 0
        val buildNum = BUILD_REGEX.find(cleaned)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: fallbackBuildNumber.takeIf { it > 0 }
        return AppVersion(major = major, minor = minor, patch = patch, buildNumber = buildNum)
    }

    fun isRemoteNewer(
        currentVersionName: String,
        currentCiRunNumber: Int,
        remoteTagName: String
    ): Boolean {
        val local = parseVersion(currentVersionName, fallbackBuildNumber = currentCiRunNumber) ?: return false
        val remote = parseVersion(remoteTagName, fallbackBuildNumber = 0) ?: return false

        if (remote.major != local.major) return remote.major > local.major
        if (remote.minor != local.minor) return remote.minor > local.minor
        if (remote.patch != local.patch) return remote.patch > local.patch

        // Same major.minor.patch: only consider newer if both track CI build numbers and remote is strictly ahead
        return local.buildNumber != null &&
            remote.buildNumber != null &&
            remote.buildNumber > local.buildNumber
    }

    fun parseLatestReleaseFromReleasesJson(
        jsonBody: String,
        preferDebugApk: Boolean = false
    ): AvailableRelease? {
        val arr = JSONArray(jsonBody)
        for (i in 0 until arr.length()) {
            val relObj = arr.optJSONObject(i) ?: continue
            if (relObj.optBoolean("draft", false)) continue
            if (relObj.optBoolean("prerelease", false)) continue

            val tagName = relObj.optString("tag_name", "").trim()
            if (tagName.isBlank()) continue

            val htmlUrl = relObj.optString("html_url", "").trim()
                .takeIf { isAllowedGitHubUrl(it) }
                ?: continue

            val assetsArr = relObj.optJSONArray("assets") ?: continue
            val apkCandidates = mutableListOf<ReleaseAssetInfo>()
            for (aIdx in 0 until assetsArr.length()) {
                val assetObj = assetsArr.optJSONObject(aIdx) ?: continue
                if (assetObj.optString("state", "uploaded") != "uploaded") continue
                val name = assetObj.optString("name", "").trim()
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val downloadUrl = assetObj.optString("browser_download_url", "").trim()
                if (!isAllowedGitHubUrl(downloadUrl)) continue
                val sizeBytes = assetObj.optLong("size", 0L)
                val sha256Hex = normalizeSha256(assetObj.optString("digest", ""))
                apkCandidates.add(
                    ReleaseAssetInfo(
                        name = name,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        sha256Hex = sha256Hex
                    )
                )
            }
            if (apkCandidates.isEmpty()) continue

            val chosenApk = if (preferDebugApk) {
                apkCandidates.firstOrNull { it.name.contains("debug", ignoreCase = true) }
                    ?: apkCandidates.first()
            } else {
                apkCandidates.firstOrNull { it.name.contains("release", ignoreCase = true) }
                    ?: apkCandidates.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
                    ?: apkCandidates.first()
            }

            val releaseName = relObj.optString("name", tagName).trim().ifBlank { tagName }
            val notesSummary = extractReleaseSummary(relObj.optString("body", ""))
            val publishedAt = relObj.optString("published_at", "")

            return AvailableRelease(
                tagName = tagName,
                releaseName = releaseName,
                notesSummary = notesSummary,
                htmlUrl = htmlUrl,
                publishedAtIso = publishedAt,
                parsedVersion = parseVersion(tagName),
                apkAsset = chosenApk
            )
        }
        return null
    }

    fun extractReleaseSummary(markdownBody: String): String {
        if (markdownBody.isBlank()) return ""
        val lines = markdownBody.lines()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("- ") &&
                    !line.contains("SHA-256", ignoreCase = true) &&
                    !line.contains("Сборка CI", ignoreCase = true) &&
                    !line.contains("Версия:", ignoreCase = true)
            }
            .take(3)
            .map { line ->
                line.removePrefix("- ")
                    .replace("**", "")
                    .replace("`", "")
                    .trim()
            }
        return lines.joinToString("\n")
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f МБ", mb)
    }
}

object AppUpdateManager {
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    private const val MIN_APK_BYTES = 100L * 1024L

    suspend fun checkForUpdates(
        repoSlug: String,
        currentVersionName: String,
        currentCiRunNumber: Int,
        preferDebugApk: Boolean
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!AppUpdateLogic.isValidRepoSlug(repoSlug)) {
            return@withContext UpdateCheckResult.Error("Некорректный репозиторий обновлений")
        }
        val apiUrl = "https://api.github.com/repos/${repoSlug.trim()}/releases?per_page=5"
        if (!AppUpdateLogic.isAllowedGitHubUrl(apiUrl)) {
            return@withContext UpdateCheckResult.Error("Недопустимый источник обновлений")
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Relsokhod-Android/$currentVersionName")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error(
                    when (code) {
                        403, 429 -> "Превышен лимит запросов к GitHub, попробуйте чуть позже"
                        404 -> "Релизы в репозитории пока не найдены"
                        else -> "Сервер обновлений ответил с кодом $code"
                    }
                )
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val latest = AppUpdateLogic.parseLatestReleaseFromReleasesJson(
                jsonBody = body,
                preferDebugApk = preferDebugApk
            ) ?: return@withContext UpdateCheckResult.UpToDate(latestTag = null)

            if (AppUpdateLogic.isRemoteNewer(currentVersionName, currentCiRunNumber, latest.tagName)) {
                UpdateCheckResult.UpdateAvailable(latest)
            } else {
                UpdateCheckResult.UpToDate(latestTag = latest.tagName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UpdateCheckResult.Error("Не удалось подключиться к GitHub для проверки обновлений")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        release: AvailableRelease,
        onProgress: (progress: Float?, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        val finalFile = File(updatesDir, "relsokhod-update.apk")
        val partFile = File(updatesDir, "relsokhod-update.apk.part")
        runCatching {
            if (finalFile.exists()) finalFile.delete()
            if (partFile.exists()) partFile.delete()
        }

        var conn: HttpURLConnection? = null
        try {
            conn = openValidatedGitHubDownloadConnection(release.apkAsset.downloadUrl)
            val totalHeader = conn.contentLengthLong
            val expectedTotal = when {
                totalHeader > 0L -> totalHeader
                release.apkAsset.sizeBytes > 0L -> release.apkAsset.sizeBytes
                else -> -1L
            }
            if (expectedTotal > MAX_APK_BYTES) {
                return@withContext Result.failure(IllegalStateException("Размер файла превышает допустимый лимит"))
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReported = 0L

            BufferedInputStream(conn.inputStream, 32 * 1024).use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw IllegalStateException("Превышен максимальный размер APK")
                        }
                        if (downloaded - lastReported >= 64 * 1024 || downloaded == expectedTotal) {
                            lastReported = downloaded
                            val progress = if (expectedTotal > 0L) {
                                (downloaded.toFloat() / expectedTotal.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            onProgress(progress, downloaded, expectedTotal)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded < MIN_APK_BYTES) {
                partFile.delete()
                return@withContext Result.failure(IllegalStateException("Скачанный файл повреждён или неполный"))
            }
            if (release.apkAsset.sizeBytes > 0L && downloaded != release.apkAsset.sizeBytes) {
                partFile.delete()
                return@withContext Result.failure(IllegalStateException("Размер скачанного APK не совпадает с релизом"))
            }

            val expectedSha = release.apkAsset.sha256Hex
            if (expectedSha != null) {
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    partFile.delete()
                    return@withContext Result.failure(IllegalStateException("Контрольная сумма SHA-256 не совпала"))
                }
            }

            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            onProgress(1f, downloaded, downloaded)
            Result.success(finalFile)
        } catch (e: CancellationException) {
            runCatching { partFile.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun openValidatedGitHubDownloadConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(6) {
            if (!AppUpdateLogic.isAllowedGitHubUrl(currentUrl)) {
                throw SecurityException("Недопустимый URL загрузки: $currentUrl")
            }
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Relsokhod-Android-Updater")
            }
            val status = conn.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Пустой редирект при загрузке APK")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else if (status in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("Ошибка скачивания APK (HTTP $status)")
            }
        }
        throw IllegalStateException("Слишком много перенаправлений при скачивании APK")
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
