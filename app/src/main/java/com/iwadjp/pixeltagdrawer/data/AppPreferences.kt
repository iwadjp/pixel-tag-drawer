package com.iwadjp.pixeltagdrawer.data

import android.content.Context

/**
 * 表示状態の最小永続化 (SharedPreferences)。
 * 保存対象: 表示モード / タグ絞り込み選択 / タグ管理の開閉。
 * 保存しない: タグ編集モード・検索文字列・選択中アプリ (毎回初期状態でよいもの)。
 * DataStore は使わず Gradle 依存も増やさない。
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 表示モード。true=アイコン(Grid), false=リスト(List)。不正/未保存は List。 */
    var isGridMode: Boolean
        get() = prefs.getString(KEY_DISPLAY_MODE, DISPLAY_LIST) == DISPLAY_GRID
        set(value) {
            prefs.edit()
                .putString(KEY_DISPLAY_MODE, if (value) DISPLAY_GRID else DISPLAY_LIST)
                .apply()
        }

    /** タグ管理UIの開閉。未保存は閉じている(false)。 */
    var showTagManagement: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TAG_MANAGEMENT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_TAG_MANAGEMENT, value).apply()
        }

    /** 「タグなし」絞り込みの選択状態。未保存は false。 */
    var showUntaggedOnly: Boolean
        get() = prefs.getBoolean(KEY_SHOW_UNTAGGED_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_UNTAGGED_ONLY, value).apply()
        }

    /**
     * 選択中タグフィルタID集合を読み込む。
     * 形式は Long ID のカンマ区切り (例: "1,3,8")。空なら空集合。
     * 数値化できない要素は無視する (削除済みID等は復元側で更に intersection される)。
     */
    fun loadFilterTagIds(): Set<Long> {
        val raw = prefs.getString(KEY_FILTER_TAG_IDS, "").orEmpty()
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    /** 選択中タグフィルタID集合を保存する (カンマ区切り、空なら空文字)。 */
    fun saveFilterTagIds(ids: Set<Long>) {
        prefs.edit().putString(KEY_FILTER_TAG_IDS, ids.joinToString(",")).apply()
    }

    private companion object {
        const val PREFS_NAME = "pixel_tag_drawer_prefs"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_SHOW_TAG_MANAGEMENT = "show_tag_management"
        const val KEY_SHOW_UNTAGGED_ONLY = "show_untagged_only"
        const val KEY_FILTER_TAG_IDS = "selected_filter_tag_ids"
        const val DISPLAY_LIST = "list"
        const val DISPLAY_GRID = "grid"
    }
}
