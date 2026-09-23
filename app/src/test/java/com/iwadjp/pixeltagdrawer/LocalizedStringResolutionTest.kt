package com.iwadjp.pixeltagdrawer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-Droidレビュー (MR !47198) で「英語端末でも日本語UIが表示される」と指摘された問題の回帰テスト。
 *
 * MainActivity 全体 (ViewModel/DB/PackageManager依存) を Compose 経由で描画せず、
 * Android標準のリソース解決 (values=英語既定 / values-ja=日本語) がロケールごとに
 * 正しく機能することを、検索欄・並び替え・エラー表示など指摘対象の文言で直接検証する。
 * Compose の stringResource() は内部でこの同じリソース解決を使うため、
 * ここでの検証はUI全体を描画する重いテストなしに同等の保証を与える。
 */
@RunWith(RobolectricTestRunner::class)
class LocalizedStringResolutionTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34], qualifiers = "en")
    fun defaultLocaleShowsEnglish() {
        val ctx = context()
        assertEquals("Search by app name / package name", ctx.getString(R.string.search_hint))
        assertEquals("Recently used", ctx.getString(R.string.sort_recent_full))
        assertEquals("Failed to load the app list", ctx.getString(R.string.apps_load_failed))
        assertEquals("No tags yet", ctx.getString(R.string.no_tags))
        assertEquals(
            "Failed to launch: Foo (com.example.foo)",
            ctx.getString(R.string.launch_failed_format, "Foo", "com.example.foo"),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "ja")
    fun japaneseLocaleShowsJapanese() {
        val ctx = context()
        assertEquals("アプリ名 / パッケージ名で検索", ctx.getString(R.string.search_hint))
        assertEquals("最近起動", ctx.getString(R.string.sort_recent_full))
        assertEquals("アプリ一覧の読み込みに失敗しました", ctx.getString(R.string.apps_load_failed))
        assertEquals("タグがありません", ctx.getString(R.string.no_tags))
        assertEquals(
            "起動に失敗しました: Foo (com.example.foo)",
            ctx.getString(R.string.launch_failed_format, "Foo", "com.example.foo"),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "fr")
    fun unsupportedLocaleFallsBackToEnglish() {
        val ctx = context()
        // フランス語の values-fr は用意していないため、Androidの標準フォールバックにより
        // 既定 (values=英語) が使われることを確認する。
        assertEquals("Search by app name / package name", ctx.getString(R.string.search_hint))
        assertEquals("Failed to load the app list", ctx.getString(R.string.apps_load_failed))
    }
}
