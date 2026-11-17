package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ALARM_TONE_CUSTOM
import com.example.audio.AlarmPreviewState
import com.example.audio.BundledAlarmTone
import com.example.audio.CnbcRadioPlayerManager
import com.example.audio.PriceAlertAlarmPlayer
import com.example.audio.RadioPlayerState
import com.example.data.model.AlertCondition
import com.example.data.model.AssetClass
import com.example.data.model.ChartTimeframe
import com.example.data.model.MarketAiBriefing
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.model.PortfolioHolding
import com.example.data.model.PortfolioSummary
import com.example.data.model.PriceAlert
import com.example.data.model.Watchlist
import com.example.data.model.WatchlistItem
import com.example.data.repository.AlertsRepository
import com.example.data.repository.AppPreferences
import com.example.data.repository.ChartSeriesResult
import com.example.data.repository.CnbcAudioItem
import com.example.data.repository.CnbcRadioRepository
import com.example.data.repository.CnbcRegion
import com.example.data.repository.MarketAiRepository
import com.example.data.repository.MarketDataRepository
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.TickerSearchResult
import com.example.data.repository.WatchlistRepository
import com.example.notifications.PriceAlertNotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiAssistantUiState {
  data object Idle : AiAssistantUiState
  data class Loading(val question: String) : AiAssistantUiState
  data class Streaming(val question: String, val answer: String) : AiAssistantUiState
  data class Success(val question: String, val answer: String) : AiAssistantUiState
  data class Error(val question: String, val message: String) : AiAssistantUiState
}

class MarketViewModel(
  private val appContext: Context,
  private val marketDataRepository: MarketDataRepository,
  private val watchlistRepository: WatchlistRepository,
  private val alertsRepository: AlertsRepository,
  private val portfolioRepository: PortfolioRepository,
  private val marketAiRepository: MarketAiRepository,
  private val preferencesRepository: PreferencesRepository,
  private val cnbcRadioRepository: CnbcRadioRepository,
  private val radioPlayerManager: CnbcRadioPlayerManager,
  private val currentUserId: String,
  private val alarmPlayer: PriceAlertAlarmPlayer = PriceAlertAlarmPlayer(appContext),
) : ViewModel() {

  val watchlistsState: StateFlow<UiState<List<Watchlist>>> =
    watchlistRepository
      .observeWatchlists(currentUserId)
      .asFirestoreUiState(TAG, "Failed to load watchlists")
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val watchlistItemsState: StateFlow<UiState<List<WatchlistItem>>> =
    watchlistRepository
      .observeWatchlistItems(currentUserId)
      .asFirestoreUiState(TAG, "Failed to load watchlist items")
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val alertsState: StateFlow<UiState<List<PriceAlert>>> =
    alertsRepository
      .observePriceAlerts(currentUserId)
      .asFirestoreUiState(TAG, "Failed to load price alerts")
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val holdingsState: StateFlow<UiState<List<PortfolioHolding>>> =
    portfolioRepository
      .observeHoldings(currentUserId)
      .asFirestoreUiState(TAG, "Failed to load portfolio holdings")
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  private val _marketSnapshot = MutableStateFlow<UiState<MarketCommandSnapshot>>(UiState.Loading)
  val marketSnapshot: StateFlow<UiState<MarketCommandSnapshot>> = _marketSnapshot.asStateFlow()

  val portfolioSummary: StateFlow<PortfolioSummary?> =
    combine(holdingsState, _marketSnapshot) { holdingsUi, snapUi ->
      val holdings = (holdingsUi as? UiState.Success)?.data ?: return@combine null
      val snap = (snapUi as? UiState.Success)?.data
      computePortfolioSummary(holdings, snap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  private val _isRefreshing = MutableStateFlow(false)
  val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

  private val _isPullRefreshing = MutableStateFlow(false)
  val isPullRefreshing: StateFlow<Boolean> = _isPullRefreshing.asStateFlow()

  private val _secondsToRefresh = MutableStateFlow(30)
  val secondsToRefresh: StateFlow<Int> = _secondsToRefresh.asStateFlow()

  private val _selectedQuote = MutableStateFlow<MarketQuote?>(null)
  val selectedQuote: StateFlow<MarketQuote?> = _selectedQuote.asStateFlow()

  private val _selectedTimeframe = MutableStateFlow(ChartTimeframe.D1)
  val selectedTimeframe: StateFlow<ChartTimeframe> = _selectedTimeframe.asStateFlow()

  private val _chartSeries = MutableStateFlow<UiState<ChartSeriesResult>>(UiState.Loading)
  val chartSeries: StateFlow<UiState<ChartSeriesResult>> = _chartSeries.asStateFlow()

  private val _aiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
  val aiState: StateFlow<AiAssistantUiState> = _aiState.asStateFlow()

  private val _aiBriefingState = MutableStateFlow<UiState<MarketAiBriefing>?>(null)
  val aiBriefingState: StateFlow<UiState<MarketAiBriefing>?> = _aiBriefingState.asStateFlow()

  private val _fcmTokenSynced = MutableStateFlow(false)
  val fcmTokenSynced: StateFlow<Boolean> = _fcmTokenSynced.asStateFlow()

  private val _radioFeeds =
    MutableStateFlow<Map<CnbcRegion, List<CnbcAudioItem>>>(emptyMap())
  val radioFeeds: StateFlow<Map<CnbcRegion, List<CnbcAudioItem>>> = _radioFeeds.asStateFlow()

  val radioState: StateFlow<RadioPlayerState> = radioPlayerManager.state
  val alarmPreviewState: StateFlow<AlarmPreviewState> = alarmPlayer.state

  private val _marketClockEt = MutableStateFlow(ZonedDateTime.now(ET_ZONE))
  val marketClockEt: StateFlow<ZonedDateTime> = _marketClockEt.asStateFlow()

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchResults = MutableStateFlow<List<TickerSearchResult>>(emptyList())
  val searchResults: StateFlow<List<TickerSearchResult>> = _searchResults.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private var chartJob: Job? = null
  private var aiJob: Job? = null
  private var aiBriefingJob: Job? = null
  private var refreshJob: Job? = null
  private var searchJob: Job? = null
  private var currentIntervalSeconds: Int = 30
  private var trackedExtraSymbols: List<String> = emptyList()
  @Volatile private var latestPreferences: AppPreferences = AppPreferences()
  @Volatile private var lastSnapshotDateEt: LocalDate? = null
  @Volatile private var lastSnapshotDateLocal: LocalDate? = null

  init {
    PriceAlertNotificationHelper.ensureChannel(appContext)
    syncFcmToken()

    viewModelScope.launch {
      preferencesRepository.localPreferences.collect { prefs ->
        latestPreferences = prefs
        _marketClockEt.value = ZonedDateTime.now(ET_ZONE)
      }
    }

    viewModelScope.launch {
      try {
        _radioFeeds.value = cnbcRadioRepository.fetchAllRegions()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Radio feed load failed", e)
      }
    }

    viewModelScope.launch {
      try {
        watchlistRepository.seedDefaultWatchlistsIfEmpty(currentUserId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Watchlist seeding skipped or failed", e)
      }
    }

    viewModelScope.launch {
      combine(watchlistItemsState, holdingsState, alertsState) { wlUi, holdUi, alertUi ->
        val wlSymbols = (wlUi as? UiState.Success)?.data?.map { it.symbol }.orEmpty()
        val holdSymbols = (holdUi as? UiState.Success)?.data?.map { it.symbol }.orEmpty()
        val alertSymbols =
          (alertUi as? UiState.Success)?.data?.filter { it.enabled }?.map { it.symbol }.orEmpty()
        (wlSymbols + holdSymbols + alertSymbols).map { it.trim().uppercase() }.distinct()
      }.collect { symbols ->
        val changed = symbols != trackedExtraSymbols
        trackedExtraSymbols = symbols
        if (changed && _marketSnapshot.value is UiState.Success) {
          refreshMarketNow(manual = false)
        }
      }
    }

    viewModelScope.launch {
      preferencesRepository.localPreferences
        .map { it.refreshInterval.seconds }
        .distinctUntilChanged()
        .collectLatest { intervalSeconds ->
          currentIntervalSeconds = intervalSeconds
          while (true) {
            refreshMarketNow(manual = false)
            _secondsToRefresh.value = intervalSeconds
            while (_secondsToRefresh.value > 0) {
              delay(1000L)
              val curEtDate = ZonedDateTime.now(ET_ZONE).toLocalDate()
              val curLocalDate = LocalDate.now()
              val rolledDay =
                lastSnapshotDateEt != null &&
                  (curEtDate != lastSnapshotDateEt || curLocalDate != lastSnapshotDateLocal)
              if (rolledDay) {
                _marketClockEt.value = ZonedDateTime.now(ET_ZONE)
                break
              }
              _secondsToRefresh.value = (_secondsToRefresh.value - 1).coerceAtLeast(0)
            }
          }
        }
    }
  }

  fun syncFcmToken() {
    viewModelScope.launch {
      runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        alertsRepository.upsertFcmToken(token).getOrThrow()
      }.onSuccess {
        _fcmTokenSynced.value = true
      }.onFailure { error ->
        Log.w(TAG, "FCM token sync deferred/unavailable", error)
      }
    }
  }

  fun checkNewDayAndRefreshIfNeeded() {
    val nowEt = ZonedDateTime.now(ET_ZONE)
    _marketClockEt.value = nowEt
    val curLocalDate = LocalDate.now()
    val prevEt = lastSnapshotDateEt
    val prevLocal = lastSnapshotDateLocal
    if (prevEt == null || nowEt.toLocalDate() != prevEt || curLocalDate != prevLocal) {
      refreshMarketNow(manual = false)
    }
  }

  fun refreshMarketNow(manual: Boolean = true) {
    refreshJob?.cancel()
    refreshJob =
      viewModelScope.launch {
        _isRefreshing.value = true
        if (manual) {
          _isPullRefreshing.value = true
        }
        val startedEt = ZonedDateTime.now(ET_ZONE)
        val startedLocalDate = LocalDate.now()
        _marketClockEt.value = startedEt
        val isNewDay =
          lastSnapshotDateEt != null &&
            (startedEt.toLocalDate() != lastSnapshotDateEt || startedLocalDate != lastSnapshotDateLocal)
        val startedAtMs = System.currentTimeMillis()
        try {
          val snapshot =
            marketDataRepository.fetchFullSnapshot(
              extraSymbols = trackedExtraSymbols,
              forceCalendarRefresh = manual || isNewDay,
            )
          val doneEt = ZonedDateTime.now(ET_ZONE)
          _marketClockEt.value = doneEt
          lastSnapshotDateEt = doneEt.toLocalDate()
          lastSnapshotDateLocal = LocalDate.now()
          _marketSnapshot.value = UiState.Success(snapshot)
          checkPriceAlerts(snapshot)
          _selectedQuote.value?.let { openQuote ->
            val all = snapshot.indices + snapshot.futures + snapshot.watchlistQuotes.values
            all.firstOrNull { it.symbol == openQuote.symbol }?.let { _selectedQuote.value = it }
          }
          if (manual) {
            _secondsToRefresh.value = currentIntervalSeconds
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Market refresh failed", e)
          if (_marketSnapshot.value !is UiState.Success) {
            _marketSnapshot.value = UiState.Error(e.localizedMessage ?: "Failed to load market data")
          } else if (manual) {
            _actionError.value = "Unable to refresh live market feeds"
          }
        } finally {
          if (manual) {
            val elapsed = System.currentTimeMillis() - startedAtMs
            if (elapsed < 350L) {
              delay(350L - elapsed)
            }
            _isPullRefreshing.value = false
          }
          _isRefreshing.value = false
        }
      }
  }

  private fun checkPriceAlerts(snapshot: MarketCommandSnapshot) {
    val activeAlerts = (alertsState.value as? UiState.Success)?.data ?: return
    val triggered = evaluateTriggeredAlerts(activeAlerts, snapshot)
    if (triggered.isEmpty()) return
    val prefs = latestPreferences
    alarmPlayer.playTriggeredAlertAlarm(
      alarmEnabled = prefs.alarmEnabled,
      alarmToneId = prefs.alarmToneId,
      alarmCustomUri = prefs.alarmCustomUri,
    )
    triggered.forEach { (alert, livePrice) ->
      val cond = AlertCondition.fromWire(alert.condition)
      PriceAlertNotificationHelper.showPriceAlertNotification(
        context = appContext,
        notificationId = alert.id.hashCode(),
        title = "${alert.symbol} Price Alert (${cond.symbolText} ${String.format(Locale.US, "$%,.2f", alert.targetPrice)})",
        body = String.format(
          Locale.US,
          "%s hit $%,.2f — threshold was %s $%,.2f.",
          alert.displayName,
          livePrice,
          cond.label.lowercase(Locale.US),
          alert.targetPrice,
        ),
      )
      viewModelScope.launch {
        alertsRepository.markAlertTriggered(alert.id, livePrice)
      }
    }
  }

  fun createPriceAlert(
    symbol: String,
    displayName: String,
    condition: AlertCondition,
    targetPrice: Double,
  ) {
    viewModelScope.launch {
      alertsRepository.createPriceAlert(symbol, displayName, condition, targetPrice).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not save price alert"
      }
    }
  }

  fun togglePriceAlert(alertId: String, enabled: Boolean) {
    viewModelScope.launch {
      alertsRepository.toggleAlertEnabled(alertId, enabled).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not update alert"
      }
    }
  }

  fun rearmPriceAlert(alertId: String, condition: AlertCondition, targetPrice: Double) {
    viewModelScope.launch {
      alertsRepository.rearmAlert(alertId, condition, targetPrice).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not re-arm alert"
      }
    }
  }

  fun deletePriceAlert(alertId: String) {
    viewModelScope.launch {
      alertsRepository.deletePriceAlert(alertId).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not delete alert"
      }
    }
  }

  fun addPortfolioHolding(
    symbol: String,
    displayName: String,
    assetClass: AssetClass,
    shares: Double,
    avgCostBasis: Double,
  ) {
    viewModelScope.launch {
      portfolioRepository.addHolding(symbol, displayName, assetClass, shares, avgCostBasis).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not save portfolio holding"
      }
    }
  }

  fun updatePortfolioHolding(
    holdingId: String,
    displayName: String,
    assetClass: AssetClass,
    shares: Double,
    avgCostBasis: Double,
  ) {
    viewModelScope.launch {
      portfolioRepository.updateHolding(holdingId, displayName, assetClass, shares, avgCostBasis)
        .onFailure { _actionError.value = it.localizedMessage ?: "Could not update holding" }
    }
  }

  fun deletePortfolioHolding(holdingId: String) {
    viewModelScope.launch {
      portfolioRepository.deleteHolding(holdingId).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not remove holding"
      }
    }
  }

  fun generateAiBriefing() {
    aiBriefingJob?.cancel()
    aiBriefingJob =
      viewModelScope.launch {
        _aiBriefingState.value = UiState.Loading
        val snapshot = (_marketSnapshot.value as? UiState.Success)?.data
        try {
          val briefing = marketAiRepository.generateMarketBriefing(snapshot)
          _aiBriefingState.value = UiState.Success(briefing)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "AI Briefing failed", e)
          _aiBriefingState.value =
            UiState.Error(
              e.localizedMessage ?: "Failed to generate AI Briefing. Verify network & App Check.",
            )
        }
      }
  }

  fun openQuoteDetail(quote: MarketQuote) {
    _selectedQuote.value = quote
    _selectedTimeframe.value = ChartTimeframe.D1
    loadChart(quote, ChartTimeframe.D1)
  }

  fun onSearchQueryChange(query: String) {
    _searchQuery.value = query
    searchJob?.cancel()
    val clean = query.trim()
    if (clean.isEmpty()) {
      _searchResults.value = emptyList()
      _isSearching.value = false
      return
    }
    searchJob =
      viewModelScope.launch {
        _isSearching.value = true
        delay(220L)
        try {
          val snapshot = (_marketSnapshot.value as? UiState.Success)?.data
          _searchResults.value = marketDataRepository.searchTickers(clean, snapshot)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Ticker search failed for $clean", e)
        } finally {
          _isSearching.value = false
        }
      }
  }

  fun clearSearch() {
    searchJob?.cancel()
    _searchQuery.value = ""
    _searchResults.value = emptyList()
    _isSearching.value = false
  }

  fun openTickerDetail(result: TickerSearchResult) {
    val existing = result.quote
    if (existing != null) {
      openQuoteDetail(existing)
      return
    }
    viewModelScope.launch {
      val quote = marketDataRepository.fetchQuote(result.symbol, result.name)
      openQuoteDetail(quote)
    }
  }

  fun closeQuoteDetail() {
    _selectedQuote.value = null
    chartJob?.cancel()
  }

  fun selectChartTimeframe(timeframe: ChartTimeframe) {
    _selectedTimeframe.value = timeframe
    _selectedQuote.value?.let { loadChart(it, timeframe) }
  }

  private fun loadChart(quote: MarketQuote, timeframe: ChartTimeframe) {
    chartJob?.cancel()
    chartJob =
      viewModelScope.launch {
        _chartSeries.value = UiState.Loading
        try {
          val result = marketDataRepository.fetchChartSeries(quote, timeframe)
          _chartSeries.value = UiState.Success(result)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _chartSeries.value = UiState.Error(e.localizedMessage ?: "Unable to load chart")
        }
      }
  }

  fun createWatchlist(name: String) {
    viewModelScope.launch {
      val currentCount = (watchlistsState.value as? UiState.Success)?.data?.size?.toLong() ?: 0L
      watchlistRepository.createWatchlist(name, currentCount).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not create watchlist"
      }
    }
  }

  fun renameWatchlist(watchlistId: String, newName: String) {
    viewModelScope.launch {
      watchlistRepository.renameWatchlist(watchlistId, newName).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not rename watchlist"
      }
    }
  }

  fun toggleWatchlistPin(watchlist: Watchlist) {
    viewModelScope.launch {
      watchlistRepository.toggleWatchlistPin(watchlist.id, !watchlist.pinnedToTop).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not pin watchlist"
      }
    }
  }

  fun deleteWatchlist(watchlist: Watchlist) {
    viewModelScope.launch {
      val itemIds =
        (watchlistItemsState.value as? UiState.Success)?.data
          ?.filter { it.watchlistId == watchlist.id }
          ?.map { it.id }
          ?: emptyList()
      watchlistRepository.deleteWatchlist(watchlist.id, itemIds).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not delete watchlist"
      }
    }
  }

  fun addWatchlistItem(
    watchlistId: String,
    symbol: String,
    displayName: String,
    assetClass: AssetClass,
  ) {
    val cleanSymbol = symbol.trim().uppercase()
    if (cleanSymbol.isEmpty()) return
    val allItems = (watchlistItemsState.value as? UiState.Success)?.data.orEmpty()
    if (allItems.any { it.watchlistId == watchlistId && it.symbol.equals(cleanSymbol, ignoreCase = true) }) {
      _actionError.value = "$cleanSymbol is already in this watchlist"
      return
    }
    viewModelScope.launch {
      val currentItems = allItems.count { it.watchlistId == watchlistId }.toLong()
      watchlistRepository
        .addWatchlistItem(watchlistId, cleanSymbol, displayName, assetClass, currentItems)
        .onFailure { _actionError.value = it.localizedMessage ?: "Could not add symbol" }
    }
  }

  fun toggleItemPin(item: WatchlistItem) {
    viewModelScope.launch {
      watchlistRepository.toggleItemPin(item.id, !item.pinnedToTop).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not pin item"
      }
    }
  }

  fun reorderWatchlistItems(orderedIds: List<String>) {
    viewModelScope.launch {
      watchlistRepository.reorderWatchlistItems(orderedIds).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not save item order"
      }
    }
  }

  fun removeWatchlistItem(itemId: String) {
    viewModelScope.launch {
      watchlistRepository.removeWatchlistItem(itemId).onFailure {
        _actionError.value = it.localizedMessage ?: "Could not remove symbol"
      }
    }
  }

  fun askMarketAi(question: String) {
    val clean = question.trim()
    if (clean.isEmpty()) return
    aiJob?.cancel()
    aiJob =
      viewModelScope.launch {
        _aiState.value = AiAssistantUiState.Loading(clean)
        val snapshot = (_marketSnapshot.value as? UiState.Success)?.data
        var latestText = ""
        try {
          marketAiRepository.streamMarketAnswer(clean, snapshot).collect { partial ->
            latestText = partial
            _aiState.value = AiAssistantUiState.Streaming(clean, partial)
          }
          _aiState.value = AiAssistantUiState.Success(clean, latestText)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "AI request failed", e)
          _aiState.value =
            AiAssistantUiState.Error(
              clean,
              e.localizedMessage ?: "Failed to reach Gemini AI. Verify network & App Check.",
            )
        }
      }
  }

  fun selectRadioRegionAndPlay(region: CnbcRegion) {
    val current = radioState.value
    val items = _radioFeeds.value[region].orEmpty()
    if (current.hasActiveSession && current.selectedRegion == region) {
      radioPlayerManager.togglePlayPause(items)
    } else if (region == CnbcRegion.USA) {
      playLiveRadioStream(region)
    } else {
      radioPlayerManager.selectRegionAndPlay(region, items)
    }
  }

  fun playLiveRadioStream(region: CnbcRegion) {
    viewModelScope.launch {
      val candidates = cnbcRadioRepository.resolveLiveStreamCandidates(region)
      radioPlayerManager.playLiveRegionStream(region, candidates)
    }
  }

  fun playRadioEpisode(item: CnbcAudioItem) {
    radioPlayerManager.playEpisode(item)
  }

  fun toggleRadioPlayPause() {
    val items = _radioFeeds.value[radioState.value.selectedRegion].orEmpty()
    radioPlayerManager.togglePlayPause(items)
  }

  fun seekRadioBy(deltaMs: Long) {
    radioPlayerManager.seekBy(deltaMs)
  }

  fun seekRadioTo(positionMs: Long) {
    radioPlayerManager.seekTo(positionMs)
  }

  fun stopRadio() {
    radioPlayerManager.stop()
  }

  fun previewBundledAlarmTone(tone: BundledAlarmTone) {
    alarmPlayer.previewBundled(tone)
  }

  fun previewCustomAlarmTone(uriString: String?) {
    alarmPlayer.previewCustomUri(uriString)
  }

  fun previewActiveAlarmTone(prefs: AppPreferences) {
    if (prefs.alarmToneId == ALARM_TONE_CUSTOM && !prefs.alarmCustomUri.isNullOrBlank()) {
      alarmPlayer.previewCustomUri(prefs.alarmCustomUri)
    } else {
      val bundled = BundledAlarmTone.fromId(prefs.alarmToneId) ?: BundledAlarmTone.OPENING_BELL
      alarmPlayer.previewBundled(bundled)
    }
  }

  fun stopAlarmPreview() {
    alarmPlayer.stop()
  }

  fun clearAlarmPreviewError() {
    alarmPlayer.clearError()
  }

  fun clearActionError() {
    _actionError.value = null
  }

  override fun onCleared() {
    super.onCleared()
    radioPlayerManager.release()
    alarmPlayer.release()
  }

  private companion object {
    const val TAG = "MarketViewModel"
    val ET_ZONE: ZoneId = ZoneId.of("America/New_York")
  }
}
