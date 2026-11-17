package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.SectorPerformance
import com.example.ui.theme.LocalMarketColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val INTRADAY_SESSION_LABELS =
  listOf(
    "09:30",
    "10:00",
    "10:30",
    "11:00",
    "11:30",
    "12:00",
    "12:30",
    "13:00",
    "13:30",
    "14:00",
    "14:30",
    "15:00",
    "15:30",
    "16:00",
  )

/**
 * Compact Vico line sparkline used inside the NYSE A/D Line grid KPI card.
 */
@Composable
fun VicoMiniAdLineChart(
  points: List<Double>,
  isPositive: Boolean,
  modifier: Modifier = Modifier,
) {
  val safePoints = remember(points) { if (points.size >= 2) points else listOf(0.0, 0.0) }
  val marketColors = LocalMarketColors.current
  val lineColor = if (isPositive) marketColors.gain else marketColors.loss
  val modelProducer = remember { CartesianChartModelProducer() }

  LaunchedEffect(safePoints) {
    modelProducer.runTransaction {
      lineSeries { series(safePoints) }
    }
  }

  val minVal = remember(safePoints) { safePoints.minOrNull() ?: 0.0 }
  val maxVal = remember(safePoints) {
    val mx = safePoints.maxOrNull() ?: 1.0
    if (mx <= minVal) minVal + 1.0 else mx
  }
  val rangeProvider = remember(minVal, maxVal) {
    val pad = ((maxVal - minVal) * 0.1).coerceAtLeast(1.0)
    CartesianLayerRangeProvider.fixed(minY = minVal - pad, maxY = maxVal + pad)
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

  CartesianChartHost(
    chart =
      rememberCartesianChart(
        rememberLineCartesianLayer(
          lineProvider = LineCartesianLayer.LineProvider.series(line),
          rangeProvider = rangeProvider,
        )
      ),
    modelProducer = modelProducer,
    scrollState = rememberVicoScrollState(scrollEnabled = false),
    modifier = modifier.testTag("vico_mini_ad_chart"),
  )
}

/**
 * Full-width Vico line chart for the Advance/Decline Ratio and cumulative NYSE A/D Line.
 */
@Composable
fun VicoAdRatioLineChart(
  values: List<Double>,
  isRatioMode: Boolean,
  isPositive: Boolean,
  modifier: Modifier = Modifier,
) {
  val safeValues = remember(values) { if (values.size >= 2) values else listOf(1.0, 1.0) }
  val marketColors = LocalMarketColors.current
  val lineColor = if (isPositive) marketColors.gain else marketColors.loss
  val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val modelProducer = remember { CartesianChartModelProducer() }

  LaunchedEffect(safeValues) {
    modelProducer.runTransaction {
      lineSeries { series(safeValues) }
    }
  }

  val minVal = remember(safeValues) { safeValues.minOrNull() ?: 0.0 }
  val maxVal = remember(safeValues) {
    val mx = safeValues.maxOrNull() ?: 1.0
    if (mx <= minVal) minVal + (if (isRatioMode) 0.2 else 10.0) else mx
  }
  val rangeProvider = remember(minVal, maxVal, isRatioMode) {
    val span = (maxVal - minVal).coerceAtLeast(if (isRatioMode) 0.15 else 25.0)
    val pad = span * 0.14
    CartesianLayerRangeProvider.fixed(minY = minVal - pad, maxY = maxVal + pad)
  }

  val yValueFormatter = remember(isRatioMode) {
    CartesianValueFormatter { _, value, _ ->
      if (isRatioMode) {
        String.format(Locale.US, "%.2fx", value)
      } else {
        val rounded = value.toInt()
        if (rounded > 0) "+$rounded" else "$rounded"
      }
    }
  }

  val xValueFormatter = remember(safeValues.size) {
    CartesianValueFormatter { _, value, _ ->
      val idx = value.roundToInt()
      val mapped =
        if (safeValues.size <= 1) 0
        else ((idx.toDouble() / (safeValues.size - 1)) * INTRADAY_SESSION_LABELS.lastIndex).roundToInt()
      INTRADAY_SESSION_LABELS.getOrElse(mapped.coerceIn(0, INTRADAY_SESSION_LABELS.lastIndex)) { "" }
    }
  }

  val line =
    LineCartesianLayer.rememberLine(
      fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
      areaFill =
        LineCartesianLayer.AreaFill.single(
          fill(
            ShaderProvider.verticalGradient(
              lineColor.copy(alpha = 0.30f).toArgb(),
              Color.Transparent.toArgb(),
            )
          )
        ),
      pointConnector = LineCartesianLayer.PointConnector.cubic(0.35f),
    )

  CartesianChartHost(
    chart =
      rememberCartesianChart(
        rememberLineCartesianLayer(
          lineProvider = LineCartesianLayer.LineProvider.series(line),
          rangeProvider = rangeProvider,
        ),
        startAxis =
          VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 11.sp),
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
    modifier = modifier.fillMaxWidth().height(190.dp).testTag("vico_ad_ratio_line_chart"),
  )
}

/**
 * Vico column chart rendering all 11 GICS S&P sectors colored by positive/negative performance.
 */
@Composable
fun VicoSectorBarChart(
  sectors: List<SectorPerformance>,
  modifier: Modifier = Modifier,
) {
  val safeSectors = remember(sectors) { sectors }
  if (safeSectors.isEmpty()) return

  val marketColors = LocalMarketColors.current
  val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val modelProducer = remember { CartesianChartModelProducer() }

  LaunchedEffect(safeSectors) {
    modelProducer.runTransaction {
      columnSeries {
        series(safeSectors.map { it.changePercent })
      }
    }
  }

  val positiveCol =
    rememberLineComponent(
      fill = fill(marketColors.gain),
      thickness = 14.dp,
      shape = CorneredShape.rounded(topLeftDp = 6f, topRightDp = 6f),
    )
  val negativeCol =
    rememberLineComponent(
      fill = fill(marketColors.loss),
      thickness = 14.dp,
      shape = CorneredShape.rounded(bottomLeftDp = 6f, bottomRightDp = 6f),
    )

  val columnProvider =
    remember(positiveCol, negativeCol) {
      object : ColumnCartesianLayer.ColumnProvider {
        override fun getColumn(
          entry: ColumnCartesianLayerModel.Entry,
          seriesIndex: Int,
          extraStore: ExtraStore,
        ): LineComponent = if (entry.y >= 0.0) positiveCol else negativeCol

        override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent =
          positiveCol
      }
    }

  val xFormatter = remember(safeSectors) {
    CartesianValueFormatter { _, value, _ ->
      safeSectors.getOrNull(value.roundToInt())?.ticker ?: ""
    }
  }

  val yFormatter = remember {
    CartesianValueFormatter { _, value, _ ->
      String.format(Locale.US, "%+.1f%%", value)
    }
  }

  CartesianChartHost(
    chart =
      rememberCartesianChart(
        rememberColumnCartesianLayer(
          columnProvider = columnProvider,
        ),
        startAxis =
          VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 10.sp),
            valueFormatter = yFormatter,
          ),
        bottomAxis =
          HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 9.sp),
            valueFormatter = xFormatter,
          ),
      ),
    modelProducer = modelProducer,
    scrollState = rememberVicoScrollState(scrollEnabled = false),
    modifier = modifier.fillMaxWidth().height(220.dp).testTag("vico_sector_bar_chart"),
  )
}

@Composable
fun BreadthStatTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun SectorHeatMapGrid(
  sectors: List<SectorPerformance>,
  selectedSector: SectorPerformance?,
  onSelectSector: (SectorPerformance) -> Unit,
) {
  val rows =
    listOf(
      sectors.take(2), // XLK (31%), XLF (13%)
      sectors.drop(2).take(3), // XLV, XLY, XLC
      sectors.drop(5).take(3), // XLI, XLP, XLE
      sectors.drop(8).take(3), // XLU, XLRE, XLB
    )

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    rows.forEach { rowItems ->
      if (rowItems.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          rowItems.forEach { sector ->
            val weight = sector.weightPercent.toFloat().coerceAtLeast(3.5f)
            SectorTile(
              sector = sector,
              isSelected = selectedSector?.ticker == sector.ticker,
              onClick = { onSelectSector(sector) },
              modifier = Modifier.weight(weight),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SectorTile(
  sector: SectorPerformance,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  val pct = sector.changePercent
  val intensity = (abs(pct) / 2.2).coerceIn(0.20, 0.92).toFloat()
  val bg =
    if (pct >= 0.0) {
      marketColors.gain.copy(alpha = intensity)
    } else {
      marketColors.loss.copy(alpha = intensity)
    }
  val textColor =
    if (intensity > 0.45f || marketColors.isOledDark) Color.White
    else MaterialTheme.colorScheme.onSurface

  Box(
    modifier =
      modifier
        .height(74.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(bg)
        .then(
          if (isSelected) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
          } else {
            Modifier
          }
        )
        .clickable(onClick = onClick)
        .padding(8.dp)
        .testTag("sector_tile_${sector.ticker}")
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = sector.ticker,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = textColor,
        )
        Text(
          text = String.format(Locale.US, "%.0f%%", sector.weightPercent),
          style = MaterialTheme.typography.labelSmall,
          color = textColor.copy(alpha = 0.85f),
        )
      }
      Column {
        Text(
          text = sector.name,
          style = MaterialTheme.typography.bodySmall,
          color = textColor.copy(alpha = 0.9f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val trendSymbol = if (pct >= 0.0) "▲" else "▼"
        Text(
          text = String.format(Locale.US, "%s %+.2f%%", trendSymbol, pct),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = textColor,
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * Derives an intraday Advance/Decline Ratio curve anchored smoothly to [currentAdRatio]
 * using the normalized trajectory of [adLineSeries].
 */
fun buildAdRatioSeries(
  adLineSeries: List<Double>,
  currentAdRatio: Double,
): List<Double> {
  if (adLineSeries.size < 2) return listOf(1.0, currentAdRatio.coerceIn(0.25, 4.5))
  val minVal = adLineSeries.minOrNull() ?: 0.0
  val maxVal = adLineSeries.maxOrNull() ?: 1.0
  val span = (maxVal - minVal).takeIf { it > 1e-3 } ?: 1.0
  val lastNorm = (adLineSeries.last() - minVal) / span
  return adLineSeries.mapIndexed { idx, v ->
    val norm = (v - minVal) / span
    if (idx == adLineSeries.lastIndex) {
      currentAdRatio.coerceIn(0.25, 4.5)
    } else {
      val baselineStart = if (currentAdRatio >= 1.0) 0.92 else 1.08
      val delta = (norm - lastNorm) * abs(currentAdRatio - baselineStart).coerceAtLeast(0.45)
      (currentAdRatio + delta).coerceIn(0.25, 4.5)
    }
  }
}
