package com.example.feedlite.data

import com.example.feedlite.data.HttpUtil.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * OpenAI 兼容翻译客户端（v1.10：磁盘缓存）。
 *
 * 支持任何提供 `/chat/completions` 的模型服务（DeepSeek / MiMo / 自定义）：
 * - POST {baseUrl}/chat/completions
 * - Authorization: Bearer <apiKey>
 * - 解析 choices[0].message.content
 *
 * 译文按「原文 SHA1」缓存到 files/translations/，重复进入 / 离线直接命中秒显。
 */
class Translator(
    private val store: TranslationStore,
    cacheDir: File?,
    private val client: OkHttpClient = OkHttpClient(),
) {

    private val cacheDir = cacheDir ?: File(System.getProperty("java.io.tmpdir"), "translations")
    private val evictLock = Any()

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        // 磁盘缓存命中（离线可用）
        getCached(text)?.let { return@withContext it }

        val cfg = store.current()
        if (cfg.apiKey.isBlank()) throw TranslationException("未配置翻译 API Key，请先到「设置」填写")
        if (text.isBlank()) throw TranslationException("没有可翻译的内容")
        // 安全：翻译端点仅允许 https（本地/内网 http 除外），防 API Key 明文出网
        if (!UrlPolicy.isAllowedTranslationBaseUrl(cfg.baseUrl)) {
            throw TranslationException("翻译端点仅支持 https://（本地/内网 http 除外），请检查 Base URL")
        }

        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", cfg.model.ifBlank { "deepseek-chat" })
            put("temperature", 0.3)
            put("max_tokens", 4000)
            put("messages", JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "你是一名专业翻译。把用户提供的内容准确翻译成${cfg.targetLang}。只输出译文本身，不要解释、不要引言、不要保留原文。代码、URL、数字与形如 @@C数字@@ 的标记必须原样保留。",
                        )
                    }
                )
                put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "请翻译以下内容：\n\n$text")
                    }
                )
            })
        }

        val conn = client.newCall(
            Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .post(body.toString().toByteArray(Charsets.UTF_8).toRequestBody(JSON_MEDIA_TYPE))
                .build()
        ).execute()
        try {
            if (!conn.isSuccessful) {
                val err = conn.body?.byteStream()?.use { it.readBounded(4096) }?.toString(Charsets.UTF_8) ?: ""
                throw TranslationException("接口返回 ${conn.code}：${err.take(200)}")
            }
            val resp = conn.body?.byteStream()?.use {
                it.readBounded(HttpUtil.MAX_TRANSLATION_BYTES).toString(Charsets.UTF_8)
            } ?: throw TranslationException("接口响应为空")
            val json = JSONObject(resp)
            val choices = json.optJSONArray("choices")
                ?: throw TranslationException("响应缺少 choices 字段")
            if (choices.length() == 0) throw TranslationException("接口未返回译文")
            val content = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (content.isEmpty()) throw TranslationException("译文为空")

            // 写入磁盘缓存
            putCache(text, content)
            content
        } finally {
            conn.close()
        }
    }

    private fun cacheFileOf(text: String): File {
        val sha = MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        cacheDir.mkdirs()
        return File(cacheDir, "$sha.txt")
    }

    private fun getCached(text: String): String? {
        return try {
            val f = cacheFileOf(text)
            if (f.exists()) f.readText(Charsets.UTF_8).takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun putCache(text: String, result: String) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            cacheFileOf(text).writeText(result, Charsets.UTF_8)
            evictIfNeeded()
        } catch (e: Exception) {
            // 写入失败忽略
        }
    }

    /** 磁盘译文缓存 LRU 淘汰（v1.33）：总量/文件数超限时删除最旧文件。 */
    private fun evictIfNeeded() = synchronized(evictLock) {
        val files = cacheDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        var count = files.size
        if (total <= DiskLimits.MAX_TRANSLATION_BYTES && count <= DiskLimits.MAX_TRANSLATION_FILES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= DiskLimits.MAX_TRANSLATION_BYTES && count <= DiskLimits.MAX_TRANSLATION_FILES) break
            total -= f.length()
            count--
            f.delete()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class TranslationException(message: String) : Exception(message)
