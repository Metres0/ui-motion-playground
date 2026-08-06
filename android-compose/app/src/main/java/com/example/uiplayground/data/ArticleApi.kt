package com.example.uiplayground.data

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 模拟远程接口（抽象为纯接口，便于 JVM 单测注入 Fake 实现，见 app/src/test）。
 */
interface ArticleApi {

    /**
     * 分页拉取列表。
     *
     * 参数是「偏移量 + 数量」，**不是页码**：
     * - [start]：本次拉取在总数据集（60 条）中的起始下标（0-based）
     * - [count]：本次最多拉取多少条
     *
     * 选偏移量语义而不是「page × pageSize」，是因为 Paging3 用 offset 作 key 才能精确分页：
     * 首屏 initialLoadSize=40 + 追加 loadSize=20 时，页码语义会重复取 20~39，
     * 产生 keyed LazyColumn 下的重复 id；offset 语义保证任何一次追加都不与已有页重叠。
     */
    suspend fun getArticles(start: Int, count: Int): List<Article>

    /** 拉取详情。 */
    suspend fun getArticleDetail(id: Long): ArticleDetail
}

/**
 * 默认实现（生产用）：用随机延迟模拟真实网络（详情页 200~800ms），
 * 图片用 picsum.photos 的稳定种子 URL，保证「渐进式加载」有真实可看的网络过程。
 */
object FakeArticleApi : ArticleApi {

    private const val total = 60

    override suspend fun getArticles(start: Int, count: Int): List<Article> {
        delay(Random.nextLong(250, 550)) // 模拟网络延迟
        if (start >= total) return emptyList()
        return (start until minOf(start + count, total)).map { index -> articleAt(index) }
    }

    override suspend fun getArticleDetail(id: Long): ArticleDetail {
        delay(Random.nextLong(200, 800)) // 模拟网络延迟
        val index = id.toInt() - 1
        val a = articleAt(index)
        return ArticleDetail(
            article = a,
            body = buildString {
                append("这是第 ${a.id} 篇文章的正文。")
                append("它演示了「路由预取 + 请求合并 + 共享元素转场 + 渐进式图片」的完整链路。\n\n")
                repeat(6) { p ->
                    append("段落 ${p + 1}：")
                    append("当你在列表页点击这一项时，路由拦截器已经提前发起了详情请求；")
                    append("详情页 ViewModel 发起的同 key 请求会被合并层吸收，")
                    append("共享元素转场的 350ms 刚好掩盖剩余的加载时间。\n\n")
                }
            },
            readTimeMin = Random.nextInt(2, 9),
        )
    }

    private fun articleAt(index: Int): Article {
        val id = index + 1L
        return Article(
            id = id,
            title = "示例文章 #$id",
            subtitle = listOf(
                "共享元素转场", "预测式返回", "Paging 预载", "请求合并",
                "路由预取", "渐进式图片", "stagger 列表", "View Transitions",
            )[index % 8],
            coverUrl = "https://picsum.photos/seed/article$id/600/400",
            seed = id.toInt(),
        )
    }
}
