package com.example.linkgenerator

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