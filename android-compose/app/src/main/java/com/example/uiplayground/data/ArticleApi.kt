package com.example.uiplayground.data

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 模拟远程接口。
 *
 * 用随机延迟模拟真实网络（详情页 200~800ms），图片用 picsum.photos 的稳定种子 URL，
 * 保证「渐进式加载」有真实可看的网络过程。
 */
class ArticleApi {

    private val total = 60

    /** 分页拉取列表。 */
    suspend fun getArticles(page: Int, pageSize: Int): List<Article> {
        delay(Random.nextLong(250, 550)) // 模拟网络延迟
        val start = (page - 1) * pageSize
        if (start >= total) return emptyList()
        return (start until minOf(start + pageSize, total)).map { index -> articleAt(index) }
    }

    /** 拉取详情。 */
    suspend fun getArticleDetail(id: Long): ArticleDetail {
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
