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

internal val PRIVACY_POLICY_TEXT = """
Pixel Tag Drawer プライバシーポリシー
最終更新日: $PRIVACY_POLICY_EFFECTIVE_DATE
対象: Pixel Tag Drawer (com.iwadjp.pixeltagdrawer)

概要
Pixel Tag Drawer は、個人情報や機微情報を外部サーバーへ送信しません。
アプリの処理はすべて端末内で完結します。

アプリがアクセスする情報
・端末上の起動可能なアプリの一覧 (アプリ名・パッケージ名等):
  一覧表示・タグ付け・絞り込みのために使用します。取得には
  ACTION_MAIN / CATEGORY_LAUNCHER によるクエリを用いており、
  端末上の全アプリを対象とする QUERY_ALL_PACKAGES 権限は使用していません。
・アプリの使用状況 (任意): 「最近起動した順」「起動回数順」「おすすめ順」を
  提供するため、許可された場合のみ Android の UsageStats (直近30日) と
  UsageEvents (直近7日) を端末内で集計します。この許可は Android の設定画面で
  ユーザーが手動で付与・取り消しでき、通常のランタイム権限ダイアログは表示されません。
  許可しない場合、これらの並び順は名前順にフォールバックし、他の機能は
  引き続き利用できます。
・作成したタグ、タグの割り当て、表示設定: 端末内のデータベースに保存します。

アプリが行わないこと
・INTERNET権限を要求せず、外部サーバーへ接続するコードを含みません。
・広告、アクセス解析 (analytics)、クラッシュレポート、テレメトリの機能・
  SDKは組み込んでいません。
・アカウント作成やログインを必要としません。
・位置情報、カメラ、マイク、連絡先へはアクセスしません。

端末外へデータが出る場合
アプリ自身がデータを送信することはありません。ユーザーの操作によってのみ、
次の2つの経路でデータが端末の外へ出る可能性があります。
・バックアップの書き出し: ユーザーがAndroidの標準的なファイル選択画面で
  選んだ保存先へ、タグ・割り当て・設定を含むファイルを書き出します。
・診断情報のコピー: アプリ内の診断表示は、ユーザーが「コピー」を
  操作したときだけクリップボードにコピーされます。アプリ名やパッケージ名を
  含む場合があるため、共有前に内容を確認してください。

Android システムバックアップについて
端末のAndroid設定でシステムバックアップ (Auto Backup) が有効な場合、
本アプリのタグ・割り当てを含むローカルデータが、ユーザー自身のGoogleアカウントへの
端末バックアップに含まれることがあります。これはAndroid/Googleが提供する
仕組みであり、本アプリが独自に送信するものではありません。取り扱いは
Googleのプライバシーポリシーに従います。

データの保持と削除
すべてのデータは端末内にのみ保存されます。アプリ内でタグを削除するか、
Androidの設定でアプリのストレージを消去するか、アプリをアンインストールすることで
削除できます。

対象年齢
本アプリは子ども向けに設計されたものではありません。

変更について
本ポリシーの内容に重要な変更がある場合は、この文書の最終更新日を更新して
反映します。

お問い合わせ
$PRIVACY_POLICY_CONTACT_URL
""".trimIndent()

/**
 * プライバシーポリシー本文を表示するダイアログ。
 * 外部Web表示に依存せず、この本文自体がGoogle Playの「アプリ内リンクまたは本文」要件を満たす。
 * DiagnosticsDialog (起動計測診断) と同じ AlertDialog + heightIn + verticalScroll の構成に合わせる。
 */
@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("プライバシーポリシー") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = PRIVACY_POLICY_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
    )
}
