package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MarketBreadthSnapshot
import com.example.ui.theme.LocalMarketColors
import java.util.Locale

@Composable
fun FearGreedSectionCard(
  breadth: MarketBreadthSnapshot,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  Card(
    modifier = modifier.fillMaxWidth().testTag("fear_greed_section"),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Fear & Greed Index",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            text = "SENTIMENT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      FearGreedGauge(
        score = breadth.fearGreedScore,
        label = breadth.fearGreedLabel,
        modifier = Modifier.fillMaxWidth().testTag("fear_greed_gauge"),
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      breadth.fearGreedDrivers.forEach { driver ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = driver.name,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${driver.score} • ${driver.rating}",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
          LinearProgressIndicator(
            progress = { (driver.score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
            color = if (driver.score >= 50) marketColors.gain else marketColors.loss,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          )
        }
      }
    }
  }
}

@Composable
fun NyseBreadthVolumeCard(
  breadth: MarketBreadthSnapshot,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  Card(
    modifier = modifier.fillMaxWidth().testTag("nyse_breadth_card"),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(
        text = "NYSE Volume & Moving Averages",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      HorizontalRatioBar(
        leftLabel = "Up Volume",
        leftValue = String.format(Locale.US, "%.2fB", breadth.nyseUpVolumeBillions),
        rightLabel = "Down Volume",
        rightValue = String.format(Locale.US, "%.2fB", breadth.nyseDownVolumeBillions),
        leftFraction =
          (breadth.nyseUpVolumeBillions /
              (breadth.nyseUpVolumeBillions + breadth.nyseDownVolumeBillions).coerceAtLeast(0.1))
            .toFloat(),
        leftColor = marketColors.gain,
        rightColor = marketColors.loss,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BreadthStatTile(
          label = "% > 50 DMA",
          value = "${breadth.pctAbove50Dma}%",
          modifier = Modifier.weight(1f),
        )
        BreadthStatTile(
          label = "% > 200 DMA",
          value = "${breadth.pctAbove200Dma}%",
          modifier = Modifier.weight(1f),
        )
        BreadthStatTile(
          label = "Arms (TRIN)",
          value = String.format(Locale.US, "%.2f", breadth.trinArmsIndex),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}
