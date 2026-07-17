package com.iwadjp.pixeltagdrawer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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

        composeRule.onNodeWithText("タグ管理を終了").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("タグ管理を終了").performClick()

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

        composeRule.onNodeWithText("タグ管理を終了").assertIsDisplayed().performClick()
        assert(exited) { "exit bar was not reachable/clickable while the tag panel was tall" }
    }
}
