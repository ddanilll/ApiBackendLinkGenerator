package com.example.linkgenerator

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class LinkController(private val linkService: LinkService) {

    @PostMapping("/generate-link")
    fun generateLink(@RequestBody request: GenerateLinkRequest): ResponseEntity<Map<String, String>> {
        if (!linkService.validateSignature(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(mapOf("url" to linkService.generateMaskedUrl(request)))
    }

    @GetMapping("/p/{linkId}")
    fun handleRedirect(
        @PathVariable linkId: String,
        @RequestHeader("User-Agent", required = false) userAgent: String?
    ) = linkService.getRedirectResponse(linkId, userAgent)
}