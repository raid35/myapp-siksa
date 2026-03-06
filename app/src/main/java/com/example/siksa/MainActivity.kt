
package com.example.siksa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        checkForSniffers()
        super.onCreate(savedInstanceState)

        // إعدادات الشاشة الكاملة
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        supportActionBar?.hide()

        setContent {
            // خلفية متحركة (يجب أن تكون معرفة في مشروعك)
            AnimatedGradientBackground {
                var selectedPackageUrl by remember { mutableStateOf<String?>(null) }
                var selectedIndex by remember { mutableStateOf(0) }
                var selectedChannelIndex by remember { mutableStateOf(0) }

                // إدارة الروابط الخارجية
                val externalUrl = remember { getExternalM3uUrl() }

                when {
                    externalUrl != null -> {
                        val lowerUrl = externalUrl.lowercase().trim()

                        // إذا كان الرابط فيديو مباشر وصريح، افتح المشغل
                        if (isActuallyDirectVideo(lowerUrl)) {
                            LaunchedEffect(externalUrl) {
                                val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                    data = Uri.parse(externalUrl)
                                    putExtra("streamUrl", externalUrl)
                                }
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            // عرض القائمة عند استقبال ملف M3U
                            ChannelListScreen(
                                m3uUrl = externalUrl,
                                selectedIndex = selectedChannelIndex,
                                onChannelClick = { selectedChannelIndex = it },
                                onBack = { finish() }
                            )
                        }
                    }

                    selectedPackageUrl == null -> {
                        PackageListScreen(
                            selectedIndex = selectedIndex,
                            onPackageClick = { index, url ->
                                handleUrlNavigation(index, url) { idx, u, isWeb ->
                                    if (isWeb) {
                                        openWebView(u)
                                    } else {
                                        selectedIndex = idx
                                        selectedPackageUrl = u
                                    }
                                }
                            }
                        )
                    }

                    else -> {
                        ChannelListScreen(
                            m3uUrl = selectedPackageUrl!!,
                            selectedIndex = selectedChannelIndex,
                            onChannelClick = { selectedChannelIndex = it },
                            onBack = {
                                selectedChannelIndex = 0
                                selectedPackageUrl = null
                            }
                        )
                    }
                }
            }
        }
    }

    // منطق التنقل الذكي
    private fun handleUrlNavigation(index: Int, url: String, onNavigate: (Int, String, Boolean) -> Unit) {
        val lowerUrl = url.lowercase().trim()
        val isWeb = lowerUrl.endsWith(".html") || lowerUrl.endsWith(".htm") || lowerUrl.contains("github.io")
        onNavigate(index, url, isWeb)
    }

    private fun openWebView(url: String) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("url", url)
            putExtra("userAgent", "Mozilla/5.0 (Linux; Android 10)")
        }
        startActivity(intent)
    }

    private fun isActuallyDirectVideo(url: String): Boolean {
        if (url.contains(".m3u") || url.contains(".m3u8") || url.contains("type=m3u")) return false
        val directExtensions = listOf(".mp4", ".ts", ".mkv", ".mov", ".avi")
        return directExtensions.any { url.endsWith(it) } || url.startsWith("rtmp://")
    }

    private fun checkForSniffers() {
        val sniffers = listOf("com.guoshi.httpcanary", "com.xk72.charles", "com.egorovandreyev.httpdebugger")
        sniffers.forEach { pkg ->
            try {
                packageManager.getPackageInfo(pkg, 0)
                finishAffinity(); System.exit(0)
            } catch (e: Exception) {}
        }
    }

    private fun getExternalM3uUrl(): String? {
        val data = intent?.data
        return when {
            intent?.action == Intent.ACTION_VIEW && data != null -> data.toString()
            intent?.hasExtra("m3u_url") == true -> intent.getStringExtra("m3u_url")
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (getExternalM3uUrl() != null) recreate()
    }
}

@Composable
fun PackageItemCard(
    pkg: PackageItem,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    imageLoader: ImageLoader
) {
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f)

    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .scale(scale)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.2f))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(pkg.logo).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            error = painterResource(android.R.drawable.ic_menu_report_image)
        )
    }
}
@Composable
fun PackageListScreen(
    selectedIndex: Int?,
    onPackageClick: (Int, String) -> Unit
) {
    var packages by remember { mutableStateOf(listOf<PackageItem>()) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .allowHardware(false)
            .crossfade(true)
            .build()
    }

    LaunchedEffect(Unit) {
        packages = loadPackagesFromM3u("https://raw.githubusercontent.com/raid35/channel-links/main/siksa_tv.m3u")
        selectedIndex?.let {
            val rowIndex = it / 5
            listState.scrollToItem(rowIndex)
        }
    }

    val rows = packages.chunked(5)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(rows) { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEachIndexed { itemIndex, pkg ->
                    val actualIndex = rowIndex * 5 + itemIndex
                    var isFocused by remember { mutableStateOf(false) }
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(Unit) {
                        if (selectedIndex == actualIndex) {
                            focusRequester.requestFocus()
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .border(
                                width = if (isFocused) 3.dp else 1.dp,
                                // تغيير اللون من الأصفر إلى الأبيض عند التركيز ليتناسق مع شاشة القنوات
                                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusable()
                            .clickable { onPackageClick(actualIndex, pkg.url) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(pkg.logo)
                                    .setHeader("User-Agent", "Mozilla/5.0")
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                error = painterResource(id = android.R.drawable.ic_menu_gallery),
                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery)
                            )
                        }
                    }
                }
                repeat(5 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
@Composable
fun ChannelListScreen(
    m3uUrl: String,
    selectedIndex: Int,
    onChannelClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var channels by remember { mutableStateOf(listOf<Channel>()) }

    BackHandler { onBack() }
    LaunchedEffect(m3uUrl) {
        channels = loadChannels(m3uUrl)
    }

    val columnCount = 6
    val rows = channels.chunked(columnCount)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(rows) { rowIndex, rowChannels ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowChannels.forEachIndexed { itemIndex, channel ->
                    val actualIndex = rowIndex * columnCount + itemIndex
                    var isFocused by remember { mutableStateOf(false) }
                    val focusRequester = remember(channel.url) { FocusRequester() }

                    LaunchedEffect(selectedIndex, channels) {
                        if (actualIndex == selectedIndex) {
                            delay(100)
                            focusRequester.requestFocus()
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(170.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusable()
                            .clickable {
                                onChannelClick(actualIndex)
                                val allUrls = ArrayList<String>()
                                channels.forEach { ch ->
                                    val decodedUrl = decodeBase64Url(ch.url)
                                    val line = "$decodedUrl|drm=${ch.drmLicense}|userAgent=${ch.userAgent}|referer=${ch.referer}"
                                    allUrls.add(line)
                                }

                                val currentChannel = channels[actualIndex]
                                val realUrl = decodeBase64Url(currentChannel.url)

                                if (isVideoStream(realUrl)) {
                                    val intent = Intent(context, PlayerActivity::class.java).apply {
                                        putStringArrayListExtra("channelsList", allUrls)
                                        putExtra("channelIndex", actualIndex)
                                        putExtra("streamUrl", realUrl)
                                        putExtra("drmLicense", currentChannel.drmLicense)
                                        putExtra("referer", currentChannel.referer)
                                        putExtra("userAgent", currentChannel.userAgent.ifEmpty { "Mozilla/5.0" })
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("url", realUrl)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(channel.logo)
                                        .setHeader("User-Agent", "Mozilla/5.0")
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                            )

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = channel.name,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (rowChannels.size < columnCount) {
                    repeat(columnCount - rowChannels.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
suspend fun loadPackagesFromM3u(url: String): List<PackageItem> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val content = response.body?.string() ?: ""
            val lines = content.lines()

            val packages = mutableListOf<PackageItem>()
            var name = ""
            var logo = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF")) {
                    name = trimmed.substringAfterLast(",").trim()
                    logo = Regex("""tvg-logo=["'](.*?)["']""").find(trimmed)?.groupValues?.get(1)?.trim() ?: ""

                } else if (trimmed.startsWith("http") && !trimmed.startsWith("#")) {
                    packages.add(PackageItem(
                        name = name.ifEmpty { "Package ${packages.size + 1}" },
                        logo = logo,
                        url = trimmed
                    ))
                    name = ""
                    logo = ""
                }
            }
            packages
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
suspend fun loadChannels(url: String): List<Channel> {
    return withContext(Dispatchers.IO) {
        val realUrl = decodeBase64Url(url)
        if (isMacPortalUrl(realUrl) || realUrl.contains("portal.php")) {
            return@withContext loadStalkerPortal(realUrl, "00:1A:79:34:62:66")
        }
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(realUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val content = response.body?.string() ?: ""
                return@withContext parseM3uContent(content)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
suspend fun loadStalkerPortal(baseUrl: String, mac: String): List<Channel> {
    val client = OkHttpClient()
    val channels = mutableListOf<Channel>()
    val portalApi = if (baseUrl.endsWith(".php")) baseUrl else "$baseUrl/portal.php"

    try {
        val handshakeUrl = "$portalApi?type=itv&action=handshake"
        val handshakeReq = Request.Builder()
            .url(handshakeUrl)
            .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=$mac")
            .build()

        val handshakeRes = client.newCall(handshakeReq).execute().body?.string() ?: ""
        val token = Regex("""token":"(.*?)"""").find(handshakeRes)?.groupValues?.get(1) ?: ""
        val getChannelsUrl = "$portalApi?type=itv&action=get_all_channels&token=$token"
        val chReq = handshakeReq.newBuilder().url(getChannelsUrl).build()

        client.newCall(chReq).execute().use { response ->
            val jsonResponse = response.body?.string() ?: ""
            val pattern = Regex("""\{"id":"(.*?)","name":"(.*?)".*?"logo":"(.*?)".*?"cmd":"(.*?)".*?\}""")
            pattern.findAll(jsonResponse).forEach { match ->
                val name = match.groupValues[2]
                val logo = match.groupValues[3].replace("\\/", "/")
                val cmd = match.groupValues[4].replace("\\/", "/")
                val cleanUrl = cmd.replace("ffmpeg ", "").replace("ffrtv ", "")

                channels.add(Channel(
                    name = name,
                    url = cleanUrl,
                    logo = logo,
                    userAgent = "Model: MAG250; Link: WiFi"
                ))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return channels
}
fun parseM3uContent(content: String): List<Channel> {
    val channels = mutableListOf<Channel>()
    val lines = content.lines()

    var currentName = ""
    var currentLogo = ""
    var currentGroup = ""
    var currentDrm = ""
    var currentReferer = ""
    var currentUserAgent = ""

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        when {
            // 1. استخراج معلومات القناة الأساسية
            trimmed.startsWith("#EXTINF") -> {
                currentName = trimmed.substringAfterLast(",").trim()
                currentLogo = Regex("""tvg-logo=["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                currentGroup = Regex("""group-title=["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
            }

            // 2. استخراج الـ User-Agent (يدعم الصيغتين: VLC و KODI)
            trimmed.contains("user-agent=", ignoreCase = true) -> {
                currentUserAgent = trimmed.substringAfter("=").trim()
            }

            // 3. استخراج الـ Referer
            trimmed.contains("referer=", ignoreCase = true) || trimmed.contains("referrer=", ignoreCase = true) -> {
                currentReferer = trimmed.substringAfter("=").trim()
            }

            // 4. استخراج مفتاح التشفير DRM (يدعم KODIPROP و EXTVLCOPT)
            // سيلتقط license_key= أو http-drm-license=
            trimmed.contains("license_key=", ignoreCase = true) ||
                    trimmed.contains("http-drm-license=", ignoreCase = true) -> {
                currentDrm = trimmed.substringAfter("=").trim()
            }

            // 5. رابط البث (نهاية بيانات القناة الحالية)
            trimmed.startsWith("http") || trimmed.startsWith("rtmp") -> {
                // تنظيف الرابط من أي بارامترات ملحقة بـ |
                val urlOnly = trimmed.substringBefore("|").trim()

                channels.add(Channel(
                    name = currentName.ifEmpty { "قناة غير مسمى" },
                    url = urlOnly,
                    logo = currentLogo,
                    drmLicense = currentDrm,
                    referer = currentReferer,
                    userAgent = currentUserAgent,
                    group = currentGroup
                ))

                // إعادة تعيين المتغيرات للقناة التالية
                currentName = ""; currentLogo = ""; currentGroup = "";
                currentDrm = ""; currentReferer = ""; currentUserAgent = ""
            }
        }
    }
    return channels
}
fun decodeBase64Url(url: String): String {
    return try {
        if (url.isNotEmpty() && url.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            val decodedBytes = android.util.Base64.decode(url, android.util.Base64.DEFAULT)
            String(decodedBytes)
        } else {
            url
        }
    } catch (e: Exception) {
        url
    }
}
fun isMacPortalUrl(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return (lowerUrl.contains("mac=") && lowerUrl.contains("stream=")) ||
            (lowerUrl.contains("/play/") && lowerUrl.contains("live.php")) ||
            (lowerUrl.contains("play_token=")) ||
            (lowerUrl.contains("/streaming/") && lowerUrl.contains("mac=")) ||
            (lowerUrl.contains("extension=ts") && lowerUrl.contains("mac="))
}

fun isXtreamCodesUrl(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return (lowerUrl.contains("username=") && lowerUrl.contains("password=")) ||
            lowerUrl.contains("/live/") ||
            lowerUrl.contains("/movie/") ||
            lowerUrl.contains("/series/") ||
            lowerUrl.contains("get.php") ||
            lowerUrl.contains("player_api.php") ||
            lowerUrl.contains("action=stream")
}

fun isVideoStream(url: String, checkHeader: Boolean = false): Boolean {
    val cleanText = url.trim()
    val lowerText = cleanText.lowercase()
    val m3uTags = listOf("#extinf", "#extvlcopt", "#extm3u", "http-drm-license", "inputstream.adaptive")
    if (m3uTags.any { lowerText.contains(it) }) return true

    val drmKeywords = listOf("clearkey", "widevine", "license_key", ".mpd", "cenc", "manifest")
    if (drmKeywords.any { lowerText.contains(it) }) return true

    val singleLineUrl = lowerText.replace("\n", " ").replace("\r", " ")
    if (singleLineUrl.contains("get.php") ||
        singleLineUrl.contains("player_api.php") ||
        (singleLineUrl.contains("username=") && singleLineUrl.contains("password="))) {
        return true
    }
    val videoExtensions = listOf(
        "m3u8", "ts", "mpd", "m3u", "mp4", "mkv", "mov", "webm", "flv", "avi", "mpg", "mpeg", "ism"
    )
    val extensionPattern = Regex("[.=](${videoExtensions.joinToString("|")})(&|$)").containsMatchIn(singleLineUrl)
    val xtreamPattern = Regex(".*/(live|movie|series)/.*|.*/[0-9]+(\\.(ts|m3u|m3u8))?$|[?&](t|type|f|format)=(live|ts|m3u8)").containsMatchIn(singleLineUrl)

    if (extensionPattern || xtreamPattern) return true
    val generalKeywords = listOf("video", "stream", "playlist", "tvg-logo", "channellist", "link", "action=stream", "php?id=")
    if (generalKeywords.any { singleLineUrl.contains(it) }) return true
    if (singleLineUrl.contains("api/") && singleLineUrl.contains("action=")) return true
    if (singleLineUrl.startsWith("rtmp://") || singleLineUrl.startsWith("rtsp://")) return true
    if (checkHeader && singleLineUrl.startsWith("http") && !singleLineUrl.contains(" ")) {
        try {
            val connection = java.net.URL(cleanText).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 2000
            connection.instanceFollowRedirects = true
            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.startsWith("video/") ||
                contentType.contains("mpegurl") ||
                contentType.contains("application/octet-stream") ||
                contentType.contains("dash+xml")) return true
        } catch (_: Exception) {}
    }

    return false
}
fun extractMediaInfo(fullText: String): Map<String, String> {
    val info = mutableMapOf<String, String>()
    val lines = fullText.lines()
    info["userAgent"] = "VLC/3.0.0 LibVLC/3.0.0"

    for (line in lines) {
        val trimmed = line.trim()
        val lower = trimmed.lowercase()

        when {
            trimmed.startsWith("http") -> {
                if (trimmed.contains("|")) {
                    val parts = trimmed.split("|")
                    info["url"] = parts[0].trim()
                    parts.forEach { part ->
                        val pLower = part.lowercase()
                        if (pLower.contains("user-agent="))
                            info["userAgent"] = part.substringAfter("=").trim()
                        if (pLower.contains("referer="))
                            info["referer"] = part.substringAfter("=").trim()
                    }
                } else {
                    info["url"] = trimmed
                }
            }
            lower.contains("http-user-agent=") -> info["userAgent"] = trimmed.substringAfter("=").trim()
            lower.contains("http-referrer=") || lower.contains("http-referer=") -> info["referer"] = trimmed.substringAfter("=").trim()
            lower.contains("http-drm-license=") -> info["drm"] = trimmed.substringAfter("=").trim()
        }
    }
    return info
}
