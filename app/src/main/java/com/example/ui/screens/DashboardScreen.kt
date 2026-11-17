package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.RadioPlayerState
import com.example.data.model.CalendarWindow
import com.example.data.model.EarningsItem
import com.example.data.model.EarningsSession
import com.example.data.model.EconomicEvent
import com.example.data.model.EventImportance
import com.example.data.model.MarketAiBriefing
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.model.PortfolioSummary
import com.example.data.repository.CnbcRegion
import com.example.ui.components.MarketSwipeRefresh
import com.example.ui.components.MiniSparklineChart
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun DashboardScreen(
  snapshot: MarketCommandSnapshot,
  radioState: RadioPlayerState,
  onSelectRadioRegion: (CnbcRegion) -> Unit,
  onOpenRadioScreen: () -> Unit,
  onOpenQuote: (MarketQuote) -> Unit,
  onOpenAiAssistant: () -> Unit,
  onOpenBreadthTab: () -> Unit,
  onOpenCalendarTab: () -> Unit,
  briefingState: UiState<MarketAiBriefing>? = null,
  portfolioSummary: PortfolioSummary? = null,
  armedAlertsCount: Int = 0,
  onGenerateBriefing: () -> Unit = {},
  onOpenPortfolio: () -> Unit = {},
  onOpenAlerts: () -> Unit = {},
  isRefreshing: Boolean = false,
  onRefresh: () -> Unit = {},
) {
  val marketColors = LocalMarketColors.current
  val todayEvents = snapshot.economicEvents.filter { it.window == CalendarWindow.TODAY }.take(8)
  val bmoEarnings =
    snapshot.earnings.filter {
      it.dateWindow == CalendarWindow.TODAY && it.session == EarningsSession.BEFORE_OPEN
    }
  val amcEarnings =
    snapshot.earnings.filter {
      it.dateWindow == CalendarWindow.TODAY && it.session == EarningsSession.AFTER_CLOSE
    }
  val tickerGridQuotes = snapshot.watchlistQuotes.values.take(8)

  MarketSwipeRefresh(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    testTag = "dashboard_swipe_refresh",
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val gridColumns = if (maxWidth >= 600.dp) 3 else 2

    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("dashboard_list"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Quick Command Bar (Breadth Pulse + AI Brief)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenBreadthTab)
                .testTag("dashboard_breadth_pill"),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(10.dp)
                    .clip(CircleShape)
                    .background(
                      if (snapshot.breadth.fearGreedScore >= 50) marketColors.gain
                      else marketColors.loss
                    )
              )
              Column {
                Text(
                  text = "Fear & Greed: ${snapshot.breadth.fearGreedScore}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "${snapshot.breadth.fearGreedLabel} • A/D ${String.format(Locale.US, "%.2f", snapshot.breadth.advanceDeclineRatio)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          Surface(
            modifier =
              Modifier.clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenAiAssistant)
                .testTag("dashboard_ai_button"),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "AI Brief",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      // CNBC Live Radio Strip (USA • Europe • Asia)
      item {
        RadioDashboardStrip(
          radioState = radioState,
          onSelectRegion = onSelectRadioRegion,
          onOpenRadioScreen = onOpenRadioScreen,
        )
      }

      // AI Briefing & Portfolio / Price Alerts Overview
      item {
        DashboardAiAndPortfolioSection(
          briefingState = briefingState,
          portfolioSummary = portfolioSummary,
          armedAlertsCount = armedAlertsCount,
          onGenerateBriefing = onGenerateBriefing,
          onOpenAiAssistant = onOpenAiAssistant,
          onOpenPortfolio = onOpenPortfolio,
          onOpenAlerts = onOpenAlerts,
        )
      }

      // Top Market Indices Summary (Grid Layout)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionHeader(title = "Top Market Indices", badge = "SUMMARY")
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Box(
              modifier =
                Modifier.size(7.dp)
                  .clip(CircleShape)
                  .background(
                    if (snapshot.anyLiveFeedConnected) marketColors.gain
                    else marketColors.amber
                  )
            )
            Text(
              text = snapshot.lastUpdatedLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        QuoteGrid(
          quotes = snapshot.indices,
          columns = gridColumns,
          testTagPrefix = "snapshot_card",
          onOpenQuote = onOpenQuote,
        )
      }

      // Real-Time Ticker Prices (Grid Layout)
      if (tickerGridQuotes.isNotEmpty()) {
        item {
          SectionHeader(
            title = "Real-Time Tickers",
            badge = if (snapshot.anyLiveFeedConnected) "LIVE" else "SNAPSHOT",
          )
          Spacer(modifier = Modifier.height(10.dp))
          QuoteGrid(
            quotes = tickerGridQuotes,
            columns = gridColumns,
            testTagPrefix = "ticker_card",
            onOpenQuote = onOpenQuote,
          )
        }
      }

      // Futures Snapshot (24/7)
      item {
        SectionHeader(title = "Futures Snapshot", badge = "24/7")
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            snapshot.futures.forEachIndexed { idx, quote ->
              FuturesRow(quote = quote, onClick = { onOpenQuote(quote) })
              if (idx < snapshot.futures.lastIndex) {
                HorizontalDivider(
                  modifier = Modifier.padding(horizontal = 16.dp),
                  color = MaterialTheme.colorScheme.outlineVariant,
                )
              }
            }
          }
        }
      }

      // Today's Economic Events
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionHeader(title = "Today's Events", badge = "${todayEvents.size} RELEASES")
          Text(
            text = "Full Calendar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenCalendarTab)
                .padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            todayEvents.forEachIndexed { idx, event ->
              EconomicEventRow(event = event)
              if (idx < todayEvents.lastIndex) {
                HorizontalDivider(
                  modifier = Modifier.padding(horizontal = 16.dp),
                  color = MaterialTheme.colorScheme.outlineVariant,
                )
              }
            }
          }
        }
      }

      // Earnings Today (BMO & AMC)
      item {
        SectionHeader(title = "Earnings Today", badge = "BMO & AMC")
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (bmoEarnings.isNotEmpty()) {
            EarningsSessionCard(title = "Before Market Open", items = bmoEarnings)
          }
          if (amcEarnings.isNotEmpty()) {
            EarningsSessionCard(title = "After Market Close", items = amcEarnings)
          }
          }
        }
      }
    }
  }
}

@Composable
private fun QuoteGrid(
  quotes: List<MarketQuote>,
  columns: Int,
  testTagPrefix: String,
  onOpenQuote: (MarketQuote) -> Unit,
) {
  val rows = quotes.chunked(columns)
  Column(
    modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_grid"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    rows.forEach { rowQuotes ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        rowQuotes.forEach { quote ->
          MarketSnapshotCard(
            quote = quote,
            onClick = { onOpenQuote(quote) },
            modifier = Modifier.weight(1f),
            cardTag = "${testTagPrefix}_${quote.displaySymbol}",
          )
        }
        repeat((columns - rowQuotes.size).coerceAtLeast(0)) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
fun SectionHeader(title: String, badge: String? = null) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (badge != null) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(999.dp),
      ) {
        Text(
          text = badge,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }
  }
}

@Composable
fun MarketSnapshotCard(
  quote: MarketQuote,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  cardTag: String = "snapshot_card_${quote.displaySymbol}",
) {
  val marketColors = LocalMarketColors.current
  val accentColor = if (quote.isPositive) marketColors.gain else marketColors.loss
  val pillBg = if (quote.isPositive) marketColors.gainSurface else marketColors.lossSurface

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onClick)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
        .testTag(cardTag),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = quote.displaySymbol,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(pillBg)
              .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
          Text(
            text = String.format(Locale.US, "%+.2f%%", quote.changePercent),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
          )
        }
      }

      Text(
        text = quote.name,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      MiniSparklineChart(
        points = quote.sparkline,
        isPositive = quote.isPositive,
        modifier = Modifier.fillMaxWidth().height(38.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Text(
          text = String.format(Locale.US, "%,.2f", quote.price),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        Text(
          text = String.format(Locale.US, "%+.2f", quote.change),
          style = MaterialTheme.typography.labelMedium,
          color = accentColor,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun FuturesRow(
  quote: MarketQuote,
  onClick: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val accentColor = if (quote.isPositive) marketColors.gain else marketColors.loss
  val pillBg = if (quote.isPositive) marketColors.gainSurface else marketColors.lossSurface

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag("futures_row_${quote.displaySymbol}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1.2f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = quote.displaySymbol,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Icon(
          imageVector = Icons.Rounded.Bolt,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
        )
      }
      Text(
        text = quote.name,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    MiniSparklineChart(
      points = quote.sparkline,
      isPositive = quote.isPositive,
      modifier = Modifier.width(68.dp).height(28.dp),
    )

    Spacer(modifier = Modifier.width(10.dp))

    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = String.format(Locale.US, "%,.2f", quote.price),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(pillBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = String.format(Locale.US, "%+.2f (%+.2f%%)", quote.change, quote.changePercent),
          style = MaterialTheme.typography.labelSmall,
          color = accentColor,
        )
      }
    }
  }
}

@Composable
fun EconomicEventRow(event: EconomicEvent) {
  val marketColors = LocalMarketColors.current
  val impColor =
    when (event.importance) {
      EventImportance.HIGH -> marketColors.loss
      EventImportance.MODERATE -> marketColors.amber
      EventImportance.LOW -> marketColors.gain
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
      ) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(impColor))
        Text(
          text = event.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Schedule,
          contentDescription = null,
          modifier = Modifier.size(13.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = event.timeEt,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      MetricMini(label = "Forecast", value = event.forecast)
      MetricMini(label = "Previous", value = event.previous)
      MetricMini(
        label = "Actual",
        value = event.actual ?: "Pending",
        highlightColor = if (event.actual != null) marketColors.gain else null,
      )
    }
  }
}

@Composable
private fun MetricMini(
  label: String,
  value: String,
  highlightColor: Color? = null,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "$label:",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun EarningsSessionCard(
  title: String,
  items: List<EarningsItem>,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.height(10.dp))
      items.forEachIndexed { idx, item ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = item.ticker,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = item.timeLabel,
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = item.company,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Est. EPS ${item.expectedEps}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Rev ${item.expectedRevenue}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (idx < items.lastIndex) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }
  }
}
