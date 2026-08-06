package com.example.feedlite.data

/**
 * 新文章通知文本生成（纯函数，可单测）。
 *
 * 输入后台同步的增量结果，输出给通知用的汇总文案。
 * 规则：
 * - 无新文章 → null（不弹通知）；
 * - 1 篇 → 「源名 新增 1 篇」；
 * - 多源 → 按新增数取前 3 个源（「源名 N 篇」用顿号连接），超过 3 个源追加「等 N 个源」。
 */
object NotificationSummary {

    fun from(added: Map<String, Int>, sourceTitles: Map<String, String>): String? {
        val total = added.values.sum()
        if (total <= 0) return null

        val top = added.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString("、") { (id, count) ->
                "${sourceTitles[id] ?: id} $count 篇"
            }
        return if (added.size > 3) "$top 等 ${added.size} 个源" else top
    }
}
