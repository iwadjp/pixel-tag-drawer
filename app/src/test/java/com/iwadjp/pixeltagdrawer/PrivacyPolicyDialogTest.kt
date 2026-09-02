package com.iwadjp.pixeltagdrawer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * アプリ内プライバシーポリシー導線 (PrivacyPolicyDialog) の回帰テスト。
 *
 * Google Playは「アプリ内のリンクまたは本文」でのプライバシーポリシー提示を全アプリに要求する。
 * 実際のAlertDialog経路 (Activity/ViewModel全体ではなく、ダイアログ単体) で、
 * 導線を開くと本文が表示され、主要な事実 (INTERNET権限なし・第三者SDKなし) が
 * 含まれ、閉じる操作で戻れることを検証する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setDismissableContent() {
        composeRule.setContent {
            var show by remember { mutableStateOf(true) }
            MaterialTheme {
                if (show) {
                    PrivacyPolicyDialog(onDismiss = { show = false })
                }
            }
        }
    }

    @Test
    fun dialogShowsTitleAndKeyPrivacyFacts() {
        setDismissableContent()

        composeRule.onNodeWithText("プライバシーポリシー").assertIsDisplayed()

        // 本文はスクロール可能な Column 内の1つの Text ノードにまとまっているため、
        // 部分一致 (substring) で主要な事実の文言が含まれることを確認する。
        composeRule.onNodeWithText("INTERNET権限を要求せず", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("SDKは組み込んでいません", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(PRIVACY_POLICY_EFFECTIVE_DATE, substring = true).assertIsDisplayed()
    }

    @Test
    fun closeButtonDismissesDialog() {
        setDismissableContent()

        composeRule.onNodeWithText("プライバシーポリシー").assertIsDisplayed()
        composeRule.onNodeWithText("閉じる").performClick()

        // ダイアログが閉じた後は本文 (タイトル) が composition から消える。
        composeRule.onAllNodesWithText("プライバシーポリシー").assertCountEquals(0)
    }
}
