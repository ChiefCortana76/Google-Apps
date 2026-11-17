package com.example.data.model

enum class ChartTimeframe(
  val label: String,
  val rangeParam: String,
  val intervalParam: String,
  val periodTitle: String,
) {
  D1("1D", "1d", "5m", "Today"),
  W1("1W", "5d", "30m", "Past 1 Week"),
  M1("1M", "1mo", "1d", "Past 1 Month"),
  Y1("1Y", "1y", "1wk", "Past 1 Year"),
}

enum class QuoteCategory {
  INDEX,
  FUTURE,
  WATCHLIST,
}

data class MarketQuote(
  val symbol: String,
  val displaySymbol: String,
  val name: String,
  val price: Double,
  val change: Double,
  val changePercent: Double,
  val previousClose: Double,
  val dayHigh: Double,
  val dayLow: Double,
  val open: Double,
  val volumeLabel: String,
  val sparkline: List<Double>,
  val category: QuoteCategory,
  val isLiveFeed: Boolean = false,
) {
  val isPositive: Boolean
    get() = change >= 0.0
}

enum class EventImportance(val label: String) {
  LOW("Low"),
  MODERATE("Moderate"),
  HIGH("High"),
}

enum class EconomicRegion(val label: String) {
  ALL("All Regions"),
  US("US"),
  EUROPE("Europe"),
  ASIA("Asia"),
}

enum class CalendarWindow(val label: String) {
  TODAY("Today"),
  TOMORROW("Tomorrow"),
  THIS_WEEK("This Week"),
  THIS_MONTH("This Month"),
}

data class EconomicEvent(
  val id: String,
  val title: String,
  val country: String,
  val region: EconomicRegion,
  val timeEt: String,
  val dateLabel: String,
  val window: CalendarWindow,
  val importance: EventImportance,
  val forecast: String,
  val previous: String,
  val actual: String?,
  val countdownMinutes: Int?,
)

enum class EarningsSession(val label: String, val shortCode: String) {
  BEFORE_OPEN("Before Market Open", "BMO"),
  AFTER_CLOSE("After Market Close", "AMC"),
}

enum class MarketCapTier(val label: String) {
  ALL("All Caps"),
  MEGA("Mega Cap"),
  LARGE("Large Cap"),
  MID("Mid Cap"),
}

enum class EarningsFilterStatus(val label: String) {
  ALL("All"),
  REPORTED("Reported"),
  UPCOMING("Upcoming"),
  SURPRISE("Surprise"),
}

data class EarningsItem(
  val id: String,
  val ticker: String,
  val company: String,
  val sector: String,
  val marketCapTier: MarketCapTier,
  val isMajorCompany: Boolean,
  val session: EarningsSession,
  val dateWindow: CalendarWindow,
  val timeLabel: String,
  val expectedEps: String,
  val expectedRevenue: String,
  val actualEps: String? = null,
) {
  val isReported: Boolean
    get() = !actualEps.isNullOrBlank()

  val epsSurprisePercent: Double?
    get() {
      val actual = parseEpsValue(actualEps) ?: return null
      val expected = parseEpsValue(expectedEps) ?: return null
      if (kotlin.math.abs(expected) < 1e-6) return null
      return ((actual - expected) / kotlin.math.abs(expected)) * 100.0
    }

  val isSurprise: Boolean
    get() = isReported && kotlin.math.abs(epsSurprisePercent ?: 0.0) >= 3.0

  private fun parseEpsValue(raw: String?): Double? =
    raw?.replace("$", "")?.replace(",", "")?.trim()?.toDoubleOrNull()
}

enum class NewsCategory(val label: String) {
  ALL("All"),
  MARKETS("Markets"),
  ECONOMICS("Economics"),
  FEDERAL_RESERVE("Fed"),
  EARNINGS("Earnings"),
  CRYPTO("Crypto"),
  TECHNOLOGY("Tech"),
  ENERGY("Energy"),
}

data class MarketNewsArticle(
  val id: String,
  val title: String,
  val summary: String,
  val source: String,
  val publishedAgo: String,
  val category: NewsCategory,
  val url: String = "",
  val sentimentPositive: Boolean? = null,
)

data class SectorPerformance(
  val ticker: String,
  val name: String,
  val weightPercent: Double,
  val changePercent: Double,
  val leaders: String,
)

data class FearGreedDriver(
  val name: String,
  val score: Int,
  val rating: String,
)

data class MarketBreadthSnapshot(
  val advancingCount: Int,
  val decliningCount: Int,
  val unchangedCount: Int,
  val advanceDeclineRatio: Double,
  val newHighs52W: Int,
  val newLows52W: Int,
  val putCallEquityRatio: Double,
  val putCallTotalRatio: Double,
  val fearGreedScore: Int,
  val fearGreedLabel: String,
  val fearGreedDrivers: List<FearGreedDriver>,
  val nyseUpVolumeBillions: Double,
  val nyseDownVolumeBillions: Double,
  val trinArmsIndex: Double,
  val mcclellanOscillator: Double,
  val pctAbove50Dma: Int,
  val pctAbove200Dma: Int,
  val adLineSeries: List<Double>,
  val sectors: List<SectorPerformance>,
)

data class MarketCommandSnapshot(
  val indices: List<MarketQuote>,
  val futures: List<MarketQuote>,
  val watchlistQuotes: Map<String, MarketQuote>,
  val economicEvents: List<EconomicEvent>,
  val earnings: List<EarningsItem>,
  val news: List<MarketNewsArticle>,
  val breadth: MarketBreadthSnapshot,
  val lastUpdatedLabel: String,
  val anyLiveFeedConnected: Boolean,
  val economicDateLabel: String = "",
  val economicLiveFeed: Boolean = false,
)
