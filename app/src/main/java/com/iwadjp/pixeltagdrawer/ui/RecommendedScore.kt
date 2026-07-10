package com.iwadjp.pixeltagdrawer.ui

import kotlin.math.ln
import kotlin.math.pow

/**
 * 「おすすめ」ソートのスコア計算。Androidに依存しない純粋関数として分離し、単体テスト可能にしている。
 * 入力は「採用済みセッション数」と「最終セッション開始時刻からの経過時間」のみ。
 * 第1段階では直近性と短期間の反復利用 (頻度) だけで順位を決める。
 */

/** 直近性の半減期 (24時間)。 */
const val RECOMMENDED_RECENCY_HALF_LIFE_MS = 24L * 60L * 60L * 1000L

/** 頻度が飽和するセッション数の目安 (64セッションでfrequency=1.0)。 */
const val RECOMMENDED_FREQUENCY_SATURATION_COUNT = 64

const val RECOMMENDED_RECENCY_WEIGHT = 0.65
const val RECOMMENDED_FREQUENCY_WEIGHT = 0.35

/**
 * 直近性。recency(a) = 2 ^ (-age / 半減期)。
 * sessionCountが0の場合は0。ageが負 (端末時刻が戻った等) の場合は0として扱う。
 */
fun recommendedRecency(sessionCount: Int, ageMs: Long): Double {
    if (sessionCount <= 0) return 0.0
    val clampedAge = if (ageMs < 0L) 0L else ageMs
    return 2.0.pow(-(clampedAge.toDouble()) / RECOMMENDED_RECENCY_HALF_LIFE_MS.toDouble())
}

/**
 * 頻度。frequency(a) = ln(1 + count) / ln(1 + 64)、最大1.0にclamp。
 * 表示対象集合の最大値等では正規化しない (同じアプリのスコアは集合が変わっても変化しない)。
 */
fun recommendedFrequency(sessionCount: Int): Double {
    if (sessionCount <= 0) return 0.0
    val value = ln(1.0 + sessionCount) / ln(1.0 + RECOMMENDED_FREQUENCY_SATURATION_COUNT)
    return if (value > 1.0) 1.0 else value
}

/**
 * 最終スコア。score(a) = 0.65 * recency(a) + 0.35 * frequency(a)。
 * 比較用途では丸めない。ログ表示等でのみ呼び出し側で丸める。
 */
fun recommendedScore(sessionCount: Int, ageMs: Long): Double {
    return RECOMMENDED_RECENCY_WEIGHT * recommendedRecency(sessionCount, ageMs) +
        RECOMMENDED_FREQUENCY_WEIGHT * recommendedFrequency(sessionCount)
}
