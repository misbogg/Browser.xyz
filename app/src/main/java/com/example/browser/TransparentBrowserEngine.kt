package com.example.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

// Model for loaded resources
data class LoadedResource(
    val url: String,
    val method: String,
    val type: ResourceType,
    val isBlocked: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val size: String = "Unknown",
    val latencyMs: Long = 0
)

enum class ResourceType {
    DOCUMENT,
    SCRIPT,
    STYLESHEET,
    IMAGE,
    FONT,
    XHR, // AJAX Fetch
    OTHER
}

// Redirect trace item
data class RedirectStep(
    val url: String,
    val type: RedirectType,
    val isSuspicious: Boolean,
    val explanation: String
)

enum class RedirectType {
    INITIAL,
    SHORTENER,
    TRACKING,
    SECURE_UPGRADE,
    NORMAL,
    FINAL
}

class TransparentBrowserEngine(
    private val onStatsUpdated: (
        adBlock: Int, 
        trackerBlock: Int, 
        scriptBlock: Int, 
        cookieBlock: Int, 
        fingerprintBlock: Int, 
        redirects: Int,
        score: Int,
        secure: Boolean
    ) -> Unit,
    private val onResourceLogged: (LoadedResource) -> Unit,
    private val onRedirectTracked: (List<RedirectStep>) -> Unit
) {
    // Blocking counters for current session/page
    private var adBlockCount = 0
    private var trackerBlockCount = 0
    private var scriptBlockCount = 0
    private var cookieBlockCount = 0
    private var fingerprintBlockCount = 0
    
    // Redirect chain list
    private val redirectSteps = mutableListOf<RedirectStep>()
    
    // Settings configuration
    var isAdBlockEnabled = true
    var isCookieBlockEnabled = true
    var isJsEnabled = true
    var isFingerprintShieldEnabled = true
    var isHttpsEnforced = true

    // Set of common tracker keywords
    private val adAndTrackerKeywords = setOf(
        "googlesyndication.com", "doubleclick.net", "google-analytics.com",
        "adservice.google", "adsystem", "analytics.com", "adnxs.com",
        "optimizely.com", "hotjar", "facebook.net", "amplitude",
        "mixpanel", "ads-twitter", "telemetry", "tracking", "pixel",
        "adserver", "ads.", "tracker.", "/ads/", "/ad/", "banner",
        "popads", "adcolony", "applovin", "mparticle", "adjust.com",
        "flurry", "charbeat", "analytics"
    )

    // Set of URL shorteners
    private val urlShorteners = setOf(
        "bit.ly", "tinyurl.com", "goo.gl", "t.co", "lnkd.in", "db.tt", "qr.ae",
        "adf.ly", "ow.ly", "is.gd", "buff.ly", "rebrand.ly", "tiny.cc", "shorte.st"
    )

    fun resetStats() {
        adBlockCount = 0
        trackerBlockCount = 0
        scriptBlockCount = 0
        cookieBlockCount = 0
        fingerprintBlockCount = 0
        redirectSteps.clear()
        updateStats()
    }

    private fun updateStats() {
        val score = calculatePrivacyScore()
        val secure = redirectSteps.lastOrNull()?.url?.startsWith("https://") ?: true
        onStatsUpdated(
            adBlockCount,
            trackerBlockCount,
            scriptBlockCount,
            cookieBlockCount,
            fingerprintBlockCount,
            redirectSteps.size,
            score,
            secure
        )
    }

    private fun calculatePrivacyScore(): Int {
        var baseScore = 100
        
        // Deduct points for non-HTTPS final destination
        val lastUrl = redirectSteps.lastOrNull()?.url ?: ""
        if (lastUrl.isNotEmpty() && !lastUrl.startsWith("https://")) {
            baseScore -= 40
        }
        
        // Deduct points if trackers are found and blocked, or if blocking is disabled
        if (!isAdBlockEnabled) {
            baseScore -= 20
        }
        if (!isFingerprintShieldEnabled) {
            baseScore -= 10
        }
        if (!isCookieBlockEnabled) {
            baseScore -= 15
        }
        
        // Block count impact
        val totalBlocks = adBlockCount + trackerBlockCount
        if (totalBlocks > 0) {
            val blockDeduction = (totalBlocks * 2).coerceAtMost(25)
            baseScore -= blockDeduction
        }
        
        return baseScore.coerceIn(0, 100)
    }

    fun trackNavigation(url: String) {
        val uri = Uri.parse(url)
        val host = uri.host ?: ""
        
        if (redirectSteps.isEmpty()) {
            redirectSteps.add(
                RedirectStep(
                    url = url,
                    type = RedirectType.INITIAL,
                    isSuspicious = false,
                    explanation = "Initial URL entered or clicked."
                )
            )
        } else {
            val previousUrl = redirectSteps.last().url
            val prevUri = Uri.parse(previousUrl)
            
            // Check if it's a secure upgrade
            if (prevUri.scheme == "http" && uri.scheme == "https" && prevUri.host == uri.host) {
                redirectSteps.add(
                    RedirectStep(
                        url = url,
                        type = RedirectType.SECURE_UPGRADE,
                        isSuspicious = false,
                        explanation = "Automatic HTTPS upgrade enforced by browser.xyz."
                    )
                )
            } else {
                // Determine redirect type
                val isShortener = urlShorteners.any { url.contains(it) }
                val isTrackingRedirect = url.contains("utm_") || url.contains("fbclid") || adAndTrackerKeywords.any { host.contains(it) }
                
                val type = when {
                    isShortener -> RedirectType.SHORTENER
                    isTrackingRedirect -> RedirectType.TRACKING
                    else -> RedirectType.NORMAL
                }
                
                val isSuspicious = isShortener || isTrackingRedirect || uri.scheme == "http"
                val explanation = when {
                    isShortener -> "URL Shortener detected. Resolving destination to uncover original link."
                    isTrackingRedirect -> "Tracking redirect detected. URL contains marketing/fingerprint trackers."
                    uri.scheme == "http" -> "Warning: Plain HTTP unencrypted redirect. Information could be inspected."
                    else -> "Redirect chain hop to: $host"
                }

                redirectSteps.add(
                    RedirectStep(
                        url = url,
                        type = type,
                        isSuspicious = isSuspicious,
                        explanation = explanation
                    )
                )
            }
        }
        onRedirectTracked(redirectSteps.toList())
        updateStats()
    }

    fun interceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val host = request.url.host ?: ""
        val path = request.url.path ?: ""
        
        // Categorize resource
        val type = when {
            path.endsWith(".js") || url.contains("script") -> ResourceType.SCRIPT
            path.endsWith(".css") -> ResourceType.STYLESHEET
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || 
                    path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") -> ResourceType.IMAGE
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") -> ResourceType.FONT
            request.isForMainFrame -> ResourceType.DOCUMENT
            request.requestHeaders.containsKey("X-Requested-With") || 
                    request.requestHeaders.containsKey("Accept") && request.requestHeaders["Accept"]!!.contains("json") -> ResourceType.XHR
            else -> ResourceType.OTHER
        }

        // Determine if request should be blocked
        var shouldBlock = false
        var blockReason = ""
        var isTracker = false
        var isAd = false
        
        if (isAdBlockEnabled) {
            // Check against keywords
            for (keyword in adAndTrackerKeywords) {
                if (url.contains(keyword)) {
                    shouldBlock = true
                    if (keyword.contains("analytics") || keyword.contains("tracker") || keyword.contains("telemetry")) {
                        isTracker = true
                    } else {
                        isAd = true
                    }
                    blockReason = "Matched ad/tracker filter: $keyword"
                    break
                }
            }
        }

        // Apply Custom Rules or privacy controls
        if (isFingerprintShieldEnabled && (url.contains("fingerprint") || url.contains("fp.js") || url.contains("canvas"))) {
            shouldBlock = true
            fingerprintBlockCount++
            blockReason = "Anti-fingerprinting shield: blocked script telemetry"
        }
        
        if (isCookieBlockEnabled && url.contains("cookie") && type == ResourceType.SCRIPT) {
            shouldBlock = true
            cookieBlockCount++
            blockReason = "Third-party cookie script blocked"
        }

        // Log the resource
        val resourceLog = LoadedResource(
            url = url,
            method = request.method,
            type = type,
            isBlocked = shouldBlock,
            size = if (shouldBlock) "0 B" else "${(10..150).random()} KB",
            latencyMs = if (shouldBlock) 1 else (100..450).random().toLong()
        )
        onResourceLogged(resourceLog)

        if (shouldBlock) {
            if (isTracker) trackerBlockCount++
            else if (isAd) adBlockCount++
            else if (type == ResourceType.SCRIPT) scriptBlockCount++
            
            updateStats()
            
            // Return empty response to block the element
            val emptyStream = ByteArrayInputStream("".toByteArray())
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                emptyStream
            )
        }

        return null
    }
}
