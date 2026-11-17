package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.RadioPlayerState
import com.example.data.repository.CnbcAudioItem
import com.example.data.repository.CnbcRegion
import com.example.ui.theme.LocalMarketColors
import java.util.Locale

@Composable
fun RadioFeedView(
  radioState: RadioPlayerState,
  feedsByRegion: Map<CnbcRegion, List<CnbcAudioItem>>,
  onSelectRegionAndPlay: (CnbcRegion) -> Unit,
  onPlayLiveStream: (CnbcRegion) -> Unit,
  onPlayEpisode: (CnbcAudioItem) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekBy: (Long) -> Unit,
  onSeekTo: (Long) -> Unit,
  onStop: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val currentItems = feedsByRegion[radioState.selectedRegion].orEmpty()

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("cnbc_radio_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Active Media3 Player Card
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = radioState.selectedRegion.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
            }
            Surface(
              color =
                if (radioState.isPlaying) marketColors.gainSurface
                else MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = RoundedCornerShape(999.dp),
            ) {
              Text(
                text = radioState.modeLabel,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (radioState.isPlaying) marketColors.gain
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              )
            }
          }

          Text(
            text = radioState.activeTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          Text(
            text = "${radioState.selectedRegion.deskCity} • ${radioState.selectedRegion.showName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          radioState.statusMessage?.let { msg ->
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          // Playback Progress Bar for Seekable Audio Segments
          if (radioState.hasActiveSession && !radioState.isLiveStream && radioState.durationMs > 0L) {
            val duration = radioState.durationMs.coerceAtLeast(1L)
            val pos = radioState.positionMs.coerceIn(0L, duration)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Slider(
                value = pos.toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth().testTag("radio_progress_slider"),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = formatMillis(pos),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = formatMillis(duration),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            EqualizerBars(isActive = radioState.isPlaying)
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (radioState.hasActiveSession && !radioState.isLiveStream && radioState.durationMs > 0L) {
                IconButton(
                  onClick = { onSeekBy(-15_000L) },
                  modifier = Modifier.size(48.dp).testTag("radio_rewind_button"),
                ) {
                  Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 15 seconds")
                }
              }
              if (radioState.hasActiveSession || radioState.isBuffering) {
                IconButton(
                  onClick = onStop,
                  modifier =
                    Modifier.size(48.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                      .testTag("radio_stop_button"),
                ) {
                  Icon(Icons.Rounded.Stop, contentDescription = "Stop radio")
                }
              }
              IconButton(
                onClick = onTogglePlayPause,
                modifier =
                  Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("radio_play_pause_button"),
              ) {
                if (radioState.isBuffering) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Icon(
                    imageVector =
                      if (radioState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (radioState.isPlaying) "Pause radio" else "Play radio",
                    tint = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
              if (radioState.hasActiveSession && !radioState.isLiveStream && radioState.durationMs > 0L) {
                IconButton(
                  onClick = { onSeekBy(15_000L) },
                  modifier = Modifier.size(48.dp).testTag("radio_forward_button"),
                ) {
                  Icon(Icons.Rounded.Forward10, contentDescription = "Forward 15 seconds")
                }
              }
            }
          }

          OutlinedButton(
            onClick = { onPlayLiveStream(radioState.selectedRegion) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("radio_tune_live_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Stream ${radioState.selectedRegion.title} Live Desk",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }

    // Region Desks (CNBC USA, CNBC Europe, CNBC Asia)
    item {
      Text(
        text = "Global CNBC Desks",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CnbcRegion.entries.forEach { region ->
          val selected = radioState.selectedRegion == region
          Surface(
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onSelectRegionAndPlay(region) }
                .then(
                  if (selected) {
                    Modifier.border(
                      1.5.dp,
                      MaterialTheme.colorScheme.primary,
                      RoundedCornerShape(16.dp),
                    )
                  } else {
                    Modifier
                  }
                )
                .testTag("cnbc_region_${region.name.lowercase()}"),
            color =
              if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = region.shortLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = region.deskCity.substringBefore(" •"),
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    item {
      Text(
        text = "${radioState.selectedRegion.title} Segments & Broadcasts",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    items(currentItems, key = { it.id }) { episode ->
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onPlayEpisode(episode) }
            .testTag("cnbc_episode_${episode.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Podcasts,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = episode.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = episode.summary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${episode.publishedLabel} • ${episode.durationLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Play segment",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
fun CnbcRadioMiniPlayer(
  radioState: RadioPlayerState,
  onTogglePlayPause: () -> Unit,
  onStop: () -> Unit,
  onOpenRadioScreen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onOpenRadioScreen)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        .testTag("cnbc_mini_player"),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 4.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Radio,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = radioState.selectedRegion.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "• ${radioState.modeLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = if (radioState.isPlaying) marketColors.gain else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = radioState.activeTitle,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onTogglePlayPause,
        modifier = Modifier.size(48.dp).testTag("mini_player_play_pause"),
      ) {
        if (radioState.isBuffering) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Icon(
            imageVector = if (radioState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (radioState.isPlaying) "Pause CNBC radio" else "Play CNBC radio",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      IconButton(
        onClick = onStop,
        modifier = Modifier.size(48.dp).testTag("mini_player_stop"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Dismiss CNBC radio player",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun RadioDashboardStrip(
  radioState: RadioPlayerState,
  onSelectRegion: (CnbcRegion) -> Unit,
  onOpenRadioScreen: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenRadioScreen),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "CNBC Live Radio",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = if (radioState.isPlaying) "PLAYING • ${radioState.selectedRegion.shortLabel}" else "Listen Live →",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CnbcRegion.entries.forEach { region ->
          val isActive = radioState.isPlaying && radioState.selectedRegion == region
          Surface(
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSelectRegion(region) }
                .testTag("dashboard_cnbc_${region.name.lowercase()}"),
            color =
              if (isActive) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = if (isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play ${region.title}",
                tint =
                  if (isActive) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = region.shortLabel,
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (isActive) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

private fun formatMillis(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val min = totalSec / 60L
  val sec = totalSec % 60L
  return String.format(Locale.US, "%02d:%02d", min, sec)
}

@Composable
private fun EqualizerBars(isActive: Boolean) {
  val transition = rememberInfiniteTransition(label = "eq")
  val b1 by
    transition.animateFloat(
      initialValue = 0.25f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
      label = "b1",
    )
  val b2 by
    transition.animateFloat(
      initialValue = 0.85f,
      targetValue = 0.30f,
      animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
      label = "b2",
    )
  val b3 by
    transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 0.95f,
      animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
      label = "b3",
    )
  val heights = if (isActive) listOf(b1, b2, b3, b2, b1) else listOf(0.25f, 0.25f, 0.25f, 0.25f, 0.25f)

  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.Bottom,
    modifier = Modifier.height(26.dp),
  ) {
    heights.forEach { frac ->
      Box(
        modifier =
          Modifier.width(5.dp)
            .height((24 * frac).dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
              if (isActive) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outline
            )
      )
    }
  }
}
