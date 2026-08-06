package com.example.uiplayground.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 路由预取拦截器 —— 演示「L1 路由级预取」。
 *
 * 在导航真正发生之前（点击瞬间），路由层就能从导航参数里拿到业务 ID，
 * 提前发起详情请求。详情页自身的请求会被 Repository 的请求合并层吸收，
 * 因此不会产生重复网络调用（见 ArticleRepository）。
 */
class PrefetchRouter(private val repository: ArticleRepository) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 在导航发生前调用。可扩展为：
     * - 按路由参数匹配预取规则（详情页/搜索/Web 容器）
     * - 实验开关 / A/B
     * - 埋点
     * - 预取失败静默（不阻塞导航）
     */
    fun intercept(destinationRoute: String, args: Map<String, Any>) {
        // 只对详情路由做预取
        val id = args["id"] as? Long ?: return
        scope.launch {
            try {
                repository.getArticleDetail(id)
                Log.d("PrefetchRouter", "预取成功 detail/$id")
            } catch (e: Exception) {
                // 预取失败不影响导航：页面自身请求会兜底
                Log.d("PrefetchRouter", "预取失败 detail/$id: ${e.message}")
            }
        }
    }
}
