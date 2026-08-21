package com.iwadjp.pixeltagdrawer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BackupJson (export/import 用JSON encode/decode) の単体テスト。 */
class BackupJsonTest {

    private fun samplePayload(): BackupPayload = BackupPayload(
        formatVersion = BackupPayload.CURRENT_FORMAT_VERSION,
        exportedAt = "2026-08-21T12:00:00Z",
        appVersion = "0.1.0",
        tags = listOf(
            BackupTag(tagId = 1L, name = "仕事", sortOrder = 0, displayLabel = "work"),
            BackupTag(tagId = 2L, name = "ゲーム \"quotes\" \\slash", sortOrder = 1, displayLabel = null),
        ),
        launcherApps = listOf(
            BackupLauncherApp(
                packageName = "com.example.app",
                className = "com.example.app.MainActivity",
                label = "Example",
                isInstalled = true,
                lastSeenAt = 1000L,
                launchCount = 5,
                lastLaunchedAt = 2000L,
            ),
        ),
        appTags = listOf(
            BackupAppTag(packageName = "com.example.app", className = "com.example.app.MainActivity", tagId = 1L),
        ),
        preferences = BackupPreferences(
            isGridMode = true,
            showTagManagement = false,
            showUntaggedOnly = false,
            multiSelectFilter = true,
            selectedFilterTagIds = listOf(1L, 2L),
            appSortMode = "recent",
            untaggedDisplayLabel = null,
        ),
    )

    @Test
    fun `encode then decode round-trips all fields`() {
        val payload = samplePayload()
        val json = BackupJson.encode(payload)
        val result = BackupJson.decode(json)
        assertTrue(result is BackupDecodeResult.Success)
        val decoded = (result as BackupDecodeResult.Success).payload
        assertEquals(payload, decoded)
    }

    @Test
    fun `decode rejects malformed JSON`() {
        val result = BackupJson.decode("{not valid json")
        assertEquals(BackupDecodeResult.InvalidJson, result)
    }

    @Test
    fun `decode rejects non-object root`() {
        val result = BackupJson.decode("[1,2,3]")
        assertEquals(BackupDecodeResult.InvalidStructure, result)
    }

    @Test
    fun `decode rejects unsupported formatVersion`() {
        val json = BackupJson.encode(samplePayload()).replace(
            "\"formatVersion\":${BackupPayload.CURRENT_FORMAT_VERSION}",
            "\"formatVersion\":999",
        )
        assertEquals(BackupDecodeResult.UnsupportedVersion, BackupJson.decode(json))
    }

    @Test
    fun `decode rejects missing required field`() {
        // preferences 全体を欠落させる
        val json = BackupJson.encode(samplePayload())
        val withoutPrefs = json.substringBeforeLast(",\"preferences\":") + "}"
        assertEquals(BackupDecodeResult.InvalidStructure, BackupJson.decode(withoutPrefs))
    }

    @Test
    fun `decode rejects appTags referencing a tagId not present in tags`() {
        val payload = samplePayload().copy(
            appTags = listOf(
                BackupAppTag(packageName = "com.example.app", className = "com.example.app.MainActivity", tagId = 999L),
            ),
        )
        val json = BackupJson.encode(payload)
        assertEquals(BackupDecodeResult.MalformedReferences, BackupJson.decode(json))
    }

    @Test
    fun `decode rejects appTags with blank keys`() {
        val payload = samplePayload().copy(
            appTags = listOf(BackupAppTag(packageName = "", className = "", tagId = 1L)),
        )
        val json = BackupJson.encode(payload)
        assertEquals(BackupDecodeResult.MalformedReferences, BackupJson.decode(json))
    }

    @Test
    fun `preferences round-trip preserves nullable and list fields`() {
        val payload = samplePayload()
        val decoded = (BackupJson.decode(BackupJson.encode(payload)) as BackupDecodeResult.Success).payload
        assertEquals(payload.preferences, decoded.preferences)
    }
}
