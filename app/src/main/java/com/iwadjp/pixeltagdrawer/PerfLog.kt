package com.iwadjp.pixeltagdrawer

import android.os.SystemClock
import android.util.Log

/**
 * 初期表示速度の調査用に、起動からの経過時間を記録する軽量ヘルパー。
 * 調査専用で、本処理の挙動・順序・State 構造には影響しない。
 *
 * - Logcat へ `Log.d(PixelTagPerf, "+<ms>ms <step>")` を出す (従来どおり)。
 * - さらにメモリ上にもイベント履歴を保持し、アプリ内の診断表示から参照できる。
 *   (adb/logcat を使わずに Pixel 単体で計測値を確認・コピーするため)
 */
object PerfLog {

    const val TAG = "PixelTagPerf"
    private const val MAX_EVENTS = 200

    /** 1イベント。elapsedMs は start() からの経過(ms)。 */
    data class PerfEvent(val elapsedMs: Long, val message: String)

    @Volatile
    private var startMs: Long = 0L

    // log() は複数スレッドから呼ばれ得るため synchronized で保護する。
    private val events = ArrayList<PerfEvent>()

    /** 計測の基準時刻を設定し、履歴をクリアする (Activity onCreate 冒頭で呼ぶ想定)。 */
    @Synchronized
    fun start() {
        startMs = SystemClock.elapsedRealtime()
        events.clear()
        addEvent(0L, "perf trace start")
        Log.d(TAG, "+0ms perf trace start")
    }

    /** 基準時刻からの経過(ms)とステップ名を記録する。start() 前は何もしない。 */
    @Synchronized
    fun log(step: String) {
        val base = startMs
        if (base == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - base
        addEvent(elapsed, step)
        Log.d(TAG, "+${elapsed}ms $step")
    }

    private fun addEvent(elapsed: Long, message: String) {
        events.add(PerfEvent(elapsed, message))
        if (events.size > MAX_EVENTS) events.removeAt(0)
    }

    /** イベント履歴のスナップショット。 */
    @Synchronized
    fun events(): List<PerfEvent> = ArrayList(events)

    /** 履歴をクリアする。 */
    @Synchronized
    fun clear() {
        events.clear()
    }

    /**
     * 診断表示/コピー用のテキストを生成する。
     * イベント一覧 + 主要区間の簡易サマリ (イベント名 contains 判定)。
     */
    @Synchronized
    fun report(): String {
        val snapshot = ArrayList(events)
        val sb = StringBuilder()
        sb.append("PixelTagPerf 診断 (events=${snapshot.size})\n")
        sb.append("== events ==\n")
        if (snapshot.isEmpty()) {
            sb.append("(no events)\n")
        } else {
            snapshot.forEach { sb.append("+${it.elapsedMs}ms ${it.message}\n") }
        }
        sb.append("== summary ==\n")
        fun firstMs(sub: String): Long? =
            snapshot.firstOrNull { it.message.contains(sub) }?.elapsedMs
        fun span(label: String, fromSub: String, toSub: String) {
            val from = firstMs(fromSub)
            val to = firstMs(toSub)
            val value = if (from != null && to != null) "${to - from}ms" else "n/a"
            sb.append("$label: $value\n")
        }
        span("onCreate→firstList", "MainActivity.onCreate start", "first visible filtered list")
        span("PM query", "launchable apps query start", "launchable apps query end")
        span("query→loaded(label+icon)", "launchable apps query end", "launchable apps loaded")
        span("loaded→uiState", "launchable apps loaded", "apps loaded into uiState")
        span("db sync", "db sync start", "db sync end")
        // ショートカット起動時のみ意味を持つ区間 (該当なければ n/a)
        span("[shortcut] applyLaunch→applied", "VM applyLaunch", "launch filter applied")
        span("[shortcut] applied→firstList", "launch filter applied", "first visible filtered list")
        return sb.toString()
    }
}
