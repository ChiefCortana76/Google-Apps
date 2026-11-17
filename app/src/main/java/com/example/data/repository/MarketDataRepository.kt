package com.example.data.repository

import android.util.Log
import android.util.Xml
import com.example.data.model.AssetClass
import com.example.data.model.CalendarWindow
import com.example.data.model.ChartTimeframe
import com.example.data.model.EarningsItem
import com.example.data.model.EarningsSession
import com.example.data.model.EconomicEvent
import com.example.data.model.EconomicRegion
import com.example.data.model.EventImportance
import com.example.data.model.FearGreedDriver
import com.example.data.model.MarketBreadthSnapshot
import com.example.data.model.MarketCapTier
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketNewsArticle
import com.example.data.model.MarketQuote
import com.example.data.model.NewsCategory
import com.example.data.model.QuoteCategory
import com.example.data.model.SectorPerformance
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class ChartPoint(
  val timestampLabel: String,
  val price: Double,
)

data class ChartSeriesResult(
  val symbol: String,
  val timeframe: ChartTimeframe,
  val points: List<ChartPoint>,
  val high: Double,
  val low: Double,
  val open: Double,
  val previousClose: Double,
  val isLiveFeed: Boolean,
)

data class TickerSearchResult(
  val symbol: String,
  val name: String,
  val assetClass: AssetClass,
  val exchange: String = "",
  val quote: MarketQuote? = null,
)

class MarketDataRepository {
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .build()

  private val etZone = ZoneId.of("America/New_York")
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss 'ET'", Locale.US)
  private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
  private val economicCalendarFetcher = EconomicCalendarFetcher(client, etZone)

  suspend fun fetchFullSnapshot(
    extraSymbols: List<String>,
    forceCalendarRefresh: Boolean = false,
  ): MarketCommandSnapshot =
    withContext(Dispatchers.IO) {
      val indexSpecs =
        listOf(
          Triple("^GSPC", "SPX", "S&P 500"),
          Triple("^IXIC", "NDX", "Nasdaq Composite"),
          Triple("^DJI", "DOW", "Dow Jones"),
          Triple("^RUT", "RUT", "Russell 2000"),
          Triple("^VIX", "VIX", "CBOE Volatility"),
        )
      val futureSpecs =
        listOf(
          Triple("ES=F", "ES", "S&P 500 Futures"),
          Triple("NQ=F", "NQ", "Nasdaq 100 Futures"),
          Triple("YM=F", "YM", "Dow Futures"),
          Triple("RTY=F", "RTY", "Russell 2000 Futures"),
          Triple("VX=F", "VX", "VIX Futures"),
        )
      val sectorSpecs =
        listOf(
          SectorMeta("XLK", "Technology", 31.4, "NVDA, AAPL, MSFT"),
          SectorMeta("XLF", "Financials", 13.1, "JPM, BRK.B, V"),
          SectorMeta("XLV", "Health Care", 11.8, "LLY, UNH, JNJ"),
          SectorMeta("XLY", "Cons. Discret.", 10.3, "AMZN, TSLA, HD"),
          SectorMeta("XLC", "Comm. Services", 8.9, "META, GOOGL, NFLX"),
          SectorMeta("XLI", "Industrials", 8.4, "GE, CAT, UNP"),
          SectorMeta("XLP", "Cons. Staples", 5.8, "COST, WMT, PG"),
          SectorMeta("XLE", "Energy", 3.6, "XOM, CVX, SLB"),
          SectorMeta("XLU", "Utilities", 2.5, "NEE, SO, DUK"),
          SectorMeta("XLRE", "Real Estate", 2.2, "PLD, AMT, EQIX"),
          SectorMeta("XLB", "Materials", 2.0, "LIN, SHW, FCX"),
        )

      val defaultDashboardTickers =
        listOf("NVDA", "AAPL", "MSFT", "SPY", "QQQ", "BTC-USD", "GC=F", "CL=F")
      val distinctExtra =
        (defaultDashboardTickers + extraSymbols)
          .map { it.trim().uppercase() }
          .filter { it.isNotEmpty() }
          .distinct()
          .take(24)

      coroutineScope {
        val indicesDeferred =
          indexSpecs.map { (sym, disp, name) ->
            async { fetchQuoteOrFallback(sym, disp, name, QuoteCategory.INDEX) }
          }
        val futuresDeferred =
          futureSpecs.map { (sym, disp, name) ->
            async { fetchQuoteOrFallback(sym, disp, name, QuoteCategory.FUTURE) }
          }
        val sectorsDeferred =
          sectorSpecs.map { meta ->
            async {
              val quote =
                fetchQuoteOrFallback(meta.ticker, meta.ticker, meta.name, QuoteCategory.WATCHLIST)
              SectorPerformance(
                ticker = meta.ticker,
                name = meta.name,
                weightPercent = meta.weightPercent,
                changePercent = quote.changePercent,
                leaders = meta.leaders,
              )
            }
          }
        val extraDeferred =
          distinctExtra.map { sym ->
            async { sym to fetchQuoteOrFallback(sym, sym, sym, QuoteCategory.WATCHLIST) }
          }
        val newsDeferred = async { fetchMarketNewsOrFallback() }
        val nowEt = ZonedDateTime.now(etZone)
        val economicDeferred =
          async {
            economicCalendarFetcher.fetchEvents(nowEt = nowEt, forceRefresh = forceCalendarRefresh)
          }

        val indices = indicesDeferred.awaitAll()
        val futures = futuresDeferred.awaitAll()
        val sectors = sectorsDeferred.awaitAll()
        val extraQuotes = extraDeferred.awaitAll().toMap()
        val news = newsDeferred.await()
        val economicResult = economicDeferred.await()

        val anyLive =
          indices.any { it.isLiveFeed } ||
            futures.any { it.isLiveFeed } ||
            extraQuotes.values.any { it.isLiveFeed } ||
            economicResult.isLiveNetworkData

        val breadth = buildBreadthSnapshot(indices, sectors)

        MarketCommandSnapshot(
          indices = indices,
          futures = futures,
          watchlistQuotes = extraQuotes,
          economicEvents = economicResult.events,
          earnings = buildEarningsCalendar(),
          news = news,
          breadth = breadth,
          lastUpdatedLabel = nowEt.format(timeFormatter),
          anyLiveFeedConnected = anyLive,
          economicDateLabel = economicResult.scheduleDateEt.format(dateFormatter),
          economicLiveFeed = economicResult.isLiveNetworkData,
        )
      }
    }

  suspend fun fetchChartSeries(
    quote: MarketQuote,
    timeframe: ChartTimeframe,
  ): ChartSeriesResult =
    withContext(Dispatchers.IO) {
      val url =
        "https://query1.finance.yahoo.com/v8/finance/chart/${quote.symbol}?range=${timeframe.rangeParam}&interval=${timeframe.intervalParam}"
      try {
        val request =
          Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) MarketPulse/1.0")
            .build()
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank()) {
              val parsed = parseChartResponse(body, quote.symbol, timeframe)
              if (parsed != null && parsed.points.size >= 4) {
                return@withContext parsed
              }
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.d(TAG, "Chart HTTP fallback for ${quote.symbol} (${timeframe.label}): ${e.message}")
      }

      buildFallbackChartSeries(quote, timeframe)
    }

  suspend fun fetchQuote(symbol: String, fallbackName: String? = null): MarketQuote =
    withContext(Dispatchers.IO) {
      val clean = symbol.trim().uppercase()
      fetchQuoteOrFallback(
        symbol = clean,
        displaySymbol = clean,
        name = fallbackName ?: BASELINE_PRICES[clean]?.third ?: clean,
        category = QuoteCategory.WATCHLIST,
      )
    }

  suspend fun searchTickers(
    query: String,
    snapshot: MarketCommandSnapshot? = null,
  ): List<TickerSearchResult> =
    withContext(Dispatchers.IO) {
      val clean = query.trim()
      if (clean.isEmpty()) return@withContext emptyList()
      val upper = clean.uppercase()
      val snapshotQuotes = buildMap {
        snapshot?.indices?.forEach { put(it.symbol.uppercase(), it) }
        snapshot?.futures?.forEach { put(it.symbol.uppercase(), it) }
        snapshot?.watchlistQuotes?.forEach { (sym, q) -> put(sym.uppercase(), q) }
      }

      val remoteResults = mutableListOf<TickerSearchResult>()
      try {
        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        val url =
          "https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=10&newsCount=0"
        val request =
          Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) MarketPulse/1.0")
            .build()
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank()) {
              val quotesArr = JSONObject(body).optJSONArray("quotes")
              if (quotesArr != null) {
                for (i in 0 until quotesArr.length()) {
                  val item = quotesArr.optJSONObject(i) ?: continue
                  val sym = item.optString("symbol").trim().uppercase()
                  if (sym.isEmpty()) continue
                  val name =
                    item.optString("shortname").takeIf { it.isNotBlank() }
                      ?: item.optString("longname").takeIf { it.isNotBlank() }
                      ?: sym
                  val qType = item.optString("quoteType").uppercase()
                  val assetClass =
                    when (qType) {
                      "ETF" -> AssetClass.ETF
                      "INDEX" -> AssetClass.INDEX
                      "CRYPTOCURRENCY" -> AssetClass.CRYPTO
                      "FUTURE" -> AssetClass.COMMODITY
                      else -> AssetClass.STOCK
                    }
                  val exchange =
                    item.optString("exchDisp").takeIf { it.isNotBlank() }
                      ?: item.optString("exchange")
                  remoteResults.add(
                    TickerSearchResult(
                      symbol = sym,
                      name = name,
                      assetClass = assetClass,
                      exchange = exchange,
                      quote = snapshotQuotes[sym],
                    )
                  )
                }
              }
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.d(TAG, "Ticker search HTTP fallback for '$clean': ${e.message}")
      }

      val localMatches =
        SEARCH_CATALOG.filter { entry ->
            entry.symbol.contains(upper, ignoreCase = true) ||
              entry.name.contains(clean, ignoreCase = true)
          }
          .sortedWith(
            compareByDescending<TickerSearchResult> { it.symbol.equals(upper, ignoreCase = true) }
              .thenByDescending { it.symbol.startsWith(upper, ignoreCase = true) }
              .thenBy { it.symbol }
          )
          .map { entry -> entry.copy(quote = snapshotQuotes[entry.symbol]) }

      val merged = linkedMapOf<String, TickerSearchResult>()
      localMatches.filter { it.symbol.equals(upper, ignoreCase = true) }.forEach {
        merged[it.symbol] = it
      }
      remoteResults.forEach { res ->
        merged.putIfAbsent(res.symbol, res)
      }
      localMatches.forEach { res ->
        merged.putIfAbsent(res.symbol, res)
      }

      if (merged.isEmpty() && upper.matches(Regex("^[A-Z0-9^.=\\-]{1,12}$"))) {
        val assetClass =
          when {
            upper.startsWith("^") -> AssetClass.INDEX
            upper.endsWith("=F") -> AssetClass.COMMODITY
            upper.endsWith("-USD") -> AssetClass.CRYPTO
            else -> AssetClass.STOCK
          }
        merged[upper] =
          TickerSearchResult(
            symbol = upper,
            name = BASELINE_PRICES[upper]?.third ?: "$upper Ticker",
            assetClass = assetClass,
            exchange = "Custom",
            quote = snapshotQuotes[upper],
          )
      }

      merged.values.take(12)
    }

  private fun fetchQuoteOrFallback(
    symbol: String,
    displaySymbol: String,
    name: String,
    category: QuoteCategory,
  ): MarketQuote {
    val url =
      "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=1d&interval=15m"
    try {
      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) MarketPulse/1.0")
          .build()
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val body = response.body?.string()
          if (!body.isNullOrBlank()) {
            val parsed = parseQuoteFromChartJson(body, symbol, displaySymbol, name, category)
            if (parsed != null) return parsed
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "Quote fallback for $symbol: ${e.message}")
    }
    return baselineQuote(symbol, displaySymbol, name, category)
  }

  private fun parseQuoteFromChartJson(
    jsonText: String,
    symbol: String,
    displaySymbol: String,
    name: String,
    category: QuoteCategory,
  ): MarketQuote? {
    val root = JSONObject(jsonText)
    val result = root.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return null
    val meta = result.optJSONObject("meta") ?: return null
    val price = meta.optDouble("regularMarketPrice", Double.NaN)
    if (price.isNaN() || price <= 0.0) return null

    val prevClose =
      meta.optDouble("chartPreviousClose", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
        ?: meta.optDouble("previousClose", price)
    val change = price - prevClose
    val changePct = if (prevClose > 0.0) (change / prevClose) * 100.0 else 0.0
    val dayHigh = meta.optDouble("regularMarketDayHigh", price)
    val dayLow = meta.optDouble("regularMarketDayLow", price)
    val open = meta.optDouble("regularMarketOpen", prevClose)
    val vol = meta.optLong("regularMarketVolume", 0L)

    val closes = mutableListOf<Double>()
    val quoteObj =
      result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
    val closeArr = quoteObj?.optJSONArray("close")
    if (closeArr != null) {
      for (i in 0 until closeArr.length()) {
        val v = closeArr.optDouble(i, Double.NaN)
        if (!v.isNaN() && v > 0.0) closes.add(v)
      }
    }
    if (closes.size < 4) {
      closes.clear()
      closes.addAll(synthesizeSparkline(prevClose, price, symbol.hashCode()))
    }

    return MarketQuote(
      symbol = symbol,
      displaySymbol = displaySymbol,
      name = meta.optString("shortName").takeIf { it.isNotBlank() } ?: name,
      price = price,
      change = change,
      changePercent = changePct,
      previousClose = prevClose,
      dayHigh = dayHigh,
      dayLow = dayLow,
      open = open,
      volumeLabel = formatVolume(vol),
      sparkline = closes,
      category = category,
      isLiveFeed = true,
    )
  }

  private fun parseChartResponse(
    jsonText: String,
    symbol: String,
    timeframe: ChartTimeframe,
  ): ChartSeriesResult? {
    val root = JSONObject(jsonText)
    val result = root.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return null
    val meta = result.optJSONObject("meta") ?: return null
    val prevClose =
      meta.optDouble("chartPreviousClose", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
        ?: meta.optDouble("regularMarketPrice", 0.0)
    val timestamps = result.optJSONArray("timestamp") ?: return null
    val closes =
      result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
        ?.optJSONArray("close") ?: return null

    val formatter =
      when (timeframe) {
        ChartTimeframe.D1 -> DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        ChartTimeframe.W1 -> DateTimeFormatter.ofPattern("EEE h a", Locale.US)
        ChartTimeframe.M1 -> DateTimeFormatter.ofPattern("MMM d", Locale.US)
        ChartTimeframe.Y1 -> DateTimeFormatter.ofPattern("MMM ''yy", Locale.US)
      }

    val points = mutableListOf<ChartPoint>()
    for (i in 0 until minOf(timestamps.length(), closes.length())) {
      val c = closes.optDouble(i, Double.NaN)
      val ts = timestamps.optLong(i, -1L)
      if (!c.isNaN() && c > 0.0 && ts > 0L) {
        val label = Instant.ofEpochSecond(ts).atZone(etZone).format(formatter)
        points.add(ChartPoint(label, c))
      }
    }
    if (points.isEmpty()) return null

    return ChartSeriesResult(
      symbol = symbol,
      timeframe = timeframe,
      points = points,
      high = points.maxOf { it.price },
      low = points.minOf { it.price },
      open = points.first().price,
      previousClose = prevClose,
      isLiveFeed = true,
    )
  }

  private fun buildFallbackChartSeries(
    quote: MarketQuote,
    timeframe: ChartTimeframe,
  ): ChartSeriesResult {
    val count =
      when (timeframe) {
        ChartTimeframe.D1 -> 40
        ChartTimeframe.W1 -> 35
        ChartTimeframe.M1 -> 30
        ChartTimeframe.Y1 -> 52
      }
    val spreadFactor =
      when (timeframe) {
        ChartTimeframe.D1 -> 0.009
        ChartTimeframe.W1 -> 0.022
        ChartTimeframe.M1 -> 0.048
        ChartTimeframe.Y1 -> 0.175
      }
    val startBase =
      if (timeframe == ChartTimeframe.D1) quote.previousClose
      else quote.price * (1.0 - spreadFactor * (if (quote.isPositive) 0.72 else -0.45))

    val now = ZonedDateTime.now(etZone)
    val w1Formatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    val m1Formatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val y1Formatter = DateTimeFormatter.ofPattern("MMM ''yy", Locale.US)

    val seed = abs(quote.symbol.hashCode() + timeframe.ordinal * 31)
    val points =
      (0 until count).map { i ->
        val progress = i.toDouble() / (count - 1).coerceAtLeast(1)
        val wave =
          sin((i + seed) * 0.48) * spreadFactor * 0.25 +
            sin((i + seed) * 0.19) * spreadFactor * 0.18
        val price =
          if (i == count - 1) quote.price
          else startBase + (quote.price - startBase) * progress + quote.price * wave * (1.0 - progress * 0.4)
        val remaining = count - 1 - i
        val label =
          when (timeframe) {
            ChartTimeframe.D1 -> {
              val mins = 570 + (i * 390) / (count - 1)
              String.format(Locale.US, "%02d:%02d", mins / 60, mins % 60)
            }
            ChartTimeframe.W1 -> {
              val daysBack = ((remaining.toDouble() / (count - 1)) * 6.0).toLong()
              now.minusDays(daysBack).format(w1Formatter)
            }
            ChartTimeframe.M1 -> now.minusDays(remaining.toLong()).format(m1Formatter)
            ChartTimeframe.Y1 -> now.minusWeeks(remaining.toLong()).format(y1Formatter)
          }
        ChartPoint(label, price.coerceAtLeast(0.01))
      }

    val seriesHigh = points.maxOf { it.price }
    val seriesLow = points.minOf { it.price }
    return ChartSeriesResult(
      symbol = quote.symbol,
      timeframe = timeframe,
      points = points,
      high = if (timeframe == ChartTimeframe.D1) maxOf(quote.dayHigh, seriesHigh) else seriesHigh,
      low = if (timeframe == ChartTimeframe.D1) minOf(quote.dayLow, seriesLow) else seriesLow,
      open = points.first().price,
      previousClose = quote.previousClose,
      isLiveFeed = false,
    )
  }

  private fun fetchMarketNewsOrFallback(): List<MarketNewsArticle> {
    val rssUrl =
      "https://feeds.finance.yahoo.com/rss/2.0/headline?s=^GSPC,QQQ,NVDA,BTC-USD,TLT,CL=F&region=US&lang=en-US"
    val baseline = baselineNews()
    try {
      val request =
        Request.Builder()
          .url(rssUrl)
          .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) MarketPulse/1.0")
          .build()
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val xml = response.body?.string()
          if (!xml.isNullOrBlank()) {
            val parsed = parseRssNews(xml)
            if (parsed.size >= 5) {
              val merged = parsed.toMutableList()
              val existingTitles = merged.mapTo(mutableSetOf()) { it.title.lowercase(Locale.US) }
              val requiredCategories =
                listOf(
                  NewsCategory.MARKETS,
                  NewsCategory.ECONOMICS,
                  NewsCategory.FEDERAL_RESERVE,
                  NewsCategory.EARNINGS,
                  NewsCategory.CRYPTO,
                )
              requiredCategories.forEach { category ->
                val currentCount = merged.count { it.category == category }
                if (currentCount < 2) {
                  baseline
                    .filter { it.category == category }
                    .take(2 - currentCount)
                    .forEach { fallbackItem ->
                      if (existingTitles.add(fallbackItem.title.lowercase(Locale.US))) {
                        merged.add(fallbackItem)
                      }
                    }
                }
              }
              return merged
            }
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "News RSS fallback: ${e.message}")
    }
    return baseline
  }

  private fun parseRssNews(xml: String): List<MarketNewsArticle> {
    val list = mutableListOf<MarketNewsArticle>()
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(xml))
    var eventType = parser.eventType
    var insideItem = false
    var title = ""
    var description = ""
    var link = ""
    var pubDate = ""
    var idx = 0
    while (eventType != XmlPullParser.END_DOCUMENT && list.size < 18) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          val tag = parser.name
          if (tag == "item") {
            insideItem = true
            title = ""
            description = ""
            link = ""
            pubDate = ""
          } else if (insideItem) {
            when (tag) {
              "title" -> title = parser.nextText().trim()
              "description" -> description = parser.nextText().replace(Regex("<[^>]*>"), "").trim()
              "link" -> link = parser.nextText().trim()
              "pubDate" -> pubDate = parser.nextText().trim()
            }
          }
        }
        XmlPullParser.END_TAG -> {
          if (parser.name == "item" && insideItem) {
            insideItem = false
            if (title.isNotBlank()) {
              list.add(
                MarketNewsArticle(
                  id = "rss_${idx++}",
                  title = title,
                  summary = description.ifBlank { "Live market wire coverage from Yahoo Finance." },
                  source = "Market Wire",
                  publishedAgo = pubDate.take(16).ifBlank { "Today" },
                  category = classifyNewsCategory(title),
                  url = link,
                  sentimentPositive = inferSentiment(title),
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

  private fun classifyNewsCategory(title: String): NewsCategory {
    val lower = title.lowercase(Locale.US)
    return when {
      "fed" in lower ||
        "powell" in lower ||
        "fomc" in lower ||
        "rate cut" in lower ||
        "rate hike" in lower ||
        "treasury" in lower ||
        "central bank" in lower ->
        NewsCategory.FEDERAL_RESERVE
      "cpi" in lower ||
        "pce" in lower ||
        "inflation" in lower ||
        "gdp" in lower ||
        "jobs" in lower ||
        "payroll" in lower ||
        "retail sales" in lower ||
        "unemployment" in lower ||
        "pmi" in lower ->
        NewsCategory.ECONOMICS
      "earnings" in lower ||
        "eps" in lower ||
        "revenue" in lower ||
        "guidance" in lower ||
        "quarterly" in lower ||
        "profit" in lower ->
        NewsCategory.EARNINGS
      "bitcoin" in lower ||
        "btc" in lower ||
        "crypto" in lower ||
        "ethereum" in lower ||
        "eth" in lower ||
        "solana" in lower ||
        "coinbase" in lower ->
        NewsCategory.CRYPTO
      "nvidia" in lower || "apple" in lower || "ai" in lower || "chip" in lower || "tech" in lower || "microsoft" in lower ->
        NewsCategory.TECHNOLOGY
      "oil" in lower || "crude" in lower || "opec" in lower || "energy" in lower || "gas" in lower ->
        NewsCategory.ENERGY
      else -> NewsCategory.MARKETS
    }
  }

  private fun inferSentiment(title: String): Boolean? {
    val lower = title.lowercase(Locale.US)
    return when {
      listOf("rally", "surge", "gain", "beat", "record", "jump", "climb", "rise").any { it in lower } ->
        true
      listOf("drop", "fall", "slump", "miss", "decline", "sink", "selloff", "plunge").any { it in lower } ->
        false
      else -> null
    }
  }

  private fun baselineQuote(
    symbol: String,
    displaySymbol: String,
    name: String,
    category: QuoteCategory,
  ): MarketQuote {
    val preset = BASELINE_PRICES[symbol]
    val basePrice = preset?.first ?: (80.0 + abs(symbol.hashCode() % 340))
    val basePct = preset?.second ?: (((symbol.hashCode() % 280) - 110) / 100.0)
    val prevClose = basePrice / (1.0 + basePct / 100.0)
    val change = basePrice - prevClose
    return MarketQuote(
      symbol = symbol,
      displaySymbol = displaySymbol,
      name = preset?.third ?: name,
      price = basePrice,
      change = change,
      changePercent = basePct,
      previousClose = prevClose,
      dayHigh = maxOf(basePrice, prevClose) * 1.006,
      dayLow = minOf(basePrice, prevClose) * 0.994,
      open = prevClose * 1.001,
      volumeLabel = if (category == QuoteCategory.INDEX) "2.41B" else "18.4M",
      sparkline = synthesizeSparkline(prevClose, basePrice, symbol.hashCode()),
      category = category,
      isLiveFeed = false,
    )
  }

  private fun synthesizeSparkline(start: Double, end: Double, seed: Int): List<Double> {
    val count = 24
    return (0 until count).map { i ->
      val t = i.toDouble() / (count - 1)
      val wiggle = sin((i + abs(seed)) * 0.55) * (start * 0.0025)
      if (i == count - 1) end else start + (end - start) * t + wiggle
    }
  }

  private fun buildBreadthSnapshot(
    indices: List<MarketQuote>,
    sectors: List<SectorPerformance>,
  ): MarketBreadthSnapshot {
    val spxPct = indices.firstOrNull { it.displaySymbol == "SPX" }?.changePercent ?: 0.42
    val vixPrice = indices.firstOrNull { it.displaySymbol == "VIX" }?.price ?: 15.8

    val advRatioFraction = (0.54 + (spxPct * 0.14)).coerceIn(0.22, 0.82)
    val totalNyse = 2940
    val advancing = (totalNyse * advRatioFraction).roundToInt()
    val unchanged = 96
    val declining = (totalNyse - advancing - unchanged).coerceAtLeast(120)
    val adRatio = advancing.toDouble() / declining.toDouble()

    val highs = (118 + (spxPct * 55)).roundToInt().coerceIn(18, 340)
    val lows = (54 - (spxPct * 24)).roundToInt().coerceIn(12, 260)

    val eqPutCall = (0.64 - spxPct * 0.06).coerceIn(0.42, 1.15)
    val totalPutCall = (0.78 - spxPct * 0.05).coerceIn(0.55, 1.25)

    val fearGreed =
      (58 + (spxPct * 14) - ((vixPrice - 16.0) * 1.8)).roundToInt().coerceIn(8, 94)
    val fgLabel =
      when {
        fearGreed < 25 -> "Extreme Fear"
        fearGreed < 45 -> "Fear"
        fearGreed <= 55 -> "Neutral"
        fearGreed <= 75 -> "Greed"
        else -> "Extreme Greed"
      }

    val upVol = (2.15 * advRatioFraction * 1.65).coerceIn(0.6, 3.8)
    val downVol = (3.45 - upVol).coerceIn(0.5, 3.2)
    val trin = ((advancing.toDouble() / declining) / (upVol / downVol)).coerceIn(0.45, 2.10)
    val mcclellan = (spxPct * 42.0 + 18.4).coerceIn(-120.0, 140.0)
    val pct50 = (62 + (spxPct * 9)).roundToInt().coerceIn(22, 88)
    val pct200 = (68 + (spxPct * 5)).roundToInt().coerceIn(30, 90)

    val adSeries =
      listOf(-420.0, -310.0, -180.0, -90.0, 45.0, 190.0, 310.0, 260.0, 410.0, 520.0, 610.0, 610.0 + (advancing - declining))

    val drivers =
      listOf(
        FearGreedDriver("Market Momentum (S&P 500 vs 125D)", (fearGreed + 6).coerceIn(10, 95), fgLabel),
        FearGreedDriver("Stock Price Breadth (McClellan)", (fearGreed - 4).coerceIn(10, 95), if (mcclellan >= 0) "Greed" else "Fear"),
        FearGreedDriver("Put / Call Options Ratio", (100 - (eqPutCall * 55).roundToInt()).coerceIn(15, 90), if (eqPutCall < 0.7) "Greed" else "Fear"),
        FearGreedDriver("Market Volatility (VIX)", (100 - ((vixPrice - 10.0) * 3.2).roundToInt()).coerceIn(12, 92), if (vixPrice < 18) "Neutral" else "Fear"),
        FearGreedDriver("52-Week Highs vs Lows", ((highs.toDouble() / (highs + lows)) * 100).roundToInt().coerceIn(10, 95), if (highs > lows) "Greed" else "Fear"),
      )

    return MarketBreadthSnapshot(
      advancingCount = advancing,
      decliningCount = declining,
      unchangedCount = unchanged,
      advanceDeclineRatio = adRatio,
      newHighs52W = highs,
      newLows52W = lows,
      putCallEquityRatio = eqPutCall,
      putCallTotalRatio = totalPutCall,
      fearGreedScore = fearGreed,
      fearGreedLabel = fgLabel,
      fearGreedDrivers = drivers,
      nyseUpVolumeBillions = upVol,
      nyseDownVolumeBillions = downVol,
      trinArmsIndex = trin,
      mcclellanOscillator = mcclellan,
      pctAbove50Dma = pct50,
      pctAbove200Dma = pct200,
      adLineSeries = adSeries,
      sectors = sectors,
    )
  }

  private fun buildEarningsCalendar(): List<EarningsItem> =
    listOf(
      EarningsItem(
        id = "er_1",
        ticker = "WMT",
        company = "Walmart Inc.",
        sector = "Consumer Staples",
        marketCapTier = MarketCapTier.MEGA,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "07:00 ET",
        expectedEps = "$0.53",
        expectedRevenue = "$167.8B",
        actualEps = "$0.58",
      ),
      EarningsItem(
        id = "er_2",
        ticker = "HD",
        company = "Home Depot Inc.",
        sector = "Consumer Discret.",
        marketCapTier = MarketCapTier.MEGA,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "06:00 ET",
        expectedEps = "$3.64",
        expectedRevenue = "$39.2B",
        actualEps = "$3.89",
      ),
      EarningsItem(
        id = "er_3",
        ticker = "DE",
        company = "Deere & Company",
        sector = "Industrials",
        marketCapTier = MarketCapTier.LARGE,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "07:30 ET",
        expectedEps = "$3.88",
        expectedRevenue = "$9.3B",
        actualEps = "$3.54",
      ),
      EarningsItem(
        id = "er_3b",
        ticker = "LOW",
        company = "Lowe's Companies",
        sector = "Consumer Discret.",
        marketCapTier = MarketCapTier.LARGE,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "06:15 ET",
        expectedEps = "$2.82",
        expectedRevenue = "$20.1B",
        actualEps = "$2.84",
      ),
      EarningsItem(
        id = "er_4",
        ticker = "NVDA",
        company = "NVIDIA Corporation",
        sector = "Technology",
        marketCapTier = MarketCapTier.MEGA,
        isMajorCompany = true,
        session = EarningsSession.AFTER_CLOSE,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "16:20 ET",
        expectedEps = "$0.75",
        expectedRevenue = "$33.1B",
      ),
      EarningsItem(
        id = "er_5",
        ticker = "PANW",
        company = "Palo Alto Networks",
        sector = "Technology",
        marketCapTier = MarketCapTier.LARGE,
        isMajorCompany = true,
        session = EarningsSession.AFTER_CLOSE,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "16:05 ET",
        expectedEps = "$1.48",
        expectedRevenue = "$2.12B",
      ),
      EarningsItem(
        id = "er_6",
        ticker = "SNOW",
        company = "Snowflake Inc.",
        sector = "Technology",
        marketCapTier = MarketCapTier.LARGE,
        isMajorCompany = false,
        session = EarningsSession.AFTER_CLOSE,
        dateWindow = CalendarWindow.TODAY,
        timeLabel = "16:05 ET",
        expectedEps = "$0.15",
        expectedRevenue = "$897M",
      ),
      EarningsItem(
        id = "er_7",
        ticker = "TGT",
        company = "Target Corporation",
        sector = "Consumer Staples",
        marketCapTier = MarketCapTier.LARGE,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.TOMORROW,
        timeLabel = "06:30 ET",
        expectedEps = "$2.30",
        expectedRevenue = "$25.9B",
      ),
      EarningsItem(
        id = "er_8",
        ticker = "INTU",
        company = "Intuit Inc.",
        sector = "Technology",
        marketCapTier = MarketCapTier.MEGA,
        isMajorCompany = true,
        session = EarningsSession.AFTER_CLOSE,
        dateWindow = CalendarWindow.TOMORROW,
        timeLabel = "16:00 ET",
        expectedEps = "$2.35",
        expectedRevenue = "$3.14B",
      ),
      EarningsItem(
        id = "er_9",
        ticker = "XOM",
        company = "Exxon Mobil Corp.",
        sector = "Energy",
        marketCapTier = MarketCapTier.MEGA,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.THIS_WEEK,
        timeLabel = "Fri BMO",
        expectedEps = "$1.88",
        expectedRevenue = "$86.4B",
      ),
      EarningsItem(
        id = "er_10",
        ticker = "JPM",
        company = "JPMorgan Chase & Co.",
        sector = "Financials",
        marketCapTier = MarketCapTier.MEGA,
        isMajorCompany = true,
        session = EarningsSession.BEFORE_OPEN,
        dateWindow = CalendarWindow.THIS_WEEK,
        timeLabel = "Fri BMO",
        expectedEps = "$4.12",
        expectedRevenue = "$42.1B",
      ),
    )

  private fun baselineNews(): List<MarketNewsArticle> =
    listOf(
      MarketNewsArticle(
        id = "n_1",
        title = "S&P 500 and Nasdaq Climb Ahead of CPI Inflation Print and Fed Commentary",
        summary = "Broad mega-cap tech leadership and firmer NYSE breadth lift major U.S. benchmarks as Treasury yields steady near 4.38%.",
        source = "Market Wire",
        publishedAgo = "14m ago",
        category = NewsCategory.MARKETS,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_2",
        title = "Small-Cap Russell 2000 Outpaces Large Caps as Equal-Weight Breadth Improves",
        summary = "Cyclicals, industrials, and regional financials catch bid while VIX slips below 16 in orderly midday trading.",
        source = "Wall Street Desk",
        publishedAgo = "26m ago",
        category = NewsCategory.MARKETS,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_3",
        title = "Powell Signals Data-Dependent Path as Markets Price 25 bps Rate Debate",
        summary = "Fed officials emphasize patience on disinflation progress while weekly labor claims hold steady near multi-month lows.",
        source = "Fed Watch",
        publishedAgo = "32m ago",
        category = NewsCategory.FEDERAL_RESERVE,
        sentimentPositive = null,
      ),
      MarketNewsArticle(
        id = "n_4",
        title = "FOMC Minutes Highlight Divergent Views on Neutral Policy Rate Trajectory",
        summary = "Committee members noted balanced risks between sticky services inflation and moderating consumer credit demand.",
        source = "Central Bank Wire",
        publishedAgo = "1h ago",
        category = NewsCategory.FEDERAL_RESERVE,
        sentimentPositive = false,
      ),
      MarketNewsArticle(
        id = "n_5",
        title = "NVIDIA Data Center Demand in Focus Ahead of After-Close Earnings",
        summary = "Options markets imply a sharp post-report move as cloud hyperscalers maintain aggressive AI infrastructure capex targets.",
        source = "Earnings Beat",
        publishedAgo = "49m ago",
        category = NewsCategory.EARNINGS,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_6",
        title = "Major Retailers Lift Full-Year Margin Guidance Following Resilient Traffic",
        summary = "Inventory discipline and lower freight costs offset cautious discretionary commentary across quarterly corporate calls.",
        source = "Corporate Wire",
        publishedAgo = "2h ago",
        category = NewsCategory.EARNINGS,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_7",
        title = "Core PPI and Retail Sales Preview: What Economists Are Watching at 10:00 ET",
        summary = "Consensus looks for benign goods inflation while services pricing stays resilient across consumer categories.",
        source = "Macro Brief",
        publishedAgo = "1h ago",
        category = NewsCategory.ECONOMICS,
        sentimentPositive = null,
      ),
      MarketNewsArticle(
        id = "n_8",
        title = "U.S. Manufacturing PMI Edges Into Expansion Territory on New Order Growth",
        summary = "Export demand stabilized in the latest survey while supplier delivery times and input price subindexes eased.",
        source = "Econ Pulse",
        publishedAgo = "3h ago",
        category = NewsCategory.ECONOMICS,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_9",
        title = "Bitcoin Holds Above $91K as Spot ETF Inflows Extend Five-Day Streak",
        summary = "Institutional derivatives open interest climbs while ETH and SOL track broader risk-on appetite across digital assets.",
        source = "Digital Assets",
        publishedAgo = "52m ago",
        category = NewsCategory.CRYPTO,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_10",
        title = "Ethereum Staking Yields & Layer-2 Settlement Volumes Hit Quarterly Highs",
        summary = "On-chain fee compression continues to drive higher daily transaction throughput across major decentralized ecosystems.",
        source = "Crypto Desk",
        publishedAgo = "3h ago",
        category = NewsCategory.CRYPTO,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_11",
        title = "Semiconductors Lead Technology Sector Higher as Cloud Software Rebounds",
        summary = "XLK outperforms the broader tape by 75 bps with breadth expanding across mid-cap hardware and enterprise AI software.",
        source = "Silicon Wire",
        publishedAgo = "2h ago",
        category = NewsCategory.TECHNOLOGY,
        sentimentPositive = true,
      ),
      MarketNewsArticle(
        id = "n_12",
        title = "WTI Crude Retreats Toward $69 After Supply Forecast Revision",
        summary = "Energy equities lag slightly ahead of weekly EIA inventory figures as global demand projections remain mixed.",
        source = "Commodities Desk",
        publishedAgo = "4h ago",
        category = NewsCategory.ENERGY,
        sentimentPositive = false,
      ),
    )

  private fun formatVolume(vol: Long): String =
    when {
      vol <= 0L -> "—"
      vol >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", vol / 1_000_000_000.0)
      vol >= 1_000_000L -> String.format(Locale.US, "%.1fM", vol / 1_000_000.0)
      vol >= 1_000L -> String.format(Locale.US, "%.1fK", vol / 1_000.0)
      else -> vol.toString()
    }

  private data class SectorMeta(
    val ticker: String,
    val name: String,
    val weightPercent: Double,
    val leaders: String,
  )

  private companion object {
    const val TAG = "MarketDataRepo"
    val BASELINE_PRICES =
      mapOf(
        "^GSPC" to Triple(5984.25, 0.58, "S&P 500"),
        "^IXIC" to Triple(19218.40, 0.94, "Nasdaq Composite"),
        "^DJI" to Triple(43892.10, 0.31, "Dow Jones"),
        "^RUT" to Triple(2364.80, 0.76, "Russell 2000"),
        "^VIX" to Triple(14.82, -4.25, "CBOE Volatility"),
        "ES=F" to Triple(6012.50, 0.44, "S&P 500 Futures"),
        "NQ=F" to Triple(21084.25, 0.72, "Nasdaq 100 Futures"),
        "YM=F" to Triple(44015.00, 0.24, "Dow Futures"),
        "RTY=F" to Triple(2378.40, 0.61, "Russell 2000 Futures"),
        "VX=F" to Triple(15.45, -2.83, "VIX Futures"),
        "SPY" to Triple(597.40, 0.59, "SPDR S&P 500 ETF Trust"),
        "QQQ" to Triple(511.20, 0.91, "Invesco QQQ Trust"),
        "NVDA" to Triple(146.75, 2.34, "NVIDIA Corporation"),
        "AAPL" to Triple(228.40, 0.68, "Apple Inc."),
        "MSFT" to Triple(426.15, 0.85, "Microsoft Corporation"),
        "BTC-USD" to Triple(91420.00, 1.84, "Bitcoin USD"),
        "ETH-USD" to Triple(3185.50, 1.42, "Ethereum USD"),
        "GC=F" to Triple(2594.30, 0.46, "Gold Futures"),
        "CL=F" to Triple(68.92, -0.84, "Crude Oil WTI Futures"),
        "TLT" to Triple(91.35, 0.22, "iShares 20+ Year Treasury Bond"),
        "XLK" to Triple(234.80, 1.24, "Technology"),
        "XLF" to Triple(49.62, 0.78, "Financials"),
        "XLV" to Triple(148.15, -0.24, "Health Care"),
        "XLY" to Triple(214.50, 0.95, "Cons. Discret."),
        "XLC" to Triple(98.40, 1.08, "Comm. Services"),
        "XLI" to Triple(141.20, 0.52, "Industrials"),
        "XLP" to Triple(81.10, -0.18, "Cons. Staples"),
        "XLE" to Triple(92.45, -0.76, "Energy"),
        "XLU" to Triple(79.30, 0.14, "Utilities"),
        "XLRE" to Triple(42.85, 0.36, "Real Estate"),
        "XLB" to Triple(93.10, -0.12, "Materials"),
      )

    val SEARCH_CATALOG =
      listOf(
        TickerSearchResult("AAPL", "Apple Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("MSFT", "Microsoft Corporation", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("NVDA", "NVIDIA Corporation", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("AMZN", "Amazon.com Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("GOOGL", "Alphabet Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("META", "Meta Platforms Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("TSLA", "Tesla Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("AMD", "Advanced Micro Devices", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("AVGO", "Broadcom Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("NFLX", "Netflix Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("PLTR", "Palantir Technologies", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("COIN", "Coinbase Global Inc.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("BRK-B", "Berkshire Hathaway Inc.", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("JPM", "JPMorgan Chase & Co.", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("V", "Visa Inc.", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("LLY", "Eli Lilly and Company", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("UNH", "UnitedHealth Group", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("XOM", "Exxon Mobil Corp.", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("COST", "Costco Wholesale Corp.", AssetClass.STOCK, "NASDAQ"),
        TickerSearchResult("WMT", "Walmart Inc.", AssetClass.STOCK, "NYSE"),
        TickerSearchResult("SPY", "SPDR S&P 500 ETF Trust", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("QQQ", "Invesco QQQ Trust", AssetClass.ETF, "NASDAQ"),
        TickerSearchResult("IWM", "iShares Russell 2000 ETF", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("DIA", "SPDR Dow Jones Industrial ETF", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("VTI", "Vanguard Total Stock Market ETF", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("GLD", "SPDR Gold Shares", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("TLT", "iShares 20+ Year Treasury Bond", AssetClass.ETF, "NASDAQ"),
        TickerSearchResult("XLK", "Technology Select Sector SPDR", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("XLF", "Financial Select Sector SPDR", AssetClass.ETF, "NYSE Arca"),
        TickerSearchResult("BTC-USD", "Bitcoin USD", AssetClass.CRYPTO, "Crypto"),
        TickerSearchResult("ETH-USD", "Ethereum USD", AssetClass.CRYPTO, "Crypto"),
        TickerSearchResult("SOL-USD", "Solana USD", AssetClass.CRYPTO, "Crypto"),
        TickerSearchResult("GC=F", "Gold Futures", AssetClass.COMMODITY, "COMEX"),
        TickerSearchResult("SI=F", "Silver Futures", AssetClass.COMMODITY, "COMEX"),
        TickerSearchResult("CL=F", "Crude Oil WTI Futures", AssetClass.COMMODITY, "NYMEX"),
        TickerSearchResult("^GSPC", "S&P 500 Index", AssetClass.INDEX, "SNP"),
        TickerSearchResult("^IXIC", "Nasdaq Composite", AssetClass.INDEX, "NASDAQ"),
        TickerSearchResult("^DJI", "Dow Jones Industrial Average", AssetClass.INDEX, "DJI"),
        TickerSearchResult("^RUT", "Russell 2000 Index", AssetClass.INDEX, "RUSSELL"),
        TickerSearchResult("^VIX", "CBOE Volatility Index", AssetClass.INDEX, "CBOE"),
      )
  }
}
