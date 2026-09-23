package com.iwadjp.pixeltagdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * アプリ内プライバシーポリシー導線。
 *
 * Google Play は「Play Console内のフィールド」に加えて「アプリ内のリンクまたは本文」でも
 * プライバシーポリシーを提示することを全アプリに要求する
 * (個人情報・機微情報へアクセスしないアプリも対象)。本文はWeb hostingに依存せず、
 * このダイアログ自体がその要件を満たす「アプリ内本文」となる。
 *
 * 本文は `app/src/main/AndroidManifest.xml` および `app/src/main/java/` から確認できる
 * 事実 (INTERNET権限なし・ネットワーク送信コードなし・第三者SDKなし・広告/analytics/
 * crash reporting/telemetryなし・アカウント/ログインなし) のみを記述する。
 * 断定を避けている箇所 (Android Auto Backup) は、`allowBackup="true"` かつ
 * `fullBackupContent`/`dataExtractionRules` 未設定のため、タグ・アプリ割り当てを含む
 * Room DBがAndroidのシステムバックアップ (ユーザー自身のGoogleアカウントへの
 * デバイスバックアップ) の対象になり得るという事実に基づく。
 */
internal const val PRIVACY_POLICY_EFFECTIVE_DATE = "2026-09-03"

internal const val PRIVACY_POLICY_CONTACT_URL = "https://github.com/iwadjp/pixel-tag-drawer"

/**
 * プライバシーポリシー本文を表示するダイアログ。
 * 外部Web表示に依存せず、この本文自体がGoogle Playの「アプリ内リンクまたは本文」要件を満たす。
 * DiagnosticsDialog (起動計測診断) と同じ AlertDialog + heightIn + verticalScroll の構成に合わせる。
 * 本文・日時・お問い合わせ先はロケール別の strings.xml (values / values-ja) が提供する。
 */
@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy_menu)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.privacy_policy_text,
                        PRIVACY_POLICY_EFFECTIVE_DATE,
                        PRIVACY_POLICY_CONTACT_URL,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
