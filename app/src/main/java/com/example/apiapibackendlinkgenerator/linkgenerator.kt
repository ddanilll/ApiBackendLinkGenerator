package com.example.apiapibackendlinkgenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.apache.commons.codec.digest.HmacUtils
import java.util.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@SpringBootApplication
class LinkGeneratorApplication

fun main(args: Array<String>) {
    runApplication<LinkGeneratorApplication>(*args)
}

data class GenerateLinkRequest(
    val paymentId: String,
    val osType: String,
    val isWebView: Boolean,
    val signature: String
)

data class LinkData(
    val paymentId: String,
    val osType: String,
    val isWebView: Boolean
)

@RestController
@RequestMapping("/api")
class LinkController(private val linkService: LinkService) {

    @PostMapping("/generate-link")
    fun generateLink(@RequestBody request: GenerateLinkRequest): ResponseEntity<Map<String, String>> {
        if (!linkService.validateSignature(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val maskedUrl = linkService.generateMaskedUrl(request)
        return ResponseEntity.ok(mapOf("url" to maskedUrl))
    }

    @GetMapping("/p/{linkId}")
    fun handleRedirect(
        @PathVariable linkId: String,
        @RequestHeader("User-Agent") userAgent: String?
    ): ResponseEntity<String> {
        val redirectUrl = linkService.getRedirectUrl(linkId, userAgent)
        return ResponseEntity.status(HttpStatus.FOUND)
            .header("Location", redirectUrl)
            .build()
    }
}

@Service
class LinkService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${app.secret-key}") private val secretKey: String,
    @Value("\${app.domain}") private val domain: String
) {
    companion object {
        private const val LINK_TTL_SECONDS = 600L // 10 минут
    }

    fun generateSignature(request: GenerateLinkRequest): String {
        val data = "${request.paymentId}|${request.osType}|${request.isWebView}"
        return HmacUtils.hmacSha256Hex(secretKey, data)
    }

    fun validateSignature(request: GenerateLinkRequest): Boolean {
        val expectedSignature = generateSignature(request)
        return expectedSignature == request.signature
    }

    fun generateMaskedUrl(request: GenerateLinkRequest): String {
        val linkId = UUID.randomUUID().toString()
        val linkData = LinkData(
            paymentId = request.paymentId,
            osType = request.osType,
            isWebView = request.isWebView
        )

        // Сохраняем в Redis на 10 минут
        redisTemplate.opsForValue().set(
            "link:$linkId",
            "${linkData.paymentId}|${linkData.osType}|${linkData.isWebView}",
            Duration.ofSeconds(LINK_TTL_SECONDS)
        )

        return "$domain/p/$linkId"
    }

    fun getRedirectUrl(linkId: String, userAgent: String?): String {
        val storedData = redisTemplate.opsForValue().get("link:$linkId")
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Link expired or not found")

        val (paymentId, osType, isWebView) = storedData.split("|")

        return when {
            userAgent?.contains(Regex("iPhone|iPad|iPod")) == true -> generateIOSUrl(paymentId)
            userAgent?.contains("Android") == true -> generateAndroidUrl(paymentId)
            else -> "https://tpay-web.com/pay/$paymentId" // Десктоп
        }
    }

    private fun generateIOSUrl(paymentId: String): String {
        return """
            <html>
                <script>
                    window.location = 'bank100000000004://tpay/$paymentId';
                    setTimeout(() => {
                        window.location = 'tinkoffbank://tpay/$paymentId';
                    }, 500);
                    setTimeout(() => {
                        window.location = '$domain/ios-fallback/$paymentId';
                    }, 1000);
                </script>
            </html>
        """.trimIndent()
    }

    private fun generateAndroidUrl(paymentId: String): String {
        return "intent://tpay/$paymentId#Intent;" +
                "scheme=tinkoffbank;" +
                "package=ru.tinkoff.android;" +
                "S.browser_fallback_url=https://rustore.ru/app/tinkoff;" +
                "end"
    }
}