package com.iwadjp.pixeltagdrawer.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
                )
            }
            .sortedBy { it.label.lowercase() }
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
}
