package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dedicated Media3 ExoPlayer controller for previewing and playing Price Alert alarm tones
 * using either bundled raw chime assets or user-selected local device audio URIs.
 */
class PriceAlertAlarmPlayer(context: Context) {
  private val appContext = context.applicationContext
  private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var exoPlayer: ExoPlayer? = null
  private var fallbackOnErrorToDefault = false

  private val _state = MutableStateFlow(AlarmPreviewState())
  val state: StateFlow<AlarmPreviewState> = _state.asStateFlow()

  private val listener =
    object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        val player = exoPlayer ?: return
        when (playbackState) {
          Player.STATE_READY -> {
            _state.value =
              _state.value.copy(
                isPlaying = player.playWhenReady && player.isPlaying,
                errorMessage = null,
              )
          }
          Player.STATE_ENDED,
          Player.STATE_IDLE -> {
            _state.value = _state.value.copy(isPlaying = false, activeToneId = null)
          }
          else -> Unit
        }
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        val current = _state.value
        if (current.activeToneId != null) {
          _state.value = current.copy(isPlaying = isPlaying)
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        Log.w(TAG, "Media3 alarm playback error (${error.errorCodeName})", error)
        if (fallbackOnErrorToDefault) {
          fallbackOnErrorToDefault = false
          val fallback = BundledAlarmTone.OPENING_BELL
          playInternal(fallback.id, buildBundledToneUri(fallback.rawResId), allowFallback = false)
        } else {
          _state.value =
            AlarmPreviewState(
              activeToneId = null,
              isPlaying = false,
              errorMessage = "Unable to play selected audio file. Try choosing another audio file or bundled tone.",
            )
        }
      }
    }

  fun previewBundled(tone: BundledAlarmTone) {
    mainScope.launch {
      val current = _state.value
      if (current.isPlaying && current.activeToneId == tone.id) {
        stopInternal()
        return@launch
      }
      playInternal(tone.id, buildBundledToneUri(tone.rawResId), allowFallback = false)
    }
  }

  fun previewCustomUri(uriString: String?) {
    mainScope.launch {
      if (uriString.isNullOrBlank()) {
        _state.value =
          AlarmPreviewState(
            activeToneId = null,
            isPlaying = false,
            errorMessage = "Choose an audio file from your device first.",
          )
        return@launch
      }
      val current = _state.value
      if (current.isPlaying && current.activeToneId == ALARM_TONE_CUSTOM) {
        stopInternal()
        return@launch
      }
      val parsed = runCatching { Uri.parse(uriString) }.getOrNull()
      if (parsed == null) {
        _state.value =
          AlarmPreviewState(
            activeToneId = null,
            isPlaying = false,
            errorMessage = "Invalid local audio file URI.",
          )
        return@launch
      }
      playInternal(ALARM_TONE_CUSTOM, parsed, allowFallback = false)
    }
  }

  fun playTriggeredAlertAlarm(
    alarmEnabled: Boolean,
    alarmToneId: String,
    alarmCustomUri: String?,
  ) {
    if (!alarmEnabled || alarmToneId == ALARM_TONE_SILENT) return
    mainScope.launch {
      when (alarmToneId) {
        ALARM_TONE_CUSTOM -> {
          val parsed = alarmCustomUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
          if (parsed != null) {
            playInternal(ALARM_TONE_CUSTOM, parsed, allowFallback = true)
          } else {
            val fallback = BundledAlarmTone.OPENING_BELL
            playInternal(fallback.id, buildBundledToneUri(fallback.rawResId), allowFallback = false)
          }
        }
        else -> {
          val tone = BundledAlarmTone.fromId(alarmToneId) ?: BundledAlarmTone.OPENING_BELL
          playInternal(tone.id, buildBundledToneUri(tone.rawResId), allowFallback = false)
        }
      }
    }
  }

  fun stop() {
    mainScope.launch { stopInternal() }
  }

  fun clearError() {
    _state.value = _state.value.copy(errorMessage = null)
  }

  fun release() {
    mainScope.launch {
      exoPlayer?.removeListener(listener)
      exoPlayer?.release()
      exoPlayer = null
      _state.value = AlarmPreviewState()
      mainScope.cancel()
    }
  }

  private fun stopInternal() {
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    _state.value = _state.value.copy(isPlaying = false, activeToneId = null)
  }

  private fun playInternal(toneId: String, uri: Uri, allowFallback: Boolean) {
    fallbackOnErrorToDefault = allowFallback
    val player = ensurePlayer()
    _state.value = AlarmPreviewState(activeToneId = toneId, isPlaying = true, errorMessage = null)
    try {
      player.stop()
      player.clearMediaItems()
      player.setMediaItem(MediaItem.fromUri(uri))
      player.prepare()
      player.playWhenReady = true
    } catch (e: Exception) {
      Log.w(TAG, "Failed preparing Media3 alarm URI: $uri", e)
      if (allowFallback) {
        fallbackOnErrorToDefault = false
        val fallback = BundledAlarmTone.OPENING_BELL
        playInternal(fallback.id, buildBundledToneUri(fallback.rawResId), allowFallback = false)
      } else {
        _state.value =
          AlarmPreviewState(
            activeToneId = null,
            isPlaying = false,
            errorMessage = "Could not open audio asset.",
          )
      }
    }
  }

  private fun ensurePlayer(): ExoPlayer {
    exoPlayer?.let { return it }
    val audioAttrs =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_ALARM)
        .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
        .build()
    return ExoPlayer.Builder(appContext)
      .setAudioAttributes(audioAttrs, false)
      .setHandleAudioBecomingNoisy(false)
      .build()
      .also { player ->
        player.addListener(listener)
        exoPlayer = player
      }
  }

  private companion object {
    const val TAG = "PriceAlertAlarmPlayer"
  }
}
