package com.example.feedlite

import com.example.feedlite.data.UrlPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UrlPolicy 单测：翻译端点 https 强制，本地/内网 http 例外。 */
class UrlPolicyTest {

    @Test
    fun `https 允许`() {
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("https://api.deepseek.com"))
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("https://api.openai.com/v1"))
    }

    @Test
    fun `明文 http 拒绝`() {
        assertFalse(UrlPolicy.isAllowedTranslationBaseUrl("http://api.deepseek.com"))
        assertFalse(UrlPolicy.isAllowedTranslationBaseUrl("http://example.com"))
    }

    @Test
    fun `本地与内网 http 放行`() {
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("http://localhost:8080"))
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("http://127.0.0.1:3000"))
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("http://10.0.2.2:8080"))
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("http://192.168.1.5:8080"))
        assertTrue(UrlPolicy.isAllowedTranslationBaseUrl("http://10.0.0.1"))
    }

    @Test
    fun `非 http 协议拒绝`() {
        assertFalse(UrlPolicy.isAllowedTranslationBaseUrl("ftp://example.com"))
        assertFalse(UrlPolicy.isAllowedTranslationBaseUrl("javascript:alert(1)"))
        assertFalse(UrlPolicy.isAllowedTranslationBaseUrl(""))
    }
}
