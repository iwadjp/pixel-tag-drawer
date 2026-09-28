package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iwadjp.pixeltagdrawer.AppListScreen
import com.iwadjp.pixeltagdrawer.R
import com.iwadjp.pixeltagdrawer.data.AppPreferences
import com.iwadjp.pixeltagdrawer.data.TagRepository
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/** Real Compose actions and ViewModels, with a file-backed Room DB reopened on restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class DeletedBulkTagTest {
    @get:Rule val composeRule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private val mounted = mutableStateOf(false)
    private lateinit var db: PixelTagDrawerDatabase
    private lateinit var tags: TagViewModel
    private lateinit var apps: AppListViewModel
    private var work = 0L
    private val dbName = "deleted-bulk-tag-test.db"

    @Suppress("DEPRECATION") // Same launcher inventory fixture as DeletedTagFilterTest.
    @Before
    fun setUp() {
        application.deleteDatabase(dbName)
        openDatabase()
        runBlocking {
            work = TagRepository(db).createTag("Work")
            TagRepository(db).createTag("Personal")
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = ResolveInfo().apply {
            nonLocalizedLabel = "Example app"
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.app"
                name = "com.example.app.Main"
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.app" }
            }
        }
        shadowOf(application.packageManager).addResolveInfoForIntent(launcherIntent, resolved)
        createViewModels()
        composeRule.setContent {
            if (mounted.value) MaterialTheme { AppListScreen(apps, tags) }
        }
        awaitLoaded(2)
    }

    private fun openDatabase() {
        val executor = Executor { it.run() }
        db = Room.databaseBuilder(application, PixelTagDrawerDatabase::class.java, dbName)
            .setQueryExecutor(executor).setTransactionExecutor(executor)
            .allowMainThreadQueries().build()
        setDatabaseInstance(db)
    }

    private fun setDatabaseInstance(value: PixelTagDrawerDatabase?) {
        PixelTagDrawerDatabase::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, value)
        }
    }

    private fun createViewModels() {
        composeRule.runOnUiThread {
            tags = TagViewModel(application)
            apps = AppListViewModel(application)
            store.put("tags", tags)
            store.put("apps", apps)
            mounted.value = true
        }
    }

    private fun awaitLoaded(count: Int) {
        composeRule.waitUntil(5_000) {
            !apps.uiState.value.isLoading && tags.uiState.value.tags.size == count
        }
        composeRule.waitForIdle()
    }

    private fun restart(count: Int) {
        composeRule.runOnIdle { mounted.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { store.clear() }
        db.close()
        setDatabaseInstance(null)
        openDatabase()
        assertEquals("Reopened Room rows", count, runBlocking { db.tagDao().getAllOnce().size })
        createViewModels()
        composeRule.waitForIdle()
        awaitLoaded(count)
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { mounted.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { store.clear() }
        db.close()
        setDatabaseInstance(null)
        application.deleteDatabase(dbName)
    }

    private fun clickText(id: Int) =
        composeRule.onNodeWithText(application.getString(id)).performClick()

    private fun menu(id: Int) {
        composeRule.onNodeWithContentDescription(application.getString(R.string.content_desc_more_options))
            .performClick()
        clickText(id)
    }

    private fun selectBulkTarget() {
        menu(R.string.tag_edit_start)
        composeRule.onNodeWithText("Example app").performClick()
        // The first chip is the list filter; the second is the bulk assignment target.
        composeRule.onAllNodesWithText("Work")[1].performClick()
    }

    @Test
    fun deletingBulkTargetCannotPersistInvisibleAssignmentAfterRestart() {
        selectBulkTarget()
        menu(R.string.tag_management_open)
        composeRule.onAllNodesWithText(application.getString(R.string.action_delete))[0]
            .performScrollTo().performClick()
        awaitLoaded(1)
        clickText(R.string.tag_management_exit_button)
        clickText(R.string.bulk_assign_button)
        composeRule.waitForIdle()
        menu(R.string.tag_edit_stop)
        clickText(R.string.untagged_label)

        restart(1)
        // Before the fix, the deleted target is written to app_tags and survives DB reopening.
        assertEquals(emptyList<Any>(), runBlocking { db.appTagDao().getAllOnce() })
        assertEquals(true, AppPreferences(application).showUntaggedOnly)
        composeRule.onNodeWithText("Example app").assertIsDisplayed()
    }

    @Config(sdk = [34], qualifiers = "ja-w411dp-h891dp")
    @Test
    fun deletedBulkTargetRestartInJapanese() {
        deletingBulkTargetCannotPersistInvisibleAssignmentAfterRestart()
    }

    @Test
    fun deletingTargetDisablesBothActionsAndAllowsSelectingAnotherTag() {
        selectBulkTarget()
        composeRule.runOnIdle { tags.deleteTag(tags.uiState.value.tags.first { it.tagId == work }) }
        awaitLoaded(1)
        composeRule.onNodeWithText(application.getString(R.string.bulk_assign_button)).assertIsNotEnabled()
        composeRule.onNodeWithText(application.getString(R.string.bulk_remove_button)).assertIsNotEnabled()
        composeRule.onAllNodesWithText("Personal")[1].performClick()
        clickText(R.string.bulk_assign_button)
        composeRule.waitUntil(5_000) { tags.uiState.value.appTagMap.isNotEmpty() }
        restart(1)
        assertEquals(tags.uiState.value.tags.single().tagId, runBlocking { db.appTagDao().getAllOnce().single().tagId })
    }

    @Test
    fun renamedTargetKeepsAssignmentAndFilterAcrossRestart() {
        selectBulkTarget()
        composeRule.runOnIdle {
            tags.startRenameTag(tags.uiState.value.tags.first { it.tagId == work })
            tags.updateEditingTagName("Renamed")
            tags.confirmRenameTag()
        }
        composeRule.waitUntil(5_000) { tags.uiState.value.tags.any { it.name == "Renamed" } }
        clickText(R.string.bulk_assign_button)
        composeRule.waitUntil(5_000) { tags.uiState.value.appTagMap.isNotEmpty() }
        menu(R.string.tag_edit_stop)
        composeRule.onNodeWithText("Renamed").performClick()
        restart(2)
        composeRule.waitUntil(5_000) { tags.uiState.value.appTagMap.isNotEmpty() }
        assertEquals(setOf(work), AppPreferences(application).loadFilterTagIds())
        assertEquals(work, runBlocking { db.appTagDao().getAllOnce().single().tagId })
        composeRule.onNodeWithText("Example app").assertIsDisplayed()
    }
}
