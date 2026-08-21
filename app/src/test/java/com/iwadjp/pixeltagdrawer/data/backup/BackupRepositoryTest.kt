package com.iwadjp.pixeltagdrawer.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.data.db.AppTagCrossRef
import com.iwadjp.pixeltagdrawer.data.db.LauncherAppEntity
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * BackupRepository の export/import を実DAO (Room, Robolectric上) 経由で検証する。
 * 本番の PixelTagDrawerDatabase.getInstance (プールされた非同期executor) は、
 * Robolectric の legacy SQLite shadow がスレッドをまたぐコネクション再利用を許容しないため
 * "Illegal connection pointer" で落ちる (Room 2.7 の新コネクションプールとの既知の非互換)。
 * そのためテスト専用に、呼び出しスレッドで同期実行するexecutorを使ったin-memory DBを構築する
 * (production の PixelTagDrawerDatabase.getInstance の設定には触れない)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directExecutor = Executor { command -> command.run() }
    private val db = Room.inMemoryDatabaseBuilder(context, PixelTagDrawerDatabase::class.java)
        .setQueryExecutor(directExecutor)
        .setTransactionExecutor(directExecutor)
        .allowMainThreadQueries()
        .build()
    private val prefs = AppPreferences(context)
    private val repository = BackupRepository(db, prefs)

    @Before
    fun clearState() = runBlocking {
        db.appTagDao().deleteAll()
        db.tagDao().deleteAll()
        db.launcherAppDao().deleteAll()
        prefs.saveFilterTagIds(emptySet())
    }

    @Test
    fun `export then import restores tags, app-tags and launcher apps`() = runBlocking {
        db.tagDao().insertAll(
            listOf(
                TagEntity(tagId = 1L, name = "仕事", sortOrder = 0, displayLabel = "work"),
                TagEntity(tagId = 2L, name = "趣味", sortOrder = 1, displayLabel = null),
            ),
        )
        db.launcherAppDao().upsertAll(
            listOf(
                LauncherAppEntity(
                    packageName = "com.example.app",
                    className = "com.example.app.MainActivity",
                    label = "Example",
                    isInstalled = true,
                    lastSeenAt = 111L,
                    launchCount = 3,
                    lastLaunchedAt = 222L,
                ),
            ),
        )
        db.appTagDao().insertAll(
            listOf(AppTagCrossRef(packageName = "com.example.app", className = "com.example.app.MainActivity", tagId = 1L)),
        )
        prefs.isGridMode = true
        prefs.appSortMode = "recent"
        prefs.saveFilterTagIds(setOf(1L, 2L))

        val json = repository.exportJson()

        // 復元前に全テーブルをクリアして「fresh install への復元」を模擬する。
        db.appTagDao().deleteAll()
        db.tagDao().deleteAll()
        db.launcherAppDao().deleteAll()
        prefs.isGridMode = false
        prefs.appSortMode = "name"
        prefs.saveFilterTagIds(emptySet())

        val outcome = repository.importJson(json)
        assertEquals(BackupImportOutcome.Success, outcome)

        val tags = db.tagDao().getAllOnce()
        assertEquals(2, tags.size)
        assertEquals("仕事", tags.first { it.tagId == 1L }.name)
        assertEquals("work", tags.first { it.tagId == 1L }.displayLabel)

        val apps = db.launcherAppDao().getAll()
        assertEquals(1, apps.size)
        assertEquals(3, apps.first().launchCount)
        assertEquals(222L, apps.first().lastLaunchedAt)

        val appTags = db.appTagDao().getAllOnce()
        assertEquals(1, appTags.size)
        assertEquals(1L, appTags.first().tagId)

        assertTrue(prefs.isGridMode)
        assertEquals("recent", prefs.appSortMode)
        assertEquals(setOf(1L, 2L), prefs.loadFilterTagIds())
    }

    @Test
    fun `invalid backup import leaves existing data untouched`() = runBlocking {
        db.tagDao().insertAll(listOf(TagEntity(tagId = 1L, name = "既存タグ", sortOrder = 0, displayLabel = null)))
        prefs.isGridMode = true

        val outcome = repository.importJson("{not valid json")
        assertEquals(BackupImportOutcome.InvalidBackup, outcome)

        val tags = db.tagDao().getAllOnce()
        assertEquals(1, tags.size)
        assertEquals("既存タグ", tags.first().name)
        assertTrue(prefs.isGridMode)
    }

    @Test
    fun `unsupported format version import leaves existing data untouched`() = runBlocking {
        db.tagDao().insertAll(listOf(TagEntity(tagId = 1L, name = "既存タグ", sortOrder = 0, displayLabel = null)))

        val futureFormatJson = """
            {"formatVersion":999,"exportedAt":"x","appVersion":"x",
            "tags":[],"launcherApps":[],"appTags":[],
            "preferences":{"isGridMode":false,"showTagManagement":false,"showUntaggedOnly":false,
            "multiSelectFilter":false,"selectedFilterTagIds":[],"appSortMode":"name","untaggedDisplayLabel":null}}
        """.trimIndent()

        val outcome = repository.importJson(futureFormatJson)
        assertEquals(BackupImportOutcome.UnsupportedFormat, outcome)

        val tags = db.tagDao().getAllOnce()
        assertEquals(1, tags.size)
    }
}
