package com.iwadjp.pixeltagdrawer.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.iwadjp.pixeltagdrawer.PerfLog
import com.iwadjp.pixeltagdrawer.data.db.LauncherAppEntity
import com.iwadjp.pixeltagdrawer.data.db.PixelTagDrawerDatabase
import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * 端末上の起動可能アプリを扱うRepository。
 * v0.1ではPackageManagerの読み取りのみ。後続でRoom/タグ管理に拡張しやすいよう分離している。
 */
class AppRepository(private val context: Context) {

    private val launcherAppDao by lazy {
        PixelTagDrawerDatabase.getInstance(context).launcherAppDao()
    }

    @Suppress("DEPRECATION")
    fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadUsageStats(days: Int = USAGE_STATS_DAYS): Map<String, AppUsageStats> {
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - days * 24L * 60L * 60L * 1000L
        val result = mutableMapOf<String, AppUsageStats>()

        usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?.forEach { stat ->
                if (stat.packageName.isNullOrBlank()) return@forEach
                val current = result[stat.packageName] ?: AppUsageStats()
                result[stat.packageName] = current.copy(
                    lastUsedAt = maxOf(current.lastUsedAt, stat.lastTimeUsed),
                )
            }

        val events = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName.isNullOrBlank() || !event.isForegroundEvent()) continue
            val current = result[event.packageName] ?: AppUsageStats()
            result[event.packageName] = current.copy(
                launchCount = current.launchCount + 1,
                lastUsedAt = maxOf(current.lastUsedAt, event.timeStamp),
            )
        }

        return result
    }

    /**
     * Intent.ACTION_MAIN + CATEGORY_LAUNCHER で解決できる起動可能Activityを列挙する。
     * QUERY_ALL_PACKAGES は使わない。
     */
    fun loadLaunchableApps(): List<LauncherApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        PerfLog.log("launchable apps query start")
        val resolved = pm.queryIntentActivities(intent, 0)
        PerfLog.log("launchable apps query end raw=${resolved.size}")
        // 初期表示を早めるため、ここでは label のみ読み icon は null。icon は loadIcon() で後追いする。
        val apps = resolved
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                LauncherApp(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = activityInfo.packageName,
                    className = activityInfo.name,
                    icon = null,
                )
            }
            .sortedBy { it.label.lowercase() }
        PerfLog.log("launchable apps loaded (label only) count=${apps.size}")
        return apps
    }

    /**
     * 指定アクティビティ (packageName + className) のアイコンを後追いロードする。
     * 初期表示後に非同期で呼ぶ想定。失敗時は null (クラッシュさせない)。
     */
    fun loadIcon(packageName: String, className: String): ImageBitmap? {
        return try {
            val pm = context.packageManager
            loadIconBitmap(pm.getActivityIcon(ComponentName(packageName, className)))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * アプリアイコンDrawableをCompose表示用ImageBitmapに変換する。
     * AdaptiveIconも含めCanvasへ描画してビットマップ化する。失敗時はnull。
     * v0.1ではメモリ上のみ。永続化・高度なキャッシュは行わない。
     */
    private fun loadIconBitmap(drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.asImageBitmap()
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_FALLBACK_PX
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_FALLBACK_PX
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 取得した起動可能アプリ一覧を launcher_apps テーブルへ同期する。
     * 取得できたものは isInstalled=true、lastSeenAt=同期時刻で更新する。
     * 起動履歴 (launchCount / lastLaunchedAt) は **上書きしない** (upsertAllSeen が保持する)。
     * 消えたアプリを false にする処理は後続。表示とは独立で、呼び出し側で失敗を握る。
     */
    suspend fun syncLaunchableApps(apps: List<LauncherApp>) {
        val now = System.currentTimeMillis()
        val entities = apps.map { app ->
            LauncherAppEntity(
                packageName = app.packageName,
                className = app.className,
                label = app.label,
                isInstalled = true,
                lastSeenAt = now,
            )
        }
        launcherAppDao.upsertAllSeen(entities, now)
    }

    /**
     * pixel-tag-drawer 内でのアプリ起動を1件記録する (launchCount +1 / lastLaunchedAt 更新)。
     * 並び替え (最近起動順 / 起動回数順) に使う。失敗は呼び出し側で握る。
     */
    suspend fun recordLaunch(packageName: String, className: String) {
        launcherAppDao.recordLaunch(packageName, className, System.currentTimeMillis())
    }

    /**
     * 起動履歴を "packageName/className" -> (launchCount, lastLaunchedAt) で読み込む。
     * 一覧へ後追いマージして並び替えに使う。未起動/未登録は呼び出し側で 0 扱い。
     */
    suspend fun loadLaunchStats(): Map<String, Pair<Int, Long>> {
        return launcherAppDao.getAll().associate { entity ->
            "${entity.packageName}/${entity.className}" to (entity.launchCount to entity.lastLaunchedAt)
        }
    }

    /**
     * 指定アプリの起動用Intentを作る。component に packageName + className を使う。
     */
    fun buildLaunchIntent(app: LauncherApp): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.className)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private companion object {
        // intrinsicサイズが取れないDrawable用のフォールバック解像度(px)。
        const val ICON_FALLBACK_PX = 96
        const val USAGE_STATS_DAYS = 30
    }
}

data class AppUsageStats(
    val launchCount: Int = 0,
    val lastUsedAt: Long = 0,
)

@Suppress("DEPRECATION")
private fun UsageEvents.Event.isForegroundEvent(): Boolean {
    return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && eventType == UsageEvents.Event.ACTIVITY_RESUMED)
}
