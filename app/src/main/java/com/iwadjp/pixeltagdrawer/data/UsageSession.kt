package com.iwadjp.pixeltagdrawer.data

import android.app.usage.UsageEvents

/**
 * UsageEvents のフォアグラウンド遷移イベントを、packageName単位の利用セッションへ変換する。
 * Activity遷移・画面回転・短時間の前景化による水増しを抑えるための土台。
 *
 * この関数群はAndroid実機/Robolectricに依存しない純粋関数として分離している。
 * classifySessionEventType は UsageEvents.Event の型定数 (コンパイル時の静的final int) だけを
 * 参照し、インスタンス (getPackageName() 等のメソッド呼び出し) は扱わないため、
 * 素のJVM単体テストでも実際の定数値のまま検証できる。
 */

/** package内のActivity単位で前景状態を管理するための1イベント。 */
data class RawSessionEvent(
    val packageName: String,
    val className: String,
    val timestampMs: Long,
    val kind: Kind,
) {
    enum class Kind { START, END }
}

/** 採用/未採用判定前の、package単位の利用区間候補 (前景Activityが1つ以上ある期間)。 */
data class UsageSession(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * UsageEvents.Event.eventType (定数値) をセッション開始/終了として分類する。
 *
 * 実機のSDK定数を確認したところ、ACTIVITY_RESUMED は MOVE_TO_FOREGROUND と、
 * ACTIVITY_PAUSED は MOVE_TO_BACKGROUND と同一の整数値のエイリアスであり (どちらも同じ値)、
 * 別イベントとして二重に届くことはない。実際にAPIレベルによって挙動が変わるのは
 * ACTIVITY_STOPPED (API29+でのみ発生しうる、旧APIには存在しない追加の終了シグナル) だけであるため、
 * includeActivityStopped で終了候補に含めるかどうかを切り替える。
 * ACTIVITY_PAUSED (旧: MOVE_TO_BACKGROUND) が既に届いた後に ACTIVITY_STOPPED が重ねて届いても、
 * buildUsageSessions側のclassName除去がidempotentなため二重計上や負数状態にはならない。
 * 対象外の種別は null。
 */
fun classifySessionEventType(eventType: Int, includeActivityStopped: Boolean): RawSessionEvent.Kind? {
    return when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> RawSessionEvent.Kind.START
        UsageEvents.Event.ACTIVITY_PAUSED -> RawSessionEvent.Kind.END
        UsageEvents.Event.ACTIVITY_STOPPED -> {
            if (includeActivityStopped) RawSessionEvent.Kind.END else null
        }
        else -> null
    }
}

/**
 * Activityクラス単位で前景状態を管理し、「package内に前景Activityが1つ以上存在する期間」を
 * 1つの候補セッションとして組み立てる。同一package内のActivity遷移 (A終了直後にB開始等) を
 * 誤って複数セッションに分割しないための最小限の状態管理。
 *
 * イベントは呼び出し順を仮定せず、packageNameごとにtimestampMs順へ並べ直してから処理する。
 * - 前景集合にないclassNameのEND (対応しないEND) は無視する (クラッシュさせない、負数状態にしない)。
 * - 既に前景集合にあるclassNameのSTART (重複START) は無視する (idempotentな集合操作)。
 * - 観測期間終端 (windowEndMs) で前景Activityが残っている場合は、そこでセッションを閉じる。
 */
fun buildUsageSessions(events: List<RawSessionEvent>, windowEndMs: Long): List<UsageSession> {
    val sessions = mutableListOf<UsageSession>()
    events.groupBy { it.packageName }.forEach { (packageName, packageEvents) ->
        val sorted = packageEvents.sortedBy { it.timestampMs }
        val foregroundClasses = mutableSetOf<String>()
        var sessionStart: Long? = null
        for (event in sorted) {
            when (event.kind) {
                RawSessionEvent.Kind.START -> {
                    if (foregroundClasses.isEmpty()) {
                        sessionStart = event.timestampMs
                    }
                    foregroundClasses.add(event.className)
                }
                RawSessionEvent.Kind.END -> {
                    if (foregroundClasses.remove(event.className) && foregroundClasses.isEmpty()) {
                        val start = sessionStart
                        if (start != null) {
                            sessions.add(UsageSession(packageName, start, event.timestampMs))
                        }
                        sessionStart = null
                    }
                }
            }
        }
        val openStart = sessionStart
        if (foregroundClasses.isNotEmpty() && openStart != null) {
            sessions.add(UsageSession(packageName, openStart, windowEndMs))
        }
    }
    return sessions
}

/**
 * 同一packageについて、前のセッション終了から次のセッション開始までが mergeGapMs 以内なら
 * 1つのセッションへ統合する (境界値 mergeGapMs ちょうども統合対象)。
 */
fun mergeCloseSessions(sessions: List<UsageSession>, mergeGapMs: Long): List<UsageSession> {
    return sessions.groupBy { it.packageName }.flatMap { (_, packageSessions) ->
        val sorted = packageSessions.sortedBy { it.startMs }
        val merged = mutableListOf<UsageSession>()
        for (session in sorted) {
            val last = merged.lastOrNull()
            if (last != null && session.startMs - last.endMs <= mergeGapMs) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, session.endMs))
            } else {
                merged.add(session)
            }
        }
        merged
    }
}

/** 前景時間が minSessionMs 未満のセッションを除外する (境界値 minSessionMs ちょうどは採用)。 */
fun filterAcceptedSessions(sessions: List<UsageSession>, minSessionMs: Long): List<UsageSession> =
    sessions.filter { it.durationMs >= minSessionMs }

/**
 * イベント列から採用済みセッション一覧を作る一連の処理 (構築 → 近接統合 → 短時間除外)。
 * 近接統合を先に行うことで、画面回転等による瞬間的な前景断絶を1つの利用として救いつつ、
 * 統合後もなお短い (閲覧に満たない) セッションだけを除外する。
 */
fun buildAcceptedSessions(
    events: List<RawSessionEvent>,
    windowEndMs: Long,
    mergeGapMs: Long,
    minSessionMs: Long,
): List<UsageSession> {
    val rawSessions = buildUsageSessions(events, windowEndMs)
    val merged = mergeCloseSessions(rawSessions, mergeGapMs)
    return filterAcceptedSessions(merged, minSessionMs)
}
