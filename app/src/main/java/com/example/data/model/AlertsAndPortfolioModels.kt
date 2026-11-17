package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AlertCondition(val wireValue: String, val label: String, val symbolText: String) {
  ABOVE("ABOVE", "Rises Above", "≥"),
  BELOW("BELOW", "Drops Below", "≤");

  companion object {
    fun fromWire(value: String): AlertCondition =
      entries.firstOrNull { it.wireValue == value } ?: ABOVE
  }
}

@IgnoreExtraProperties
data class PriceAlert(
  @DocumentId val id: String = "",
  val userId: String = "",
  val symbol: String = "",
  val displayName: String = "",
  val condition: String = AlertCondition.ABOVE.wireValue,
  val targetPrice: Double = 0.0,
  val enabled: Boolean = true,
  val triggered: Boolean = false,
  val lastTriggeredPrice: Double? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PortfolioHolding(
  @DocumentId val id: String = "",
  val userId: String = "",
  val symbol: String = "",
  val displayName: String = "",
  val assetClass: String = AssetClass.STOCK.wireValue,
  val shares: Double = 0.0,
  val avgCostBasis: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class PortfolioPositionSummary(
  val holding: PortfolioHolding,
  val quote: MarketQuote,
  val marketValue: Double,
  val costBasis: Double,
  val dayChangeDollar: Double,
  val dayChangePercent: Double,
  val totalReturnDollar: Double,
  val totalReturnPercent: Double,
  val weightPercent: Double,
)

data class BenchmarkComparison(
  val name: String,
  val symbol: String,
  val indexChangePercent: Double,
  val portfolioAlphaPercent: Double,
)

data class PortfolioSummary(
  val totalValue: Double,
  val totalCost: Double,
  val dayChangeDollar: Double,
  val dayChangePercent: Double,
  val totalGainDollar: Double,
  val totalGainPercent: Double,
  val positions: List<PortfolioPositionSummary>,
  val benchmarks: List<BenchmarkComparison>,
)

data class MarketAiBriefing(
  val headline: String,
  val sentimentTag: String,
  val executiveSummary: String,
  val keyDrivers: List<String>,
  val watchNext: List<String>,
  val generatedAtLabel: String,
)
