package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AlarmPreviewState
import com.example.audio.BundledAlarmTone
import com.example.data.model.RefreshInterval
import com.example.data.model.ThemeMode
import com.example.data.repository.AppPreferences
import com.example.ui.theme.LocalMarketColors
import com.example.ui.theme.isUsMarketOpenNow
import com.example.ui.theme.resolveDarkTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class ThemeOptionMeta(
  val mode: ThemeMode,
  val shortLabel: String,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val segmentTag: String,
  val rowTag: String,
)

private val THEME_OPTIONS =
  listOf(
    ThemeOptionMeta(
      mode = ThemeMode.LIGHT,
      shortLabel = "Light",
      title = "Light",
      subtitle = "Clean white Robinhood-style appearance",
      icon = Icons.Rounded.LightMode,
      segmentTag = "theme_segment_light",
      rowTag = "theme_option_light",
    ),
    ThemeOptionMeta(
      mode = ThemeMode.DARK,
      shortLabel = "Dark",
      title = "Dark",
      subtitle = "Pure black OLED background with emerald accents",
      icon = Icons.Rounded.DarkMode,
      segmentTag = "theme_segment_dark",
      rowTag = "theme_option_dark",
    ),
    ThemeOptionMeta(
      mode = ThemeMode.MARKET_AUTO,
      shortLabel = "Market Hours",
      title = "Market Hours",
      subtitle = "Light during U.S. market open, OLED Dark after close",
      icon = Icons.Rounded.Schedule,
      segmentTag = "theme_segment_market_hours",
      rowTag = "theme_option_auto",
    ),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  preferences: AppPreferences,
  alarmPreviewState: AlarmPreviewState,
  userEmail: String?,
  nowEt: ZonedDateTime = ZonedDateTime.now(ZoneId.of("America/New_York")),
  onSelectThemeMode: (ThemeMode) -> Unit,
  onUpdateMarketHours: (Int, Int) -> Unit,
  onSelectRefreshInterval: (RefreshInterval) -> Unit,
  onSetAlarmEnabled: (Boolean) -> Unit,
  onSelectBundledAlarmTone: (BundledAlarmTone) -> Unit,
  onSelectExistingCustomAlarmTone: () -> Unit,
  onSetCustomAlarmAudio: (String, String) -> Unit,
  onPreviewBundledAlarmTone: (BundledAlarmTone) -> Unit,
  onPreviewCustomAlarmTone: (String?) -> Unit,
  onStopAlarmPreview: () -> Unit,
  onSetBottomBarVisible: (Boolean) -> Unit,
  onMoveBottomBarTab: (String, Int) -> Unit,
  onSetBottomBarTabEnabled: (String, Boolean) -> Unit,
  onResetBottomBarDefaults: () -> Unit,
  onSignOut: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler {
    onStopAlarmPreview()
    onBack()
  }
  val marketColors = LocalMarketColors.current
  val marketOpenNow =
    isUsMarketOpenNow(preferences.marketOpenMinutesEt, preferences.marketCloseMinutesEt, nowEt)
  val activeDarkTheme =
    resolveDarkTheme(
      themeMode = preferences.themeMode,
      openMinutesEt = preferences.marketOpenMinutesEt,
      closeMinutesEt = preferences.marketCloseMinutesEt,
      nowEt = nowEt,
    )
  val isAutoEnabled = preferences.themeMode == ThemeMode.MARKET_AUTO
  val etTimeLabel = nowEt.format(DateTimeFormatter.ofPattern("EEE h:mm a 'ET'", Locale.US))

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(
      modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 640.dp).testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = {
                  onStopAlarmPreview()
                  onBack()
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                  contentDescription = stringResource(R.string.cd_back),
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(8.dp)
                      .clip(CircleShape)
                      .background(
                        if (activeDarkTheme) MaterialTheme.colorScheme.primary
                        else marketColors.amber
                      )
                )
                Text(
                  text = if (activeDarkTheme) "Active: Dark" else "Active: Light",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }

        // Theme Mode & DataStore Persistence Card
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Appearance & Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "Saved automatically on device via DataStore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              // Quick 3-way Segmented Toggle: Light | Dark | Market Hours
              SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().testTag("theme_segmented_toggle")
              ) {
                THEME_OPTIONS.forEachIndexed { index, option ->
                  val selected = preferences.themeMode == option.mode
                  SegmentedButton(
                    selected = selected,
                    onClick = { onSelectThemeMode(option.mode) },
                    shape =
                      SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = THEME_OPTIONS.size,
                      ),
                    icon = {
                      SegmentedButtonDefaults.Icon(active = selected) {
                        Icon(
                          imageVector = option.icon,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                        )
                      }
                    },
                    modifier = Modifier.testTag(option.segmentTag),
                  ) {
                    Text(
                      text = option.shortLabel,
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                  }
                }
              }

              // Detailed radio options
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                THEME_OPTIONS.forEach { option ->
                  ThemeOptionRow(
                    title = option.title,
                    subtitle = option.subtitle,
                    icon = option.icon,
                    selected = preferences.themeMode == option.mode,
                    onClick = { onSelectThemeMode(option.mode) },
                    tag = option.rowTag,
                  )
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Auto Theme by U.S. Market Hours",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text =
                      if (marketOpenNow) "$etTimeLabel • Open → Light Theme"
                      else "$etTimeLabel • Closed → Dark Theme",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                      if (marketOpenNow) marketColors.gain
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = isAutoEnabled,
                  onCheckedChange = { checked ->
                    onSelectThemeMode(if (checked) ThemeMode.MARKET_AUTO else ThemeMode.DARK)
                  },
                  modifier = Modifier.testTag("auto_theme_switch"),
                )
              }

              AnimatedVisibility(visible = isAutoEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Customizable Market Hours (ET)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  MarketTimeStepperRow(
                    label = "Market Open",
                    minutesSinceMidnight = preferences.marketOpenMinutesEt,
                    onStep = { delta ->
                      val next = (preferences.marketOpenMinutesEt + delta).coerceIn(0, 1425)
                      onUpdateMarketHours(next, preferences.marketCloseMinutesEt)
                    },
                  )
                  MarketTimeStepperRow(
                    label = "Market Close",
                    minutesSinceMidnight = preferences.marketCloseMinutesEt,
                    onStep = { delta ->
                      val next = (preferences.marketCloseMinutesEt + delta).coerceIn(15, 1439)
                      onUpdateMarketHours(preferences.marketOpenMinutesEt, next)
                    },
                  )
                }
              }
            }
          }
        }

        // Bottom Ribbon Tabs Customization
        item {
          BottomBarSettingsCard(
            preferences = preferences,
            onSetBottomBarVisible = onSetBottomBarVisible,
            onMoveTab = onMoveBottomBarTab,
            onSetTabEnabled = onSetBottomBarTabEnabled,
            onResetDefaults = onResetBottomBarDefaults,
          )
        }

        // Refresh Interval
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Refresh Interval",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "Auto-polls live indices, futures, breadth & news • Saved via DataStore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                  Text(
                    text = "Every ${preferences.refreshInterval.shortLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }

              SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().testTag("refresh_interval_segmented_toggle")
              ) {
                RefreshInterval.entries.forEachIndexed { index, interval ->
                  val selected = preferences.refreshInterval == interval
                  SegmentedButton(
                    selected = selected,
                    onClick = { onSelectRefreshInterval(interval) },
                    shape =
                      SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = RefreshInterval.entries.size,
                      ),
                    modifier = Modifier.testTag("refresh_segment_${interval.shortLabel}"),
                  ) {
                    Text(
                      text = interval.shortLabel,
                      maxLines = 1,
                      softWrap = false,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                  }
                }
              }

              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RefreshInterval.entries.forEach { interval ->
                  val selected = preferences.refreshInterval == interval
                  val borderColor =
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                  val containerColor =
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surface

                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(containerColor)
                        .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
                        .clickable { onSelectRefreshInterval(interval) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("refresh_option_${interval.seconds}"),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    RadioButton(
                      selected = selected,
                      onClick = { onSelectRefreshInterval(interval) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = interval.label,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                  }
                }
              }
            }
          }
        }

        // Alarm Tone (Media3 Bundled & Local Audio)
        item {
          AlarmSettingsCard(
            preferences = preferences,
            previewState = alarmPreviewState,
            onSetAlarmEnabled = onSetAlarmEnabled,
            onSelectBundledTone = onSelectBundledAlarmTone,
            onSelectExistingCustomTone = onSelectExistingCustomAlarmTone,
            onSetCustomAudio = onSetCustomAlarmAudio,
            onPreviewBundledTone = onPreviewBundledAlarmTone,
            onPreviewCustomTone = onPreviewCustomAlarmTone,
            onStopPreview = onStopAlarmPreview,
          )
        }

        // Account & Sign Out
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "Cloud Sync Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = userEmail ?: "Signed in with Google",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                  ),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_sign_out), fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ThemeOptionRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  tag: String,
) {
  val borderColor =
    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
  val containerColor =
    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    else MaterialTheme.colorScheme.surface

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(14.dp))
        .background(containerColor)
        .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp)
        .testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Spacer(modifier = Modifier.width(6.dp))
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun MarketTimeStepperRow(
  label: String,
  minutesSinceMidnight: Int,
  onStep: (Int) -> Unit,
) {
  val hour = minutesSinceMidnight / 60
  val min = minutesSinceMidnight % 60
  val formatted = String.format(Locale.US, "%02d:%02d ET", hour, min)

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = { onStep(-15) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label by 15 minutes")
      }
      Text(
        text = formatted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
      IconButton(
        onClick = { onStep(15) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Increase $label by 15 minutes")
      }
    }
  }
}
