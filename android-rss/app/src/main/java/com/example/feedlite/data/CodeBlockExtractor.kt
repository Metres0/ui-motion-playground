package com.example.feedlite.data

/**
 * 翻译前代码块提取器 —— 保证「代码块不参与翻译」。
 *
 * 流程：
 * 1. [extract]：把 HTML 中的 <pre> 代码块整体挖出，替换为占位符 `@@C0@@`；
 * 2. 占位文本参与翻译（提示词会保留 @@ 标记）；
 * 3. [restore]：把译文中的占位符替换回代码原文（``` 围栏包裹展示）。
 *
 * 若翻译引擎改写了占位符，fallback 把未还原的代码块按原顺序追加到译文末尾。
 */
object CodeBlockExtractor {

    private val PLACEHOLDER = Regex("""@@C(\d+)@@""")

    data class Extracted(val placeholderText: String, val codes: List<String>)

    fun extract(html: String): Extracted {
        val codes = mutableListOf<String>()
        val result = Regex("""(?is)<pre[^>]*>([\s\S]*?)</pre>""").replace(html) { m ->
            codes += cleanCode(m.groupValues[1])
            "\n@@C${codes.size - 1}@@\n"
        }
        return Extracted(result, codes)
    }

    fun restore(translated: String, codes: List<String>): String {
        var s = translated
        val restoredFlags = BooleanArray(codes.size)
        s = PLACEHOLDER.replace(s) { m ->
            val idx = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            if (idx in codes.indices) {
                restoredFlags[idx] = true
                "\n```\n${codes[idx]}\n```\n"
            } else m.value
        }
        // fallback：被翻译改写的占位符，代码块按顺序追加在末尾
        val leftover = codes.indices.filter { !restoredFlags[it] }
        if (leftover.isNotEmpty()) {
            val sb = StringBuilder(s)
            for (idx in leftover) {
                sb.append("\n\n[代码块]\n```\n").append(codes[idx]).append("\n```\n")
            }
            s = sb.toString()
        }
        return s.trim()
    }

    private fun cleanCode(raw: String): String = raw
        .replace(Regex("""(?is)<code[^>]*>"""), "")
        .replace(Regex("""(?i)</code>"""), "")
        .trim()
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace("\r\n", "\n")
}
