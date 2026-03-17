package com.example.siksa

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Professional Stream Type Detection & Configuration
 * يدعم تحديد نوع البث التلقائي مع أولويات محددة
 */
@OptIn(UnstableApi::class)
object StreamTypeConfig {

    enum class StreamType {
        HLS_M3U8,          // .m3u8 manifests
        DASH_MPD,          // .mpd manifests
        MPEG_TS,           // .ts segments
        HTTP_PROGRESSIVE   // Direct media files
    }

    data class StreamConfig(
        val type: StreamType,
        val mimeType: String,
        val isDrmRequired: Boolean = false,
        val needsSpecialHeaders: Boolean = false,
        val optimizedTimeout: Long = 20000L,
        val description: String = ""
    )

    /**
     * Intelligent stream detection with priority logic
     * الكشف الذكي يعطي الأولوية للامتدادات الصريحة
     */
    fun detectStreamType(url: String): StreamConfig {
        val lowerUrl = url.lowercase().trim()

        // Stage 1: Check for explicit file extensions (highest priority)
        return when {
            // M3U8 detection - explicit extension takes priority
            lowerUrl.contains(".m3u8") -> {
                StreamConfig(
                    type = StreamType.HLS_M3U8,
                    mimeType = MimeTypes.APPLICATION_M3U8,
                    optimizedTimeout = 20000L,
                    description = "HLS M3U8 Playlist"
                )
            }

            // MPD detection - explicit extension (most reliable for DASH with DRM)
            lowerUrl.contains(".mpd") -> {
                StreamConfig(
                    type = StreamType.DASH_MPD,
                    mimeType = MimeTypes.APPLICATION_MPD,
                    isDrmRequired = true,
                    optimizedTimeout = 30000L,
                    description = "DASH MPD with DRM Support"
                )
            }

            // TS/MPEG-TS segments & IPTV Direct Links
            lowerUrl.contains(".ts") || 
            lowerUrl.contains("extension=ts") || 
            lowerUrl.contains("f=ts") ||
            lowerUrl.contains("output=ts") ||
            lowerUrl.contains("type=ts") ||
            (lowerUrl.endsWith(".ts") || lowerUrl.contains(".ts?")) -> {
                StreamConfig(
                    type = StreamType.MPEG_TS,
                    mimeType = MimeTypes.VIDEO_MP2T,
                    optimizedTimeout = 30000L, 
                    description = "MPEG-TS IPTV Stream"
                )
            }

            // Stage 2: Check for path-based indicators
            lowerUrl.contains("/dash/") || lowerUrl.contains("/dash-") -> {
                StreamConfig(
                    type = StreamType.DASH_MPD,
                    mimeType = MimeTypes.APPLICATION_MPD,
                    isDrmRequired = true,
                    optimizedTimeout = 30000L,
                    description = "DASH Path Detected"
                )
            }

            lowerUrl.contains("/hls/") || (lowerUrl.contains("m3u") && !lowerUrl.contains("mpd")) -> {
                StreamConfig(
                    type = StreamType.HLS_M3U8,
                    mimeType = MimeTypes.APPLICATION_M3U8,
                    optimizedTimeout = 20000L,
                    description = "HLS Path Detected"
                )
            }

            // Stage 3: Check for query parameters and patterns
            // For URLs like index.m3u8?aws.manifestfilter=...
            lowerUrl.contains("index.m3u8") ||
                    lowerUrl.contains("playlist.m3u8") ||
                    lowerUrl.contains("manifest.m3u8") -> {
                StreamConfig(
                    type = StreamType.HLS_M3U8,
                    mimeType = MimeTypes.APPLICATION_M3U8,
                    optimizedTimeout = 20000L,
                    description = "HLS Manifest"
                )
            }

            lowerUrl.contains("index.mpd") ||
                    lowerUrl.contains("manifest.mpd") -> {
                StreamConfig(
                    type = StreamType.DASH_MPD,
                    mimeType = MimeTypes.APPLICATION_MPD,
                    isDrmRequired = true,
                    optimizedTimeout = 30000L,
                    description = "DASH Manifest"
                )
            }

            // Stage 4: Keyword-based detection (lowest priority)
            (lowerUrl.contains("dash") && !lowerUrl.contains("m3u8")) || lowerUrl.contains("isml") -> {
                StreamConfig(
                    type = StreamType.DASH_MPD,
                    mimeType = MimeTypes.APPLICATION_MPD,
                    isDrmRequired = true,
                    optimizedTimeout = 30000L,
                    description = "DASH Stream (keyword detected)"
                )
            }

            lowerUrl.contains("hls") && !lowerUrl.contains("mpd") -> {
                StreamConfig(
                    type = StreamType.HLS_M3U8,
                    mimeType = MimeTypes.APPLICATION_M3U8,
                    optimizedTimeout = 20000L,
                    description = "HLS Stream (keyword detected)"
                )
            }

            // Default: Treat as progressive HTTP if looks like direct video
            else -> {
                StreamConfig(
                    type = StreamType.HTTP_PROGRESSIVE,
                    mimeType = guessMimeTypeFromExtension(lowerUrl),
                    optimizedTimeout = 20000L,
                    description = "Progressive HTTP Stream"
                )
            }
        }
    }

    /**
     * Guess MIME type from file extension
     */
    private fun guessMimeTypeFromExtension(url: String): String {
        return when {
            url.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            url.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            url.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            url.endsWith(".flv") -> MimeTypes.VIDEO_FLV
            url.endsWith(".avi") -> "video/x-msvideo"
            else -> MimeTypes.VIDEO_UNKNOWN
        }
    }

    /**
     * Validate if a stream URL is correctly formatted
     */
    fun isValidStreamUrl(url: String): Boolean {
        return url.isNotEmpty() &&
                (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtmp")) &&
                url.length > 10
    }

    /**
     * Get appropriate headers for stream type
     */
    fun getOptimalHeaders(streamType: StreamType): Map<String, String> {
        return when (streamType) {
            StreamType.HLS_M3U8 -> mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive",
                "Cache-Control" to "no-cache"
            )

            StreamType.DASH_MPD -> mapOf(
                "Accept" to "application/dash+xml,*/*",
                "Connection" to "keep-alive",
                "Accept-Encoding" to "gzip, deflate",
                "Cache-Control" to "no-cache"
            )

            StreamType.MPEG_TS -> mapOf(
                "Accept" to "video/*,*/*",
                "Connection" to "keep-alive"
            )

            else -> mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            )
        }
    }

    /**
     * Get optimal user-agent for stream type
     */
    fun getOptimalUserAgent(streamType: StreamType): String {
        return when (streamType) {
            StreamType.DASH_MPD -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            StreamType.HLS_M3U8 -> "VLC/3.0.0 (compatible; HLS Player)"
            else -> "VLC/3.0.0"
        }
    }

    /**
     * Get optimal network timeout for stream type
     */
    fun getOptimalTimeout(streamType: StreamType): Long {
        return when (streamType) {
            StreamType.DASH_MPD -> 30000L    // 30 seconds for manifest parsing
            StreamType.HLS_M3U8 -> 20000L    // 20 seconds for playlist
            StreamType.MPEG_TS -> 15000L     // 15 seconds for segments
            else -> 20000L
        }
    }
}
