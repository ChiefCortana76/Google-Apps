package com.example.data.repository

import android.util.Log
import android.util.Xml
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

private const val CNBC_US_STREAMGUYS_URL = "https://tunein.streamguys1.com/cnbc"
private const val CNBC_US_TUNEIN_OPML_URL =
  "https://opml.radiotime.com/Tune.ashx?id=s110052&render=json&formats=aac,mp3,hls"

enum class CnbcRegion(
  val title: String,
  val shortLabel: String,
  val deskCity: String,
  val showName: String,
  val rssUrl: String,
  val liveStreamUrl: String,
  val tuneInStationId: String?,
) {
  USA(
    title = "CNBC USA",
    shortLabel = "USA",
    deskCity = "New York • NYSE Desk",
    showName = "CNBC Live Audio & Squawk Box",
    rssUrl = "https://feeds.simplecast.com/VHAcgaw_",
    liveStreamUrl = CNBC_US_STREAMGUYS_URL,
    tuneInStationId = "s110052",
  ),
  EUROPE(
    title = "CNBC Europe",
    shortLabel = "Europe",
    deskCity = "London • EMEA Desk",
    showName = "Squawk Box Europe",
    rssUrl = "https://rss.art19.com/squawk-box-europe",
    liveStreamUrl = CNBC_US_STREAMGUYS_URL,
    tuneInStationId = "s110052",
  ),
  ASIA(
    title = "CNBC Asia",
    shortLabel = "Asia",
    deskCity = "Singapore • APAC Desk",
    showName = "Squawk Box Asia",
    rssUrl = "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=15838831",
    liveStreamUrl = CNBC_US_STREAMGUYS_URL,
    tuneInStationId = "s110052",
  ),
}

data class CnbcAudioItem(
  val id: String,
  val region: CnbcRegion,
  val title: String,
  val summary: String,
  val publishedLabel: String,
  val durationLabel: String,
  val audioUrl: String?,
)

class CnbcRadioRepository {
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  suspend fun fetchAllRegions(): Map<CnbcRegion, List<CnbcAudioItem>> =
    withContext(Dispatchers.IO) {
      coroutineScope {
        val usDeferred = async { fetchRegionFeed(CnbcRegion.USA) }
        val euDeferred = async { fetchRegionFeed(CnbcRegion.EUROPE) }
        val apacDeferred = async { fetchRegionFeed(CnbcRegion.ASIA) }
        mapOf(
          CnbcRegion.USA to usDeferred.await(),
          CnbcRegion.EUROPE to euDeferred.await(),
          CnbcRegion.ASIA to apacDeferred.await(),
        )
      }
    }

  suspend fun resolveLiveStreamCandidates(region: CnbcRegion): List<String> =
    withContext(Dispatchers.IO) {
      val candidates = linkedSetOf<String>()
      val stationId = region.tuneInStationId
      if (!stationId.isNullOrBlank()) {
        resolveTuneInStationUrls(stationId).forEach { resolved ->
          normalizeCnbcStreamUrl(resolved)?.let { candidates.add(it) }
        }
      }
      candidates.add(region.liveStreamUrl)
      candidates.add(CNBC_US_STREAMGUYS_URL)
      candidates.toList()
    }

  private fun resolveTuneInStationUrls(stationId: String): List<String> {
    val urls = mutableListOf<String>()
    try {
      val opmlUrl =
        if (stationId == "s110052") CNBC_US_TUNEIN_OPML_URL
        else "https://opml.radiotime.com/Tune.ashx?id=$stationId&render=json&formats=aac,mp3,hls"
      val request =
        Request.Builder()
          .url(opmlUrl)
          .header("User-Agent", USER_AGENT)
          .header("Accept", "application/json")
          .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        val bodyArray = JSONObject(body).optJSONArray("body") ?: return emptyList()
        for (i in 0 until bodyArray.length()) {
          val entry = bodyArray.optJSONObject(i) ?: continue
          val rawUrl = entry.optString("url").trim()
          if (rawUrl.isBlank() || isDisallowedLegacyMount(rawUrl)) continue
          val lower = rawUrl.lowercase()
          if (lower.endsWith(".pls") || (lower.endsWith(".m3u") && !lower.endsWith(".m3u8"))) {
            urls.addAll(unpackPlaylistUrls(rawUrl))
          } else {
            urls.add(rawUrl)
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "TuneIn OPML resolution fallback for $stationId: ${e.message}")
    }
    return urls
  }

  private fun unpackPlaylistUrls(playlistUrl: String): List<String> {
    return try {
      val request = Request.Builder().url(playlistUrl).header("User-Agent", USER_AGENT).build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        val content = response.body?.string().orEmpty()
        content
          .lineSequence()
          .map { it.trim() }
          .mapNotNull { line ->
            when {
              line.startsWith("http://", ignoreCase = true) ||
                line.startsWith("https://", ignoreCase = true) -> line
              line.startsWith("File", ignoreCase = true) && line.contains("=") ->
                line.substringAfter("=").trim()
              else -> null
            }
          }
          .filter { !isDisallowedLegacyMount(it) }
          .toList()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun normalizeCnbcStreamUrl(url: String): String? {
    if (url.isBlank() || isDisallowedLegacyMount(url)) return null
    return when {
      url.startsWith("https://", ignoreCase = true) -> url
      url.startsWith("http://tunein.streamguys1.com", ignoreCase = true) ->
        url.replaceFirst("http://", "https://", ignoreCase = true)
      else -> null
    }
  }

  private fun isDisallowedLegacyMount(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("3516_96") || lower.contains("schwab")
  }

  private fun fetchRegionFeed(region: CnbcRegion): List<CnbcAudioItem> {
    try {
      val request =
        Request.Builder()
          .url(region.rssUrl)
          .header("User-Agent", USER_AGENT)
          .build()
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val xml = response.body?.string()
          if (!xml.isNullOrBlank()) {
            val parsed = parseRssAudio(xml, region)
            if (parsed.isNotEmpty()) return parsed
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "CNBC RSS fallback for ${region.name}: ${e.message}")
    }
    return fallbackItems(region)
  }

  private fun parseRssAudio(xml: String, region: CnbcRegion): List<CnbcAudioItem> {
    val list = mutableListOf<CnbcAudioItem>()
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(xml))
    var eventType = parser.eventType
    var insideItem = false
    var title = ""
    var description = ""
    var pubDate = ""
    var duration = ""
    var enclosureUrl: String? = null
    var idx = 0

    while (eventType != XmlPullParser.END_DOCUMENT && list.size < 8) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          val tag = parser.name
          if (tag == "item") {
            insideItem = true
            title = ""
            description = ""
            pubDate = ""
            duration = ""
            enclosureUrl = null
          } else if (insideItem) {
            when (tag) {
              "title" -> title = parser.nextText().trim()
              "description" -> description = parser.nextText().replace(Regex("<[^>]*>"), "").trim()
              "pubDate" -> pubDate = parser.nextText().trim()
              "duration", "itunes:duration" -> duration = parser.nextText().trim()
              "enclosure" -> {
                val url = parser.getAttributeValue(null, "url")
                if (!url.isNullOrBlank()) enclosureUrl = url
              }
            }
          }
        }
        XmlPullParser.END_TAG -> {
          if (parser.name == "item" && insideItem) {
            insideItem = false
            if (title.isNotBlank()) {
              list.add(
                CnbcAudioItem(
                  id = "${region.name}_${idx++}",
                  region = region,
                  title = title,
                  summary = description.take(220).ifBlank { "${region.showName} broadcast bulletin." },
                  publishedLabel = pubDate.take(16).ifBlank { "Latest" },
                  durationLabel = duration.ifBlank { "Audio" },
                  audioUrl = enclosureUrl,
                )
              )
            }
          }
        }
      }
      eventType = parser.next()
    }
    return list
  }

  private fun fallbackItems(region: CnbcRegion): List<CnbcAudioItem> =
    when (region) {
      CnbcRegion.USA ->
        listOf(
          CnbcAudioItem(
            id = "us_1",
            region = CnbcRegion.USA,
            title = "CNBC US Live Audio — Wall Street Opening & Macro Desk",
            summary = "Live U.S. equities coverage, Treasury yields, Fed rate expectations, and earnings reaction from New York.",
            publishedLabel = "Live Desk",
            durationLabel = "LIVE",
            audioUrl = region.liveStreamUrl,
          ),
          CnbcAudioItem(
            id = "us_2",
            region = CnbcRegion.USA,
            title = "Squawk Pod: CPI Inflation Setup & Tech Leadership",
            summary = "Pre-market positioning across S&P 500, Nasdaq futures, and semiconductor earnings guidance.",
            publishedLabel = "Today",
            durationLabel = "18:40",
            audioUrl = region.liveStreamUrl,
          ),
        )
      CnbcRegion.EUROPE ->
        listOf(
          CnbcAudioItem(
            id = "eu_1",
            region = CnbcRegion.EUROPE,
            title = "Squawk Box Europe — London Market & ECB Policy Pulse",
            summary = "FTSE 100, DAX, STOXX 600 movers, Eurozone GDP outlook, and European bond yield moves.",
            publishedLabel = "EMEA Desk",
            durationLabel = "14:20",
            audioUrl = region.liveStreamUrl,
          ),
          CnbcAudioItem(
            id = "eu_2",
            region = CnbcRegion.EUROPE,
            title = "European Close: Energy, Luxury & Financials Wrap",
            summary = "Key sector rotation across European blue chips ahead of the U.S. midday session.",
            publishedLabel = "Today",
            durationLabel = "11:05",
            audioUrl = region.liveStreamUrl,
          ),
        )
      CnbcRegion.ASIA ->
        listOf(
          CnbcAudioItem(
            id = "apac_1",
            region = CnbcRegion.ASIA,
            title = "Squawk Box Asia — Nikkei, Hang Seng & BoJ Outlook",
            summary = "Overnight APAC equity trading, USD/JPY moves, China stimulus commentary, and Asian chipmakers.",
            publishedLabel = "APAC Desk",
            durationLabel = "LIVE",
            audioUrl = region.liveStreamUrl,
          ),
          CnbcAudioItem(
            id = "apac_2",
            region = CnbcRegion.ASIA,
            title = "Street Signs Asia: Commodities & Regional FX Briefing",
            summary = "Singapore & Tokyo desk wrap on crude oil, gold futures, and sovereign yields.",
            publishedLabel = "Today",
            durationLabel = "09:45",
            audioUrl = region.liveStreamUrl,
          ),
        )
    }

  private companion object {
    const val TAG = "CnbcRadioRepo"
    const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile) SquawkOnTheStreet/4.0"
  }
}
