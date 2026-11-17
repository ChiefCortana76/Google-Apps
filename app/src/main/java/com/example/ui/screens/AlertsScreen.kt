package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.R
import com.example.audio.AlarmPreviewState
import com.example.audio.resolveActiveAlarmLabel
import com.example.data.model.AlertCondition
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.PriceAlert
import com.example.data.model.WatchlistItem
import com.example.data.repository.AppPreferences
import com.example.notifications.PriceAlertNotificationHelper
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun AlertsScreen(
  alertsState: UiState<List<PriceAlert>>,
  watchlistItems: List<WatchlistItem>,
  snapshot: MarketCommandSnapshot?,
  fcmTokenSynced: Boolean,
  preferences: AppPreferences,
  alarmPreviewState: AlarmPreviewState,
  prefillSymbol: String? = null,
  onClearPrefill: () -> Unit = {},
  onCreateAlert: (String, String, AlertCondition, Double) -> Unit,
  onToggleAlert: (String, Boolean) -> Unit,
  onRearmAlert: (String, AlertCondition, Double) -> Unit,
  onDeleteAlert: (String) -> Unit,
  onSyncFcmToken: () -> Unit,
  onPreviewActiveAlarm: () -> Unit,
  onStopAlarmPreview: () -> Unit,
  onOpenAlarmSettings: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler {
    onStopAlarmPreview()
    onBack()
  }

  val context = LocalContext.current
  val marketColors = LocalMarketColors.current
  var hasNotificationPermission by remember {
    mutableStateOf(PriceAlertNotificationHelper.hasNotificationPermission(context))
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
      if (granted) {
        PriceAlertNotificationHelper.ensureChannel(context)
        onSyncFcmToken()
      }
    }

  var showCreateDialog by remember { mutableStateOf(false) }
  var dialogInitialSymbol by remember { mutableStateOf("") }

  LaunchedEffect(prefillSymbol) {
    if (!prefillSymbol.isNullOrBlank()) {
      dialogInitialSymbol = prefillSymbol
      showCreateDialog = true
      onClearPrefill()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          dialogInitialSymbol = watchlistItems.firstOrNull()?.symbol ?: "SPY"
          showCreateDialog = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("create_price_alert_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Create price alert")
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("alerts_screen"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
              modifier = Modifier.testTag("alerts_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
              )
            }
            Column {
              Text(
                text = "Watchlist Price Alerts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "FCM push alerts synced to your account",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Surface(
            shape = RoundedCornerShape(999.dp),
            color =
              if (fcmTokenSynced) marketColors.gainSurface
              else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onSyncFcmToken),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(8.dp)
                    .clip(CircleShape)
                    .background(if (fcmTokenSynced) marketColors.gain else marketColors.amber)
              )
              Text(
                text = if (fcmTokenSynced) "FCM Ready" else "Sync FCM",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                  if (fcmTokenSynced) marketColors.gain
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Enable Push Notifications",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Allow system notifications so triggered watchlist targets alert you in the background.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                modifier = Modifier.testTag("grant_notifications_button"),
              ) {
                Text("Allow")
              }
            }
          }
        }
      }

      item {
        val activeAlarmLabel =
          resolveActiveAlarmLabel(
            alarmEnabled = preferences.alarmEnabled,
            alarmToneId = preferences.alarmToneId,
            alarmCustomLabel = preferences.alarmCustomLabel,
          )
        Card(
          shape = RoundedCornerShape(18.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
          modifier = Modifier.fillMaxWidth().testTag("alerts_alarm_summary_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Alarm,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Alert Alarm Tone",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = activeAlarmLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (preferences.alarmEnabled) {
              IconButton(
                onClick = {
                  if (alarmPreviewState.isPlaying) onStopAlarmPreview() else onPreviewActiveAlarm()
                },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("alerts_preview_alarm_button"),
              ) {
                Icon(
                  imageVector =
                    if (alarmPreviewState.isPlaying) Icons.Rounded.Stop
                    else Icons.Rounded.PlayArrow,
                  contentDescription =
                    if (alarmPreviewState.isPlaying) "Stop alarm preview"
                    else "Preview active alarm tone",
                )
              }
            }
            OutlinedButton(
              onClick = {
                onStopAlarmPreview()
                onOpenAlarmSettings()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("alerts_change_alarm_button"),
            ) {
              Text("Change")
            }
          }
        }
      }

      if (watchlistItems.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Quick Alert From Watchlist",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(watchlistItems.distinctBy { it.symbol }, key = { it.symbol }) { wlItem ->
                FilterChip(
                  selected = false,
                  onClick = {
                    dialogInitialSymbol = wlItem.symbol
                    showCreateDialog = true
                  },
                  label = { Text("+ ${wlItem.symbol}") },
                  modifier = Modifier.testTag("quick_alert_chip_${wlItem.symbol}"),
                )
              }
            }
          }
        }
      }

      when (alertsState) {
        is UiState.Loading -> {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
        }
        is UiState.Error -> {
          item {
            Text(
              text = alertsState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        is UiState.Success -> {
          val alerts = alertsState.data
          if (alerts.isEmpty()) {
            item {
              Card(
                shape = RoundedCornerShape(20.dp),
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                  ),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                  )
                  Text(
                    text = "No Price Alerts Set",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text =
                      "Tap a watchlist ticker above or + to set a target threshold. Notifications fire automatically via Firebase Cloud Messaging and live quote checks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Button(
                    onClick = {
                      dialogInitialSymbol = watchlistItems.firstOrNull()?.symbol ?: "SPY"
                      showCreateDialog = true
                    }
                  ) {
                    Text("Set First Price Alert")
                  }
                }
              }
            }
          } else {
            items(alerts, key = { it.id }) { alert ->
              val currentPrice = findLivePrice(alert.symbol, snapshot)
              PriceAlertCard(
                alert = alert,
                currentPrice = currentPrice,
                onToggle = { onToggleAlert(alert.id, it) },
                onRearm = {
                  onRearmAlert(
                    alert.id,
                    AlertCondition.fromWire(alert.condition),
                    alert.targetPrice,
                  )
                },
                onDelete = { onDeleteAlert(alert.id) },
              )
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreatePriceAlertDialog(
      initialSymbol = dialogInitialSymbol,
      watchlistItems = watchlistItems,
      snapshot = snapshot,
      onDismiss = { showCreateDialog = false },
      onConfirm = { symbol, name, condition, targetPrice ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
          permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onCreateAlert(symbol, name, condition, targetPrice)
        showCreateDialog = false
      },
    )
  }
}

@Composable
private fun PriceAlertCard(
  alert: PriceAlert,
  currentPrice: Double?,
  onToggle: (Boolean) -> Unit,
  onRearm: () -> Unit,
  onDelete: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val condition = AlertCondition.fromWire(alert.condition)
  val statusColor =
    when {
      alert.triggered -> marketColors.amber
      alert.enabled -> marketColors.gain
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
  val statusLabel =
    when {
      alert.triggered -> "Triggered"
      alert.enabled -> "Armed"
      else -> "Paused"
    }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().testTag("price_alert_card_${alert.symbol}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = alert.symbol,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Surface(
              color = statusColor.copy(alpha = 0.16f),
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
          Text(
            text = alert.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Switch(
          checked = alert.enabled,
          onCheckedChange = onToggle,
          modifier = Modifier.testTag("alert_switch_${alert.symbol}"),
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "${condition.label} ${String.format(Locale.US, "$%,.2f", alert.targetPrice)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color =
              if (condition == AlertCondition.ABOVE) marketColors.gain else marketColors.loss,
          )
          val priceNote =
            currentPrice?.let { price ->
              val diffPct = ((alert.targetPrice - price) / price.coerceAtLeast(0.01)) * 100.0
              String.format(
                Locale.US,
                "Live: $%,.2f (%+.1f%% to target)",
                price,
                diffPct,
              )
            } ?: "Live price updates on next refresh"
          Text(
            text = priceNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (alert.triggered) {
            OutlinedButton(
              onClick = onRearm,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Re-arm", style = MaterialTheme.typography.labelMedium)
            }
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = stringResource(R.string.cd_delete),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CreatePriceAlertDialog(
  initialSymbol: String,
  watchlistItems: List<WatchlistItem>,
  snapshot: MarketCommandSnapshot?,
  onDismiss: () -> Unit,
  onConfirm: (String, String, AlertCondition, Double) -> Unit,
) {
  val distinctItems = remember(watchlistItems) { watchlistItems.distinctBy { it.symbol } }
  var symbol by remember { mutableStateOf(initialSymbol.uppercase()) }
  var displayName by remember {
    mutableStateOf(
      distinctItems.firstOrNull { it.symbol.equals(initialSymbol, true) }?.displayName
        ?: findLiveQuote(initialSymbol, snapshot)?.name
        ?: initialSymbol
    )
  }
  var condition by remember { mutableStateOf(AlertCondition.ABOVE) }
  val livePrice = findLivePrice(symbol, snapshot)
  var targetPriceText by remember(symbol) {
    mutableStateOf(
      livePrice?.let { String.format(Locale.US, "%.2f", it * 1.02) } ?: ""
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Watchlist Price Alert") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (distinctItems.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(distinctItems.take(8), key = { it.symbol }) { item ->
              FilterChip(
                selected = symbol.equals(item.symbol, ignoreCase = true),
                onClick = {
                  symbol = item.symbol
                  displayName = item.displayName
                },
                label = { Text(item.symbol) },
              )
            }
          }
        }

        OutlinedTextField(
          value = symbol,
          onValueChange = {
            symbol = it.uppercase()
            displayName =
              distinctItems.firstOrNull { item -> item.symbol == symbol }?.displayName
                ?: findLiveQuote(symbol, snapshot)?.name
                ?: symbol
          },
          label = { Text("Ticker Symbol") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("alert_symbol_input"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AlertCondition.entries.forEach { cond ->
            FilterChip(
              selected = condition == cond,
              onClick = { condition = cond },
              label = { Text("${cond.symbolText} ${cond.label}") },
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (livePrice != null) {
          Text(
            text = String.format(Locale.US, "Current Price: $%,.2f", livePrice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val offsets = listOf(-5.0, -2.0, 2.0, 5.0)
            items(offsets) { pct ->
              FilterChip(
                selected = false,
                onClick = {
                  condition = if (pct >= 0) AlertCondition.ABOVE else AlertCondition.BELOW
                  targetPriceText =
                    String.format(Locale.US, "%.2f", livePrice * (1.0 + pct / 100.0))
                },
                label = { Text(String.format(Locale.US, "%+.0f%%", pct)) },
              )
            }
          }
        }

        OutlinedTextField(
          value = targetPriceText,
          onValueChange = { targetPriceText = it },
          label = { Text("Target Price ($)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("alert_target_price_input"),
        )
      }
    },
    confirmButton = {
      val parsed = targetPriceText.toDoubleOrNull()
      Button(
        onClick = {
          if (symbol.isNotBlank() && parsed != null && parsed > 0.0) {
            onConfirm(symbol.trim(), displayName.trim().ifBlank { symbol.trim() }, condition, parsed)
          }
        },
        enabled = symbol.isNotBlank() && parsed != null && parsed > 0.0,
        modifier = Modifier.testTag("confirm_create_alert_button"),
      ) {
        Text("Arm Alert")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

private fun findLiveQuote(symbol: String, snapshot: MarketCommandSnapshot?) =
  snapshot?.watchlistQuotes?.get(symbol.uppercase())
    ?: (snapshot?.indices.orEmpty() + snapshot?.futures.orEmpty())
      .firstOrNull {
        it.symbol.equals(symbol, true) || it.displaySymbol.equals(symbol, true)
      }

private fun findLivePrice(symbol: String, snapshot: MarketCommandSnapshot?): Double? =
  findLiveQuote(symbol, snapshot)?.price
