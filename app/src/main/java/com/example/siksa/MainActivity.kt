package com.example.siksa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        supportActionBar?.hide()

        setContent {
            AnimatedGradientBackground {
                var selectedPackageUrl by remember { mutableStateOf<String?>(null) }
                var selectedIndex by remember { mutableStateOf(0) }
                var selectedChannelIndex by remember { mutableStateOf(0) }

                val externalUrl = remember { getExternalM3uUrl() }

                when {
                    externalUrl != null -> {
                        val lowerUrl = externalUrl.lowercase().trim()
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
            } catch (_: Exception) {}
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
        val encodedUrl = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL3JhaWQzNS9jaGFubmVsLWxpbmtzL21haW4vc2lrc2FfdHYubTN1"
        val decodedUrl = try {
            val data = android.util.Base64.decode(encodedUrl, android.util.Base64.DEFAULT)
            String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
        if (decodedUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val loadedPackages = loadPackagesFromM3u(decodedUrl)
                withContext(Dispatchers.Main) {
                    packages = loadedPackages
                    selectedIndex?.let {
                        val rowIndex = it / 5
                        listState.scrollToItem(rowIndex)
                    }
                }
            }
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

                    LaunchedEffect(packages) {
                        if (selectedIndex == actualIndex) {
                            delay(50)
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
                                ChannelData.list = channels

                                val currentChannel = channels[actualIndex]
                                val realUrl = decodeBase64Url(currentChannel.url)

                                if (isVideoStream(realUrl)) {
                                    val intent = Intent(context, PlayerActivity::class.java).apply {
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
            val packages = mutableListOf<PackageItem>()
            if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
                try {
                    val jsonArray = org.json.JSONArray(content)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        packages.add(PackageItem(
                            name = obj.optString("name", "Package ${i + 1}"),
                            logo = obj.optString("logo", ""),
                            url = obj.optString("url", "")
                        ))
                    }
                    return@withContext packages
                } catch (e: Exception) {
                }
            }
            val lines = content.lines()
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
        if (isXtreamCodesUrl(realUrl)) return@withContext loadXtreamChannels(realUrl)

        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(realUrl).header("User-Agent", "Mozilla/5.0").build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val content = response.body?.string() ?: ""
                val trimmedContent = content.trim()
                if (trimmedContent.startsWith("{")) {
                    return@withContext parseJsonContent(trimmedContent)
                } else {
                    return@withContext parseM3uContent(trimmedContent)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
fun parseJsonContent(jsonString: String): List<Channel> {
    val channels = mutableListOf<Channel>()
    try {
        val root = org.json.JSONObject(jsonString)
        val jsonArray = root.getJSONArray("channels")

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val rawUrl = obj.optString("url")
            val decodedUrl = decodeBase64Url(rawUrl)

            channels.add(Channel(
                name = obj.optString("name"),
                url = decodedUrl,
                logo = obj.optString("logo"),
                drmLicense = obj.optString("drmLicense"),
                group = obj.optString("group")
            ))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
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
            trimmed.startsWith("#EXTINF") -> {
                currentName = trimmed.substringAfterLast(",").trim()
                currentLogo = Regex("""tvg-logo=["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                currentGroup = Regex("""group-title=["']([^"']+)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
            }
            trimmed.startsWith("#EXTVLCOPT:") -> {
                val opt = trimmed.substringAfter("#EXTVLCOPT:").lowercase()
                when {
                    opt.contains("http-user-agent=") -> currentUserAgent = trimmed.substringAfter("=")
                    opt.contains("http-referrer=") || opt.contains("http-referer=") -> currentReferer = trimmed.substringAfter("=")
                    opt.contains("http-drm-license=") -> currentDrm = trimmed.substringAfter("=")
                    opt.contains("clearkey") -> {
                    }
                }
            }
            trimmed.contains("license_key=", ignoreCase = true) ||
                    trimmed.contains("http-drm-license=", ignoreCase = true) -> {
                currentDrm = trimmed.substringAfter("=").trim()
            }
            trimmed.startsWith("http") || trimmed.startsWith("rtmp") -> {
                val urlOnly = if (trimmed.contains("|")) trimmed.substringBefore("|").trim() else trimmed
                if (trimmed.contains("|")) {
                    val suffix = trimmed.substringAfter("|")
                    if (suffix.contains("User-Agent=", true)) {
                        currentUserAgent = suffix.substringAfter("User-Agent=").substringBefore("&")
                    }
                }

                channels.add(Channel(
                    name = currentName.ifEmpty { "قناة غير مسمى" },
                    url = urlOnly,
                    logo = currentLogo,
                    drmLicense = currentDrm,
                    referer = currentReferer,
                    userAgent = currentUserAgent,
                    group = currentGroup
                ))
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
suspend fun loadXtreamChannels(baseUrl: String): List<Channel> {
    return withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        val client = OkHttpClient()
        val apiUrl = if (baseUrl.contains("player_api.php")) {
            if (baseUrl.contains("action=get_live_streams")) baseUrl
            else "$baseUrl&action=get_live_streams"
        } else {
            "${baseUrl.removeSuffix("/")}/player_api.php?action=get_live_streams"
        }

        try {
            val request = Request.Builder().url(apiUrl).build()
            client.newCall(request).execute().use { response ->
                val jsonResponse = response.body?.string() ?: ""
                val jsonArray = org.json.JSONArray(jsonResponse)
                val user = Regex("username=([^&]+)").find(baseUrl)?.groupValues?.get(1) ?: ""
                val pass = Regex("password=([^&]+)").find(baseUrl)?.groupValues?.get(1) ?: ""
                val server = baseUrl.substringBefore("/player_api.php")

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val streamId = obj.optString("stream_id")
                    val streamUrl = "$server/live/$user/$pass/$streamId.ts"

                    channels.add(Channel(
                        name = obj.optString("name"),
                        url = streamUrl,
                        logo = obj.optString("stream_icon"),
                        group = obj.optString("category_id")
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        channels
    }
}
