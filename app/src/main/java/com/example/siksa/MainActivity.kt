package com.example.siksa

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val logo: String,
    val drmLicense: String = ""
)

data class PackageItem(
    val name: String,
    val logo: String,
    val url: String
)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(android.view.WindowInsets.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        supportActionBar?.hide()

        setContent {
            AnimatedGradientBackground {
                var selectedPackageUrl by remember { mutableStateOf<String?>(null) }

                if (selectedPackageUrl == null) {
                    PackageListScreen { url -> selectedPackageUrl = url }
                } else {
                    ChannelListScreen(
                        m3uUrl = selectedPackageUrl!!,
                        onBack = { selectedPackageUrl = null }
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedGradientBackground(content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1e3c72),
                        Color(0xFF2a5298),
                        Color.Black,
                        Color(0xFF3b1d56)
                    ),
                    start = Offset(0f, offset),
                    end = Offset(offset, 0f)
                )
            )
    ) {
        content()
    }
}

@Composable
fun PackageListScreen(onPackageClick: (String) -> Unit) {
    var packages by remember { mutableStateOf(listOf<PackageItem>()) }

    LaunchedEffect(Unit) {
        packages = loadPackagesFromM3u("https://raw.githubusercontent.com/raid35/channel-links/main/siksa.m3u")
    }

    val rows = packages.chunked(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (pkg in rowItems) {
                    var isFocused by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .onFocusChanged { isFocused = it.isFocused }
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusable()
                            .clickable { onPackageClick(pkg.url) },
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
                                painter = rememberAsyncImagePainter(pkg.logo),
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
fun ChannelListScreen(m3uUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var channels by remember { mutableStateOf(listOf<Channel>()) }

    BackHandler { onBack() }

    LaunchedEffect(m3uUrl) {
        channels = loadChannels(m3uUrl) // ✅ استبدال الدالة هنا
    }

    val rows = channels.chunked(7)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows) { rowChannels ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(rowChannels) { channel ->
                    var isFocused by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .focusable()
                            .clickable {
                                val intent = when {
                                    isYouTubeUrl(channel.url) || isFacebookUrl(channel.url) -> {
                                        Intent(context, WebViewActivity::class.java).apply {
                                            putExtra("url", channel.url)
                                        }
                                    }
                                    isVideoStream(channel.url) -> {
                                        Intent(context, PlayerActivity::class.java).apply {
                                            putExtra("streamUrl", channel.url)
                                            putExtra("channelName", channel.name)
                                            putExtra("drmLicense", channel.drmLicense)
                                        }
                                    }
                                    else -> {
                                        Intent(context, WebViewActivity::class.java).apply {
                                            putExtra("url", channel.url)
                                        }
                                    }
                                }
                                context.startActivity(intent)
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
                                painter = rememberAsyncImagePainter(channel.logo),
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

suspend fun loadChannels(url: String): List<Channel> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: ""

            if (url.endsWith(".json") || content.trim().startsWith("[")) {
                val jsonArray = JSONArray(content)
                val channels = mutableListOf<Channel>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val name = item.optString("name")
                    val streamUrl = item.optString("url")
                    val logo = item.optString("logo")
                    val licenseType = item.optString("license_type", "")
                    val licenseKey = item.optString("license_key", "")
                    val drm = if (licenseType.isNotEmpty() && licenseKey.isNotEmpty())
                        "$licenseType:$licenseKey"
                    else
                        ""

                    channels.add(Channel(name, streamUrl, logo, drm))
                }
                channels
            } else {
                val lines = content.lines()
                val channels = mutableListOf<Channel>()
                var name = ""
                var logo = ""
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("#EXTINF")) {
                        name = line.substringAfter(",").trim()
                        logo = Regex("""tvg-logo="(.*?)"""").find(line)?.groupValues?.get(1) ?: ""
                    } else if (line.startsWith("http")) {
                        channels.add(Channel(name, line.trim(), logo))
                    }
                }
                channels
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
            val content = response.body?.string() ?: ""
            val lines = content.lines()

            val packages = mutableListOf<PackageItem>()
            var name = ""
            var logo = ""

            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXTINF")) {
                    name = line.substringAfter(",").trim()
                    logo = Regex("""tvg-logo="(.*?)"""").find(line)?.groupValues?.get(1) ?: ""
                } else if (line.startsWith("http")) {
                    packages.add(PackageItem(name, logo, line.trim()))
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

    val knownPlatforms = listOf(
        "youtube.com", "youtu.be", "facebook.com/watch", "fb.watch", "twitch.tv", "dailymotion.com"
    )

    try {
        val uri = URI(lowerUrl)
        val path = uri.path
        if (videoExtensions.any { path.endsWith(".$it") }) return true
    } catch (_: Exception) {}

    if (videoKeywords.any { lowerUrl.contains(it) }) return true
    if (knownPlatforms.any { lowerUrl.contains(it) }) return true
    if (lowerUrl.contains("username=") && lowerUrl.contains("password=")) return true
    if (lowerUrl.contains("/series/") || lowerUrl.contains("/live/") || lowerUrl.contains("/movie/")) return true

    if (checkHeader) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.startsWith("video") ||
                contentType.contains("application/vnd.apple.mpegurl") ||
                contentType.contains("dash+xml")
            ) {
                return true
            }
        } catch (_: Exception) {}
    }

    return false
}
fun isYouTubeUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("youtube.com/watch") || lower.contains("youtu.be/")
}

fun isFacebookUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("facebook.com/watch") || lower.contains("fb.watch")
}
