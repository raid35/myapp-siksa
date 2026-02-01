package com.example.siksa

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import androidx.core.view.WindowInsetsCompat
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import android.util.Base64
import org.json.JSONObject
import com.example.siksa.PlayerActivity
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        supportActionBar?.hide()

        setContent {
            AnimatedGradientBackground {
                var selectedPackageUrl by remember { mutableStateOf<String?>(null) }
                var selectedIndex by remember { mutableStateOf(0) }
                var selectedChannelIndex by remember { mutableStateOf(0) }

                val externalM3uUrl = remember { getExternalM3uUrl() }

                when {
                    externalM3uUrl != null -> {
                        ChannelListScreen(
                            m3uUrl = externalM3uUrl,
                            selectedIndex = selectedChannelIndex,
                            onChannelClick = { selectedChannelIndex = it },
                            onBack = { finish() }
                        )
                    }
                    selectedPackageUrl == null -> {
                        PackageListScreen(
                            selectedIndex = selectedIndex,
                            onPackageClick = { index, url ->
                                selectedIndex = index
                                selectedChannelIndex = 0
                                selectedPackageUrl = url
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

    private fun getExternalM3uUrl(): String? {
        return when {
            // إذا جاء Intent من خارج التطبيق (رابط مباشر)
            intent?.action == Intent.ACTION_VIEW && intent.data != null -> {
                val url = intent.data.toString()
                if (url.endsWith(".m3u") || url.endsWith(".m3u8") || url.endsWith(".json") || url.contains("m3u")) {
                    url
                } else null
            }
            // إذا أرسلنا رابط M3U أو JSON من MainActivity أو أي Activity أخرى
            intent?.hasExtra("m3u_url") == true -> {
                intent.getStringExtra("m3u_url")
            }
            // إذا أرسلنا رابط الباقة مباشرة عبر streamUrl
            intent?.hasExtra("streamUrl") == true -> {
                val url = intent.getStringExtra("streamUrl")
                if (url?.endsWith(".m3u") == true || url?.endsWith(".m3u8") == true || url?.endsWith(".json") == true) {
                    url
                } else null
            }
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
fun PackageListScreen(
    selectedIndex: Int?,
    onPackageClick: (Int, String) -> Unit
) {
    var packages by remember { mutableStateOf(listOf<PackageItem>()) }
    val listState = rememberLazyListState()

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
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(rows) { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusable()
                            .clickable {
                                onPackageClick(actualIndex, pkg.url)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(pkg.logo)
                                        .setHeader("User-Agent", "Mozilla/5.0")
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                pkg.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
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

    val rows = channels.chunked(7)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(rows) { rowIndex, rowChannels ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                itemsIndexed(rowChannels) { itemIndex, channel ->
                    val actualIndex = rowIndex * 7 + itemIndex
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
                            .width(120.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .focusable()
                            .clickable {
                                onChannelClick(actualIndex)

                                val realUrl = decodeBase64Url(channel.url) // 👈 فك الرابط هنا

                                if (isVideoStream(realUrl)) {
                                    val allUrls = ArrayList<String>()
                                    channels.forEach { allUrls.add(decodeBase64Url(it.url)) } // فك جميع الروابط

                                    val intent = Intent(context, PlayerActivity::class.java).apply {
                                        putExtra("streamUrl", realUrl) // الرابط المفكوك
                                        putExtra("channelIndex", actualIndex)
                                        putStringArrayListExtra("channelsList", allUrls)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("url", realUrl)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(channel.logo)
                                        .setHeader("User-Agent", "Mozilla/5.0")
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(bottom = 6.dp)
                            )
                            Text(
                                text = channel.name,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .build()
            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: ""
            val lines = content.lines()

            val packages = mutableListOf<PackageItem>()
            var name = ""
            var logo = ""

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXTINF")) {
                    name = line.substringAfter(",").trim()
                    logo = Regex("""tvg-logo="(.*?)"""").find(line)?.groupValues?.get(1) ?: ""
                } else if (line.startsWith("http") && line.isNotEmpty()) {
                    packages.add(PackageItem(name.ifEmpty { "Channel ${packages.size + 1}" }, logo, line))
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
        try {
            val realUrl = if (url.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                // يبدو مشفر Base64
                try { String(Base64.decode(url, Base64.DEFAULT)) } catch (_: Exception) { url }
            } else url

            val client = OkHttpClient()
            val request = Request.Builder().url(realUrl).header("User-Agent", "Mozilla/5.0").build()
            val response = client.newCall(request).execute()
            val content = response.body?.string()?.trim() ?: ""

            // JSON مع Array فقط
            if (content.startsWith("[")) {
                val jsonArray = JSONArray(content)
                val channels = mutableListOf<Channel>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    channels.add(Channel(
                        name = item.optString("name"),
                        url = item.optString("url"),
                        logo = item.optString("logo"),
                        drmLicense = item.optString("key")
                    ))
                }
                return@withContext channels
            }

            // JSON مع مجموعات
            if (content.startsWith("{")) {
                val jsonObj = JSONObject(content)
                val channels = mutableListOf<Channel>()
                jsonObj.keys().forEach { group ->
                    val array = jsonObj.getJSONArray(group)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        channels.add(Channel(
                            name = item.optString("name"),
                            url = item.optString("url"),
                            logo = item.optString("logo"),
                            drmLicense = item.optString("key"),
                            group = group
                        ))
                    }
                }
                return@withContext channels
            }

            // M3U التقليدي
            val lines = content.lines()
            val channels = mutableListOf<Channel>()
            var name = ""
            var logo = ""
            var group = ""
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF")) {
                    name = trimmed.substringAfter(",").trim()
                    logo = Regex("""tvg-logo="(.*?)"""").find(trimmed)?.groupValues?.get(1) ?: ""
                    group = Regex("""group-title="(.*?)"""").find(trimmed)?.groupValues?.get(1) ?: ""
                } else if (trimmed.startsWith("http")) {
                    channels.add(Channel(
                        name = name.ifEmpty { "Channel ${channels.size + 1}" },
                        url = trimmed,
                        logo = logo,
                        drmLicense = "",
                        group = group
                    ))
                    name = ""
                    logo = ""
                    group = ""
                }
            }
            channels

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
fun decodeBase64Url(url: String): String {
    return try {
        // إذا كان url يبدو مشفر Base64
        if (url.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            String(android.util.Base64.decode(url, android.util.Base64.DEFAULT))
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
            lowerUrl.contains("player_api.php")
}

fun isVideoStream(url: String, checkHeader: Boolean = false): Boolean {
    val lowerUrl = url.lowercase()

    val videoExtensions = listOf(
        "m3u", "m3u8", "ts", "mpd", "ism", "isml", "f4m",
        "mp4", "mov", "mkv", "webm", "flv", "avi", "mpg", "mpeg", "3gp", "ogg", "wmv", "asf"
    )

    val videoKeywords = listOf(
        "video", "stream", "manifest", "playlist", "media",
        "hls", "dash", "live", "series", "movie", "episode",
        "token=", "expires=", "signature=", "key=",
        "akamai", "edgecast", "cdn", "proxy", "relay", "redirect"
    )

    val streamProtocols = listOf("rtmp://", "rtsp://", "udp://")

    // التحقق من البروتوكولات الخاصة بالبث
    if (streamProtocols.any { lowerUrl.startsWith(it) }) return true

    // التحقق من الامتدادات
    try {
        val uri = URI(lowerUrl)
        val allParts = listOfNotNull(uri.path, uri.query, uri.fragment)

        if (allParts.any { part ->
                videoExtensions.any { ext -> part.endsWith(".$ext") || part.contains(".$ext") }
            }
        ) return true
    } catch (_: Exception) {}

    // التحقق من الكلمات المفتاحية والأنماط
    if (videoKeywords.any { lowerUrl.contains(it) }) return true
    if (lowerUrl.contains("username=") && lowerUrl.contains("password=")) return true
    if (lowerUrl.contains("/series/") || lowerUrl.contains("/live/") || lowerUrl.contains("/movie/")) return true

    // التحقق من نوع المحتوى من خلال الهيدر إذا طُلب ذلك
    if (checkHeader) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.instanceFollowRedirects = true

            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.startsWith("video") ||
                contentType.contains("application/vnd.apple.mpegurl") ||  // HLS
                contentType.contains("application/dash+xml") ||          // DASH
                contentType.contains("application/x-mpegurl")
            ) {
                return true
            }
        } catch (_: Exception) {}
    }

    return false
}
