package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 后台同步健康记录（v1.33）：按源追踪连续失败次数，供 WorkManager 调度时跳过退避中的源。
 *
 * 动机：某个源长时间不可达（如源站屏蔽/域名失效）不应每周期反复拉取拖慢同步；
 * 连续失败达到阈值后该源暂停直到下一次调度周期，成功后立即清零。
 */
class SyncFailureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_health", Context.MODE_PRIVATE)

    /** 某源连续失败次数。 */
    fun consecutiveFailures(sourceId: String): Int =
        prefs.getInt(key(sourceId), 0).coerceAtLeast(0)

    /** 是否进入退避（连续失败 ≥ [MAX_CONSECUTIVE_FAILURES]），调度时应跳过该源。 */
    fun isInBackoff(sourceId: String): Boolean =
        consecutiveFailures(sourceId) >= MAX_CONSECUTIVE_FAILURES

    /** 记录一次失败（计数+1）。 */
    fun recordFailure(sourceId: String) {
        prefs.edit().putInt(key(sourceId), consecutiveFailures(sourceId) + 1).apply()
    }

    /** 记录一次成功（清零）。 */
    fun recordSuccess(sourceId: String) {
        prefs.edit().remove(key(sourceId)).apply()
    }

    /** 强制清除某源记录（用户手动刷新成功时可调用）。 */
    fun clear(sourceId: String) = recordSuccess(sourceId)

    private fun key(sourceId: String) = "failures_$sourceId"

    companion object {
        /** 连续失败达到该次数后暂停该源的自动同步。 */
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
