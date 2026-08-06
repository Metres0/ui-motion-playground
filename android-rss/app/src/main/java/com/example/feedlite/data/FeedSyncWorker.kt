package com.example.feedlite.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 后台自动同步（v1.32；v1.33：per-source 失败计数 + 退避）。
 *
 * - 只更新满足更新间隔的启用源（[UpdateSettings.needsUpdate]）；
 * - 连续失败达到 [SyncFailureStore.MAX_CONSECUTIVE_FAILURES] 的源暂停本轮（退避），成功后清零；
 * - 本轮全部失败时返回 [Result.retry]，交给 WorkManager 指数退避；
 * - 与前台刷新共用 [RssRepository.updateSources]（含失败追踪与重试）。
 */
class FeedSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as com.example.feedlite.FeedLiteApp).container
        val store = container.subscriptionStore
        val settings = container.updateSettings
        val repository = container.repository
        val health = container.syncFailureStore

        val enabled = store.enabledIds()
        val sources = enabled.mapNotNull { id -> store.allSources().firstOrNull { it.id == id } }
        val due = sources
            .filter { settings.needsUpdate(repository, it.id) }
            .filterNot { health.isInBackoff(it.id) } // ★ 退避中的源本轮跳过
        if (due.isEmpty()) return Result.success()

        val result = repository.updateSources(due)
        // 更新每源健康记录：成功清零，失败 +1
        for (src in due) {
            if (src.id in result.failures) health.recordFailure(src.id) else health.recordSuccess(src.id)
        }
        // ★ v1.34：新文章通知（开启且无新增时才不弹）
        if (settings.load().notifyEnabled) {
            val titles = store.allSources().associate { it.id to it.title }
            NotificationSummary.from(result.added, titles)?.let { summary ->
                NewArticleNotifier(applicationContext).notify(result.added.values.sum(), summary)
            }
        }
        return if (result.failures.size == due.size) {
            Result.retry() // 全部失败：WorkManager 指数退避重试
        } else {
            Result.success()
        }
    }
}

/**
 * 同步调度器：按设置的更新间隔注册 PeriodicWorkRequest。
 * 间隔 = 0（手动）时取消调度；间隔变化后重新注册。
 */
object SyncScheduler {

    private const val WORK_NAME = "feedlite_feed_sync"

    /** 按当前设置调度（或取消）。应用启动与设置变更时调用。 */
    fun schedule(context: Context) {
        val intervalHours = UpdateSettings(context).load().intervalHours
        if (intervalHours <= 0) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<FeedSyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
