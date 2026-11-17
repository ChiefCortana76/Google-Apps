package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.ALARM_TONE_CUSTOM
import com.example.audio.AlarmPreviewState
import com.example.audio.BundledAlarmTone
import com.example.audio.queryAudioDisplayName
import com.example.audio.resolveActiveAlarmLabel
import com.example.data.repository.AppPreferences

@Composable
fun AlarmSettingsCard(
  preferences: AppPreferences,
  previewState: AlarmPreviewState,
  onSetAlarmEnabled: (Boolean) -> Unit,
  onSelectBundledTone: (BundledAlarmTone) -> Unit,
  onSelectExistingCustomTone: () -> Unit,
  onSetCustomAudio: (uriString: String, displayName: String) -> Unit,
  onPreviewBundledTone: (BundledAlarmTone) -> Unit,
  onPreviewCustomTone: (String?) -> Unit,
  onStopPreview: () -> Unit,
) {
  val context = LocalContext.current
  val activeLabel =
    resolveActiveAlarmLabel(
      alarmEnabled = preferences.alarmEnabled,
      alarmToneId = preferences.alarmToneId,
      alarmCustomLabel = preferences.alarmCustomLabel,
    )

  val pickAudioLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        val displayName = queryAudioDisplayName(context, uri)
        val uriString = uri.toString()
        onSetCustomAudio(uriString, displayName)
        onPreviewCustomTone(uriString)
      }
    }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.testTag("alarm_settings_card"),
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
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
          ) {
            Icon(
              imageVector = Icons.Rounded.Alarm,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp).size(20.dp),
            )
          }
          Column {
            Text(
              text = stringResource(R.string.alarm_settings_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = stringResource(R.string.alarm_settings_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
      ) {
        Text(
          text = "Active: $activeLabel",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Play Alarm Sound on Price Alert",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Uses Media3 audio playback when a watchlist price threshold hits",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = preferences.alarmEnabled,
          onCheckedChange = { enabled ->
            if (!enabled) onStopPreview()
            onSetAlarmEnabled(enabled)
          },
          modifier = Modifier.testTag("alarm_audio_switch"),
        )
      }

      AnimatedVisibility(visible = preferences.alarmEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          if (!previewState.errorMessage.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = previewState.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
              )
            }
          }

          Text(
            text = "Bundled Market Chimes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          BundledAlarmTone.entries.forEach { tone ->
            val selected = preferences.alarmToneId == tone.id
            val isThisPlaying = previewState.isPlaying && previewState.activeToneId == tone.id
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
                  .clickable { onSelectBundledTone(tone) }
                  .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                  .testTag("alarm_tone_${tone.id}"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = selected, onClick = { onSelectBundledTone(tone) })
              Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint =
                  if (selected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = tone.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  )
                  Text(
                    text = tone.durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = tone.subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { onPreviewBundledTone(tone) },
                colors =
                  IconButtonDefaults.iconButtonColors(
                    containerColor =
                      if (isThisPlaying) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor =
                      if (isThisPlaying) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurface,
                  ),
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("alarm_preview_${tone.id}"),
              ) {
                Icon(
                  imageVector = if (isThisPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                  contentDescription =
                    if (isThisPlaying) stringResource(R.string.cd_stop_alarm_preview, tone.label)
                    else stringResource(R.string.cd_play_alarm_preview, tone.label),
                )
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Text(
            text = "Local Device Audio",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          val hasCustomUri = !preferences.alarmCustomUri.isNullOrBlank()
          val customSelected = preferences.alarmToneId == ALARM_TONE_CUSTOM && hasCustomUri
          val customPlaying =
            previewState.isPlaying && previewState.activeToneId == ALARM_TONE_CUSTOM
          val customBorder =
            if (customSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
          val customBg =
            if (customSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface

          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(customBg)
                .border(width = 1.dp, color = customBorder, shape = RoundedCornerShape(14.dp))
                .clickable {
                  if (hasCustomUri) onSelectExistingCustomTone()
                  else pickAudioLauncher.launch(arrayOf("audio/*"))
                }
                .padding(10.dp)
                .testTag("alarm_tone_custom_local"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = customSelected,
                onClick = {
                  if (hasCustomUri) onSelectExistingCustomTone()
                  else pickAudioLauncher.launch(arrayOf("audio/*"))
                },
              )
              Icon(
                imageVector = Icons.Rounded.AudioFile,
                contentDescription = null,
                tint =
                  if (customSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    preferences.alarmCustomLabel?.takeIf { it.isNotBlank() }
                      ?: "Custom Audio File",
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = if (customSelected) FontWeight.SemiBold else FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    if (hasCustomUri) "Stored local audio asset via Media3"
                    else "Pick any MP3, WAV, M4A, or OGG file on device",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (hasCustomUri) {
                IconButton(
                  onClick = { onPreviewCustomTone(preferences.alarmCustomUri) },
                  colors =
                    IconButtonDefaults.iconButtonColors(
                      containerColor =
                        if (customPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                      contentColor =
                        if (customPlaying) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("alarm_preview_custom_local"),
                ) {
                  Icon(
                    imageVector =
                      if (customPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription =
                      if (customPlaying) "Stop custom audio preview"
                      else "Play custom audio preview",
                  )
                }
              }
            }

            OutlinedButton(
              onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) },
              shape = RoundedCornerShape(12.dp),
              modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag("choose_custom_audio_button"),
            ) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (hasCustomUri) "Change Local Audio File" else "Choose Local Audio File",
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }
  }
}
