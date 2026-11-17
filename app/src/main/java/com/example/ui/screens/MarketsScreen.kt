package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssetClass
import com.example.data.model.ChartTimeframe
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.model.Watchlist
import com.example.data.model.WatchlistItem
import com.example.data.repository.ChartSeriesResult
import com.example.data.repository.TickerSearchResult
import com.example.ui.components.MarketSwipeRefresh
import com.example.ui.components.MiniSparklineChart
import com.example.ui.components.SelectWatchlistForTickerDialog
import com.example.ui.components.TickerSearchBarSection
import com.example.ui.components.VicoHistoricalChartCard
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun MarketsScreen(
  snapshot: MarketCommandSnapshot,
  onOpenQuote: (MarketQuote) -> Unit,
  isRefreshing: Boolean = false,
  onRefresh: () -> Unit = {},
  searchQuery: String = "",
  onSearchQueryChange: (String) -> Unit = {},
  onClearSearch: () -> Unit = {},
  searchResults: List<TickerSearchResult> = emptyList(),
  isSearching: Boolean = false,
  watchlists: List<Watchlist> = emptyList(),
  watchlistItems: List<WatchlistItem> = emptyList(),
  onInspectTicker: (TickerSearchResult) -> Unit = {},
  onAddTickerToWatchlist: (String, String, String, AssetClass) -> Unit = { _, _, _, _ -> },
) {
  var tickerPendingWatchlistPick by remember { mutableStateOf<TickerSearchResult?>(null) }
  val allSavedSymbols =
    remember(watchlistItems) { watchlistItems.map { it.symbol.uppercase() }.toSet() }

  Column(modifier = Modifier.fillMaxSize()) {
    TickerSearchBarSection(
      query = searchQuery,
      onQueryChange = onSearchQueryChange,
      onClearQuery = onClearSearch,
      results = searchResults,
      isSearching = isSearching,
      existingSymbolsInTarget = if (watchlists.size <= 1) allSavedSymbols else emptySet(),
      addButtonLabel = "Watchlist",
      onInspectTicker = onInspectTicker,
      onAddTicker = { ticker ->
        if (watchlists.size == 1) {
          val target = watchlists.first()
          onAddTickerToWatchlist(target.id, ticker.symbol, ticker.name, ticker.assetClass)
        } else {
          tickerPendingWatchlistPick = ticker
        }
      },
      searchFieldTag = "markets_search_input",
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )

    MarketSwipeRefresh(
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      testTag = "markets_swipe_refresh",
      modifier = Modifier.weight(1f),
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("markets_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          SectionHeader(title = "Major U.S. Indices", badge = "REAL-TIME")
        }

        items(snapshot.indices, key = { it.symbol }) { quote ->
          MarketFullRowCard(quote = quote, onClick = { onOpenQuote(quote) })
        }
      }
    }
  }

  tickerPendingWatchlistPick?.let { ticker ->
    SelectWatchlistForTickerDialog(
      ticker = ticker,
      watchlists = watchlists,
      watchlistItems = watchlistItems,
      onSelectWatchlist = { wl ->
        onAddTickerToWatchlist(wl.id, ticker.symbol, ticker.name, ticker.assetClass)
      },
      onDismiss = { tickerPendingWatchlistPick = null },
    )
  }
}

@Composable
fun FuturesScreen(
  snapshot: MarketCommandSnapshot,
  onOpenQuote: (MarketQuote) -> Unit,
  isRefreshing: Boolean = false,
  onRefresh: () -> Unit = {},
) {
  MarketSwipeRefresh(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    testTag = "futures_swipe_refresh",
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("futures_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionHeader(title = "Overnight & Globex Futures", badge = "24/7")
      }

      items(snapshot.futures, key = { it.symbol }) { quote ->
        MarketFullRowCard(quote = quote, onClick = { onOpenQuote(quote) })
      }
    }
  }
}

@Composable
private fun MarketFullRowCard(
  quote: MarketQuote,
  onClick: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val accentColor = if (quote.isPositive) marketColors.gain else marketColors.loss
  val pillBg = if (quote.isPositive) marketColors.gainSurface else marketColors.lossSurface

  Card(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onClick)
        .testTag("market_card_${quote.displaySymbol}"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = quote.displaySymbol,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = quote.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = String.format(Locale.US, "%,.2f", quote.price),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(pillBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(
              text = String.format(Locale.US, "%+.2f (%+.2f%%)", quote.change, quote.changePercent),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = accentColor,
            )
          }
        }
      }

      MiniSparklineChart(
        points = quote.sparkline,
        isPositive = quote.isPositive,
        modifier = Modifier.fillMaxWidth().height(54.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        StatTiny("Open", String.format(Locale.US, "%,.2f", quote.open))
        StatTiny("High", String.format(Locale.US, "%,.2f", quote.dayHigh))
        StatTiny("Low", String.format(Locale.US, "%,.2f", quote.dayLow))
        StatTiny("Vol", quote.volumeLabel)
      }
    }
  }
}

@Composable
private fun StatTiny(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
fun QuoteDetailScreen(
  quote: MarketQuote,
  selectedTimeframe: ChartTimeframe,
  chartState: UiState<ChartSeriesResult>,
  onSelectTimeframe: (ChartTimeframe) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val marketColors = LocalMarketColors.current
  val accentColor = if (quote.isPositive) marketColors.gain else marketColors.loss

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .testTag("quote_detail_screen"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          Column {
            Text(
              text = quote.displaySymbol,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = quote.name,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = String.format(Locale.US, "%,.2f", quote.price),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              String.format(
                Locale.US,
                "%+.2f (%+.2f%%) Today",
                quote.change,
                quote.changePercent,
              ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
          )
        }
      }

      item {
        VicoHistoricalChartCard(
          quote = quote,
          selectedTimeframe = selectedTimeframe,
          chartState = chartState,
          onSelectTimeframe = onSelectTimeframe,
        )
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = "Session Statistics",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              StatDetailBlock("Open", String.format(Locale.US, "%,.2f", quote.open))
              StatDetailBlock("Prev Close", String.format(Locale.US, "%,.2f", quote.previousClose))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              StatDetailBlock("Day High", String.format(Locale.US, "%,.2f", quote.dayHigh))
              StatDetailBlock("Day Low", String.format(Locale.US, "%,.2f", quote.dayLow))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              StatDetailBlock("Volume", quote.volumeLabel)
              StatDetailBlock("Symbol", quote.symbol)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatDetailBlock(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
