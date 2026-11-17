package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MarketAiBriefing
import com.example.data.model.PortfolioSummary
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun DashboardAiAndPortfolioSection(
  briefingState: UiState<MarketAiBriefing>?,
  portfolioSummary: PortfolioSummary?,
  armedAlertsCount: Int,
  onGenerateBriefing: () -> Unit,
  onOpenAiAssistant: () -> Unit,
  onOpenPortfolio: () -> Unit,
  onOpenAlerts: () -> Unit,
) {
  val marketColors = LocalMarketColors.current

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // AI Briefing Hero Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .clickable(onClick = onOpenAiAssistant)
          .testTag("dashboard_ai_briefing_card"),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "AI Market Briefing",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text = "Open Brief →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        when (briefingState) {
          null -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Tap to synthesize today's macro & index movers with Gemini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
              )
              Button(
                onClick = onGenerateBriefing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("dashboard_generate_briefing_button"),
              ) {
                Text("Summarize", style = MaterialTheme.typography.labelLarge)
              }
            }
          }
          is UiState.Loading -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              Text(
                text = "Analyzing live indices & headlines...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          is UiState.Error -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = briefingState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
              )
              OutlinedButton(onClick = onGenerateBriefing) {
                Text("Retry")
              }
            }
          }
          is UiState.Success -> {
            val briefing = briefingState.data
            Text(
              text = briefing.headline,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = briefing.executiveSummary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    // Portfolio & Price Alerts Quick Strip
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier =
          Modifier.weight(1.4f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenPortfolio)
            .testTag("dashboard_portfolio_card"),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.PieChart,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Portfolio",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          if (portfolioSummary == null || portfolioSummary.positions.isEmpty()) {
            Text(
              text = "Add holdings & compare vs SPX",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          } else {
            val spxAlpha =
              portfolioSummary.benchmarks.firstOrNull { it.symbol == "SPX" }?.portfolioAlphaPercent
            val dayColor =
              if (portfolioSummary.dayChangePercent >= 0.0) marketColors.gain else marketColors.loss
            Text(
              text = String.format(Locale.US, "$%,.2f", portfolioSummary.totalValue),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                spxAlpha?.let {
                  String.format(
                    Locale.US,
                    "%+.2f%% today (α %+.2f%% SPX)",
                    portfolioSummary.dayChangePercent,
                    it,
                  )
                } ?: String.format(Locale.US, "%+.2f%% today", portfolioSummary.dayChangePercent),
              style = MaterialTheme.typography.labelSmall,
              color = dayColor,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
          Modifier.weight(0.9f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenAlerts)
            .testTag("dashboard_alerts_card"),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Alerts",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          Spacer(modifier = Modifier.width(2.dp))
          Text(
            text = "$armedAlertsCount armed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Manage targets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
          )
        }
      }
    }
  }
}
