package com.iwadjp.pixeltagdrawer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.iwadjp.pixeltagdrawer.R
import com.iwadjp.pixeltagdrawer.TagManagementExitBar
import com.iwadjp.pixeltagdrawer.TagSection
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * タグ管理パネル (TagSection) と終了フッター (TagManagementExitBar) の回帰テスト。
 *
 * 直前の実装 (weight(1f, fill=false) 同士の比率制御) では、実機上でタグ管理パネルが
 * ほぼ潰れて見えなくなる不具合が発生した。さらにその修正過程で試した
 * 「ヘッダー固定 + タグ一覧だけ weight+scroll」案も、ヘッダーの実高さが利用可能な
 * 上限を超える組み合わせ (メッセージ表示中 + 低い画面高) でタグ一覧が 0dp まで
 * 押し潰されて完全に消えることが本テストで判明したため撤回した (git 履歴参照)。
 *
 * 最終形は「TagSection 全体を1つの verticalScroll にまとめ、終了バーは
 * TagSection の外側 (別コンポーネント) に常設する」という構成。ここでは
 * AppListScreen 全体 (ViewModel/DB/PackageManager 依存) を経由せず、
 * TagSection / TagManagementExitBar を単体で高さ制約付きコンテナに描画し、
 * タグ行や終了ボタンが実際に存在・到達可能であることを Robolectric 上で検証する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagManagementLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun tags(count: Int): List<TagEntity> =
        (1..count).map { TagEntity(tagId = it.toLong(), name = "タグ$it", sortOrder = it) }

    // 表示文言はロケール別リソースへ抽出済みのため、テストのデフォルトロケール (en) で
    // 実際に解決される文言をリソース経由で取得し、ハードコードした日本語と比較しない。
    private fun exitBarLabel(): String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.tag_management_exit_button)

    // heightIn(max=...) で潰されたケースを再現するため、実際の呼び出し元 (AppListScreen) と
    // 同様に高さを制約したコンテナへ乗せる (無制約な Box に置くとバグを再現できない)。
    private fun setTagSectionContent(
        state: TagUiState,
        shortcutMessage: String? = null,
        maxHeight: androidx.compose.ui.unit.Dp = 280.dp,
    ) {
        composeRule.setContent {
            Column(modifier = Modifier.height(maxHeight)) {
                TagSection(
                    modifier = Modifier,
                    state = state,
                    shortcutMessage = shortcutMessage,
                    onNameChange = {},
                    onCreate = {},
                    onDelete = {},
                    onStartRename = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onEditingNameChange = {},
                    onEditingDisplayLabelChange = {},
                    onConfirmRename = {},
                    onCancelRename = {},
                    onPinTag = {},
                    onPinUntagged = {},
                    onStartEditUntagged = {},
                    onEditingUntaggedLabelChange = {},
                    onConfirmUntaggedLabel = {},
                    onCancelEditUntaggedLabel = {},
                )
            }
        }
    }

    @Test
    fun `existing tag rows are shown in tag management mode`() {
        setTagSectionContent(state = TagUiState(tags = tags(3)))

        composeRule.onNodeWithText("# タグ1").assertIsDisplayed()
    }

    @Test
    fun `home-add message keeps existing tag rows reachable`() {
        // 280dp は極端に低い画面 (横向きの小型端末など) を模した厳しめの制約。
        // このような場合、メッセージ+タグ追加欄+ホーム追加行だけで領域の大半を使い切り、
        // タグ一覧はスクロールしないと画面内に入りきらないことがある。
        // 重要なのは「消えて二度と出せない」状態にならないこと (=スクロールすれば必ず出てくること)。
        setTagSectionContent(
            state = TagUiState(tags = tags(3)),
            shortcutMessage = "「タグ1」のホーム追加をリクエストしました",
        )

        composeRule.onNodeWithText("「タグ1」のホーム追加をリクエストしました").assertIsDisplayed()
        composeRule.onNodeWithText("# タグ1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home-add message does not hide tag rows on a normal-sized screen`() {
        // 実機の一般的な余裕を模した高さでは、メッセージ表示後もスクロールなしで
        // 先頭のタグ行がそのまま見えること (=通常利用で不便を感じないこと)。
        setTagSectionContent(
            state = TagUiState(tags = tags(3)),
            shortcutMessage = "「タグ1」のホーム追加をリクエストしました",
            maxHeight = 420.dp,
        )

        composeRule.onNodeWithText("「タグ1」のホーム追加をリクエストしました").assertIsDisplayed()
        composeRule.onNodeWithText("# タグ1").assertIsDisplayed()
    }

    @Test
    fun `many tags remain reachable via scrolling`() {
        setTagSectionContent(state = TagUiState(tags = tags(40)))

        // 先頭タグは即座に見える。
        composeRule.onNodeWithText("# タグ1").assertIsDisplayed()
        // 末尾タグは高さ制約で画面外にあるが、スクロールすれば到達できること
        // (=領域自体がスクロール可能であること) を確認する。
        composeRule.onNodeWithText("# タグ40").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `exit bar is displayed and clickable`() {
        var exited = false
        composeRule.setContent {
            TagManagementExitBar(onExit = { exited = true })
        }

        composeRule.onNodeWithText(exitBarLabel()).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText(exitBarLabel()).performClick()

        assert(exited) { "onExit was not invoked by clicking the exit bar" }
    }

    @Test
    fun `exit bar stays reachable even when the tag panel is tall`() {
        // AppListScreen での実際の並び (TagSection の下に TagManagementExitBar) を再現し、
        // タグ数が多く TagSection がその上限いっぱいまで伸びても、終了バー自体は
        // 別要素として Column に残り続け、常に到達可能であることを確認する。
        var exited = false
        composeRule.setContent {
            Column {
                TagSection(
                    modifier = Modifier.height(280.dp),
                    state = TagUiState(tags = tags(40)),
                    shortcutMessage = "「タグ1」のホーム追加をリクエストしました",
                    onNameChange = {},
                    onCreate = {},
                    onDelete = {},
                    onStartRename = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onEditingNameChange = {},
                    onEditingDisplayLabelChange = {},
                    onConfirmRename = {},
                    onCancelRename = {},
                    onPinTag = {},
                    onPinUntagged = {},
                    onStartEditUntagged = {},
                    onEditingUntaggedLabelChange = {},
                    onConfirmUntaggedLabel = {},
                    onCancelEditUntaggedLabel = {},
                )
                TagManagementExitBar(onExit = { exited = true })
            }
        }

        composeRule.onNodeWithText(exitBarLabel()).assertIsDisplayed().performClick()
        assert(exited) { "exit bar was not reachable/clickable while the tag panel was tall" }
    }

    // ---- Issue #2: コンパクトな1段行 + 並べ替えモード ----

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class Calls {
        val pinned = mutableListOf<Long>()
        val renamed = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        val movedUp = mutableListOf<Long>()
        val movedDown = mutableListOf<Long>()
    }

    private fun setSection(
        state: TagUiState,
        calls: Calls = Calls(),
        widthDp: Int = 360,
        fontScale: Float = 1f,
        startInReorder: Boolean = false,
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            var reorder by remember { mutableStateOf(startInReorder) }
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Column(modifier = Modifier.width(widthDp.dp).height(900.dp)) {
                    TagSection(
                        modifier = Modifier,
                        state = state,
                        shortcutMessage = null,
                        onNameChange = {},
                        onCreate = {},
                        onDelete = { calls.deleted += it.tagId },
                        onStartRename = { calls.renamed += it.tagId },
                        onMoveUp = { calls.movedUp += it.tagId },
                        onMoveDown = { calls.movedDown += it.tagId },
                        onEditingNameChange = {},
                        onEditingDisplayLabelChange = {},
                        onConfirmRename = {},
                        onCancelRename = {},
                        onPinTag = { calls.pinned += it.tagId },
                        onPinUntagged = {},
                        onStartEditUntagged = {},
                        onEditingUntaggedLabelChange = {},
                        onConfirmUntaggedLabel = {},
                        onCancelEditUntaggedLabel = {},
                        reorderMode = reorder,
                        onReorderModeChange = { reorder = it },
                    )
                }
            }
        }
    }

    private fun home() = ctx.getString(R.string.pin_tag_to_home_button)
    private fun change() = ctx.getString(R.string.action_rename)
    private fun delete() = ctx.getString(R.string.action_delete)
    private fun reorderStart() = ctx.getString(R.string.tag_reorder_start)
    private fun reorderDone() = ctx.getString(R.string.tag_reorder_done)
    private fun moreActions(name: String) = ctx.getString(R.string.content_desc_tag_actions_format, name)

    @Test
    fun `normal mode shows a compact single row with Home and the more menu only`() {
        setSection(TagUiState(tags = tags(3)))

        val name1 = composeRule.onNodeWithText("# タグ1").getBoundsInRoot()
        val name2 = composeRule.onNodeWithText("# タグ2").getBoundsInRoot()
        val home1 = composeRule.onAllNodesWithText(home())[0].getBoundsInRoot()
        val more1 = composeRule.onNodeWithContentDescription(moreActions("タグ1")).getBoundsInRoot()

        // Home と ⋮ はタグ名と同じ行 (縦方向に重なる) にあり、名前の右側に並ぶ。
        assert(home1.top < name1.bottom) { "Home is not on the name row: home=$home1 name=$name1" }
        assert(more1.top < name1.bottom) { "more menu is not on the name row: more=$more1 name=$name1" }
        assert(home1.left >= name1.right && more1.left >= home1.right) { "order broken: $name1 $home1 $more1" }
        // 1行の高さは 56dp 前後 (固定高ではなく最小 56dp。短い名前なら伸びない)
        val pitch = name2.top - name1.top
        assert(pitch.value in 56f..64f) { "row pitch $pitch is not about 56dp" }
        // ⋮ のタップ領域は 48dp 以上
        assert((more1.right - more1.left).value >= 48f && (more1.bottom - more1.top).value >= 48f)
        // 通常時は ▲▼・変更・削除を常時表示しない
        composeRule.onAllNodesWithContentDescription(ctx.getString(R.string.content_desc_move_up)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(ctx.getString(R.string.content_desc_move_down)).assertCountEquals(0)
        composeRule.onNodeWithText(delete()).assertDoesNotExist()
    }

    @Test
    fun `Home button requests a pin for that tag`() {
        val calls = Calls()
        setSection(TagUiState(tags = tags(3)), calls)

        composeRule.onAllNodesWithText(home())[1].performClick()

        assert(calls.pinned == listOf(2L)) { "pinned=${calls.pinned}" }
    }

    @Test
    fun `more menu offers Change and Delete`() {
        val calls = Calls()
        setSection(TagUiState(tags = tags(3)), calls)

        composeRule.onNodeWithContentDescription(moreActions("タグ2")).performClick()
        composeRule.onNodeWithText(delete()).assertIsDisplayed()
        composeRule.onNodeWithText(delete()).performClick()
        assert(calls.deleted == listOf(2L)) { "deleted=${calls.deleted}" }
        // 削除は従来どおり確認なしで実行される (新しい確認手順は追加しない)
        assert(calls.renamed.isEmpty())

        composeRule.onNodeWithContentDescription(moreActions("タグ3")).performClick()
        // タグなし行の「変更」ボタンとは別に、メニュー内の「変更」が最後に現れる
        composeRule.onAllNodesWithText(change()).onLast().performClick()
        assert(calls.renamed == listOf(3L)) { "renamed=${calls.renamed}" }
        composeRule.onNodeWithText(delete()).assertDoesNotExist()
    }

    @Test
    fun `reorder mode shows name with up and down only and can be left`() {
        val calls = Calls()
        setSection(TagUiState(tags = tags(3)), calls)

        composeRule.onNodeWithText(reorderStart()).performClick()

        composeRule.onNodeWithText(reorderDone()).assertIsDisplayed()
        composeRule.onAllNodesWithText(home()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(moreActions("タグ1")).assertCountEquals(0)
        composeRule.onAllNodesWithText(delete()).assertCountEquals(0)

        val up = ctx.getString(R.string.content_desc_move_up)
        val down = ctx.getString(R.string.content_desc_move_down)
        val ups = composeRule.onAllNodesWithContentDescription(up)
        val downs = composeRule.onAllNodesWithContentDescription(down)
        ups.assertCountEquals(3)
        downs.assertCountEquals(3)
        // 先頭の▲と末尾の▼は無効のまま
        ups[0].assertIsNotEnabled()
        ups[1].assertIsEnabled()
        downs[1].assertIsEnabled()
        downs[2].assertIsNotEnabled()

        downs[0].performClick()
        ups[2].performClick()
        assert(calls.movedDown == listOf(1L)) { "movedDown=${calls.movedDown}" }
        assert(calls.movedUp == listOf(3L)) { "movedUp=${calls.movedUp}" }

        composeRule.onNodeWithText(reorderDone()).performClick()
        composeRule.onAllNodesWithText(home()).assertCountEquals(3)
        composeRule.onAllNodesWithContentDescription(up).assertCountEquals(0)
    }

    @Test
    fun `reorder row keeps up and down on the name row`() {
        setSection(TagUiState(tags = tags(3)), startInReorder = true)

        val name = composeRule.onNodeWithText("# タグ1").getBoundsInRoot()
        val up = composeRule.onAllNodesWithContentDescription(ctx.getString(R.string.content_desc_move_up))[0].getBoundsInRoot()
        assert(up.top < name.bottom && up.left >= name.right) { "up=$up name=$name" }
        assert((up.right - up.left).value >= 48f && (up.bottom - up.top).value >= 48f)
    }

    @Test
    fun `reorder toggle is disabled while a tag name is being edited`() {
        val tag = tags(3)[0]
        setSection(TagUiState(tags = tags(3), editingTag = tag, editingTagName = "編集中"))

        composeRule.onNodeWithText(reorderStart()).assertIsNotEnabled()
    }

    @Test
    fun `reorder toggle is unavailable with a single tag`() {
        setSection(TagUiState(tags = tags(1)))

        composeRule.onNodeWithText(reorderStart()).assertIsNotEnabled()
    }

    @Test
    fun `add-tag input is hidden while reordering`() {
        setSection(TagUiState(tags = tags(2)))
        composeRule.onNodeWithText(ctx.getString(R.string.add_tag_button)).assertIsDisplayed()

        composeRule.onNodeWithText(reorderStart()).performClick()

        composeRule.onNodeWithText(ctx.getString(R.string.add_tag_button)).assertDoesNotExist()
    }

    private fun assertRowsUsable(widthDp: Int, fontScale: Float, tagCount: Int) {
        val longName = "SuperLongTagNameWithoutAnySpaces"
        val list = (1..tagCount).map {
            TagEntity(tagId = it.toLong(), name = if (it == 1) longName else "タグ$it", sortOrder = it)
        }
        setSection(TagUiState(tags = list), widthDp = widthDp, fontScale = fontScale)

        val label = "widthDp=$widthDp fontScale=$fontScale tags=$tagCount"
        val homes = composeRule.onAllNodesWithText(home())
        homes.assertCountEquals(tagCount)
        for (i in 0 until tagCount) {
            val h = homes[i].performScrollTo()
            h.assertIsDisplayed().assertHasClickAction()
            val b = h.getBoundsInRoot()
            assert(b.right.value <= widthDp) { "Home overflows ($label): $b" }
        }
        val more = composeRule.onNodeWithContentDescription(moreActions(longName))
        more.performScrollTo().assertIsDisplayed().assertHasClickAction()
        val mb = more.getBoundsInRoot()
        assert(mb.right.value <= widthDp) { "more menu overflows ($label): $mb" }
        assert((mb.right - mb.left).value >= 48f) { "tap target too small ($label): $mb" }
        // 長い名前でも ⋮ メニューから変更・削除に到達できる
        more.performClick()
        composeRule.onNodeWithText(delete()).assertIsDisplayed()
    }

    @Test
    fun `rows stay usable at 320dp and 360dp with font scales up to 2x`() {
        // setContent は1テストで1回しか呼べないため、組み合わせごとにテストを分ける
        assertRowsUsable(320, 1f, 17)
    }

    @Test
    fun `rows usable 320dp 1_5x`() = assertRowsUsable(320, 1.5f, 17)

    @Test
    fun `rows usable 320dp 2x`() = assertRowsUsable(320, 2f, 17)

    @Test
    fun `rows usable 360dp 1x`() = assertRowsUsable(360, 1f, 17)

    @Test
    fun `rows usable 360dp 1_5x`() = assertRowsUsable(360, 1.5f, 17)

    @Test
    fun `rows usable 360dp 2x`() = assertRowsUsable(360, 2f, 17)
}
