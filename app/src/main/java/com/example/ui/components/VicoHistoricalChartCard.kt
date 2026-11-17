package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChartTimeframe
import com.example.data.model.MarketQuote
import com.example.data.repository.ChartPoint
import com.example.data.repository.ChartSeriesResult
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Historical Chart card on the ticker detail screen powered by Vico (`CartesianChartHost` +
 * `LineCartesianLayer`) supporting 1D, 1W, 1M, and 1Y timeframes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VicoHistoricalChartCard(
  quote: MarketQuote,
  selectedTimeframe: ChartTimeframe,
  chartState: UiState<ChartSeriesResult>,
  onSelectTimeframe: (ChartTimeframe) -> Unit,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  val successSeries = (chartState as? UiState.Success)?.data
  val baseline =
    if (selectedTimeframe == ChartTimeframe.D1) {
      successSeries?.previousClose ?: quote.previousClose
    } else {
      successSeries?.open ?: quote.previousClose
    }
  val latestPrice = successSeries?.points?.lastOrNull()?.price ?: quote.price
  val periodChange =
    if (selectedTimeframe == ChartTimeframe.D1 && successSeries == null) quote.change
    else latestPrice - baseline
  val periodChangePct =
    if (selectedTimeframe == ChartTimeframe.D1 && successSeries == null) quote.changePercent
    else if (baseline > 0.0) (periodChange / baseline) * 100.0
    else 0.0
  val isTrendPositive = periodChange >= 0.0
  val trendColor = if (isTrendPositive) marketColors.gain else marketColors.loss

  Card(
    modifier = modifier.fillMaxWidth().testTag("historical_chart_card"),
    shape = RoundedCornerShape(22.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header + Period Return + Feed Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = trendColor.copy(alpha = 0.14f),
          ) {
            Icon(
              imageVector = Icons.Rounded.ShowChart,
              contentDescription = null,
              tint = trendColor,
              modifier = Modifier.padding(7.dp).size(18.dp),
            )
          }
          Column {
            Text(
              text = "Historical Chart",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                String.format(
                  Locale.US,
                  "%s: %+.2f (%+.2f%%)",
                  selectedTimeframe.periodTitle,
                  periodChange,
                  periodChangePct,
                ),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = trendColor,
            )
          }
        }

        if (successSeries != null) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color =
              if (successSeries.isLiveFeed) marketColors.gainSurface
              else MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Text(
              text = if (successSeries.isLiveFeed) "LIVE" else "BASELINE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color =
                if (successSeries.isLiveFeed) marketColors.gain
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }

      // Timeframe Selector: 1D | 1W | 1M | 1Y
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().testTag("historical_timeframe_selector")
      ) {
        ChartTimeframe.entries.forEachIndexed { index, tf ->
          val selected = selectedTimeframe == tf
          SegmentedButton(
            selected = selected,
            onClick = { onSelectTimeframe(tf) },
            shape =
              SegmentedButtonDefaults.itemShape(
                index = index,
                count = ChartTimeframe.entries.size,
              ),
            modifier = Modifier.testTag("timeframe_${tf.label}"),
          ) {
            Text(
              text = tf.label,
              maxLines = 1,
              softWrap = false,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
          }
        }
      }

      // Vico Chart Surface
      when (chartState) {
        is UiState.Loading -> {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(225.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is UiState.Error -> {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(225.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = chartState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        is UiState.Success -> {
          val series = chartState.data
          VicoPriceTrendLineChart(
            points = series.points,
            lineColor = trendColor,
            modifier = Modifier.fillMaxWidth(),
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          // Range Indicator & Period Breakdown
          PeriodRangeBar(
            timeframeLabel = selectedTimeframe.label,
            low = series.low,
            high = series.high,
            current = latestPrice,
            accentColor = trendColor,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            ChartMetricMini(
              label = "${selectedTimeframe.label} Open",
              value = formatChartPrice(series.open),
            )
            ChartMetricMini(
              label = "${selectedTimeframe.label} High",
              value = formatChartPrice(series.high),
            )
            ChartMetricMini(
              label = "${selectedTimeframe.label} Low",
              value = formatChartPrice(series.low),
            )
            ChartMetricMini(
              label = "${selectedTimeframe.label} Return",
              value = String.format(Locale.US, "%+.2f%%", periodChangePct),
              valueColor = trendColor,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VicoPriceTrendLineChart(
  points: List<ChartPoint>,
  lineColor: Color,
  modifier: Modifier = Modifier,
) {
  val safePoints =
    remember(points) {
      if (points.size >= 2) points
      else if (points.size == 1) listOf(points[0], points[0])
      else listOf(ChartPoint("--", 100.0), ChartPoint("--", 100.0))
    }
  val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val modelProducer = remember { CartesianChartModelProducer() }

  LaunchedEffect(safePoints) {
    modelProducer.runTransaction {
      lineSeries {
        series(safePoints.map { it.price })
      }
    }
  }

  val minPrice = remember(safePoints) { safePoints.minOf { it.price } }
  val maxPrice = remember(safePoints) { safePoints.maxOf { it.price } }
  val rangeProvider = remember(minPrice, maxPrice) {
    val span = abs(maxPrice - minPrice).coerceAtLeast(maxPrice * 0.004).coerceAtLeast(0.05)
    val pad = span * 0.14
    CartesianLayerRangeProvider.fixed(
      minY = (minPrice - pad).coerceAtLeast(0.0),
      maxY = maxPrice + pad,
    )
  }

  val yValueFormatter = remember(maxPrice) {
    CartesianValueFormatter { _, value, _ ->
      when {
        maxPrice >= 10_000.0 -> String.format(Locale.US, "$%,.0f", value)
        maxPrice >= 100.0 -> String.format(Locale.US, "$%,.1f", value)
        else -> String.format(Locale.US, "$%.2f", value)
      }
    }
  }

  val xValueFormatter = remember(safePoints) {
    CartesianValueFormatter { _, value, _ ->
      val idx = value.roundToInt().coerceIn(0, safePoints.lastIndex)
      safePoints.getOrNull(idx)?.timestampLabel ?: ""
    }
  }

  val line =
    LineCartesianLayer.rememberLine(
      fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
      areaFill =
        LineCartesianLayer.AreaFill.single(
          fill(
            ShaderProvider.verticalGradient(
              lineColor.copy(alpha = 0.28f).toArgb(),
              Color.Transparent.toArgb(),
            )
          )
        ),
      pointConnector = LineCartesianLayer.PointConnector.cubic(0.35f),
    )

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier,
  ) {
    CartesianChartHost(
      chart =
        rememberCartesianChart(
          rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(line),
            rangeProvider = rangeProvider,
          ),
          startAxis =
            VerticalAxis.rememberStart(
              label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 10.sp),
              valueFormatter = yValueFormatter,
            ),
          bottomAxis =
            HorizontalAxis.rememberBottom(
              label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 10.sp),
              valueFormatter = xValueFormatter,
            ),
        ),
      modelProducer = modelProducer,
      scrollState = rememberVicoScrollState(scrollEnabled = false),
      modifier =
        Modifier.fillMaxWidth()
          .height(225.dp)
          .padding(horizontal = 6.dp, vertical = 8.dp)
          .testTag("vico_historical_price_chart"),
    )
  }
}

@Composable
private fun PeriodRangeBar(
  timeframeLabel: String,
  low: Double,
  high: Double,
  current: Double,
  accentColor: Color,
) {
  val span = (high - low).takeIf { it > 1e-6 } ?: 1.0
  val fraction = ((current - low) / span).toFloat().coerceIn(0.03f, 0.97f)

  Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "$timeframeLabel Low: ${formatChartPrice(low)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "$timeframeLabel High: ${formatChartPrice(high)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(fraction)
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.78f))
      )
    }
  }
}

@Composable
private fun ChartMetricMini(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Column(horizontalAlignment = Alignment.Start) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = valueColor,
      maxLines = 1,
    )
  }
}

private fun formatChartPrice(price: Double): String =
  if (price >= 1000.0) String.format(Locale.US, "$%,.2f", price)
  else String.format(Locale.US, "$%.2f", price)
