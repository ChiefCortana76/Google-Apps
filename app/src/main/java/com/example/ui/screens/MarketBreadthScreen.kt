package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MarketBreadthSnapshot
import com.example.data.model.SectorPerformance
import com.example.ui.components.BreadthStatTile
import com.example.ui.components.FearGreedSectionCard
import com.example.ui.components.HorizontalRatioBar
import com.example.ui.components.MarketSwipeRefresh
import com.example.ui.components.NewHighsLowsComparisonCard
import com.example.ui.components.NyseBreadthVolumeCard
import com.example.ui.components.SectorHeatMapGrid
import com.example.ui.components.SectorHeatMapLegend
import com.example.ui.components.SectorHeatMapSort
import com.example.ui.components.VicoAdRatioLineChart
import com.example.ui.components.VicoMiniAdLineChart
import com.example.ui.components.VicoSectorBarChart
import com.example.ui.components.buildAdRatioSeries
import com.example.ui.components.sortSectorsForHeatMap
import com.example.ui.theme.LocalMarketColors
import java.util.Locale

private enum class AdChartMode(val label: String) {
  RATIO("A/D Ratio"),
  CUMULATIVE("A/D Line"),
}

private enum class SectorViewMode(val label: String) {
  HEATMAP("Heat Map"),
  BAR_CHART("Bar Chart"),
}

@Composable
fun MarketBreadthScreen(
  breadth: MarketBreadthSnapshot,
  isRefreshing: Boolean = false,
  onRefresh: () -> Unit = {},
) {
  val marketColors = LocalMarketColors.current
  var selectedSector by remember { mutableStateOf<SectorPerformance?>(null) }
  var adChartMode by remember { mutableStateOf(AdChartMode.RATIO) }
  var sectorViewMode by remember { mutableStateOf(SectorViewMode.HEATMAP) }
  var heatMapSort by remember { mutableStateOf(SectorHeatMapSort.WEIGHT) }

  val sortedSectors =
    remember(breadth.sectors, heatMapSort) {
      sortSectorsForHeatMap(breadth.sectors, heatMapSort)
    }

  val ratioSeries =
    remember(breadth.adLineSeries, breadth.advanceDeclineRatio) {
      buildAdRatioSeries(breadth.adLineSeries, breadth.advanceDeclineRatio)
    }

  MarketSwipeRefresh(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    testTag = "breadth_swipe_refresh",
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val gridColumns = if (maxWidth >= 600.dp) 4 else 2

      LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize().testTag("market_breadth_screen"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // 1. Advance / Decline Ratio (Grid Card)
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("advance_decline_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Column {
                Text(
                  text = "Advance / Decline",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Unch: ${breadth.unchangedCount}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = String.format(Locale.US, "%.2f : 1", breadth.advanceDeclineRatio),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (breadth.advanceDeclineRatio >= 1.0) marketColors.gain else marketColors.loss,
              )
              HorizontalRatioBar(
                leftLabel = "Adv",
                leftValue = "${breadth.advancingCount}",
                rightLabel = "Dec",
                rightValue = "${breadth.decliningCount}",
                leftFraction =
                  breadth.advancingCount.toFloat() /
                    (breadth.advancingCount + breadth.decliningCount).coerceAtLeast(1),
                leftColor = marketColors.gain,
                rightColor = marketColors.loss,
              )
            }
          }
        }

        // 2. New Highs / Lows (Grid Card)
        item {
          val netHl = breadth.newHighs52W - breadth.newLows52W
          Card(
            modifier = Modifier.fillMaxWidth().testTag("new_highs_lows_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Column {
                Text(
                  text = "New Highs / Lows",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "52-Week NYSE",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = String.format(Locale.US, "Net %+d", netHl),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (netHl >= 0) marketColors.gain else marketColors.loss,
              )
              HorizontalRatioBar(
                leftLabel = "Highs",
                leftValue = "${breadth.newHighs52W}",
                rightLabel = "Lows",
                rightValue = "${breadth.newLows52W}",
                leftFraction =
                  breadth.newHighs52W.toFloat() /
                    (breadth.newHighs52W + breadth.newLows52W).coerceAtLeast(1),
                leftColor = marketColors.gain,
                rightColor = marketColors.loss,
              )
            }
          }
        }

        // 3. Put / Call Ratio (Grid Card)
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("put_call_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Put / Call Ratio",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = String.format(Locale.US, "%.2f", breadth.putCallEquityRatio),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color =
                  if (breadth.putCallEquityRatio < 0.70) marketColors.gain
                  else if (breadth.putCallEquityRatio > 0.90) marketColors.loss
                  else marketColors.amber,
              )
              Text(
                text = "Equity P/C • Total ${String.format(Locale.US, "%.2f", breadth.putCallTotalRatio)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  when {
                    breadth.putCallEquityRatio < 0.68 -> "Bullish Call Skew"
                    breadth.putCallEquityRatio > 0.90 -> "Elevated Hedging"
                    else -> "Balanced Positioning"
                  },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        // 4. NYSE A/D Line Mini Vico Chart (Grid Card)
        item {
          val isAdPositive =
            (breadth.adLineSeries.lastOrNull() ?: 0.0) >= (breadth.adLineSeries.firstOrNull() ?: 0.0)
          Card(
            modifier = Modifier.fillMaxWidth().testTag("nyse_ad_line_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "NYSE A/D Line",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              VicoMiniAdLineChart(
                points = breadth.adLineSeries,
                isPositive = isAdPositive,
                modifier = Modifier.fillMaxWidth().height(54.dp),
              )
              Text(
                text =
                  String.format(
                    Locale.US,
                    "McClellan %+.1f • TRIN %.2f",
                    breadth.mcclellanOscillator,
                    breadth.trinArmsIndex,
                  ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // 5. Advance / Decline Ratio Line Chart (Full-Width Vico Chart Span)
        item(span = { GridItemSpan(maxLineSpan) }) {
          val isRatioMode = adChartMode == AdChartMode.RATIO
          val activeSeries = if (isRatioMode) ratioSeries else breadth.adLineSeries
          val isSeriesPositive =
            if (isRatioMode) breadth.advanceDeclineRatio >= 1.0
            else (activeSeries.lastOrNull() ?: 0.0) >= (activeSeries.firstOrNull() ?: 0.0)

          Card(
            modifier = Modifier.fillMaxWidth().testTag("ad_ratio_line_chart_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Advance / Decline Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text =
                      if (isRatioMode) {
                        "Intraday NYSE Advance/Decline Ratio (${String.format(Locale.US, "%.2fx", breadth.advanceDeclineRatio)})"
                      } else {
                        "Cumulative NYSE Net Advancing Issues"
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AdChartMode.entries.forEachIndexed { index, mode ->
                  SegmentedButton(
                    selected = adChartMode == mode,
                    onClick = { adChartMode = mode },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AdChartMode.entries.size),
                    modifier = Modifier.testTag("ad_chart_mode_${mode.name.lowercase(Locale.US)}"),
                  ) {
                    Text(
                      text = mode.label,
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                    )
                  }
                }
              }

              VicoAdRatioLineChart(
                values = activeSeries,
                isRatioMode = isRatioMode,
                isPositive = isSeriesPositive,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                BreadthStatTile(
                  label = "A/D Ratio",
                  value = String.format(Locale.US, "%.2fx", breadth.advanceDeclineRatio),
                  modifier = Modifier.weight(1f),
                )
                BreadthStatTile(
                  label = "McClellan",
                  value = String.format(Locale.US, "%+.1f", breadth.mcclellanOscillator),
                  modifier = Modifier.weight(1f),
                )
                BreadthStatTile(
                  label = "Net Issues",
                  value = String.format(Locale.US, "%+d", breadth.advancingCount - breadth.decliningCount),
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        // 6. New Highs vs New Lows Comparison (Vico Grouped Column Chart)
        item(span = { GridItemSpan(maxLineSpan) }) {
          NewHighsLowsComparisonCard(breadth = breadth)
        }

        // 7. S&P Sector Performance Heat Map & Bar Chart (Full-Width Grid Span)
        item(span = { GridItemSpan(maxLineSpan) }) {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("sector_heatmap_section"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Column {
                Text(
                  text = "S&P 500 Sector Heat Map",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text =
                    if (sectorViewMode == SectorViewMode.HEATMAP) {
                      "Dynamic GICS sectors • Tap any tile to inspect leaders"
                    } else {
                      "11 GICS sector daily % change comparison"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SectorViewMode.entries.forEachIndexed { index, mode ->
                  SegmentedButton(
                    selected = sectorViewMode == mode,
                    onClick = { sectorViewMode = mode },
                    shape =
                      SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SectorViewMode.entries.size,
                      ),
                    modifier = Modifier.testTag("sector_view_${mode.name.lowercase(Locale.US)}"),
                  ) {
                    Text(
                      text = mode.label,
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                    )
                  }
                }
              }

              if (sectorViewMode == SectorViewMode.HEATMAP) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SectorHeatMapSort.entries.forEach { sortMode ->
                    FilterChip(
                      selected = heatMapSort == sortMode,
                      onClick = { heatMapSort = sortMode },
                      label = { Text(sortMode.label, style = MaterialTheme.typography.labelMedium) },
                      modifier = Modifier.testTag("heatmap_sort_${sortMode.name.lowercase(Locale.US)}"),
                    )
                  }
                }
                SectorHeatMapLegend()
                SectorHeatMapGrid(
                  sectors = sortedSectors,
                  selectedSector = selectedSector,
                  onSelectSector = { sector ->
                    selectedSector = if (selectedSector?.ticker == sector.ticker) null else sector
                  },
                )
              } else {
                VicoSectorBarChart(sectors = sortedSectors)
              }

              AnimatedVisibility(visible = selectedSector != null && sectorViewMode == SectorViewMode.HEATMAP) {
                selectedSector?.let { sector ->
                  val isPos = sector.changePercent >= 0.0
                  val accent = if (isPos) marketColors.gain else marketColors.loss
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "${sector.name} (${sector.ticker})",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = "Weight: ${String.format(Locale.US, "%.1f%%", sector.weightPercent)} • Leaders: ${sector.leaders}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = String.format(Locale.US, "%+.2f%%", sector.changePercent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // 7. Fear & Greed Index (Full-Width Grid Span)
        item(span = { GridItemSpan(maxLineSpan) }) {
          FearGreedSectionCard(breadth = breadth)
        }

        // 8. NYSE Breadth Visualization (Full-Width Grid Span)
        item(span = { GridItemSpan(maxLineSpan) }) {
          NyseBreadthVolumeCard(breadth = breadth)
        }
      }
    }
  }
}
