package com.example.feedlite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
class Translator(private val store: TranslationStore, cacheDir: File?) {

    private val cacheDir = cacheDir ?: File(System.getProperty("java.io.tmpdir"), "translations")

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        // 磁盘缓存命中（离线可用）
        getCached(text)?.let { return@withContext it }

        val cfg = store.current()
        if (cfg.apiKey.isBlank()) throw TranslationException("未配置翻译 API Key，请先到「设置」填写")
        if (text.isBlank()) throw TranslationException("没有可翻译的内容")

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

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                throw TranslationException("接口返回 $code：${err.take(200)}")
            }
            val resp = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
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
            conn.disconnect()
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
            cacheFileOf(text).writeText(result, Charsets.UTF_8)
        } catch (e: Exception) {
            // 写入失败忽略
        }
    }
}

class TranslationException(message: String) : Exception(message)
