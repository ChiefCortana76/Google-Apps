package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.RadioPlayerState
import com.example.data.model.CalendarWindow
import com.example.data.model.EarningsFilterStatus
import com.example.data.model.EarningsItem
import com.example.data.model.EarningsSession
import com.example.data.model.EconomicEvent
import com.example.data.model.EconomicRegion
import com.example.data.model.EventImportance
import com.example.data.model.MarketAiBriefing
import com.example.data.model.MarketCapTier
import com.example.data.model.MarketCommandSnapshot
import com.example.data.repository.CnbcAudioItem
import com.example.data.repository.CnbcRegion
import com.example.ui.components.MarketSwipeRefresh
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.AiAssistantUiState
import com.example.ui.viewmodel.UiState
import java.util.Locale
import kotlin.math.abs

enum class IntelSubTab(val label: String) {
  ECONOMIC("Econ"),
  EARNINGS("Earnings"),
  NEWS("News"),
  RADIO("Radio"),
  AI_BRIEF("AI"),
}

@Composable
fun IntelScreen(
  snapshot: MarketCommandSnapshot,
  activeSubTab: IntelSubTab,
  onSelectSubTab: (IntelSubTab) -> Unit,
  radioState: RadioPlayerState,
  radioFeeds: Map<CnbcRegion, List<CnbcAudioItem>>,
  onSelectRadioRegion: (CnbcRegion) -> Unit,
  onPlayLiveRadioStream: (CnbcRegion) -> Unit,
  onPlayRadioEpisode: (CnbcAudioItem) -> Unit,
  onToggleRadioPlayPause: () -> Unit,
  onSeekRadioBy: (Long) -> Unit,
  onSeekRadioTo: (Long) -> Unit,
  onStopRadio: () -> Unit,
  aiState: AiAssistantUiState,
  onAskAi: (String) -> Unit,
  briefingState: UiState<MarketAiBriefing>? = null,
  onGenerateBriefing: () -> Unit = {},
  isRefreshing: Boolean = false,
  onRefresh: () -> Unit = {},
) {
  Column(modifier = Modifier.fillMaxSize().testTag("intel_screen")) {
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      IntelSubTab.entries.forEachIndexed { idx, tab ->
        SegmentedButton(
          selected = activeSubTab == tab,
          onClick = { onSelectSubTab(tab) },
          shape = SegmentedButtonDefaults.itemShape(index = idx, count = IntelSubTab.entries.size),
          modifier = Modifier.testTag("intel_tab_${tab.name.lowercase()}"),
        ) {
          Text(text = tab.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
      }
    }

    when (activeSubTab) {
      IntelSubTab.ECONOMIC ->
        EconomicCalendarView(
          events = snapshot.economicEvents,
          scheduleDateLabel = snapshot.economicDateLabel,
          lastUpdatedLabel = snapshot.lastUpdatedLabel,
          isLiveFeed = snapshot.economicLiveFeed,
          isRefreshing = isRefreshing,
          onRefresh = onRefresh,
        )
      IntelSubTab.EARNINGS -> EarningsCalendarView(earnings = snapshot.earnings)
      IntelSubTab.NEWS ->
        NewsScreen(
          articles = snapshot.news,
          isRefreshing = isRefreshing,
          onRefresh = onRefresh,
        )
      IntelSubTab.RADIO ->
        RadioFeedView(
          radioState = radioState,
          feedsByRegion = radioFeeds,
          onSelectRegionAndPlay = onSelectRadioRegion,
          onPlayLiveStream = onPlayLiveRadioStream,
          onPlayEpisode = onPlayRadioEpisode,
          onTogglePlayPause = onToggleRadioPlayPause,
          onSeekBy = onSeekRadioBy,
          onSeekTo = onSeekRadioTo,
          onStop = onStopRadio,
        )
      IntelSubTab.AI_BRIEF ->
        AiAssistantView(
          briefingState = briefingState,
          onGenerateBriefing = onGenerateBriefing,
          aiState = aiState,
          onAskAi = onAskAi,
        )
    }
  }
}

@Composable
private fun EconomicCalendarView(
  events: List<EconomicEvent>,
  scheduleDateLabel: String,
  lastUpdatedLabel: String,
  isLiveFeed: Boolean,
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  var selectedWindow by remember { mutableStateOf(CalendarWindow.TODAY) }
  var selectedRegion by remember { mutableStateOf(EconomicRegion.ALL) }

  val filtered =
    events.filter { event ->
      val windowMatch =
        when (selectedWindow) {
          CalendarWindow.TODAY -> event.window == CalendarWindow.TODAY
          CalendarWindow.TOMORROW -> event.window == CalendarWindow.TOMORROW
          CalendarWindow.THIS_WEEK ->
            event.window == CalendarWindow.TODAY ||
              event.window == CalendarWindow.TOMORROW ||
              event.window == CalendarWindow.THIS_WEEK
          CalendarWindow.THIS_MONTH -> true
        }
      val regionMatch = selectedRegion == EconomicRegion.ALL || event.region == selectedRegion
      windowMatch && regionMatch
    }

  MarketSwipeRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("economic_calendar_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (scheduleDateLabel.isNotBlank()) "Schedule • $scheduleDateLabel (ET)"
              else "Economic Schedule (ET)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color =
              if (isLiveFeed) marketColors.gainSurface
              else MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Text(
              text =
                (if (isLiveFeed) "LIVE • " else "") + lastUpdatedLabel,
              style = MaterialTheme.typography.labelSmall,
              color =
                if (isLiveFeed) marketColors.gain
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(CalendarWindow.entries) { win ->
            FilterChip(
              selected = selectedWindow == win,
              onClick = { selectedWindow = win },
              label = { Text(win.label) },
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(EconomicRegion.entries) { reg ->
            FilterChip(
              selected = selectedRegion == reg,
              onClick = { selectedRegion = reg },
              label = { Text(reg.label) },
            )
          }
        }
      }

      if (filtered.isEmpty()) {
        item {
          Text(
            text = "No economic releases match the current region & timeframe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
      items(filtered, key = { it.id }) { event ->
        val impColor =
          when (event.importance) {
            EventImportance.LOW -> marketColors.gain
            EventImportance.MODERATE -> marketColors.amber
            EventImportance.HIGH -> marketColors.loss
          }
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(impColor))
                Text(
                  text = event.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              Surface(
                color = impColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = event.importance.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = impColor,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }

            Text(
              text = "${event.country} • ${event.dateLabel} at ${event.timeEt}" +
                (event.countdownMinutes?.let { " • In ${it}m" } ?: ""),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Column {
                Text("Forecast", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.forecast, style = MaterialTheme.typography.labelLarge)
              }
              Column {
                Text("Previous", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.previous, style = MaterialTheme.typography.labelLarge)
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("Actual", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = event.actual ?: "Pending",
                  style = MaterialTheme.typography.labelLarge,
                  color = if (event.actual != null) marketColors.gain else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
      }
    }
  }
}

@Composable
private fun EarningsCalendarView(earnings: List<EarningsItem>) {
  val marketColors = LocalMarketColors.current
  var selectedStatus by remember { mutableStateOf(EarningsFilterStatus.ALL) }
  var selectedWindow by remember { mutableStateOf(CalendarWindow.TODAY) }
  var selectedCap by remember { mutableStateOf(MarketCapTier.ALL) }
  var selectedSector by remember { mutableStateOf("All Sectors") }
  var majorOnly by remember { mutableStateOf(false) }

  val sectors = remember(earnings) { listOf("All Sectors") + earnings.map { it.sector }.distinct() }

  val windowScopedEarnings =
    remember(earnings, selectedWindow, selectedCap, selectedSector, majorOnly) {
      earnings.filter { item ->
        val winMatch =
          when (selectedWindow) {
            CalendarWindow.TODAY -> item.dateWindow == CalendarWindow.TODAY
            CalendarWindow.TOMORROW -> item.dateWindow == CalendarWindow.TOMORROW
            CalendarWindow.THIS_WEEK, CalendarWindow.THIS_MONTH -> true
          }
        val capMatch = selectedCap == MarketCapTier.ALL || item.marketCapTier == selectedCap
        val sectorMatch = selectedSector == "All Sectors" || item.sector == selectedSector
        val majorMatch = !majorOnly || item.isMajorCompany
        winMatch && capMatch && sectorMatch && majorMatch
      }
    }

  val filtered =
    remember(windowScopedEarnings, selectedStatus) {
      when (selectedStatus) {
        EarningsFilterStatus.ALL -> windowScopedEarnings
        EarningsFilterStatus.REPORTED -> windowScopedEarnings.filter { it.isReported }
        EarningsFilterStatus.UPCOMING -> windowScopedEarnings.filter { !it.isReported }
        EarningsFilterStatus.SURPRISE ->
          windowScopedEarnings
            .filter { it.isSurprise }
            .sortedByDescending { abs(it.epsSurprisePercent ?: 0.0) }
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("earnings_calendar_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().testTag("earnings_status_filter_row")
      ) {
        EarningsFilterStatus.entries.forEachIndexed { idx, status ->
          SegmentedButton(
            selected = selectedStatus == status,
            onClick = { selectedStatus = status },
            shape =
              SegmentedButtonDefaults.itemShape(
                index = idx,
                count = EarningsFilterStatus.entries.size,
              ),
            modifier = Modifier.testTag("earnings_status_${status.name.lowercase(Locale.US)}"),
          ) {
            Text(
              text = status.label,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf(CalendarWindow.TODAY, CalendarWindow.TOMORROW, CalendarWindow.THIS_WEEK)) { win ->
          FilterChip(
            selected = selectedWindow == win,
            onClick = { selectedWindow = win },
            label = { Text(win.label) },
          )
        }
        item {
          FilterChip(
            selected = majorOnly,
            onClick = { majorOnly = !majorOnly },
            label = { Text("Major Only") },
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MarketCapTier.entries) { tier ->
          FilterChip(
            selected = selectedCap == tier,
            onClick = { selectedCap = tier },
            label = { Text(tier.label) },
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sectors) { sec ->
          FilterChip(
            selected = selectedSector == sec,
            onClick = { selectedSector = sec },
            label = { Text(sec) },
          )
        }
      }
    }

    if (filtered.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          modifier = Modifier.fillMaxWidth().testTag("earnings_empty_state"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "No ${selectedStatus.label.lowercase(Locale.US)} earnings match these filters",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Try switching timeframe or resetting status filters.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = {
                selectedStatus = EarningsFilterStatus.ALL
                selectedWindow = CalendarWindow.THIS_WEEK
                selectedCap = MarketCapTier.ALL
                selectedSector = "All Sectors"
                majorOnly = false
              },
              modifier = Modifier.testTag("earnings_reset_filters_button"),
            ) {
              Text("Reset Filters")
            }
          }
        }
      }
    } else {
      items(filtered, key = { it.id }) { item ->
        val surprisePct = item.epsSurprisePercent
        val isBeat = (surprisePct ?: 0.0) >= 0.0
        val surpriseColor =
          when {
            surprisePct == null -> MaterialTheme.colorScheme.onSurfaceVariant
            item.isSurprise && isBeat -> marketColors.gain
            item.isSurprise && !isBeat -> marketColors.loss
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          }

        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          modifier = Modifier.fillMaxWidth().testTag("earnings_card_${item.ticker}"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = item.ticker,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Surface(
                  color =
                    if (item.session == EarningsSession.BEFORE_OPEN)
                      MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = "${item.session.shortCode} • ${item.timeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
                if (item.isReported && surprisePct != null) {
                  Surface(
                    color =
                      if (item.isSurprise) surpriseColor.copy(alpha = 0.16f)
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                  ) {
                    Text(
                      text =
                        if (item.isSurprise) {
                          val tag = if (isBeat) "Beat" else "Miss"
                          String.format(Locale.US, "%+.1f%% %s", surprisePct, tag)
                        } else {
                          "Reported"
                        },
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = surpriseColor,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                } else {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                  ) {
                    Text(
                      text = "Upcoming",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
              }
              Text(
                text = "${item.company} • ${item.sector}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              if (item.actualEps != null) {
                Text(
                  text = "Act ${item.actualEps}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = surpriseColor,
                )
                Text(
                  text = "Est ${item.expectedEps} • ${item.expectedRevenue}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                Text(
                  text = "Est ${item.expectedEps}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Rev ${item.expectedRevenue}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
