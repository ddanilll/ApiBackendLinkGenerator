package com.example.linkgenerator

import android.os.Build
import androidx.annotation.RequiresApi
import org.apache.commons.codec.digest.HmacUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*
import java.time.Duration
import org.springframework.http.HttpStatus

@Service
class LinkService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${app.secret-key}") private val secretKey: String,
    @Value("\${app.domain}") private val domain: String
) {
    companion object {
        private const val LINK_TTL_MINUTES = 10L
        private const val IOS_PATTERN = "iPhone|iPad|iPod"
    }

    fun validateSignature(request: GenerateLinkRequest): Boolean {
        val data = "${request.paymentId}|${request.osType}|${request.isWebView}"
        return HmacUtils.hmacSha256Hex(secretKey, data) == request.signature
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateMaskedUrl(request: GenerateLinkRequest): String {
        val linkId = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(
            "link:$linkId",
            "${request.paymentId}|${request.osType}|${request.isWebView}",
            Duration.ofMinutes(LINK_TTL_MINUTES)
        )
        return "$domain/api/p/$linkId"
    }

    fun getRedirectResponse(linkId: String, userAgent: String?): ResponseEntity<String> {
        val storedData = redisTemplate.opsForValue().get("link:$linkId")
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Link expired")

        val (paymentId, osType, isWebView) = storedData.split("|")
        val redirectContent = when {
            userAgent?.contains(Regex(IOS_PATTERN)) == true -> generateIOSContent(paymentId)
            userAgent?.contains("Android") == true -> generateAndroidIntent(paymentId)
            else -> generateDesktopUrl(paymentId)
        }

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8")
            .body(redirectContent)
    }

    private fun generateIOSContent(paymentId: String) = """
        <html>
            <script>
                function tryOpen(scheme, fallback) {
                    window.location = scheme;
                    setTimeout(() => {
                        if (!document.hidden) window.location = fallback;
                    }, 1000);
                }
                tryOpen(
                    'bank100000000004://tpay/$paymentId',
                    'tinkoffbank://tpay/$paymentId'
                );
                setTimeout(() => {
                    window.location = '$domain/ios-fallback/$paymentId';
                }, 2000);
            </script>
        </html>
    """.trimIndent()

    private fun generateAndroidIntent(paymentId: String) = """
        intent://tpay/$paymentId
        #Intent;
            scheme=tinkoffbank;
            package=ru.tinkoff.android;
            S.browser_fallback_url=${encodeURL("https://rustore.ru/app/tinkoff")};
        end
    """.trimIndent().replace("\n", "")

    private fun generateDesktopUrl(paymentId: String) =
        "https://tpay-web.com/pay/$paymentId"

    private fun encodeURL(url: String) =
        java.net.URLEncoder.encode(url, "UTF-8")
}