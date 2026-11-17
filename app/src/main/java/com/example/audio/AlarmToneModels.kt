package com.example.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import com.example.R

const val ALARM_TONE_CUSTOM = "custom_local"
const val ALARM_TONE_SILENT = "silent"

enum class BundledAlarmTone(
  val id: String,
  val label: String,
  val subtitle: String,
  val durationLabel: String,
  @RawRes val rawResId: Int,
) {
  OPENING_BELL(
    id = "opening_bell",
    label = "Opening Bell",
    subtitle = "Crisp NYSE brass double-bell chime",
    durationLabel = "1.4s",
    rawResId = R.raw.tone_opening_bell,
  ),
  BULLISH_CHIME(
    id = "bullish_chime",
    label = "Bullish Chime",
    subtitle = "Ascending major triad chime",
    durationLabel = "1.3s",
    rawResId = R.raw.tone_bullish_chime,
  ),
  SONAR_PING(
    id = "sonar_ping",
    label = "Sonar Ping",
    subtitle = "High-clarity dual desk radar ping",
    durationLabel = "1.1s",
    rawResId = R.raw.tone_sonar_ping,
  ),
  CLOSING_GONG(
    id = "closing_gong",
    label = "Closing Gong",
    subtitle = "Warm resonant market closing bell",
    durationLabel = "1.6s",
    rawResId = R.raw.tone_closing_gong,
  );

  companion object {
    fun fromId(id: String?): BundledAlarmTone? = entries.firstOrNull { it.id == id }
  }
}

data class AlarmPreviewState(
  val activeToneId: String? = null,
  val isPlaying: Boolean = false,
  val errorMessage: String? = null,
)

fun resolveActiveAlarmLabel(
  alarmEnabled: Boolean,
  alarmToneId: String,
  alarmCustomLabel: String?,
): String {
  if (!alarmEnabled || alarmToneId == ALARM_TONE_SILENT) return "Muted (Notification only)"
  if (alarmToneId == ALARM_TONE_CUSTOM) {
    val label = alarmCustomLabel?.takeIf { it.isNotBlank() } ?: "Custom Device Audio"
    return "Custom: $label"
  }
  return BundledAlarmTone.fromId(alarmToneId)?.label ?: BundledAlarmTone.OPENING_BELL.label
}

@androidx.annotation.OptIn(UnstableApi::class)
fun buildBundledToneUri(@RawRes rawResId: Int): Uri =
  RawResourceDataSource.buildRawResourceUri(rawResId)

fun queryAudioDisplayName(context: Context, uri: Uri): String {
  runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) {
          val name = cursor.getString(idx)
          if (!name.isNullOrBlank()) return name
        }
      }
    }
  }
  return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Custom Audio"
}
