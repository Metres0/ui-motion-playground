package com.example.feedlite

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.example.feedlite.data.ImageContext
import com.example.feedlite.data.SyncScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局配置：依赖容器 + Coil 图片加载器。
 *
 * 解决「多源图片加载失败」两大问题：
 * 1. **防盗链**：多数图片 CDN（如 theverge / 部分中文站）校验 Referer，
 *    拦截器自动带上 `Referer: https://{图域}/` 与完整 UA；
 * 2. **超时**：弱网下给足读取时间。
 */
class FeedLiteApp : Application() {

    /** 应用级依赖容器（v1.32：旋转/配置变更不重建，缓存与在途请求不丢）。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)

        // ★ v1.32：按「自动更新间隔」注册后台同步（0=手动则取消）
        SyncScheduler.schedule(this)

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val imageHost = request.url.host
                // 详情页内：优先用文章域作为 Referer（防盗链最稳）；
                // 离开详情页后回退图片域（列表缩略图场景）
                val refererHost = ImageContext.articleRefererHost ?: imageHost
                val builder = request.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (FeedLite/1.32; Android)")
                if (refererHost.isNotBlank()) {
                    builder.header("Referer", "https://$refererHost/")
                }
                chain.proceed(builder.build())
            }
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
