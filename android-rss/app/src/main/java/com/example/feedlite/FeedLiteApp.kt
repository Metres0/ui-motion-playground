package com.example.feedlite

import android.app.Application
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局配置：Coil 图片加载器。
 *
 * 解决「多源图片加载失败」两大问题：
 * 1. **防盗链**：多数图片 CDN（如 theverge / 部分中文站）校验 Referer，
 *    拦截器自动带上 `Referer: https://{图域}/` 与完整 UA；
 * 2. **超时**：弱网下给足读取时间。
 */
class FeedLiteApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val builder = request.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (FeedLite/1.2; Android)")
                if (host.isNotBlank()) {
                    builder.header("Referer", "https://$host/")
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
