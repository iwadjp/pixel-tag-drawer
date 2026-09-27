package com.iwadjp.pixeltagdrawer.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iwadjp.pixeltagdrawer.AppListScreen
import com.iwadjp.pixeltagdrawer.LaunchFilter
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

/** Real screen -> ViewModel -> Room/prefs; only the installed-app inventory is simulated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class DeletedTagFilterTest {
    @get:Rule val composeRule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: PixelTagDrawerDatabase
    private lateinit var tags: TagViewModel
    private lateinit var apps: AppListViewModel
    private val prefs get() = AppPreferences(application)
    private val intentSequence = mutableIntStateOf(0)
    private val launchFilter = mutableStateOf<LaunchFilter?>(null)

    @Suppress("DEPRECATION") // Robolectric's explicit launcher query fixture.
    @Before
    fun setUp() {
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(application, PixelTagDrawerDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor)
            .allowMainThreadQueries().build()
        setDatabaseInstance(db)
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
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { store.clear() }
        db.close()
        setDatabaseInstance(null)
    }

    private fun setDatabaseInstance(value: PixelTagDrawerDatabase?) {
        PixelTagDrawerDatabase::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, value)
        }
    }

    private fun createTag(name: String): Long = runBlocking { TagRepository(db).createTag(name) }

    private fun startScreen() {
        composeRule.runOnUiThread {
            tags = TagViewModel(application)
            apps = AppListViewModel(application)
            store.put("tags", tags)
            store.put("apps", apps)
        }
        composeRule.setContent {
            MaterialTheme {
                AppListScreen(apps, tags, launchFilter.value, newIntentSeq = intentSequence.intValue)
            }
        }
        composeRule.waitUntil(5_000) { !apps.uiState.value.isLoading }
        composeRule.waitForIdle()
    }

    private fun awaitTagCount(count: Int) {
        composeRule.waitUntil(5_000) { tags.uiState.value.tags.size == count }
        composeRule.waitForIdle()
    }

    @Test
    fun deletedManualFilterDoesNotHideAppsOnNormalRelaunch() {
        createTag("Work")
        prefs.showTagManagement = true
        startScreen()
        awaitTagCount(1)
        composeRule.onNodeWithText("Work").performClick()
        composeRule.onNodeWithText(application.getString(R.string.action_delete))
            .performScrollTo().performClick()
        awaitTagCount(0)
        composeRule.onNodeWithText(application.getString(R.string.tag_management_exit_button)).performClick()
        composeRule.onNodeWithText("Example app").assertIsDisplayed()

        // AppListScreen's actual normal-launch LaunchedEffect calls restoreManualFilters.
        composeRule.runOnIdle { intentSequence.intValue++ }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Example app").assertIsDisplayed()
        assertEquals(emptySet<Long>(), prefs.loadFilterTagIds())
    }

    @Config(sdk = [34], qualifiers = "ja-w411dp-h891dp")
    @Test
    fun deletedManualFilterDoesNotHideAppsOnNormalRelaunchInJapanese() {
        deletedManualFilterDoesNotHideAppsOnNormalRelaunch()
    }

    @Test
    fun deletedShortcutFilterDoesNotHideAppsAfterTagsHaveLoaded() {
        val id = createTag("Work")
        startScreen()
        awaitTagCount(1)
        composeRule.runOnIdle { tags.deleteTag(tags.uiState.value.tags.single()) }
        awaitTagCount(0)

        composeRule.runOnIdle { launchFilter.value = LaunchFilter.Tag(id) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Example app").assertIsDisplayed()
        assertEquals(emptySet<Long>(), tags.uiState.value.selectedFilterTagIds)
    }

    @Test
    fun deletingOneManualFilterPreservesOtherFiltersDuringShortcutSession() {
        val work = createTag("Work")
        val personal = createTag("Personal")
        prefs.multiSelectFilter = true
        prefs.saveFilterTagIds(setOf(work, personal))
        startScreen()
        awaitTagCount(2)
        composeRule.runOnIdle { tags.applyShortcutUntaggedFilter() }
        composeRule.runOnIdle {
            tags.deleteTag(tags.uiState.value.tags.first { it.tagId == work })
        }
        awaitTagCount(1)
        composeRule.runOnIdle { tags.restoreManualFilters() }
        assertEquals(setOf(personal), tags.uiState.value.selectedFilterTagIds)
        assertEquals(setOf(personal), prefs.loadFilterTagIds())
    }

    @Test
    fun validSavedFilterSurvivesInitialDatabaseLoadAndShortcutRoundTrip() {
        val id = createTag("Work")
        prefs.saveFilterTagIds(setOf(id))
        startScreen()
        awaitTagCount(1)
        assertEquals(setOf(id), tags.uiState.value.selectedFilterTagIds)
        composeRule.runOnIdle { tags.applyShortcutUntaggedFilter() }
        composeRule.runOnIdle { tags.restoreManualFilters() }
        assertEquals(setOf(id), tags.uiState.value.selectedFilterTagIds)
        assertEquals(setOf(id), prefs.loadFilterTagIds())
    }

    @Test
    fun coldStartRemovesPreviouslyDeletedSavedFilter() {
        prefs.saveFilterTagIds(setOf(999L))
        startScreen()
        composeRule.waitUntil(5_000) { prefs.loadFilterTagIds().isEmpty() }
        composeRule.onNodeWithText("Example app").assertIsDisplayed()
        assertEquals(emptySet<Long>(), tags.uiState.value.selectedFilterTagIds)
    }

    @Test
    fun validShortcutSurvivesColdStartWithoutReplacingManualFilter() {
        val work = createTag("Work")
        val personal = createTag("Personal")
        prefs.saveFilterTagIds(setOf(personal))
        runBlocking { TagRepository(db).assignTag("com.example.app", "com.example.app.Main", work) }
        launchFilter.value = LaunchFilter.Tag(work)
        startScreen()
        awaitTagCount(2)
        composeRule.waitUntil(5_000) { tags.uiState.value.appTagMap.isNotEmpty() }
        composeRule.onNodeWithText("Example app").assertIsDisplayed()
        assertEquals(setOf(work), tags.uiState.value.selectedFilterTagIds)
        assertEquals(setOf(personal), prefs.loadFilterTagIds())
    }
}
