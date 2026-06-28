package com.iwadjp.pixeltagdrawer.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.iwadjp.pixeltagdrawer.model.LauncherApp

/**
 * 端末上の起動可能アプリを扱うRepository。
 * v0.1ではPackageManagerの読み取りのみ。後続でRoom/タグ管理に拡張しやすいよう分離している。
 */
class AppRepository(private val context: Context) {

    /**
     * Intent.ACTION_MAIN + CATEGORY_LAUNCHER で解決できる起動可能Activityを列挙する。
     * QUERY_ALL_PACKAGES は使わない。
     */
    fun loadLaunchableApps(): List<LauncherApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                LauncherApp(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = activityInfo.packageName,
                    className = activityInfo.name,
                    icon = loadIconBitmap(resolveInfo.loadIcon(pm)),
                )
            }
            .sortedBy { it.label.lowercase() }
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
    }
}
