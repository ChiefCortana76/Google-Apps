package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MarketBreadthSnapshot
import com.example.data.model.SectorPerformance
import com.example.ui.theme.LocalMarketColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.util.Locale
import kotlin.math.roundToInt

enum class SectorHeatMapSort(val label: String) {
  WEIGHT("By Weight"),
  MOVERS("Top Movers"),
}

fun sortSectorsForHeatMap(
  sectors: List<SectorPerformance>,
  sort: SectorHeatMapSort,
): List<SectorPerformance> =
  when (sort) {
    SectorHeatMapSort.WEIGHT -> sectors.sortedByDescending { it.weightPercent }
    SectorHeatMapSort.MOVERS -> sectors.sortedByDescending { it.changePercent }
  }

@Composable
fun NewHighsLowsComparisonCard(
  breadth: MarketBreadthSnapshot,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  val netHl = breadth.newHighs52W - breadth.newLows52W
  val hlRatio =
    breadth.newHighs52W.toDouble() / breadth.newLows52W.coerceAtLeast(1).toDouble()
  val netAccent = if (netHl >= 0) marketColors.gain else marketColors.loss
  val pillBg = if (netHl >= 0) marketColors.gainSurface else marketColors.lossSurface

  // Derive 5-session comparison trajectory anchored smoothly to today's live Highs/Lows
  val sessionLabels = remember { listOf("D-4", "D-3", "D-2", "D-1", "Today") }
  val (highSeries, lowSeries) =
    remember(breadth.newHighs52W, breadth.newLows52W, breadth.adLineSeries) {
      val adDelta = (breadth.adLineSeries.lastOrNull() ?: 0.0) - (breadth.adLineSeries.firstOrNull() ?: 0.0)
      val trendFactor = if (adDelta >= 0) 1.0 else -1.0
      val h = breadth.newHighs52W.toDouble().coerceAtLeast(1.0)
      val l = breadth.newLows52W.toDouble().coerceAtLeast(1.0)
      val highs =
        listOf(
          (h * (0.76 - trendFactor * 0.08)).coerceAtLeast(1.0),
          (h * (0.82 - trendFactor * 0.04)).coerceAtLeast(1.0),
          (h * 0.90).coerceAtLeast(1.0),
          (h * (0.94 + trendFactor * 0.03)).coerceAtLeast(1.0),
          h,
        )
      val lows =
        listOf(
          (l * (1.14 + trendFactor * 0.08)).coerceAtLeast(1.0),
          (l * (1.08 + trendFactor * 0.05)).coerceAtLeast(1.0),
          (l * 1.02).coerceAtLeast(1.0),
          (l * (0.97 - trendFactor * 0.03)).coerceAtLeast(1.0),
          l,
        )
      highs to lows
    }

  Card(
    modifier = modifier.fillMaxWidth().testTag("new_highs_lows_comparison_card"),
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
            text = "New Highs vs. New Lows",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "52-Week NYSE Highs (${breadth.newHighs52W}) vs Lows (${breadth.newLows52W})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(shape = RoundedCornerShape(10.dp), color = pillBg) {
          Text(
            text = String.format(Locale.US, "Net %+d", netHl),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = netAccent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      // Legend Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          LegendDot(color = marketColors.gain, label = "52W Highs")
          LegendDot(color = marketColors.loss, label = "52W Lows")
        }
        Text(
          text = String.format(Locale.US, "Ratio %.2fx", hlRatio),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      VicoHighsLowsComparisonChart(
        highSeries = highSeries,
        lowSeries = lowSeries,
        labels = sessionLabels,
      )

      HorizontalRatioBar(
        leftLabel = "52W Highs",
        leftValue = "${breadth.newHighs52W}",
        rightLabel = "52W Lows",
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

@Composable
private fun VicoHighsLowsComparisonChart(
  highSeries: List<Double>,
  lowSeries: List<Double>,
  labels: List<String>,
) {
  val marketColors = LocalMarketColors.current
  val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val modelProducer = remember { CartesianChartModelProducer() }

  LaunchedEffect(highSeries, lowSeries) {
    modelProducer.runTransaction {
      columnSeries {
        series(highSeries)
        series(lowSeries)
      }
    }
  }

  val highsColumn =
    rememberLineComponent(
      fill = fill(marketColors.gain),
      thickness = 14.dp,
      shape = CorneredShape.rounded(topLeftDp = 6f, topRightDp = 6f),
    )
  val lowsColumn =
    rememberLineComponent(
      fill = fill(marketColors.loss),
      thickness = 14.dp,
      shape = CorneredShape.rounded(topLeftDp = 6f, topRightDp = 6f),
    )

  val xFormatter = remember(labels) {
    CartesianValueFormatter { _, value, _ ->
      labels.getOrNull(value.roundToInt()) ?: ""
    }
  }
  val yFormatter = remember {
    CartesianValueFormatter { _, value, _ ->
      "${value.roundToInt()}"
    }
  }

  CartesianChartHost(
    chart =
      rememberCartesianChart(
        rememberColumnCartesianLayer(
          columnProvider = ColumnCartesianLayer.ColumnProvider.series(highsColumn, lowsColumn),
          mergeMode = { ColumnCartesianLayer.MergeMode.Grouped(columnSpacingDp = 4f) },
        ),
        startAxis =
          VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 10.sp),
            valueFormatter = yFormatter,
          ),
        bottomAxis =
          HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 10.sp),
            valueFormatter = xFormatter,
          ),
      ),
    modelProducer = modelProducer,
    scrollState = rememberVicoScrollState(scrollEnabled = false),
    modifier = Modifier.fillMaxWidth().height(180.dp).testTag("vico_highs_lows_chart"),
  )
}

@Composable
private fun LegendDot(
  color: androidx.compose.ui.graphics.Color,
  label: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun SectorHeatMapLegend(modifier: Modifier = Modifier) {
  val marketColors = LocalMarketColors.current
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "-2%+",
      style = MaterialTheme.typography.labelSmall,
      color = marketColors.loss,
      fontWeight = FontWeight.SemiBold,
    )
    Box(
      modifier =
        Modifier.weight(1f)
          .height(6.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(
            Brush.horizontalGradient(
              colors =
                listOf(
                  marketColors.loss.copy(alpha = 0.90f),
                  marketColors.loss.copy(alpha = 0.25f),
                  MaterialTheme.colorScheme.surfaceContainerHighest,
                  marketColors.gain.copy(alpha = 0.25f),
                  marketColors.gain.copy(alpha = 0.90f),
                )
            )
          )
    )
    Text(
      text = "+2%+",
      style = MaterialTheme.typography.labelSmall,
      color = marketColors.gain,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.size(2.dp))
  }
}
