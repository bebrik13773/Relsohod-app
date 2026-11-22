package ru.relsokhod.spb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.relsokhod.spb.update.AppUpdateLogic

class AppUpdateTest {

    @Test
    fun parsesAndComparesVersionsWithBuildNumbers() {
        assertFalse(
            AppUpdateLogic.isRemoteNewer(
                currentVersionName = "2.3.0-native",
                currentCiRunNumber = 12,
                remoteTagName = "v2.3.0-build.12"
            )
        )
        assertTrue(
            AppUpdateLogic.isRemoteNewer(
                currentVersionName = "2.3.0-native",
                currentCiRunNumber = 12,
                remoteTagName = "v2.3.0-build.13"
            )
        )
        assertTrue(
            AppUpdateLogic.isRemoteNewer(
                currentVersionName = "2.2.0-native",
                currentCiRunNumber = 0,
                remoteTagName = "v2.3.0-build.1"
            )
        )
        assertFalse(
            AppUpdateLogic.isRemoteNewer(
                currentVersionName = "2.3.0-native",
                currentCiRunNumber = 0,
                remoteTagName = "v2.1.0-build.8"
            )
        )
        assertFalse(
            AppUpdateLogic.isRemoteNewer(
                currentVersionName = "2.3.0-native",
                currentCiRunNumber = 0,
                remoteTagName = "v2.3.0-build.15"
            )
        )
    }

    @Test
    fun validatesGitHubUrlsStrictly() {
        assertTrue(AppUpdateLogic.isValidRepoSlug("bebrik13773/Reslohid"))
        assertFalse(AppUpdateLogic.isValidRepoSlug("bebrik13773/Reslohid/extra"))
        assertFalse(AppUpdateLogic.isValidRepoSlug("https://github.com/a/b"))

        assertTrue(AppUpdateLogic.isAllowedGitHubUrl("https://api.github.com/repos/bebrik13773/Reslohid/releases"))
        assertTrue(AppUpdateLogic.isAllowedGitHubUrl("https://github.com/bebrik13773/Reslohid/releases/download/v2.3.0/Relsokhod-release.apk"))
        assertTrue(AppUpdateLogic.isAllowedGitHubUrl("https://objects.githubusercontent.com/github-production-release-asset/foo.apk"))

        assertFalse(AppUpdateLogic.isAllowedGitHubUrl("http://github.com/bebrik13773/Reslohid/releases"))
        assertFalse(AppUpdateLogic.isAllowedGitHubUrl("https://evil.example.com/Relsokhod-release.apk"))
        assertFalse(AppUpdateLogic.isAllowedGitHubUrl("javascript:alert(1)"))
    }

    @Test
    fun parsesGitHubReleasesJsonAndSelectsMatchingApk() {
        val sampleJson = """
            [
              {
                "tag_name": "v2.4.0-draft",
                "draft": true,
                "prerelease": false,
                "html_url": "https://github.com/bebrik13773/Reslohid/releases/tag/v2.4.0-draft",
                "assets": []
              },
              {
                "tag_name": "v2.3.0-build.14",
                "name": "🚋 Рельсоход v2.3.0 (сборка #14)",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/bebrik13773/Reslohid/releases/tag/v2.3.0-build.14",
                "published_at": "2026-11-22T12:00:00Z",
                "body": "# Рельсоход\n- **Официальные аудиогиды**: прямые ссылки\n- **Обновления**: скачивание APK внутри приложения\n- **Версия:** 2.3.0",
                "assets": [
                  {
                    "name": "Relsokhod-debug.apk",
                    "state": "uploaded",
                    "size": 16166203,
                    "digest": "sha256:c08cfb5aa9caad62558e8f2cc386752f1b9a1b66ed69590a8bc6f37b32c58a3f",
                    "browser_download_url": "https://github.com/bebrik13773/Reslohid/releases/download/v2.3.0-build.14/Relsokhod-debug.apk"
                  },
                  {
                    "name": "Relsokhod-release.apk",
                    "state": "uploaded",
                    "size": 9412345,
                    "digest": "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                    "browser_download_url": "https://github.com/bebrik13773/Reslohid/releases/download/v2.3.0-build.14/Relsokhod-release.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val releaseApk = AppUpdateLogic.parseLatestReleaseFromReleasesJson(sampleJson, preferDebugApk = false)
        assertNotNull(releaseApk)
        assertEquals("v2.3.0-build.14", releaseApk!!.tagName)
        assertEquals("Relsokhod-release.apk", releaseApk.apkAsset.name)
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", releaseApk.apkAsset.sha256Hex)
        assertTrue(releaseApk.notesSummary.contains("Официальные аудиогиды"))
        assertFalse(releaseApk.notesSummary.contains("Версия:"))

        val debugApk = AppUpdateLogic.parseLatestReleaseFromReleasesJson(sampleJson, preferDebugApk = true)
        assertNotNull(debugApk)
        assertEquals("Relsokhod-debug.apk", debugApk!!.apkAsset.name)

        assertNull(AppUpdateLogic.normalizeSha256("md5:1234"))
    }
}
