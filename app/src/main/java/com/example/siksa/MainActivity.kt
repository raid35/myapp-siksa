package com.example.siksa

import android.content.Intent
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. جعل المحتوى يمتد خلف حواف الشاشة (خلف الأزرار وشريط الساعة)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2. إخفاء شريط الحالة وأزرار التنقل (أزرار الهاتف)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.apply {
            // إخفاء كل أشرطة النظام
            hide(WindowInsetsCompat.Type.systemBars())
            // جعلها تظهر فقط عند السحب من الحافة وتختفي تلقائياً (الوضع الغامر)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 3. إخفاء العنوان العلوي الافتراضي
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
        val action = intent?.action
        val data = intent?.data
        val mimeType = intent?.type

        return when {
            // حالة استقبال رابط مباشر أو ملف من مدير الملفات
            action == Intent.ACTION_VIEW && data != null -> {
                val url = data.toString()
                // فحص النوع بناءً على الملحق أو النوع البرمجي (MIME Type)
                if (url.endsWith(".m3u") || url.endsWith(".m3u8") || url.endsWith(".json") ||
                    url.contains("m3u") || mimeType?.contains("mpegurl") == true ||
                    mimeType?.contains("video/") == true || mimeType?.contains("application/octet-stream") == true
                ) {
                    url
                } else {
                    url // نمرر الرابط على أي حال ليحاول التطبيق تشغيله
                }
            }
            // الروابط المرسلة من داخل التطبيق عبر Extras
            intent?.hasExtra("m3u_url") == true -> intent.getStringExtra("m3u_url")
            intent?.hasExtra("streamUrl") == true -> intent.getStringExtra("streamUrl")
            else -> null
        }
    }

    // تحديث التعامل مع الأوامر الجديدة عند إعادة تشغيل النشاط
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // فحص الرابط الجديد، إذا وجد نقوم بتحديث الحالة
        val newUrl = getExternalM3uUrl()
        if (newUrl != null) {
            recreate() // إعادة تشغيل الواجهة لتحميل الرابط الجديد
        }
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
            // جعل السطر يملأ كامل العرض
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
                            .weight(1f) // لضمان التمدد وتغطية المساحات الفارغة جهة اليمين
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
                // إضافة مساحات فارغة (Spacers) بنفس الوزن للحفاظ على توازن السطر الأخير
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

    // عدد الأعمدة 6 لضمان التنسيق في الشاشات الكبيرة
    val columnCount = 6
    val rows = channels.chunked(columnCount)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(rows) { rowIndex, rowChannels ->
            // هنا استخدمنا Row مع fillMaxWidth لحل مشكلة الفراغ جهة اليمين
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
                            // الحل السحري: weight(1f) يجعل القنوات تتوزع بالتساوي وتملأ الشاشة
                            .weight(1f)
                            .height(170.dp) // حافظنا على الارتفاع الأصلي لك
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
                                // --- كود التشغيل الخاص بك (دون أي تغيير) ---
                                val allUrls = ArrayList<String>()
                                channels.forEach { ch ->
                                    val decodedUrl = decodeBase64Url(ch.url)
                                    val formattedUrl = if (ch.drmLicense.isNotEmpty()) "$decodedUrl|${ch.drmLicense}" else decodedUrl
                                    allUrls.add(formattedUrl)
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
                                        putExtra("userAgent", currentChannel.userAgent)
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
                        // عدنا للألوان الأصلية الخاصة بك تماماً
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

                            // شريط اسم القناة (بنفس شفافية كودك السابق 0.5f)
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
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .fillMaxWidth(),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // في حال كان السطر يحتوي أقل من 6 قنوات، نضيف فراغات ليبقى التنسيق سليم
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
            // استخدام User-Agent موحد كما في دالة القنوات
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
                    // استخراج الاسم بعد الفاصلة الأخيرة (نفس منطق دالة القنوات)
                    name = trimmed.substringAfterLast(",").trim()

                    // استخراج اللوجو مع إضافة .trim() لضمان حذف المسافات الزائدة التي لاحظناها في ملفك
                    logo = Regex("""tvg-logo=["'](.*?)["']""").find(trimmed)?.groupValues?.get(1)?.trim() ?: ""

                } else if (trimmed.startsWith("http") && !trimmed.startsWith("#")) {
                    // إضافة الباقة وتصفير المتغيرات للدورة القادمة
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
        try {
            val client = OkHttpClient()

            // فك تشفير رابط ملف الـ M3U/JSON إذا كان Base64
            val realUrl = if (url.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                try { String(android.util.Base64.decode(url, android.util.Base64.DEFAULT)) } catch (_: Exception) { url }
            } else url

            val request = Request.Builder()
                .url(realUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val content = response.body?.string()?.trim() ?: ""
                val channels = mutableListOf<Channel>()

                // --- الحالة الأولى: إذا كان الملف بصيغة JSON ---
                if (content.startsWith("[") || content.startsWith("{")) {
                    try {
                        val jsonArray = if (content.startsWith("{")) {
                            // إذا كان الكائن JSON يحتوي على مصفوفة بداخله (مثلاً تحت مفتاح "channels")
                            org.json.JSONObject(content).getJSONArray("channels")
                        } else {
                            org.json.JSONArray(content)
                        }

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            channels.add(Channel(
                                name = obj.optString("name", "Unknown"),
                                url = obj.optString("url", ""),
                                logo = obj.optString("logo", ""),
                                drmLicense = obj.optString("drmLicense", ""),
                                referer = obj.optString("referer", ""),
                                userAgent = obj.optString("userAgent", ""),
                                group = obj.optString("group", "")
                            ))
                        }
                        return@withContext channels
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // إذا فشل تحليل JSON، نترك الكود يكمل لربما يكون M3U بطريقة ما
                    }
                }

                // --- الحالة الثانية: إذا كان الملف بصيغة M3U (الكود القديم الخاص بك) ---
                val lines = content.lines()
                var name = ""
                var logo = ""
                var group = ""
                var currentReferer = ""
                var currentUserAgent = ""
                var currentClearKey = ""

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue

                    when {
                        trimmed.startsWith("#EXTINF") -> {
                            name = trimmed.substringAfter(",").trim()
                            logo = Regex("""tvg-logo="(.*?)"""").find(trimmed)?.groupValues?.get(1) ?: ""
                            group = Regex("""group-title="(.*?)"""").find(trimmed)?.groupValues?.get(1) ?: ""
                        }
                        trimmed.startsWith("#EXTVLCOPT:http-referrer=") -> {
                            currentReferer = trimmed.substringAfter("=").trim()
                        }
                        trimmed.startsWith("#EXTVLCOPT:http-user-agent=") -> {
                            currentUserAgent = trimmed.substringAfter("=").trim()
                        }
                        trimmed.startsWith("#KODIPROP:inputstream.adaptive.license_key=") -> {
                            currentClearKey = trimmed.substringAfter("=").trim()
                        }
                        !trimmed.startsWith("#") -> {
                            channels.add(Channel(
                                name = name.ifEmpty { "Channel ${channels.size + 1}" },
                                url = trimmed,
                                logo = logo,
                                drmLicense = currentClearKey,
                                referer = currentReferer,
                                userAgent = currentUserAgent,
                                group = group
                            ))
                            name = ""; logo = ""; group = ""; currentReferer = ""; currentUserAgent = ""; currentClearKey = ""
                        }
                    }
                }
                return@withContext channels
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
fun decodeBase64Url(url: String): String {
    return try {
        // نتحقق إذا كان الرابط فعلاً Base64 (لا يبدأ بـ http ويحتوي رموز التشفير)
        if (!url.startsWith("http") && url.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            val data = android.util.Base64.decode(url, android.util.Base64.DEFAULT)
            String(data, Charsets.UTF_8)
        } else {
            url
        }
    } catch (e: Exception) {
        url // في حال الفشل نرجع الرابط الأصلي
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
    val lowerUrl = url.lowercase().trim() // أضفنا trim هنا لضمان دقة الفحص

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

    // --- الإضافة الجديدة لروابط Xtream والروابط الرقمية ---
    // هذا النمط يكتشف الروابط التي تنتهي بـ /رقم (مثل الرابط الخاص بك)
    val isXtreamPattern = Regex(".*/[0-9]+$").matches(lowerUrl)
    if (isXtreamPattern) return true
    // ---------------------------------------------------

    // التحقق من البروتوكولات الخاصة بالبث
    if (streamProtocols.any { lowerUrl.startsWith(it) }) return true

    // التحقق من الامتدادات
    try {
        val uri = java.net.URI(lowerUrl)
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
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.instanceFollowRedirects = true

            val contentType = connection.contentType?.lowercase() ?: ""
            if (contentType.startsWith("video/") ||
                contentType.contains("application/vnd.apple.mpegurl") ||
                contentType.contains("application/dash+xml") ||
                contentType.contains("application/x-mpegurl")
            ) {
                return true
            }
        } catch (_: Exception) {}
    }

    return false
}
