package com.example.customwidgets

import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Immutable
enum class NewsCategory(val displayName: String, val feedUrl: String, val sourceBadge: String) {
    TOP_WORLD("Top World", "https://feeds.bbci.co.uk/news/world/rss.xml", "BBC WORLD"),
    TECH_AI("Tech & AI", "https://hnrss.org/frontpage", "HACKER NEWS"),
    SCIENCE("Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "SCIENCE"),
    BUSINESS("Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "MARKETS"),
    SPORTS("Sports", "https://feeds.bbci.co.uk/sport/rss.xml", "SPORT")
}

@Immutable
data class NewsArticle(
    val title: String,
    val link: String,
    val source: String,
    val pubDate: String,
    val summary: String
)

@Immutable
data class NewsFeedState(
    val activeCategory: NewsCategory = NewsCategory.TOP_WORLD,
    val articles: List<NewsArticle> = defaultFallbackArticles,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

private val defaultFallbackArticles = listOf(
    NewsArticle(
        title = "Tap Refresh when connected to load live global & tech headlines",
        link = "https://news.google.com",
        source = "DOTOS NEWS",
        pubDate = "LIVE FEED",
        summary = "Switch between Top World, Tech & AI, Science, Business, and Sports right from News Space."
    ),
    NewsArticle(
        title = "Next-gen mobile silicon pushes on-device AI & 120Hz efficiency",
        link = "https://news.ycombinator.com",
        source = "TECH PULSE",
        pubDate = "RECENT",
        summary = "Modern mobile architectures continue cutting frame latency and idle GPU power consumption."
    ),
    NewsArticle(
        title = "Deep-space observatories map new exoplanet atmospheres in infrared",
        link = "https://www.nasa.gov/news/",
        source = "SCIENCE",
        pubDate = "RECENT",
        summary = "High-resolution spectroscopy reveals water vapor and cloud dynamics across nearby star systems."
    )
)

object NewsRepository {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<NewsCategory, List<NewsArticle>>()
    private val lastFetchMs = ConcurrentHashMap<NewsCategory, Long>()

    private val _state = MutableStateFlow(NewsFeedState())
    val state: StateFlow<NewsFeedState> = _state.asStateFlow()

    suspend fun selectCategory(category: NewsCategory, forceRefresh: Boolean = false) {
        val cached = cache[category]
        if (cached != null && !forceRefresh) {
            _state.value = NewsFeedState(
                activeCategory = category,
                articles = cached,
                isLoading = false,
                errorMessage = null
            )
            val age = System.currentTimeMillis() - (lastFetchMs[category] ?: 0L)
            if (age < 10 * 60 * 1000L) return
        }
        refresh(category, force = forceRefresh)
    }

    suspend fun refresh(
        category: NewsCategory = _state.value.activeCategory,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cache[category]
        if (!force && cached != null && now - (lastFetchMs[category] ?: 0L) < 5 * 60 * 1000L) {
            _state.value = _state.value.copy(
                activeCategory = category,
                articles = cached,
                isLoading = false,
                errorMessage = null
            )
            return@withContext
        }

        _state.value = _state.value.copy(
            activeCategory = category,
            articles = cached ?: _state.value.articles,
            isLoading = true,
            errorMessage = null
        )

        runCatching {
            val req = Request.Builder()
                .url(category.feedUrl)
                .header("User-Agent", "DotOSLauncher/1.3")
                .build()
            val xml = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val parsed = parseRssXml(xml, category.sourceBadge)
            if (parsed.isEmpty()) error("Empty feed")
            parsed
        }.onSuccess { list ->
            cache[category] = list
            lastFetchMs[category] = System.currentTimeMillis()
            _state.value = NewsFeedState(
                activeCategory = category,
                articles = list,
                isLoading = false,
                errorMessage = null
            )
        }.onFailure { err ->
            _state.value = _state.value.copy(
                activeCategory = category,
                articles = cached ?: defaultFallbackArticles,
                isLoading = false,
                errorMessage = err.localizedMessage ?: "Offline"
            )
        }
    }

    private fun parseRssXml(xmlContent: String, defaultSource: String): List<NewsArticle> {
        if (xmlContent.isBlank()) return emptyList()
        val results = ArrayList<NewsArticle>(24)
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var insideItem = false
        var title = ""
        var link = ""
        var desc = ""
        var pubDate = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT && results.size < 22) {
            val tagName = parser.name?.lowercase().orEmpty()
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        insideItem = true
                        title = ""
                        link = ""
                        desc = ""
                        pubDate = ""
                    } else if (insideItem) {
                        when (tagName) {
                            "title" -> title = runCatching { parser.nextText() }.getOrDefault("").trim()
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                val txt = runCatching { parser.nextText() }.getOrDefault("").trim()
                                link = (href ?: txt).trim()
                            }
                            "description", "summary" -> {
                                desc = stripHtml(runCatching { parser.nextText() }.getOrDefault(""))
                            }
                            "pubdate", "published", "updated" -> {
                                pubDate = cleanPubDate(runCatching { parser.nextText() }.getOrDefault(""))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ((tagName == "item" || tagName == "entry") && insideItem) {
                        insideItem = false
                        if (title.isNotBlank() && link.isNotBlank()) {
                            results.add(
                                NewsArticle(
                                    title = stripHtml(title),
                                    link = link,
                                    source = defaultSource,
                                    pubDate = pubDate.ifBlank { "LATEST" },
                                    summary = desc.take(180)
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return results
    }

    private fun stripHtml(raw: String): String {
        return raw
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanPubDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length <= 16) return trimmed.uppercase()
        // e.g. "Fri, 27 Nov 2026 11:24:00 GMT" -> "27 NOV • 11:24"
        val parts = trimmed.split(" ")
        return if (parts.size >= 5) {
            val day = parts[1]
            val mon = parts[2].uppercase()
            val hm = parts[4].take(5)
            "$day $mon • $hm"
        } else {
            trimmed.take(16).uppercase()
        }
    }
}
