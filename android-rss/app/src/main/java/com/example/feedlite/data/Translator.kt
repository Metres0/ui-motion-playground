package com.example.feedlite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容翻译客户端。
 *
 * 支持任何提供 `/chat/completions` 的模型服务（DeepSeek / MiMo / 自定义）：
 * - POST {baseUrl}/chat/completions
 * - Authorization: Bearer <apiKey>
 * - 解析 choices[0].message.content
 *
 * 全程 IO 线程执行；失败抛出 [TranslationException] 供 UI 展示。
 */
class Translator(private val store: TranslationStore) {

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
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
            content
        } finally {
            conn.disconnect()
        }
    }
}

class TranslationException(message: String) : Exception(message)
