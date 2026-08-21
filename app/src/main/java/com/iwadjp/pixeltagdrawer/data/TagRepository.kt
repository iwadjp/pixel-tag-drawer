package com.iwadjp.pixeltagdrawer.data

import android.content.Context
import androidx.room.withTransaction
import com.iwadjp.pixeltagdrawer.data.db.AppTagCrossRef
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * タグ機能の土台となるRepository。TagDao / AppTagDao を束ねる。
 * 今回は土台のみで、ViewModel / UI からはまだ使わない。
 */
class TagRepository(private val db: PixelTagDrawerDatabase) {

    /** 通常の呼び出し元向け: Context からプロセス内シングルトンDBを解決する。 */
    constructor(context: Context) : this(PixelTagDrawerDatabase.getInstance(context))

    private val tagDao = db.tagDao()
    private val appTagDao = db.appTagDao()

    /** タグ一覧を監視する (sortOrder→name 順)。 */
    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    /**
     * タグを作成する。name は trim し、空文字なら挿入しない。
     * sortOrder は MAX+1 で末尾に追加する。
     * 重複 (name unique) は IGNORE のためクラッシュしない。
     * @return 挿入された tagId。未挿入 (空文字 or 重複) の場合は -1。
     */
    suspend fun createTag(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return tagDao.insert(TagEntity(name = trimmed, sortOrder = tagDao.nextSortOrder()))
    }

    /**
     * タグを削除する。
     * 削除前に、そのtagIdを参照する app_tags を先に削除する (孤児行を残さないため)。
     * 途中失敗で片方だけ消えないよう1トランザクションにまとめる。
     */
    suspend fun deleteTag(tag: TagEntity) = db.withTransaction {
        appTagDao.deleteByTagId(tag.tagId)
        tagDao.delete(tag)
    }

    /**
     * タグ名を変更する。name は trim し、空文字なら更新しない。
     * 重複 (name unique) は OR IGNORE のためクラッシュしない。
     * @return 更新された行数。空文字・重複・該当なしの場合は 0。
     */
    suspend fun renameTag(tagId: Long, newName: String): Int {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return 0
        return tagDao.updateTagName(tagId, trimmed)
    }

    /**
     * 横スクロール用の表示名を変更する。blank は null (未設定=name 表示) に正規化する。
     * @return 更新された行数。
     */
    suspend fun updateDisplayLabel(tagId: Long, displayLabel: String?): Int =
        tagDao.updateDisplayLabel(tagId, displayLabel?.trim()?.ifEmpty { null })

    /**
     * タグの sortOrder を更新する (▲▼ 並び替え用)。
     * @return 更新された行数。
     */
    suspend fun updateSortOrder(tagId: Long, sortOrder: Int): Int =
        tagDao.updateSortOrder(tagId, sortOrder)

    /** 隣接2タグの sortOrder を交換する (▲▼ 並び替え用)。 */
    suspend fun swapSortOrder(a: TagEntity, b: TagEntity) =
        tagDao.swapSortOrder(a.tagId, a.sortOrder, b.tagId, b.sortOrder)

    /** アプリ (packageName + className) にタグを付与する。重複は IGNORE。 */
    suspend fun assignTag(packageName: String, className: String, tagId: Long) =
        appTagDao.insert(AppTagCrossRef(packageName, className, tagId))

    /** アプリからタグを外す。 */
    suspend fun removeTag(packageName: String, className: String, tagId: Long) =
        appTagDao.delete(AppTagCrossRef(packageName, className, tagId))

    /** 指定アプリに付与されたタグID一覧を監視する。 */
    fun observeTagIdsForApp(packageName: String, className: String): Flow<List<Long>> =
        appTagDao.observeTagIdsForApp(packageName, className)

    /** 指定タグが付いたアプリ (中間テーブル行) を監視する。 */
    fun observeAppsByTag(tagId: Long): Flow<List<AppTagCrossRef>> =
        appTagDao.observeByTag(tagId)

    /** 全アプリ-タグ割り当て (中間テーブル全行) を監視する。一覧絞り込み用。 */
    fun observeAllAppTags(): Flow<List<AppTagCrossRef>> =
        appTagDao.observeAll()
}
