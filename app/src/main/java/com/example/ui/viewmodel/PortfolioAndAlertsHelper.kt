package com.example.ui.viewmodel

import android.util.Log
import com.example.data.model.AlertCondition
import com.example.data.model.BenchmarkComparison
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.model.PortfolioHolding
import com.example.data.model.PortfolioPositionSummary
import com.example.data.model.PortfolioSummary
import com.example.data.model.PriceAlert
import com.example.data.model.QuoteCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Flow<T>.asFirestoreUiState(tag: String, fallbackError: String): Flow<UiState<T>> =
  map<T, UiState<T>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError =
        generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(tag, fallbackError, rootError)
      emit(UiState.Error(rootError.message ?: fallbackError))
    }

fun computePortfolioSummary(
  holdings: List<PortfolioHolding>,
  snapshot: MarketCommandSnapshot?,
): PortfolioSummary {
  if (holdings.isEmpty()) {
    return PortfolioSummary(
      totalValue = 0.0,
      totalCost = 0.0,
      dayChangeDollar = 0.0,
      dayChangePercent = 0.0,
      totalGainDollar = 0.0,
      totalGainPercent = 0.0,
      positions = emptyList(),
      benchmarks = emptyList(),
    )
  }

  val allQuotes =
    snapshot?.watchlistQuotes.orEmpty() +
      snapshot?.indices.orEmpty().associateBy { it.symbol } +
      snapshot?.futures.orEmpty().associateBy { it.symbol }

  val rawPositions =
    holdings.map { holding ->
      val key = holding.symbol.uppercase()
      val quote =
        allQuotes[key]
          ?: MarketQuote(
            symbol = key,
            displaySymbol = key,
            name = holding.displayName,
            price = holding.avgCostBasis,
            change = 0.0,
            changePercent = 0.0,
            previousClose = holding.avgCostBasis,
            dayHigh = holding.avgCostBasis,
            dayLow = holding.avgCostBasis,
            open = holding.avgCostBasis,
            volumeLabel = "—",
            sparkline = listOf(holding.avgCostBasis, holding.avgCostBasis),
            category = QuoteCategory.WATCHLIST,
          )
      val marketValue = holding.shares * quote.price
      val costBasis = holding.shares * holding.avgCostBasis
      val dayChangeDollar = holding.shares * quote.change
      val totalReturnDollar = marketValue - costBasis
      val totalReturnPercent =
        if (costBasis > 0.0) (totalReturnDollar / costBasis) * 100.0 else 0.0
      Triple(holding, quote, Triple(marketValue, costBasis, dayChangeDollar to (totalReturnDollar to totalReturnPercent)))
    }

  val totalValue = rawPositions.sumOf { it.third.first }
  val totalCost = rawPositions.sumOf { it.third.second }
  val dayChangeDollar = rawPositions.sumOf { it.third.third.first }
  val prevTotalValue = (totalValue - dayChangeDollar).coerceAtLeast(0.0)
  val dayChangePercent =
    if (prevTotalValue > 0.0) (dayChangeDollar / prevTotalValue) * 100.0 else 0.0
  val totalGainDollar = totalValue - totalCost
  val totalGainPercent = if (totalCost > 0.0) (totalGainDollar / totalCost) * 100.0 else 0.0

  val positions =
    rawPositions.map { (holding, quote, metrics) ->
      val marketValue = metrics.first
      val costBasis = metrics.second
      val posDayDollar = metrics.third.first
      val (posTotalDollar, posTotalPct) = metrics.third.second
      val weight = if (totalValue > 0.0) (marketValue / totalValue) * 100.0 else 0.0
      PortfolioPositionSummary(
        holding = holding,
        quote = quote,
        marketValue = marketValue,
        costBasis = costBasis,
        dayChangeDollar = posDayDollar,
        dayChangePercent = quote.changePercent,
        totalReturnDollar = posTotalDollar,
        totalReturnPercent = posTotalPct,
        weightPercent = weight,
      )
    }.sortedByDescending { it.marketValue }

  val targetBenchmarks =
    listOf(
      "SPX" to "S&P 500",
      "NDX" to "Nasdaq",
      "DOW" to "Dow Jones",
    )
  val benchmarks =
    targetBenchmarks.mapNotNull { (sym, name) ->
      val indexQuote =
        snapshot?.indices?.firstOrNull { it.displaySymbol.equals(sym, ignoreCase = true) }
          ?: return@mapNotNull null
      BenchmarkComparison(
        name = name,
        symbol = sym,
        indexChangePercent = indexQuote.changePercent,
        portfolioAlphaPercent = dayChangePercent - indexQuote.changePercent,
      )
    }

  return PortfolioSummary(
    totalValue = totalValue,
    totalCost = totalCost,
    dayChangeDollar = dayChangeDollar,
    dayChangePercent = dayChangePercent,
    totalGainDollar = totalGainDollar,
    totalGainPercent = totalGainPercent,
    positions = positions,
    benchmarks = benchmarks,
  )
}

fun evaluateTriggeredAlerts(
  alerts: List<PriceAlert>,
  snapshot: MarketCommandSnapshot,
): List<Pair<PriceAlert, Double>> {
  val quotesBySymbol =
    snapshot.watchlistQuotes +
      snapshot.indices.associateBy { it.symbol.uppercase() } +
      snapshot.indices.associateBy { it.displaySymbol.uppercase() } +
      snapshot.futures.associateBy { it.symbol.uppercase() }

  return alerts.mapNotNull { alert ->
    if (!alert.enabled || alert.triggered) return@mapNotNull null
    val quote = quotesBySymbol[alert.symbol.uppercase()] ?: return@mapNotNull null
    val condition = AlertCondition.fromWire(alert.condition)
    val hit =
      when (condition) {
        AlertCondition.ABOVE -> quote.price >= alert.targetPrice
        AlertCondition.BELOW -> quote.price <= alert.targetPrice
      }
    if (hit) alert to quote.price else null
  }
}
