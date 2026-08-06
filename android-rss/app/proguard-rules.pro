# FeedLite 混淆规则
# 无反射 / 无序列化库，主要为 Coil / OkHttp 保留规则
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class com.example.feedlite.data.** { *; }
