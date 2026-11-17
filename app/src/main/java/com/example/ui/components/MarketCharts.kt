package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.ChartPoint
import com.example.data.repository.ChartSeriesResult
import com.example.ui.theme.LocalMarketColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MiniSparklineChart(
  points: List<Double>,
  isPositive: Boolean,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  val lineColor = if (isPositive) marketColors.gain else marketColors.loss

  Canvas(modifier = modifier) {
    if (points.size < 2) return@Canvas
    val minVal = points.minOrNull() ?: return@Canvas
    val maxVal = points.maxOrNull() ?: return@Canvas
    val range = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0
    val padY = size.height * 0.12f
    val usableHeight = (size.height - padY * 2f).coerceAtLeast(1f)
    val stepX = size.width / (points.size - 1).coerceAtLeast(1)

    val linePath = Path()
    val fillPath = Path()

    points.forEachIndexed { idx, value ->
      val x = idx * stepX
      val normY = ((value - minVal) / range).toFloat()
      val y = size.height - padY - normY * usableHeight
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }
    fillPath.lineTo(size.width, size.height)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush =
        Brush.verticalGradient(
          colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent),
          startY = 0f,
          endY = size.height,
        ),
    )

    // Baseline dashed line at first point
    val baseNorm = ((points.first() - minVal) / range).toFloat()
    val baseY = size.height - padY - baseNorm * usableHeight
    drawLine(
      color = lineColor.copy(alpha = 0.35f),
      start = Offset(0f, baseY),
      end = Offset(size.width, baseY),
      strokeWidth = 1.dp.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
    )

    drawPath(
      path = linePath,
      color = lineColor,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
fun InteractiveMarketChart(
  series: ChartSeriesResult,
  isPositive: Boolean,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  val lineColor = if (isPositive) marketColors.gain else marketColors.loss
  val points = series.points
  var scrubbedIndex by remember(series) { mutableStateOf<Int?>(null) }

  val activePoint: ChartPoint? = scrubbedIndex?.let { points.getOrNull(it) }

  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (activePoint != null) {
        val diff = activePoint.price - series.previousClose
        val pct =
          if (series.previousClose > 0.0) (diff / series.previousClose) * 100.0 else 0.0
        Text(
          text = String.format(Locale.US, "%.2f (%+.2f%%)", activePoint.price, pct),
          style = MaterialTheme.typography.labelLarge,
          color = if (diff >= 0) marketColors.gain else marketColors.loss,
        )
        Text(
          text = activePoint.timestampLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Text(
          text = "Drag across chart to inspect prices",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = if (series.isLiveFeed) "LIVE" else "BASELINE",
          style = MaterialTheme.typography.labelSmall,
          color = if (series.isLiveFeed) marketColors.gain else marketColors.neutral,
        )
      }
    }

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(210.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
      Canvas(
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(points) {
              detectTapGestures(
                onPress = { offset ->
                  if (points.isNotEmpty()) {
                    val idx =
                      ((offset.x / size.width.toFloat()) * (points.size - 1))
                        .roundToInt()
                        .coerceIn(0, points.lastIndex)
                    scrubbedIndex = idx
                  }
                  tryAwaitRelease()
                  scrubbedIndex = null
                }
              )
            }
            .pointerInput(points) {
              detectDragGestures(
                onDragStart = { offset ->
                  if (points.isNotEmpty()) {
                    scrubbedIndex =
                      ((offset.x / size.width.toFloat()) * (points.size - 1))
                        .roundToInt()
                        .coerceIn(0, points.lastIndex)
                  }
                },
                onDrag = { change, _ ->
                  change.consume()
                  if (points.isNotEmpty()) {
                    scrubbedIndex =
                      ((change.position.x / size.width.toFloat()) * (points.size - 1))
                        .roundToInt()
                        .coerceIn(0, points.lastIndex)
                  }
                },
                onDragEnd = { scrubbedIndex = null },
                onDragCancel = { scrubbedIndex = null },
              )
            }
      ) {
        if (points.size < 2) return@Canvas
        val minVal = minOf(points.minOf { it.price }, series.previousClose)
        val maxVal = maxOf(points.maxOf { it.price }, series.previousClose)
        val range = (maxVal - minVal).takeIf { it > 1e-6 } ?: 1.0
        val padY = size.height * 0.08f
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()
        val coords =
          points.mapIndexed { idx, pt ->
            val x = idx * stepX
            val y = size.height - padY - ((pt.price - minVal) / range).toFloat() * usableH
            Offset(x, y)
          }

        coords.forEachIndexed { idx, offset ->
          if (idx == 0) {
            linePath.moveTo(offset.x, offset.y)
            fillPath.moveTo(offset.x, size.height)
            fillPath.lineTo(offset.x, offset.y)
          } else {
            linePath.lineTo(offset.x, offset.y)
            fillPath.lineTo(offset.x, offset.y)
          }
        }
        fillPath.lineTo(coords.last().x, size.height)
        fillPath.close()

        drawPath(
          path = fillPath,
          brush =
            Brush.verticalGradient(
              colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
              startY = 0f,
              endY = size.height,
            ),
        )

        val prevY =
          size.height - padY - ((series.previousClose - minVal) / range).toFloat() * usableH
        drawLine(
          color = lineColor.copy(alpha = 0.45f),
          start = Offset(0f, prevY),
          end = Offset(size.width, prevY),
          strokeWidth = 1.2.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
        )

        drawPath(
          path = linePath,
          color = lineColor,
          style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        scrubbedIndex?.let { idx ->
          coords.getOrNull(idx)?.let { pt ->
            drawLine(
              color = lineColor.copy(alpha = 0.65f),
              start = Offset(pt.x, 0f),
              end = Offset(pt.x, size.height),
              strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(
              color = lineColor.copy(alpha = 0.25f),
              radius = 10.dp.toPx(),
              center = pt,
            )
            drawCircle(
              color = lineColor,
              radius = 4.5.dp.toPx(),
              center = pt,
            )
          }
        }
      }
    }
  }
}

@Composable
fun FearGreedGauge(
  score: Int,
  label: String,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  val needleColor = MaterialTheme.colorScheme.onSurface
  val clamped = score.coerceIn(0, 100)

  val scoreColor =
    when {
      clamped < 25 -> marketColors.loss
      clamped < 45 -> Color(0xFFFF7A33)
      clamped <= 55 -> marketColors.amber
      clamped <= 75 -> Color(0xFF4CD964)
      else -> marketColors.gain
    }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().height(155.dp),
      contentAlignment = Alignment.BottomCenter,
    ) {
      Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val strokeW = 16.dp.toPx()
        val diameter = minOf(size.width, size.height * 1.85f) - strokeW
        val topLeft = Offset((size.width - diameter) / 2f, size.height - diameter / 2f - 8.dp.toPx())
        val arcSize = Size(diameter, diameter)

        val segments =
          listOf(
            0f to 36f to Color(0xFFFF3B30),
            36f to 36f to Color(0xFFFF7A33),
            72f to 36f to Color(0xFFFFB020),
            108f to 36f to Color(0xFF62D866),
            144f to 36f to Color(0xFF00C805),
          )
        segments.forEach { (angles, color) ->
          drawArc(
            color = color.copy(alpha = 0.85f),
            startAngle = 180f + angles.first,
            sweepAngle = angles.second - 2.5f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt),
          )
        }

        // Needle
        val center = Offset(topLeft.x + diameter / 2f, topLeft.y + diameter / 2f)
        val angleRad = PI - (clamped / 100.0) * PI
        val needleLen = diameter / 2f - strokeW * 0.9f
        val tip =
          Offset(
            center.x + (cos(angleRad) * needleLen).toFloat(),
            center.y - (sin(angleRad) * needleLen).toFloat(),
          )
        drawLine(
          color = needleColor,
          start = center,
          end = tip,
          strokeWidth = 3.dp.toPx(),
          cap = StrokeCap.Round,
        )
        drawCircle(color = scoreColor, radius = 6.dp.toPx(), center = center)
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "$clamped",
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Bold,
          color = scoreColor,
        )
        Text(
          text = label.uppercase(Locale.US),
          style = MaterialTheme.typography.labelLarge,
          color = scoreColor,
        )
      }
    }
  }
}

@Composable
fun HorizontalRatioBar(
  leftLabel: String,
  leftValue: String,
  rightLabel: String,
  rightValue: String,
  leftFraction: Float,
  leftColor: Color,
  rightColor: Color,
  modifier: Modifier = Modifier,
) {
  val clamped = leftFraction.coerceIn(0.05f, 0.95f)
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          text = leftLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = leftValue,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = leftColor,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = rightLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = rightValue,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = rightColor,
        )
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(10.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(rightColor.copy(alpha = 0.25f))
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(clamped)
            .height(10.dp)
            .clip(RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp))
            .background(leftColor)
      )
      Box(
        modifier =
          Modifier.weight(1f)
            .height(10.dp)
            .clip(RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp))
            .background(rightColor)
      )
    }
  }
}
