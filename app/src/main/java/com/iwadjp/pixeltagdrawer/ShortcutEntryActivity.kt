package com.iwadjp.pixeltagdrawer

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Pinned Shortcut 専用の薄い中継 Activity (画面なし)。
 *
 * 目的:
 * 通常ランチャーアイコン (MainActivity の MAIN/LAUNCHER) とショートカット起動を task レベルで分離し、
 * 「ショートカット起動状態の MainActivity が、通常アイコンタップでそのまま純resume されてしまう」問題を避ける。
 *
 * 仕組み:
 * - Manifest で taskAffinity="" / excludeFromRecents="true" を指定し、通常ランチャー task とは別 task に置く。
 * - 受け取った FILTER_TAG_ID / SHOW_UNTAGGED_ONLY を MainActivity へ ACTION_VIEW で転送する。
 * - 転送 Intent には NEW_TASK を付けない。付けると MainActivity 既定 affinity の通常 task に入ってしまうため、
 *   この中継 (空 affinity) の task 内へ MainActivity を載せて通常 task と分離する。
 * - 転送後すぐ finish する (画面は持たない)。
 *
 * これにより通常アイコンタップは別 affinity の通常 task を起こし、MainActivity が
 * MAIN/LAUNCHER の onCreate を受けて normalLauncher=true → restoreManualFilters に入れる。
 */
class ShortcutEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasTag = intent.hasExtra(MainActivity.EXTRA_FILTER_TAG_ID)
        val untagged = intent.getBooleanExtra(MainActivity.EXTRA_SHOW_UNTAGGED_ONLY, false)
        // 計測トレース用 (best-effort)。MainActivity.onCreate が PerfLog.start() を呼ぶ前は記録されない。
        PerfLog.log("[LM] ShortcutEntryActivity.forward tag=$hasTag untagged=$untagged")

        val forward = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // ショートカット用 extras だけを引き継ぐ (値は変更しない)。タグなしを優先。
            if (untagged) {
                putExtra(MainActivity.EXTRA_SHOW_UNTAGGED_ONLY, true)
            } else if (hasTag) {
                putExtra(
                    MainActivity.EXTRA_FILTER_TAG_ID,
                    intent.getLongExtra(MainActivity.EXTRA_FILTER_TAG_ID, -1L),
                )
            }
            // NEW_TASK は付けない (この中継 task 内へ MainActivity を載せ、通常ランチャー task と分離する)。
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(forward)
        finish()
    }
}
