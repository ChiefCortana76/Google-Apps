package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.repository.CnbcAudioItem
import com.example.data.repository.CnbcRegion
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RadioPlayerState(
  val selectedRegion: CnbcRegion = CnbcRegion.USA,
  val activeTitle: String = CnbcRegion.USA.showName,
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isBuffering: Boolean = false,
  val hasActiveSession: Boolean = false,
  val isLiveStream: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val modeLabel: String = "READY",
  val statusMessage: String? = null,
)

class CnbcRadioPlayerManager(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var exoPlayer: ExoPlayer? = null
  private var progressJob: Job? = null
  private var usingTtsFallback = false
  private var activeFallbackBriefing: String = ""
  private val pendingStreamCandidates = ArrayDeque<String>()

  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var pendingSpeechText: String? = null

  private val _state = MutableStateFlow(RadioPlayerState())
  val state: StateFlow<RadioPlayerState> = _state.asStateFlow()

  private val playerListener =
    object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        val player = exoPlayer ?: return
        when (playbackState) {
          Player.STATE_BUFFERING -> {
            _state.value =
              _state.value.copy(
                isBuffering = true,
                isPlaying = false,
                hasActiveSession = true,
                modeLabel = "BUFFERING",
              )
          }
          Player.STATE_READY -> {
            val isLive = player.isCurrentMediaItemLive || _state.value.isLiveStream
            val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            val playing = player.playWhenReady && player.isPlaying
            _state.value =
              _state.value.copy(
                isBuffering = false,
                isPlaying = playing,
                isPaused = !playing && _state.value.hasActiveSession,
                isLiveStream = isLive,
                durationMs = duration,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                modeLabel =
                  when {
                    !playing -> "PAUSED"
                    isLive -> "LIVE STREAM"
                    else -> "CNBC AUDIO"
                  },
                statusMessage = null,
              )
            if (playing) startProgressLoop() else stopProgressLoop()
          }
          Player.STATE_ENDED -> {
            stopProgressLoop()
            _state.value =
              _state.value.copy(
                isPlaying = false,
                isPaused = false,
                isBuffering = false,
                positionMs = _state.value.durationMs,
                modeLabel = "ENDED",
              )
          }
          Player.STATE_IDLE -> {
            stopProgressLoop()
          }
        }
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        val player = exoPlayer ?: return
        if (usingTtsFallback) return
        val current = _state.value
        if (!current.hasActiveSession) return
        val isLive = player.isCurrentMediaItemLive || current.isLiveStream
        if (isPlaying) {
          startProgressLoop()
          _state.value =
            current.copy(
              isPlaying = true,
              isPaused = false,
              isBuffering = false,
              isLiveStream = isLive,
              modeLabel = if (isLive) "LIVE STREAM" else "CNBC AUDIO",
            )
        } else if (player.playbackState == Player.STATE_READY) {
          stopProgressLoop()
          _state.value =
            current.copy(
              isPlaying = false,
              isPaused = true,
              isBuffering = false,
              modeLabel = "PAUSED",
            )
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        val current = _state.value
        val nextUrl = pendingStreamCandidates.removeFirstOrNull()
        if (nextUrl != null) {
          Log.w(
            TAG,
            "Media3 playback error (${error.errorCodeName}) for ${current.selectedRegion}; trying next CNBC stream candidate",
            error,
          )
          prepareAndPlayUrl(
            region = current.selectedRegion,
            title = current.activeTitle,
            url = nextUrl,
            fallbackBriefingText = activeFallbackBriefing,
          )
          return
        }
        Log.w(
          TAG,
          "Media3 playback error (${error.errorCodeName}) for ${current.selectedRegion}; falling back to Desk Wire Audio",
          error,
        )
        switchToDeskBriefing(
          region = current.selectedRegion,
          title = current.activeTitle,
          briefingText = activeFallbackBriefing,
        )
      }
    }

  init {
    tts =
      TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          tts?.language = Locale.US
          ttsReady = true
          tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                _state.value =
                  _state.value.copy(
                    isPlaying = true,
                    isPaused = false,
                    isBuffering = false,
                    hasActiveSession = true,
                    modeLabel = "DESK WIRE AUDIO",
                  )
              }

              override fun onDone(utteranceId: String?) {
                _state.value =
                  _state.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    isBuffering = false,
                    modeLabel = "COMPLETED",
                  )
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                _state.value =
                  _state.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    isBuffering = false,
                    statusMessage = "Audio playback unavailable on this device.",
                  )
              }
            }
          )
          pendingSpeechText?.let { text ->
            pendingSpeechText = null
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cnbc_brief")
          }
        }
      }
  }

  private var activeIsLiveFeed = false

  private fun getOrCreatePlayer(): ExoPlayer {
    exoPlayer?.let { return it }
    val audioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .build()
    val httpFactory =
      DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Android 14; Mobile) SquawkOnTheStreet/4.0")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8000)
        .setReadTimeoutMs(8000)
    val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory))
    return ExoPlayer.Builder(appContext)
      .setMediaSourceFactory(mediaSourceFactory)
      .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
      .setHandleAudioBecomingNoisy(true)
      .build()
      .also { player ->
        player.addListener(playerListener)
        exoPlayer = player
      }
  }

  fun selectRegionAndPlay(region: CnbcRegion, items: List<CnbcAudioItem>) {
    val firstPlayable = items.firstOrNull { !it.audioUrl.isNullOrBlank() } ?: items.firstOrNull()
    val streamUrl = firstPlayable?.audioUrl ?: region.liveStreamUrl
    val title = firstPlayable?.title ?: region.showName
    val briefingText =
      "${region.title}, ${region.deskCity}. ${firstPlayable?.title ?: region.showName}. ${firstPlayable?.summary ?: ""}"
    playAudioUrls(
      region = region,
      title = title,
      urls = listOf(streamUrl),
      isLiveFeed = streamUrl == region.liveStreamUrl || firstPlayable?.durationLabel == "LIVE",
      fallbackBriefingText = briefingText,
    )
  }

  fun playLiveRegionStream(
    region: CnbcRegion,
    streamCandidates: List<String> = listOf(region.liveStreamUrl),
  ) {
    val briefingText =
      "${region.title} Live Desk from ${region.deskCity}. ${region.showName} market coverage."
    playAudioUrls(
      region = region,
      title = "${region.title} Live Broadcast",
      urls = streamCandidates.ifEmpty { listOf(region.liveStreamUrl) },
      isLiveFeed = true,
      fallbackBriefingText = briefingText,
    )
  }

  fun playEpisode(item: CnbcAudioItem) {
    val url = item.audioUrl ?: item.region.liveStreamUrl
    val briefingText = "${item.region.title}. ${item.title}. ${item.summary}"
    playAudioUrls(
      region = item.region,
      title = item.title,
      urls = listOf(url),
      isLiveFeed = url == item.region.liveStreamUrl || item.durationLabel == "LIVE",
      fallbackBriefingText = briefingText,
    )
  }

  fun togglePlayPause(regionItems: List<CnbcAudioItem>) {
    val current = _state.value
    val player = exoPlayer
    if (!usingTtsFallback && player != null && current.hasActiveSession) {
      if (player.isPlaying) {
        player.pause()
      } else if (current.isPaused || player.playbackState == Player.STATE_READY) {
        player.play()
      } else {
        selectRegionAndPlay(current.selectedRegion, regionItems)
      }
      return
    }
    if (current.isPlaying || current.isBuffering) {
      stop()
    } else {
      selectRegionAndPlay(current.selectedRegion, regionItems)
    }
  }

  fun seekBy(deltaMs: Long) {
    val player = exoPlayer ?: return
    if (usingTtsFallback || _state.value.isLiveStream) return
    val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: Long.MAX_VALUE
    val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
    player.seekTo(target)
    _state.value = _state.value.copy(positionMs = target)
  }

  fun seekTo(positionMs: Long) {
    val player = exoPlayer ?: return
    if (usingTtsFallback || _state.value.isLiveStream) return
    val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: Long.MAX_VALUE
    val target = positionMs.coerceIn(0L, duration)
    player.seekTo(target)
    _state.value = _state.value.copy(positionMs = target)
  }

  fun stop() {
    stopProgressLoop()
    usingTtsFallback = false
    pendingStreamCandidates.clear()
    activeIsLiveFeed = false
    runCatching { exoPlayer?.stop() }
    runCatching { tts?.stop() }
    pendingSpeechText = null
    _state.value =
      _state.value.copy(
        isPlaying = false,
        isPaused = false,
        isBuffering = false,
        hasActiveSession = false,
        positionMs = 0L,
        durationMs = 0L,
        modeLabel = "READY",
        statusMessage = null,
      )
  }

  private fun playAudioUrls(
    region: CnbcRegion,
    title: String,
    urls: List<String>,
    isLiveFeed: Boolean,
    fallbackBriefingText: String,
  ) {
    stopProgressLoop()
    runCatching { tts?.stop() }
    pendingSpeechText = null
    usingTtsFallback = false
    activeFallbackBriefing = fallbackBriefingText
    activeIsLiveFeed = isLiveFeed

    pendingStreamCandidates.clear()
    val distinctUrls = urls.filter { it.isNotBlank() }.distinct()
    val firstUrl = distinctUrls.firstOrNull() ?: region.liveStreamUrl
    distinctUrls.drop(1).forEach { pendingStreamCandidates.addLast(it) }

    prepareAndPlayUrl(
      region = region,
      title = title,
      url = firstUrl,
      fallbackBriefingText = fallbackBriefingText,
    )
  }

  private fun prepareAndPlayUrl(
    region: CnbcRegion,
    title: String,
    url: String,
    fallbackBriefingText: String,
  ) {
    val isHls = url.contains(".m3u8", ignoreCase = true)
    _state.value =
      RadioPlayerState(
        selectedRegion = region,
        activeTitle = title,
        isPlaying = false,
        isPaused = false,
        isBuffering = true,
        hasActiveSession = true,
        isLiveStream = isHls || activeIsLiveFeed,
        positionMs = 0L,
        durationMs = 0L,
        modeLabel = "CONNECTING",
        statusMessage = null,
      )

    scope.launch {
      try {
        val player = getOrCreatePlayer()
        val mediaItem =
          if (isHls) {
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
          } else {
            MediaItem.fromUri(url)
          }
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
      } catch (e: Exception) {
        Log.w(TAG, "Media3 player initialization failed; switching to Desk Wire Audio", e)
        switchToDeskBriefing(region, title, fallbackBriefingText)
      }
    }
  }

  private fun switchToDeskBriefing(
    region: CnbcRegion,
    title: String,
    briefingText: String,
  ) {
    stopProgressLoop()
    usingTtsFallback = true
    runCatching { exoPlayer?.stop() }
    _state.value =
      RadioPlayerState(
        selectedRegion = region,
        activeTitle = title,
        isPlaying = true,
        isPaused = false,
        isBuffering = false,
        hasActiveSession = true,
        isLiveStream = false,
        positionMs = 0L,
        durationMs = 0L,
        modeLabel = "DESK WIRE AUDIO",
        statusMessage = "Live CDN restricted; playing CNBC ${region.shortLabel} audio wire briefing.",
      )
    if (ttsReady) {
      tts?.speak(briefingText, TextToSpeech.QUEUE_FLUSH, null, "cnbc_brief")
    } else {
      pendingSpeechText = briefingText
    }
  }

  private fun startProgressLoop() {
    if (progressJob?.isActive == true) return
    progressJob =
      scope.launch {
        while (isActive) {
          val player = exoPlayer
          if (player != null && player.isPlaying && !usingTtsFallback) {
            val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            _state.value =
              _state.value.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
              )
          }
          delay(500L)
        }
      }
  }

  private fun stopProgressLoop() {
    progressJob?.cancel()
    progressJob = null
  }

  fun release() {
    stop()
    runCatching {
      exoPlayer?.removeListener(playerListener)
      exoPlayer?.release()
    }
    exoPlayer = null
    runCatching { tts?.shutdown() }
    tts = null
    scope.cancel()
  }

  private companion object {
    const val TAG = "CnbcRadioPlayer"
  }
}
