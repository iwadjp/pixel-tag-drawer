package com.iwadjp.pixeltagdrawer

import android.os.SystemClock
import android.util.Log

/**
 * 初期表示速度の調査用に、起動からの経過時間を Logcat に出す軽量ヘルパー。
 * 調査専用で、本処理の挙動・順序・State 構造には影響しない。
 * 使い方: MainActivity.onCreate 冒頭で start() し、各計測点で log("...") を呼ぶ。
 * 例: `PixelTagPerf: +420ms launchable apps loaded count=123`
 */
object PerfLog {

    const val TAG = "PixelTagPerf"

    @Volatile
    private var startMs: Long = 0L

    /** 計測の基準時刻を設定する (Activity onCreate 冒頭で呼ぶ想定)。 */
    fun start() {
        startMs = SystemClock.elapsedRealtime()
        Log.d(TAG, "+0ms perf trace start")
    }

    /** 基準時刻からの経過(ms)とステップ名を出す。start() 前は何もしない。 */
    fun log(step: String) {
        val base = startMs
        if (base == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - base
        Log.d(TAG, "+${elapsed}ms $step")
    }
}
