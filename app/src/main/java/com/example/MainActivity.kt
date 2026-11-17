package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.audio.CnbcRadioPlayerManager
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.AlertsRepository
import com.example.data.repository.AppPreferences
import com.example.data.repository.CnbcRadioRepository
import com.example.data.repository.MarketAiRepository
import com.example.data.repository.MarketDataRepository
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.WatchlistRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.navigation.MainNavTab
import com.example.ui.navigation.ROUTE_ALERTS
import com.example.ui.navigation.ROUTE_DASHBOARD
import com.example.ui.navigation.ROUTE_PORTFOLIO
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.CnbcRadioMiniPlayer
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FuturesScreen
import com.example.ui.screens.IntelScreen
import com.example.ui.screens.IntelSubTab
import com.example.ui.screens.MarketBreadthScreen
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.NewsScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.QuoteDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WatchlistsScreen
import com.example.ui.theme.LocalMarketColors
import com.example.ui.theme.MarketPulseTheme
import com.example.ui.theme.isUsMarketOpenNow
import com.example.ui.theme.resolveDarkTheme
import com.example.ui.viewmodel.MarketViewModel
import com.example.ui.viewmodel.PreferencesViewModel
import com.example.ui.viewmodel.UiState
import com.example.widget.MarketWidgetUpdater
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Mandatory Step 1: Configure App Check with launch intent before any UI or Firebase calls
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current
      val localPrefsRepo = remember(context) { PreferencesRepository(context) }
      val localPrefs by
        localPrefsRepo.localPreferences.collectAsStateWithLifecycle(initialValue = AppPreferences())
      val rootClockEt by
        produceState(
          initialValue = ZonedDateTime.now(ZoneId.of("America/New_York")),
          key1 = localPrefs.refreshInterval.seconds,
        ) {
          val intervalMs = (localPrefs.refreshInterval.seconds.coerceAtLeast(15)) * 1000L
          while (true) {
            value = ZonedDateTime.now(ZoneId.of("America/New_York"))
            delay(intervalMs)
          }
        }
      val isDark =
        resolveDarkTheme(
          themeMode = localPrefs.themeMode,
          openMinutesEt = localPrefs.marketOpenMinutesEt,
          closeMinutesEt = localPrefs.marketCloseMinutesEt,
          nowEt = rootClockEt,
        )

      MarketPulseTheme(darkTheme = isDark) {
        AppRoot(localPrefs = localPrefs)
      }
    }
  }
}

@Composable
private fun AppRoot(localPrefs: AppPreferences) {
  val auth = remember { Firebase.auth }
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      val prefsViewModel: PreferencesViewModel =
        viewModel(
          key = "prefs_${user.uid}",
          factory =
            viewModelFactory {
              initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                PreferencesViewModel(PreferencesRepository(app, db, auth), user.uid)
              }
            },
        )
      val marketViewModel: MarketViewModel =
        viewModel(
          key = "market_${user.uid}",
          factory =
            viewModelFactory {
              initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                MarketViewModel(
                  appContext = app.applicationContext,
                  marketDataRepository = MarketDataRepository(),
                  watchlistRepository = WatchlistRepository(db, auth),
                  alertsRepository = AlertsRepository(db, auth),
                  portfolioRepository = PortfolioRepository(db, auth),
                  marketAiRepository = MarketAiRepository(),
                  preferencesRepository = PreferencesRepository(app, db, auth),
                  cnbcRadioRepository = CnbcRadioRepository(),
                  radioPlayerManager = CnbcRadioPlayerManager(app),
                  currentUserId = user.uid,
                )
              }
            },
        )

      val syncedPrefs by
        prefsViewModel.preferences.collectAsStateWithLifecycle(initialValue = localPrefs)
      val marketClockEt by marketViewModel.marketClockEt.collectAsStateWithLifecycle()
      val isDark =
        resolveDarkTheme(
          themeMode = syncedPrefs.themeMode,
          openMinutesEt = syncedPrefs.marketOpenMinutesEt,
          closeMinutesEt = syncedPrefs.marketCloseMinutesEt,
          nowEt = marketClockEt,
        )

      MarketPulseTheme(darkTheme = isDark) {
        MarketCommandCenterApp(
          user = user,
          preferences = syncedPrefs,
          marketClockEt = marketClockEt,
          prefsViewModel = prefsViewModel,
          marketViewModel = marketViewModel,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketCommandCenterApp(
  user: FirebaseUser,
  preferences: AppPreferences,
  marketClockEt: ZonedDateTime,
  prefsViewModel: PreferencesViewModel,
  marketViewModel: MarketViewModel,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  DisposableEffect(lifecycleOwner, marketViewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        marketViewModel.checkNewDayAndRefreshIfNeeded()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val navigateToTab: (String) -> Unit = { route ->
    if (navBackStackEntry != null) {
      navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    }
  }
  val navigateToSecondary: (String) -> Unit = { route ->
    if (navBackStackEntry != null) {
      navController.navigate(route) {
        launchSingleTop = true
      }
    }
  }
  val visibleNavTabs =
    remember(preferences.bottomBarTabOrder, preferences.hiddenBottomBarTabs) {
      MainNavTab.resolveVisibleTabs(
        savedRoutes = preferences.bottomBarTabOrder,
        hiddenRoutes = preferences.hiddenBottomBarTabs,
      )
    }
  val fallbackHomeRoute = visibleNavTabs.firstOrNull()?.route ?: ROUTE_DASHBOARD
  val showBottomNavigation = preferences.bottomBarVisible && visibleNavTabs.isNotEmpty()

  var intelSubTab by rememberSaveable { mutableStateOf(IntelSubTab.ECONOMIC) }
  var showSettings by rememberSaveable { mutableStateOf(false) }
  var prefillAlertSymbol by rememberSaveable { mutableStateOf<String?>(null) }

  val snapshotState by marketViewModel.marketSnapshot.collectAsStateWithLifecycle()
  val isRefreshing by marketViewModel.isRefreshing.collectAsStateWithLifecycle()
  val isPullRefreshing by marketViewModel.isPullRefreshing.collectAsStateWithLifecycle()
  val secondsToRefresh by marketViewModel.secondsToRefresh.collectAsStateWithLifecycle()
  val watchlistsState by marketViewModel.watchlistsState.collectAsStateWithLifecycle()
  val itemsState by marketViewModel.watchlistItemsState.collectAsStateWithLifecycle()
  val alertsState by marketViewModel.alertsState.collectAsStateWithLifecycle()
  val holdingsState by marketViewModel.holdingsState.collectAsStateWithLifecycle()
  val portfolioSummary by marketViewModel.portfolioSummary.collectAsStateWithLifecycle()
  val aiBriefingState by marketViewModel.aiBriefingState.collectAsStateWithLifecycle()
  val fcmTokenSynced by marketViewModel.fcmTokenSynced.collectAsStateWithLifecycle()
  val selectedQuote by marketViewModel.selectedQuote.collectAsStateWithLifecycle()
  val selectedTimeframe by marketViewModel.selectedTimeframe.collectAsStateWithLifecycle()
  val chartSeries by marketViewModel.chartSeries.collectAsStateWithLifecycle()
  val aiState by marketViewModel.aiState.collectAsStateWithLifecycle()
  val radioState by marketViewModel.radioState.collectAsStateWithLifecycle()
  val alarmPreviewState by marketViewModel.alarmPreviewState.collectAsStateWithLifecycle()
  val radioFeeds by marketViewModel.radioFeeds.collectAsStateWithLifecycle()
  val searchQuery by marketViewModel.searchQuery.collectAsStateWithLifecycle()
  val searchResults by marketViewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by marketViewModel.isSearching.collectAsStateWithLifecycle()
  val actionError by marketViewModel.actionError.collectAsStateWithLifecycle()

  val armedAlertsCount =
    remember(alertsState) {
      (alertsState as? UiState.Success)?.data?.count { it.enabled && !it.triggered } ?: 0
    }

  LaunchedEffect(actionError) {
    actionError?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      marketViewModel.clearActionError()
    }
  }

  LaunchedEffect(snapshotState) {
    val data = (snapshotState as? UiState.Success)?.data ?: return@LaunchedEffect
    MarketWidgetUpdater.updateAllWidgets(context, data)
  }

  val marketColors = LocalMarketColors.current
  val marketOpenNow =
    isUsMarketOpenNow(
      openMinutesEt = preferences.marketOpenMinutesEt,
      closeMinutesEt = preferences.marketCloseMinutesEt,
      nowEt = marketClockEt,
    )

  Box(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useRail = maxWidth >= 600.dp

      Row(modifier = Modifier.fillMaxSize()) {
        if (useRail && showBottomNavigation) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            Spacer(modifier = Modifier.size(12.dp))
            visibleNavTabs.forEach { tab ->
              val selected =
                currentDestination?.hierarchy?.any { it.route == tab.route }
                  ?: (tab == visibleNavTabs.first())
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = {
                  Text(
                    text = tab.label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                  )
                },
                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
              )
            }
          }
        }

        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            TopAppBar(
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.background,
                  titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
              title = {
                Column(
                  modifier =
                    Modifier.clip(RoundedCornerShape(10.dp))
                      .clickable { navigateToTab(fallbackHomeRoute) }
                      .minimumInteractiveComponentSize()
                      .padding(horizontal = 4.dp, vertical = 2.dp)
                      .testTag("topbar_home_button")
                ) {
                  Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(7.dp)
                          .clip(CircleShape)
                          .background(if (marketOpenNow) marketColors.gain else marketColors.amber)
                    )
                    Text(
                      text =
                        if (marketOpenNow) "US MARKET OPEN"
                        else "AFTER HOURS • 24/7 FUTURES",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              },
              actions = {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  shape = RoundedCornerShape(999.dp),
                  modifier =
                    Modifier.clip(RoundedCornerShape(999.dp))
                      .clickable { marketViewModel.refreshMarketNow() }
                      .testTag("refresh_pill"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    if (isRefreshing) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                    Text(
                      text = "${secondsToRefresh}s",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                  onClick = { navigateToSecondary(ROUTE_PORTFOLIO) },
                  modifier = Modifier.testTag("portfolio_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.PieChart,
                    contentDescription = stringResource(R.string.cd_portfolio),
                  )
                }
                IconButton(
                  onClick = { navigateToSecondary(ROUTE_ALERTS) },
                  modifier = Modifier.testTag("alerts_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = stringResource(R.string.cd_alerts),
                  )
                }
                IconButton(
                  onClick = { showSettings = true },
                  modifier = Modifier.testTag("settings_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                  )
                }
              },
            )
          },
          bottomBar = {
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .then(
                    if ((useRail || !showBottomNavigation) && radioState.hasActiveSession) {
                      Modifier.navigationBarsPadding()
                    } else {
                      Modifier
                    }
                  )
            ) {
              if (radioState.hasActiveSession) {
                CnbcRadioMiniPlayer(
                  radioState = radioState,
                  onTogglePlayPause = { marketViewModel.toggleRadioPlayPause() },
                  onStop = { marketViewModel.stopRadio() },
                  onOpenRadioScreen = {
                    intelSubTab = IntelSubTab.RADIO
                    navigateToTab(MainNavTab.CALENDAR.route)
                  },
                )
              }
              if (!useRail && showBottomNavigation) {
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  tonalElevation = 3.dp,
                  modifier = Modifier.fillMaxWidth().testTag("bottom_navigation_bar"),
                ) {
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .heightIn(min = 66.dp)
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    visibleNavTabs.forEach { tab ->
                      val selected =
                        currentDestination?.hierarchy?.any { it.route == tab.route }
                          ?: (tab == visibleNavTabs.first())
                      Column(
                        modifier =
                          Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { navigateToTab(tab.route) }
                            .minimumInteractiveComponentSize()
                            .padding(vertical = 2.dp, horizontal = 1.dp)
                            .testTag("nav_${tab.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                      ) {
                        Box(
                          modifier =
                            Modifier.width(42.dp)
                              .height(26.dp)
                              .clip(RoundedCornerShape(999.dp))
                              .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                              ),
                          contentAlignment = Alignment.Center,
                        ) {
                          Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(19.dp),
                            tint =
                              if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                        }
                        Text(
                          text = tab.label,
                          maxLines = 1,
                          softWrap = false,
                          overflow = TextOverflow.Ellipsis,
                          fontSize = 9.sp,
                          lineHeight = 11.sp,
                          letterSpacing = (-0.3).sp,
                          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                          color =
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
              }
            }
          },
        ) { innerPadding ->
          Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val snap = snapshotState) {
              is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                      text = "Connecting to market command feed...",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
              is UiState.Error -> {
                Box(
                  modifier = Modifier.fillMaxSize().padding(24.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = snap.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                  )
                }
              }
              is UiState.Success -> {
                val snapshot = snap.data
                NavHost(
                  navController = navController,
                  startDestination = MainNavTab.DASHBOARD.route,
                  modifier = Modifier.fillMaxSize(),
                ) {
                  composable(MainNavTab.DASHBOARD.route) {
                    DashboardScreen(
                      snapshot = snapshot,
                      radioState = radioState,
                      onSelectRadioRegion = { region ->
                        marketViewModel.selectRadioRegionAndPlay(region)
                      },
                      onOpenRadioScreen = {
                        intelSubTab = IntelSubTab.RADIO
                        navigateToTab(MainNavTab.CALENDAR.route)
                      },
                      onOpenQuote = { marketViewModel.openQuoteDetail(it) },
                      onOpenAiAssistant = {
                        intelSubTab = IntelSubTab.AI_BRIEF
                        navigateToTab(MainNavTab.CALENDAR.route)
                      },
                      onOpenBreadthTab = { navigateToTab(MainNavTab.BREADTH.route) },
                      onOpenCalendarTab = {
                        intelSubTab = IntelSubTab.ECONOMIC
                        navigateToTab(MainNavTab.CALENDAR.route)
                      },
                      briefingState = aiBriefingState,
                      portfolioSummary = portfolioSummary,
                      armedAlertsCount = armedAlertsCount,
                      onGenerateBriefing = { marketViewModel.generateAiBriefing() },
                      onOpenPortfolio = { navigateToSecondary(ROUTE_PORTFOLIO) },
                      onOpenAlerts = { navigateToSecondary(ROUTE_ALERTS) },
                      isRefreshing = isPullRefreshing,
                      onRefresh = { marketViewModel.refreshMarketNow(manual = true) },
                    )
                  }
                  composable(MainNavTab.MARKETS.route) {
                    MarketsScreen(
                      snapshot = snapshot,
                      onOpenQuote = { marketViewModel.openQuoteDetail(it) },
                      isRefreshing = isPullRefreshing,
                      onRefresh = { marketViewModel.refreshMarketNow(manual = true) },
                      searchQuery = searchQuery,
                      onSearchQueryChange = { marketViewModel.onSearchQueryChange(it) },
                      onClearSearch = { marketViewModel.clearSearch() },
                      searchResults = searchResults,
                      isSearching = isSearching,
                      watchlists = (watchlistsState as? UiState.Success)?.data.orEmpty(),
                      watchlistItems = (itemsState as? UiState.Success)?.data.orEmpty(),
                      onInspectTicker = { marketViewModel.openTickerDetail(it) },
                      onAddTickerToWatchlist = { wlId, sym, name, cls ->
                        marketViewModel.addWatchlistItem(wlId, sym, name, cls)
                      },
                    )
                  }
                  composable(MainNavTab.FUTURES.route) {
                    FuturesScreen(
                      snapshot = snapshot,
                      onOpenQuote = { marketViewModel.openQuoteDetail(it) },
                      isRefreshing = isPullRefreshing,
                      onRefresh = { marketViewModel.refreshMarketNow(manual = true) },
                    )
                  }
                  composable(MainNavTab.WATCHLISTS.route) {
                    WatchlistsScreen(
                      watchlistsState = watchlistsState,
                      itemsState = itemsState,
                      snapshot = snapshot,
                      onCreateWatchlist = { marketViewModel.createWatchlist(it) },
                      onRenameWatchlist = { id, name -> marketViewModel.renameWatchlist(id, name) },
                      onToggleWatchlistPin = { marketViewModel.toggleWatchlistPin(it) },
                      onDeleteWatchlist = { marketViewModel.deleteWatchlist(it) },
                      onAddSymbol = { wlId, sym, name, cls ->
                        marketViewModel.addWatchlistItem(wlId, sym, name, cls)
                      },
                      onToggleItemPin = { marketViewModel.toggleItemPin(it) },
                      onReorderItems = { marketViewModel.reorderWatchlistItems(it) },
                      onRemoveItem = { marketViewModel.removeWatchlistItem(it) },
                      onOpenQuote = { marketViewModel.openQuoteDetail(it) },
                      searchQuery = searchQuery,
                      onSearchQueryChange = { marketViewModel.onSearchQueryChange(it) },
                      onClearSearch = { marketViewModel.clearSearch() },
                      searchResults = searchResults,
                      isSearching = isSearching,
                      onInspectTicker = { marketViewModel.openTickerDetail(it) },
                      onOpenAlerts = { symbol ->
                        prefillAlertSymbol = symbol
                        navigateToSecondary(ROUTE_ALERTS)
                      },
                      onOpenPortfolio = { navigateToSecondary(ROUTE_PORTFOLIO) },
                    )
                  }
                  composable(MainNavTab.CALENDAR.route) {
                    IntelScreen(
                      snapshot = snapshot,
                      activeSubTab = intelSubTab,
                      onSelectSubTab = { intelSubTab = it },
                      radioState = radioState,
                      radioFeeds = radioFeeds,
                      onSelectRadioRegion = { marketViewModel.selectRadioRegionAndPlay(it) },
                      onPlayLiveRadioStream = { marketViewModel.playLiveRadioStream(it) },
                      onPlayRadioEpisode = { marketViewModel.playRadioEpisode(it) },
                      onToggleRadioPlayPause = { marketViewModel.toggleRadioPlayPause() },
                      onSeekRadioBy = { marketViewModel.seekRadioBy(it) },
                      onSeekRadioTo = { marketViewModel.seekRadioTo(it) },
                      onStopRadio = { marketViewModel.stopRadio() },
                      aiState = aiState,
                      onAskAi = { marketViewModel.askMarketAi(it) },
                      briefingState = aiBriefingState,
                      onGenerateBriefing = { marketViewModel.generateAiBriefing() },
                      isRefreshing = isPullRefreshing,
                      onRefresh = { marketViewModel.refreshMarketNow(manual = true) },
                    )
                  }
                  composable(MainNavTab.NEWS.route) {
                    NewsScreen(
                      articles = snapshot.news,
                      isRefreshing = isPullRefreshing,
                      onRefresh = { marketViewModel.refreshMarketNow(manual = true) },
                    )
                  }
                  composable(MainNavTab.BREADTH.route) {
                    MarketBreadthScreen(
                      breadth = snapshot.breadth,
                      isRefreshing = isPullRefreshing,
                      onRefresh = { marketViewModel.refreshMarketNow(manual = true) },
                    )
                  }
                  composable(ROUTE_PORTFOLIO) {
                    PortfolioScreen(
                      holdingsState = holdingsState,
                      portfolioSummary = portfolioSummary,
                      snapshot = snapshot,
                      onAddHolding = { sym, name, cls, sh, cost ->
                        marketViewModel.addPortfolioHolding(sym, name, cls, sh, cost)
                      },
                      onUpdateHolding = { id, name, cls, sh, cost ->
                        marketViewModel.updatePortfolioHolding(id, name, cls, sh, cost)
                      },
                      onDeleteHolding = { marketViewModel.deletePortfolioHolding(it) },
                      onOpenQuote = { marketViewModel.openQuoteDetail(it) },
                      onBack = { navController.popBackStack() },
                    )
                  }
                  composable(ROUTE_ALERTS) {
                    AlertsScreen(
                      alertsState = alertsState,
                      watchlistItems = (itemsState as? UiState.Success)?.data.orEmpty(),
                      snapshot = snapshot,
                      fcmTokenSynced = fcmTokenSynced,
                      preferences = preferences,
                      alarmPreviewState = alarmPreviewState,
                      prefillSymbol = prefillAlertSymbol,
                      onClearPrefill = { prefillAlertSymbol = null },
                      onCreateAlert = { sym, name, cond, price ->
                        marketViewModel.createPriceAlert(sym, name, cond, price)
                      },
                      onToggleAlert = { id, enabled ->
                        marketViewModel.togglePriceAlert(id, enabled)
                      },
                      onRearmAlert = { id, cond, price ->
                        marketViewModel.rearmPriceAlert(id, cond, price)
                      },
                      onDeleteAlert = { marketViewModel.deletePriceAlert(it) },
                      onSyncFcmToken = { marketViewModel.syncFcmToken() },
                      onPreviewActiveAlarm = {
                        marketViewModel.previewActiveAlarmTone(preferences)
                      },
                      onStopAlarmPreview = { marketViewModel.stopAlarmPreview() },
                      onOpenAlarmSettings = { showSettings = true },
                      onBack = { navController.popBackStack() },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Full-screen Detail Overlay
    selectedQuote?.let { quote ->
      QuoteDetailScreen(
        quote = quote,
        selectedTimeframe = selectedTimeframe,
        chartState = chartSeries,
        onSelectTimeframe = { marketViewModel.selectChartTimeframe(it) },
        onBack = { marketViewModel.closeQuoteDetail() },
      )
    }

    // Dedicated Settings Overlay
    if (showSettings) {
      SettingsScreen(
        preferences = preferences,
        alarmPreviewState = alarmPreviewState,
        userEmail = user.email,
        nowEt = marketClockEt,
        onSelectThemeMode = { prefsViewModel.setThemeMode(it) },
        onUpdateMarketHours = { openMin, closeMin ->
          prefsViewModel.setMarketHours(openMin, closeMin)
        },
        onSelectRefreshInterval = { prefsViewModel.setRefreshInterval(it) },
        onSetAlarmEnabled = { prefsViewModel.setAlarmEnabled(it) },
        onSelectBundledAlarmTone = { prefsViewModel.selectBundledAlarmTone(it) },
        onSelectExistingCustomAlarmTone = { prefsViewModel.selectExistingCustomAlarmTone() },
        onSetCustomAlarmAudio = { uriString, displayName ->
          prefsViewModel.setCustomAlarmAudio(uriString, displayName)
        },
        onPreviewBundledAlarmTone = { marketViewModel.previewBundledAlarmTone(it) },
        onPreviewCustomAlarmTone = { marketViewModel.previewCustomAlarmTone(it) },
        onStopAlarmPreview = { marketViewModel.stopAlarmPreview() },
        onSetBottomBarVisible = { prefsViewModel.setBottomBarVisible(it) },
        onMoveBottomBarTab = { route, direction ->
          prefsViewModel.moveBottomBarTab(route, direction)
        },
        onSetBottomBarTabEnabled = { route, enabled ->
          prefsViewModel.setBottomBarTabEnabled(route, enabled)
          if (!enabled && currentDestination?.route == route) {
            val nextHome =
              visibleNavTabs.firstOrNull { it.route != route }?.route ?: ROUTE_DASHBOARD
            navigateToTab(nextHome)
          }
        },
        onResetBottomBarDefaults = { prefsViewModel.resetBottomBarDefaults() },
        onSignOut = {
          marketViewModel.stopRadio()
          marketViewModel.stopAlarmPreview()
          showSettings = false
          signOutUser(credentialManager = credentialManager, scope = scope)
        },
        onBack = {
          marketViewModel.stopAlarmPreview()
          showSettings = false
        },
      )
    }
  }
}
