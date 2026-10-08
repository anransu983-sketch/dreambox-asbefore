package com.suanran.dreambox.feature.settings.presentation.music

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import com.suanran.dreambox.core.util.HttpClientProfile
import com.suanran.dreambox.core.util.createHttpClient
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * 外部封面刮削：服务端无封面（蓝碟片占位图）时，按歌手+歌名抓封面。
 * 数据源：iTunes（免费无 key）→ QQ音乐（中文歌覆盖好）→ Deezer（欧美兜底）。
 * 内存 LRU 缓存（含"确定没有"的结果，避免滚动列表时重复打接口）。
 */
object ArtworkScraper {
    private const val MAX_CACHE = 500
    private val cache = object : LinkedHashMap<String, String>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > MAX_CACHE
    }

    /** 限流：搜索接口都有频率限制，长列表同时打几十个请求会被 429，直接变蓝碟片 */
    private val semaphore = Semaphore(4)

    /** 这种歌手名只会污染搜索词，直接按纯歌名搜 */
    private val junkArtists = setOf(
        "unknown artist", "[unknown artist]", "未知艺术家", "佚名",
        "various artists", "群星",
    )

    private val http by lazy {
        createHttpClient(
            HttpClientProfile.API,
            installContentNegotiation = false,
            userAgent = "DreamBox/1.0",
        )
    }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 返回封面 URL；找不到返回 null。
     * key 为 "$artist|$title"，歌名空白直接返回 null 不请求。
     */
    suspend fun scrape(artist: String, title: String): String? {
        val t = title.trim()
        if (t.isEmpty()) return null
        val a = artist.trim().let { if (junkArtists.contains(it.lowercase())) "" else it }
        val key = "$a|$t"
        synchronized(cache) { cache[key]?.let { return it.ifBlank { null } } }

        return withContext(Dispatchers.IO) {
            semaphore.withPermit {
                // 先"歌手+歌名"，搜不到再退化成纯歌名（歌手名可能是错的）
                val queries = if (a.isEmpty()) listOf(t) else listOf("$a $t", t)
                for (q in queries) {
                    queryItunes(q)?.let { return@withPermit cacheAndReturn(key, it) }
                    queryQq(q)?.let { return@withPermit cacheAndReturn(key, it) }
                    queryDeezer(q)?.let { return@withPermit cacheAndReturn(key, it) }
                }
                // 都没找到也缓存空结果，避免滚动时反复请求
                synchronized(cache) { cache[key] = "" }
                null
            }
        }
    }

    private fun cacheAndReturn(key: String, art: String): String {
        synchronized(cache) { cache[key] = art }
        return art
    }

    private suspend fun queryItunes(query: String): String? {
        return try {
            val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=music&entity=song&limit=1"
            val body = http.get(url).bodyAsText()
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
            val art100 = results?.firstOrNull()?.jsonObject?.get("artworkUrl100")?.jsonPrimitive?.content
            art100?.replace("100x100bb", "600x600bb")?.ifBlank { null }
        } catch (e: Exception) {
            Timber.d(e, "iTunes 封面刮削失败: $query")
            null
        }
    }

    private suspend fun queryQq(query: String): String? {
        return try {
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&w=${URLEncoder.encode(query, "UTF-8")}&n=1"
            val body = http.get(url) {
                headers {
                    append("User-Agent", "Mozilla/5.0")
                    append("Referer", "https://y.qq.com/")
                }
            }.bodyAsText()
            val list = json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonObject
                ?.get("song")?.jsonObject
                ?.get("list")?.jsonArray
            val albummid = list?.firstOrNull()?.jsonObject?.get("albummid")?.jsonPrimitive?.content
            if (albummid.isNullOrBlank()) null
            else "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albummid}.jpg"
        } catch (e: Exception) {
            Timber.d(e, "QQ音乐封面刮削失败: $query")
            null
        }
    }

    private suspend fun queryDeezer(query: String): String? {
        return try {
            val url = "https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=1"
            val body = http.get(url).bodyAsText()
            val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            data?.firstOrNull()?.jsonObject
                ?.get("album")?.jsonObject
                ?.get("cover_xl")?.jsonPrimitive?.content?.ifBlank { null }
        } catch (e: Exception) {
            Timber.d(e, "Deezer 封面刮削失败: $query")
            null
        }
    }
}
