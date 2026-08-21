package com.iwadjp.pixeltagdrawer.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iwadjp.pixeltagdrawer.data.db.AppTagCrossRef
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * TagRepository.deleteTag が app_tags の対応行も削除し、孤児を残さないことを検証する。
 * Robolectric legacy SQLite shadow が Room 2.7 の非同期コネクションプールと相性が悪いため、
 * BackupRepositoryTest と同様に direct executor の in-memory DB を使う。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directExecutor = Executor { command -> command.run() }
    private val db = Room.inMemoryDatabaseBuilder(context, PixelTagDrawerDatabase::class.java)
        .setQueryExecutor(directExecutor)
        .setTransactionExecutor(directExecutor)
        .allowMainThreadQueries()
        .build()
    private val repository = TagRepository(db)

    @Test
    fun `deleting a tag also deletes its app_tags assignments`() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(name = "work", sortOrder = 0))
        val otherTagId = db.tagDao().insert(TagEntity(name = "hobby", sortOrder = 1))
        db.appTagDao().insert(AppTagCrossRef("com.example.app", "com.example.app.Main", tagId))
        db.appTagDao().insert(AppTagCrossRef("com.example.app", "com.example.app.Main", otherTagId))

        val tagToDelete = db.tagDao().getAllOnce().first { it.tagId == tagId }
        repository.deleteTag(tagToDelete)

        val remainingTags = db.tagDao().getAllOnce()
        assertEquals(1, remainingTags.size)
        assertEquals(otherTagId, remainingTags.first().tagId)

        val remainingAppTags = db.appTagDao().getAllOnce()
        assertEquals(1, remainingAppTags.size)
        assertEquals(otherTagId, remainingAppTags.first().tagId)
        assertTrue(remainingAppTags.none { it.tagId == tagId })
    }
}
