package com.cooper.briefingwidget

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 负责 feed.json 的拉取、缓存与解析。
 *
 * 数据来源是一个网络 URL（见 [DEFAULT_FEED_URL]），默认就是 Cooper 每天更新的
 * GitHub 固定地址（briefing-feed 仓库的 feed.json）。如需改用其他地址：
 * 1. 直接把下面 [DEFAULT_FEED_URL] 的值换掉，重新构建安装；
 * 2. 不改代码，在 App 主界面上方的输入框里填入新地址并点“保存地址”
 *    （保存在 SharedPreferences 中，优先级高于默认值，小组件同样使用它）。
 *
 * 拉取失败时自动回退到上次成功缓存（filesDir/feed_cache.json），
 * 从未成功拉取过则用 APK 内置的 assets/feed.json 作为离线初始数据。
 */
object FeedRepository {

    // 正式地址：Cooper 每天早上约 8 点更新这份 feed.json。
    const val DEFAULT_FEED_URL = "https://raw.githubusercontent.com/990-crypto-ll/briefing-feed/main/feed.json"

    private const val PREFS_NAME = "briefing_prefs"
    private const val KEY_FEED_URL = "feed_url"
    private const val CACHE_FILE = "feed_cache.json"
    private const val ASSET_FILE = "feed.json"

    private val executor = Executors.newSingleThreadExecutor()

    fun getFeedUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FEED_URL, DEFAULT_FEED_URL) ?: DEFAULT_FEED_URL
    }

    fun setFeedUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FEED_URL, url.trim())
            .apply()
    }

    /**
     * 加载快报。forceRefresh=true 时先尝试网络拉取；否则先用缓存/内置数据快速展示，
     * 再在后台拉取一次并通过 callback 返回更新（callback 可能被调用两次）。
     * callback 始终在调用方线程之外执行，UI 层请用 runOnUiThread 切换。
     */
    fun loadFeed(context: Context, forceRefresh: Boolean, callback: (Feed?, String?) -> Unit) {
        val appContext = context.applicationContext
        if (!forceRefresh) {
            loadFromCacheOrAssets(appContext)?.let { callback(it, null) }
        }
        executor.execute {
            val fresh = fetchAndCache(appContext)
            if (fresh != null) {
                callback(fresh, null)
            } else {
                val fallback = loadFromCacheOrAssets(appContext)
                if (fallback != null) {
                    callback(fallback, "网络拉取失败，已显示上次缓存")
                } else {
                    callback(null, "网络拉取失败，且没有可用缓存")
                }
            }
        }
    }

    /** 由当前 feed 地址推导某天的历史快报地址：把 feed.json 换成 feed-<日期>.json。 */
    fun datedFeedUrl(context: Context, date: String): String? {
        val base = getFeedUrl(context)
        if (!base.contains("feed.json")) return null
        return base.replace("feed.json", "feed-$date.json")
    }

    /** 加载某一天的历史快报（只走网络，不写缓存）。主地址拉不到时自动走镜像。 */
    fun loadFeedForDate(context: Context, date: String, callback: (Feed?) -> Unit) {
        val appContext = context.applicationContext
        val url = datedFeedUrl(appContext, date)
        if (url == null) {
            callback(null)
            return
        }
        executor.execute {
            var feed: Feed? = null
            for (candidate in candidateUrls(url)) {
                val text = fetchText(candidate) ?: continue
                feed = parseFeed(text)
                if (feed != null) break
            }
            callback(feed)
        }
    }

    /**
     * 给一个 feed 地址生成候选地址列表：原地址在前；若是 GitHub raw 地址，
     * 再补 jsDelivr 和 githack 两个 CDN 镜像。某些网络直连 GitHub 不稳定，
     * 拉取时按顺序尝试，第一个成功解析的为准。
     */
    private fun candidateUrls(url: String): List<String> {
        val urls = mutableListOf(url)
        val m = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$").find(url)
        if (m != null) {
            val (owner, repo, branch, path) = m.destructured
            urls.add("https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path")
            urls.add("https://raw.githack.com/$owner/$repo/$branch/$path")
        }
        return urls
    }

    /** 简单 GET 拉文本，失败返回 null。 */
    private fun fetchText(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 同步版本，供小组件在后台线程中使用。 */
    fun loadFeedBlocking(context: Context): Feed? {
        val appContext = context.applicationContext
        return fetchAndCache(appContext) ?: loadFromCacheOrAssets(appContext)
    }

    /** 只读缓存/内置数据（不走网络），供小组件先快速渲染一帧。 */
    fun loadFeedBlockingFromCacheOnly(context: Context): Feed? {
        return loadFromCacheOrAssets(context.applicationContext)
    }

    private fun fetchAndCache(context: Context): Feed? {
        // 主地址（GitHub raw）在部分网络下连不上，按候选顺序尝试镜像
        for (candidate in candidateUrls(getFeedUrl(context))) {
            val text = fetchText(candidate) ?: continue
            val feed = parseFeed(text) ?: continue
            // 拉取成功才写缓存，保证缓存永远是完整的合法数据
            try {
                File(context.filesDir, CACHE_FILE).writeText(text, Charsets.UTF_8)
            } catch (e: Exception) {
                // 缓存写失败不影响本次展示
            }
            return feed
        }
        return null
    }

    private fun loadFromCacheOrAssets(context: Context): Feed? {
        val cache = File(context.filesDir, CACHE_FILE)
        if (cache.exists()) {
            try {
                parseFeed(cache.readText(Charsets.UTF_8))?.let { return it }
            } catch (e: Exception) {
                // 缓存损坏则继续走内置数据
            }
        }
        return try {
            val text = context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseFeed(text)
        } catch (e: Exception) {
            null
        }
    }

    /** 用系统自带的 org.json 解析，不引第三方库。字段缺失时给空值，不让整份数据解析失败。 */
    fun parseFeed(json: String): Feed? {
        return try {
            val root = JSONObject(json)
            val quotesJson = root.optJSONArray("quotes")
            val quotes = mutableListOf<Quote>()
            if (quotesJson != null) {
                for (i in 0 until quotesJson.length()) {
                    val q = quotesJson.optJSONObject(i) ?: continue
                    quotes.add(
                        Quote(
                            symbol = q.optString("symbol"),
                            name = q.optString("name"),
                            price = q.optDouble("price", 0.0),
                            changePercent = q.optDouble("changePercent", 0.0),
                            currency = q.optString("currency", "USD")
                        )
                    )
                }
            }
            val itemsJson = root.optJSONArray("items")
            val items = mutableListOf<BriefingItem>()
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    val it = itemsJson.optJSONObject(i) ?: continue
                    items.add(
                        BriefingItem(
                            section = it.optString("section"),
                            title = it.optString("title"),
                            titleZh = it.optString("titleZh"),
                            detail = it.optString("detail"),
                            detailZh = it.optString("detailZh"),
                            sourceName = it.optString("sourceName"),
                            sourceUrl = it.optString("sourceUrl")
                        )
                    )
                }
            }
            Feed(
                date = root.optString("date"),
                fetchedAt = root.optString("fetchedAt"),
                quotes = quotes,
                items = items
            )
        } catch (e: Exception) {
            null
        }
    }
}
