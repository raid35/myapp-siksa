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

    // هنا يتم استدعاء الدالة، وسيختفي تنبيه "Never used" بمجرد تشغيل التطبيق
    LaunchedEffect(m3uUrl) {
        // هنا يتم "استخدام" الدالة فعلياً وتختفي رسالة "Never used"
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

                                // تجهيز القائمة للمشغل بصيغة النص المدمج |
                                val allUrls = ArrayList<String>()
                                channels.forEach { ch ->
                                    val decodedUrl = decodeBase64Url(ch.url)
                                    // نجمع كل خصائص القناة في سطر واحد ليفهمها المشغل عند التقليب
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

                // ملء الفراغات لضمان توازن الأعمدة
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
            val realUrl = decodeBase64Url(url)

            val request = Request.Builder()
                .url(realUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val content = response.body?.string()?.trim() ?: ""
                val channels = mutableListOf<Channel>()

                if (content.contains("#EXTM3U") || content.contains("#EXTINF")) {
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
                                currentLogo = Regex("""tvg-logo=["'](.*?)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                                currentGroup = Regex("""group-title=["'](.*?)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                            }
                            // استخراج الخصائص المتقدمة (هام جداً للمشغل الخاص بك)
                            trimmed.startsWith("#EXTVLCOPT:http-referrer=") || trimmed.startsWith("#EXTVLCOPT:referer=") -> {
                                currentReferer = trimmed.substringAfter("=").trim()
                            }
                            trimmed.startsWith("#EXTVLCOPT:http-user-agent=") || trimmed.startsWith("#EXTVLCOPT:user-agent=") -> {
                                currentUserAgent = trimmed.substringAfter("=").trim()
                            }
                            trimmed.startsWith("#KODIPROP:inputstream.adaptive.license_key=") -> {
                                currentDrm = trimmed.substringAfter("=").trim()
                            }
                            // عند الوصول للرابط
                            !trimmed.startsWith("#") && (trimmed.startsWith("http") || trimmed.startsWith("rtmp")) -> {
                                channels.add(Channel(
                                    name = currentName.ifEmpty { "Channel ${channels.size + 1}" },
                                    url = trimmed,
                                    logo = currentLogo,
                                    drmLicense = currentDrm,
                                    referer = currentReferer,
                                    userAgent = currentUserAgent,
                                    group = currentGroup
                                ))
                                // تصفير المتغيرات للقناة القادمة
                                currentName = ""; currentLogo = ""; currentGroup = ""; currentDrm = ""; currentReferer = ""; currentUserAgent = ""
                            }
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
        // نستخدم android.util.Base64 مباشرة لتجنب أي تعارض في الشاشة الرئيسية
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
            lowerUrl.contains("player_api.php")
}

fun isVideoStream(url: String, checkHeader: Boolean = false): Boolean {
    val cleanText = url.trim()
    val lowerText = cleanText.lowercase()

    // 1. فحص وسوم M3U و DRM (إذا كان النص يحتوي على محتوى الملف نفسه وليس الرابط فقط)
    val m3uTags = listOf("#extinf", "#extvlcopt", "#extm3u", "http-drm-license", "inputstream.adaptive")
    if (m3uTags.any { lowerText.contains(it) }) return true

    val drmKeywords = listOf("clearkey", "widevine", "license_key", ".mpd", "cenc", "manifest")
    if (drmKeywords.any { lowerText.contains(it) }) return true

    val singleLineUrl = lowerText.replace("\n", " ").replace("\r", " ")

    // 2. فحص روابط Xtream Codes و m3u_plus بشكل مباشر
    // هذه الروابط يجب أن تعتبر دائماً "بث" ليتم معالجتها في loadChannels
    if (singleLineUrl.contains("get.php") ||
        singleLineUrl.contains("player_api.php") ||
        (singleLineUrl.contains("username=") && singleLineUrl.contains("password="))) {
        return true
    }

    // 3. قائمة الامتدادات المعروفة
    val videoExtensions = listOf(
        "m3u8", "ts", "mpd", "m3u", "mp4", "mkv", "mov", "webm", "flv", "avi", "mpg", "mpeg", "ism"
    )

    // نمط 1: فحص الامتدادات والمعاملات (مثل .m3u8 أو type=m3u)
    val extensionPattern = Regex("[.=](${videoExtensions.joinToString("|")})(&|$)").containsMatchIn(singleLineUrl)

    // نمط 2: التقاط روابط Xtream الهيكلية (مثل /live/user/pass/123.ts)
    val xtreamPattern = Regex(".*/(live|movie|series)/.*|.*/[0-9]+(\\.(ts|m3u|m3u8))?$|[?&](t|type|f|format)=(live|ts|m3u8)").containsMatchIn(singleLineUrl)

    if (extensionPattern || xtreamPattern) return true

    // 4. فحص الكلمات العامة في الرابط
    val generalKeywords = listOf("video", "stream", "playlist", "tvg-logo", "channellist", "link")
    if (generalKeywords.any { singleLineUrl.contains(it) }) return true

    // البروتوكولات المباشرة
    if (singleLineUrl.startsWith("rtmp://") || singleLineUrl.startsWith("rtsp://")) return true

    // 5. فحص الرأس (Header) - اختياري لتحقق أعمق
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

    // وضع User-Agent افتراضي يتوافق مع VLC
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
