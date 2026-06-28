package com.iwadjp.pixeltagdrawer.data

import android.content.Context
import com.iwadjp.pixeltagdrawer.data.db.AppTagCrossRef
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import com.iwadjp.pixeltagdrawer.data.db.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * タグ機能の土台となるRepository。TagDao / AppTagDao を束ねる。
 * 今回は土台のみで、ViewModel / UI からはまだ使わない。
 */
class TagRepository(context: Context) {

    private val tagDao = PixelTagDrawerDatabase.getInstance(context).tagDao()
    private val appTagDao = PixelTagDrawerDatabase.getInstance(context).appTagDao()

    /** タグ一覧を監視する (sortOrder→name 順)。 */
    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    /**
     * タグを作成する。name は trim し、空文字なら挿入しない。
     * 重複 (name unique) は IGNORE のためクラッシュしない。
     * @return 挿入された tagId。未挿入 (空文字 or 重複) の場合は -1。
     */
    suspend fun createTag(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return tagDao.insert(TagEntity(name = trimmed, sortOrder = 0))
    }

    /** タグを削除する。 */
    suspend fun deleteTag(tag: TagEntity) = tagDao.delete(tag)

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
