package com.example.data.repository

import android.util.Log
import com.example.data.model.CalendarWindow
import com.example.data.model.EconomicEvent
import com.example.data.model.EconomicRegion
import com.example.data.model.EventImportance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class EconomicCalendarFetchResult(
  val events: List<EconomicEvent>,
  val isLiveNetworkData: Boolean,
  val scheduleDateEt: LocalDate,
)

private data class RawEconomicEvent(
  val title: String,
  val country: String,
  val region: EconomicRegion,
  val scheduledAtEt: ZonedDateTime,
  val importance: EventImportance,
  val forecast: String,
  val previous: String,
  val actual: String?,
)

class EconomicCalendarFetcher(
  private val client: OkHttpClient,
  private val etZone: ZoneId = ZoneId.of("America/New_York"),
) {
  private val fetchMutex = Mutex()
  private val timeLabelFmt = DateTimeFormatter.ofPattern("HH:mm 'ET'", Locale.US)
  private val dayDateFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
  private val shortMonthDayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)

  @Volatile private var cachedDateEt: LocalDate? = null
  @Volatile private var cachedDateLocal: LocalDate? = null
  @Volatile private var cachedAtMs: Long = 0L
  @Volatile private var lastNetworkAttemptMs: Long = 0L
  @Volatile private var cachedEvents: List<RawEconomicEvent> = emptyList()
  @Volatile private var cachedFromNetwork: Boolean = false

  suspend fun fetchEvents(
    nowEt: ZonedDateTime,
    nowLocalDate: LocalDate = LocalDate.now(),
    forceRefresh: Boolean = false,
  ): EconomicCalendarFetchResult {
    val todayEt = nowEt.toLocalDate()
    val nowMs = System.currentTimeMillis()

    val isNewDay =
      cachedDateEt != todayEt || cachedDateLocal != nowLocalDate

    if (isNewDay) {
      Log.d(TAG, "New calendar day detected (ET=$todayEt, local=$nowLocalDate); refreshing economic data")
    }

    val needsNetworkFetch =
      isNewDay ||
        cachedEvents.isEmpty() ||
        !cachedFromNetwork ||
        (nowMs - cachedAtMs) >= CACHE_TTL_MS ||
        (forceRefresh && (nowMs - lastNetworkAttemptMs) >= MANUAL_COOLDOWN_MS)

    if (needsNetworkFetch) {
      fetchMutex.withLock {
        val currentMs = System.currentTimeMillis()
        val stillNewDay = cachedDateEt != todayEt || cachedDateLocal != nowLocalDate
        val stillNeedsFetch =
          stillNewDay ||
            cachedEvents.isEmpty() ||
            !cachedFromNetwork ||
            (currentMs - cachedAtMs) >= CACHE_TTL_MS ||
            (forceRefresh && (currentMs - lastNetworkAttemptMs) >= MANUAL_COOLDOWN_MS)

        if (stillNeedsFetch) {
          lastNetworkAttemptMs = currentMs
          val allowFairEconomy =
            stillNewDay || !cachedFromNetwork || (currentMs - cachedAtMs) >= FAIRECONOMY_COOLDOWN_MS
          val liveEvents = fetchLiveFromSources(todayEt, allowFairEconomy = allowFairEconomy)
          if (liveEvents.isNotEmpty()) {
            cachedEvents =
              if (allowFairEconomy || !cachedFromNetwork || stillNewDay) {
                liveEvents
              } else {
                mergeEconomicEvents(existing = cachedEvents, incoming = liveEvents)
              }
            cachedFromNetwork = true
            cachedAtMs = currentMs
            cachedDateEt = todayEt
            cachedDateLocal = nowLocalDate
          } else if (stillNewDay || cachedEvents.isEmpty()) {
            cachedEvents = buildDynamicFallbackRawEvents(todayEt)
            cachedFromNetwork = false
            cachedAtMs = currentMs
            cachedDateEt = todayEt
            cachedDateLocal = nowLocalDate
          }
        }
      }
    }

    val projected = projectEventsForNow(cachedEvents, nowEt)
    return EconomicCalendarFetchResult(
      events = projected,
      isLiveNetworkData = cachedFromNetwork,
      scheduleDateEt = todayEt,
    )
  }

  private fun mergeEconomicEvents(
    existing: List<RawEconomicEvent>,
    incoming: List<RawEconomicEvent>,
  ): List<RawEconomicEvent> {
    val byKey = LinkedHashMap<String, RawEconomicEvent>()
    fun keyOf(e: RawEconomicEvent) =
      "${e.scheduledAtEt.toLocalDate()}_${e.title.lowercase(Locale.US)}"
    existing.forEach { byKey[keyOf(it)] = it }
    incoming.forEach { fresh ->
      val k = keyOf(fresh)
      val prev = byKey[k]
      byKey[k] =
        if (prev == null) fresh
        else prev.copy(
          actual = fresh.actual ?: prev.actual,
          forecast = if (fresh.forecast != "—") fresh.forecast else prev.forecast,
          previous = if (fresh.previous != "—") fresh.previous else prev.previous,
        )
    }
    return byKey.values.sortedBy { it.scheduledAtEt.toInstant() }
  }

  private fun fetchLiveFromSources(
    todayEt: LocalDate,
    allowFairEconomy: Boolean,
  ): List<RawEconomicEvent> {
    val collected = mutableListOf<RawEconomicEvent>()
    if (allowFairEconomy) {
      collected.addAll(fetchFairEconomyFeed(THIS_WEEK_URL))
      val hasUpcomingNextWeek =
        todayEt.dayOfWeek >= DayOfWeek.THURSDAY ||
          collected.count { it.scheduledAtEt.toLocalDate() >= todayEt } < 4
      if (hasUpcomingNextWeek) {
        collected.addAll(fetchFairEconomyFeed(NEXT_WEEK_URL))
      }
    }

    // Supplement / fallback with Nasdaq daily economic calendar for today & tomorrow
    if (!allowFairEconomy || collected.none { it.scheduledAtEt.toLocalDate() == todayEt } || collected.isEmpty()) {
      collected.addAll(fetchNasdaqEconomicEvents(todayEt))
      collected.addAll(fetchNasdaqEconomicEvents(todayEt.plusDays(1)))
    }

    return collected
      .distinctBy { "${it.scheduledAtEt.toLocalDate()}_${it.scheduledAtEt.toLocalTime()}_${it.title.lowercase(Locale.US)}" }
      .sortedBy { it.scheduledAtEt.toInstant() }
  }

  private fun fetchFairEconomyFeed(url: String): List<RawEconomicEvent> {
    try {
      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", USER_AGENT)
          .header("Accept", "application/json")
          .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string()?.trim() ?: return emptyList()
        if (!body.startsWith("[")) return emptyList()
        val array = JSONArray(body)
        val out = mutableListOf<RawEconomicEvent>()
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          parseFairEconomyItem(obj)?.let { out.add(it) }
        }
        return out
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "FairEconomy calendar fetch skipped ($url): ${e.message}")
      return emptyList()
    }
  }

  private fun parseFairEconomyItem(obj: JSONObject): RawEconomicEvent? {
    val title = obj.optString("title").trim()
    val dateStr = obj.optString("date").trim()
    val code = obj.optString("country").trim().uppercase(Locale.US)
    if (title.isEmpty() || dateStr.isEmpty()) return null

    val impactRaw = obj.optString("impact").trim().lowercase(Locale.US)
    if (impactRaw == "holiday") return null

    val zonedEt =
      runCatching { OffsetDateTime.parse(dateStr).atZoneSameInstant(etZone) }.getOrNull()
        ?: return null

    val importance =
      when (impactRaw) {
        "high" -> EventImportance.HIGH
        "medium" -> EventImportance.MODERATE
        else -> EventImportance.LOW
      }

    val (countryName, region) = mapCountryAndRegion(code)
    val forecast = obj.optString("forecast").trim().ifEmpty { "—" }
    val previous = obj.optString("previous").trim().ifEmpty { "—" }
    val actual = obj.optString("actual").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }

    return RawEconomicEvent(
      title = title,
      country = countryName,
      region = region,
      scheduledAtEt = zonedEt,
      importance = importance,
      forecast = forecast,
      previous = previous,
      actual = actual,
    )
  }

  private fun fetchNasdaqEconomicEvents(date: LocalDate): List<RawEconomicEvent> {
    try {
      val dateParam = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
      val url = "https://api.nasdaq.com/api/calendar/economicevents?date=$dateParam"
      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", USER_AGENT)
          .header("Accept", "application/json, text/plain, */*")
          .header("Origin", "https://www.nasdaq.com")
          .header("Referer", "https://www.nasdaq.com/")
          .build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        val rows =
          JSONObject(body).optJSONObject("data")?.optJSONArray("rows") ?: return emptyList()
        val out = mutableListOf<RawEconomicEvent>()
        for (i in 0 until rows.length()) {
          val row = rows.optJSONObject(i) ?: continue
          val title =
            row.optString("eventName").trim().ifEmpty { row.optString("title").trim() }
          if (title.isEmpty()) continue
          val countryRaw = row.optString("country").trim().ifEmpty { "United States" }
          val (_, region) = mapCountryAndRegion(countryRaw.uppercase(Locale.US))
          val timeRaw = row.optString("time").trim().ifEmpty { row.optString("gmt").trim() }
          val timeEt = parseTimeOrDefault(date, timeRaw)
          val forecast =
            row.optString("consensus").trim().ifEmpty { row.optString("forecast").trim() }.ifEmpty { "—" }
          val previous = row.optString("previous").trim().ifEmpty { "—" }
          val actual = row.optString("actual").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
          val importance = inferImportance(title)

          out.add(
            RawEconomicEvent(
              title = title,
              country = countryRaw,
              region = region,
              scheduledAtEt = timeEt,
              importance = importance,
              forecast = forecast,
              previous = previous,
              actual = actual,
            )
          )
        }
        return out
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "Nasdaq calendar fetch skipped ($date): ${e.message}")
      return emptyList()
    }
  }

  private fun parseTimeOrDefault(date: LocalDate, rawTime: String): ZonedDateTime {
    val clean = rawTime.trim()
    if (clean.contains("T")) {
      runCatching { return OffsetDateTime.parse(clean).atZoneSameInstant(etZone) }
      runCatching { return LocalDateTime.parse(clean).atZone(etZone) }
    }
    val match = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", RegexOption.IGNORE_CASE).find(clean)
    val time =
      if (match != null) {
        var hour = match.groupValues[1].toIntOrNull() ?: 8
        val min = match.groupValues[2].toIntOrNull() ?: 30
        val amPm = match.groupValues[3].uppercase(Locale.US)
        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0
        LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
      } else {
        LocalTime.of(8, 30)
      }
    return ZonedDateTime.of(date, time, etZone)
  }

  private fun inferImportance(title: String): EventImportance {
    val lower = title.lowercase(Locale.US)
    return when {
      listOf("cpi", "fomc", "fed ", "interest rate", "payroll", "nfp", "gdp", "pce", "jobless", "retail sales", "unemployment")
        .any { it in lower } -> EventImportance.HIGH
      listOf("pmi", "ppi", "housing", "confidence", "durable", "trade balance", "ism")
        .any { it in lower } -> EventImportance.MODERATE
      else -> EventImportance.LOW
    }
  }

  private fun mapCountryAndRegion(codeOrName: String): Pair<String, EconomicRegion> {
    return when {
      codeOrName == "USD" || codeOrName == "US" || "UNITED STATES" in codeOrName ->
        "United States" to EconomicRegion.US
      codeOrName == "CAD" || "CANADA" in codeOrName ->
        "Canada" to EconomicRegion.US
      codeOrName == "EUR" || codeOrName == "EU" || "EURO" in codeOrName ->
        "Euro Area" to EconomicRegion.EUROPE
      codeOrName == "GBP" || codeOrName == "UK" || "UNITED KINGDOM" in codeOrName ->
        "United Kingdom" to EconomicRegion.EUROPE
      codeOrName == "CHF" || "SWITZ" in codeOrName ->
        "Switzerland" to EconomicRegion.EUROPE
      "GERMANY" in codeOrName || "FRANCE" in codeOrName ->
        codeOrName.lowercase(Locale.US).replaceFirstChar { it.uppercase() } to EconomicRegion.EUROPE
      codeOrName == "JPY" || "JAPAN" in codeOrName ->
        "Japan" to EconomicRegion.ASIA
      codeOrName == "CNY" || "CHINA" in codeOrName ->
        "China" to EconomicRegion.ASIA
      codeOrName == "AUD" || "AUSTRALIA" in codeOrName ->
        "Australia" to EconomicRegion.ASIA
      codeOrName == "NZD" || "NEW ZEALAND" in codeOrName ->
        "New Zealand" to EconomicRegion.ASIA
      else -> codeOrName.ifEmpty { "Global" } to EconomicRegion.ALL
    }
  }

  private fun projectEventsForNow(
    rawEvents: List<RawEconomicEvent>,
    nowEt: ZonedDateTime,
  ): List<EconomicEvent> {
    val todayEt = nowEt.toLocalDate()
    val tomorrowEt = todayEt.plusDays(1)
    val weekEndEt = todayEt.plusDays(6)

    return rawEvents
      .filter { !it.scheduledAtEt.toLocalDate().isBefore(todayEt) }
      .sortedBy { it.scheduledAtEt.toInstant() }
      .mapIndexed { idx, raw ->
        val eventDate = raw.scheduledAtEt.toLocalDate()
        val window =
          when {
            eventDate == todayEt -> CalendarWindow.TODAY
            eventDate == tomorrowEt -> CalendarWindow.TOMORROW
            !eventDate.isAfter(weekEndEt) -> CalendarWindow.THIS_WEEK
            else -> CalendarWindow.THIS_MONTH
          }
        val dateLabel =
          when (eventDate) {
            todayEt -> "Today (${eventDate.format(shortMonthDayFmt)})"
            tomorrowEt -> "Tomorrow (${eventDate.format(shortMonthDayFmt)})"
            else -> eventDate.format(dayDateFmt)
          }
        val minutesUntil =
          if (raw.scheduledAtEt.isAfter(nowEt)) {
            ChronoUnit.MINUTES.between(nowEt, raw.scheduledAtEt).toInt().takeIf { it in 1..(24 * 60) }
          } else {
            null
          }
        val idHash = abs("${eventDate}_${raw.title}".hashCode())
        EconomicEvent(
          id = "ec_${eventDate}_${idHash}_$idx",
          title = raw.title,
          country = raw.country,
          region = raw.region,
          timeEt = raw.scheduledAtEt.format(timeLabelFmt),
          dateLabel = dateLabel,
          window = window,
          importance = raw.importance,
          forecast = raw.forecast,
          previous = raw.previous,
          actual = raw.actual,
          countdownMinutes = minutesUntil,
        )
      }
  }

  private fun buildDynamicFallbackRawEvents(todayEt: LocalDate): List<RawEconomicEvent> {
    val nextTradingDay =
      when (todayEt.dayOfWeek) {
        DayOfWeek.SATURDAY -> todayEt.plusDays(2)
        DayOfWeek.SUNDAY -> todayEt.plusDays(1)
        else -> todayEt
      }
    val secondTradingDay =
      when (nextTradingDay.plusDays(1).dayOfWeek) {
        DayOfWeek.SATURDAY -> nextTradingDay.plusDays(3)
        DayOfWeek.SUNDAY -> nextTradingDay.plusDays(2)
        else -> nextTradingDay.plusDays(1)
      }
    val thirdTradingDay =
      when (secondTradingDay.plusDays(1).dayOfWeek) {
        DayOfWeek.SATURDAY -> secondTradingDay.plusDays(3)
        DayOfWeek.SUNDAY -> secondTradingDay.plusDays(2)
        else -> secondTradingDay.plusDays(1)
      }

    fun atEt(date: LocalDate, hour: Int, min: Int): ZonedDateTime =
      ZonedDateTime.of(date, LocalTime.of(hour, min), etZone)

    return listOf(
      RawEconomicEvent(
        title = "U.S. Initial Jobless Claims",
        country = "United States",
        region = EconomicRegion.US,
        scheduledAtEt = atEt(nextTradingDay, 8, 30),
        importance = EventImportance.HIGH,
        forecast = "218K",
        previous = "221K",
        actual = null,
      ),
      RawEconomicEvent(
        title = "U.S. CPI YoY (Consumer Price Index)",
        country = "United States",
        region = EconomicRegion.US,
        scheduledAtEt = atEt(nextTradingDay, 10, 0),
        importance = EventImportance.HIGH,
        forecast = "2.6%",
        previous = "2.5%",
        actual = null,
      ),
      RawEconomicEvent(
        title = "EIA Crude Oil Inventories",
        country = "United States",
        region = EconomicRegion.US,
        scheduledAtEt = atEt(nextTradingDay, 10, 30),
        importance = EventImportance.LOW,
        forecast = "-1.2M",
        previous = "1.8M",
        actual = null,
      ),
      RawEconomicEvent(
        title = "FOMC Policy Statement & Treasury Guidance",
        country = "United States",
        region = EconomicRegion.US,
        scheduledAtEt = atEt(nextTradingDay, 14, 0),
        importance = EventImportance.HIGH,
        forecast = "4.25%",
        previous = "4.50%",
        actual = null,
      ),
      RawEconomicEvent(
        title = "Eurozone CPI & GDP Prelim",
        country = "Euro Area",
        region = EconomicRegion.EUROPE,
        scheduledAtEt = atEt(secondTradingDay, 5, 0),
        importance = EventImportance.HIGH,
        forecast = "0.3%",
        previous = "0.2%",
        actual = null,
      ),
      RawEconomicEvent(
        title = "U.S. Retail Sales MoM",
        country = "United States",
        region = EconomicRegion.US,
        scheduledAtEt = atEt(secondTradingDay, 8, 30),
        importance = EventImportance.HIGH,
        forecast = "0.4%",
        previous = "0.2%",
        actual = null,
      ),
      RawEconomicEvent(
        title = "BoJ Policy Rate & Outlook Statement",
        country = "Japan",
        region = EconomicRegion.ASIA,
        scheduledAtEt = atEt(secondTradingDay, 23, 0),
        importance = EventImportance.HIGH,
        forecast = "0.50%",
        previous = "0.50%",
        actual = null,
      ),
      RawEconomicEvent(
        title = "Non-Farm Payrolls (NFP) & Unemployment Rate",
        country = "United States",
        region = EconomicRegion.US,
        scheduledAtEt = atEt(thirdTradingDay, 8, 30),
        importance = EventImportance.HIGH,
        forecast = "175K",
        previous = "164K",
        actual = null,
      ),
      RawEconomicEvent(
        title = "China Industrial Production YoY",
        country = "China",
        region = EconomicRegion.ASIA,
        scheduledAtEt = atEt(todayEt.plusDays(7), 21, 0),
        importance = EventImportance.MODERATE,
        forecast = "5.4%",
        previous = "5.3%",
        actual = null,
      ),
      RawEconomicEvent(
        title = "UK Core CPI YoY",
        country = "United Kingdom",
        region = EconomicRegion.EUROPE,
        scheduledAtEt = atEt(todayEt.plusDays(8), 2, 0),
        importance = EventImportance.MODERATE,
        forecast = "3.1%",
        previous = "3.2%",
        actual = null,
      ),
    )
  }

  private companion object {
    const val TAG = "EconCalFetcher"
    const val USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    const val THIS_WEEK_URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"
    const val NEXT_WEEK_URL = "https://nfs.faireconomy.media/ff_calendar_nextweek.json"
    const val CACHE_TTL_MS = 15 * 60_000L
    const val FAIRECONOMY_COOLDOWN_MS = 5 * 60_000L
    const val MANUAL_COOLDOWN_MS = 45_000L
  }
}
