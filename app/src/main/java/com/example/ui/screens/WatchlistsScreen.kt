package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.model.AssetClass
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.model.QuoteCategory
import com.example.data.model.Watchlist
import com.example.data.model.WatchlistItem
import com.example.data.repository.TickerSearchResult
import com.example.ui.components.MiniSparklineChart
import com.example.ui.components.TickerSearchBarSection
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import java.util.Locale
import kotlin.math.roundToInt

private enum class WatchlistSortOption(
  val label: String,
  val defaultAscending: Boolean,
  val tagKey: String,
) {
  CUSTOM("Custom", true, "custom"),
  TICKER_NAME("Ticker Name", true, "ticker_name"),
  PRICE("Price", false, "price"),
  DAILY_CHANGE("Daily Change", false, "daily_change"),
}

@Composable
fun WatchlistsScreen(
  watchlistsState: UiState<List<Watchlist>>,
  itemsState: UiState<List<WatchlistItem>>,
  snapshot: MarketCommandSnapshot?,
  onCreateWatchlist: (String) -> Unit,
  onRenameWatchlist: (String, String) -> Unit,
  onToggleWatchlistPin: (Watchlist) -> Unit,
  onDeleteWatchlist: (Watchlist) -> Unit,
  onAddSymbol: (String, String, String, AssetClass) -> Unit,
  onToggleItemPin: (WatchlistItem) -> Unit,
  onReorderItems: (List<String>) -> Unit,
  onRemoveItem: (String) -> Unit,
  onOpenQuote: (MarketQuote) -> Unit,
  searchQuery: String = "",
  onSearchQueryChange: (String) -> Unit = {},
  onClearSearch: () -> Unit = {},
  searchResults: List<TickerSearchResult> = emptyList(),
  isSearching: Boolean = false,
  onInspectTicker: (TickerSearchResult) -> Unit = {},
  onOpenAlerts: (String?) -> Unit = {},
  onOpenPortfolio: () -> Unit = {},
) {
  var selectedWatchlistId by remember { mutableStateOf<String?>(null) }
  var showCreateWatchlistDialog by remember { mutableStateOf(false) }
  var watchlistToRename by remember { mutableStateOf<Watchlist?>(null) }
  var showAddSymbolDialog by remember { mutableStateOf(false) }
  var sortOption by remember { mutableStateOf(WatchlistSortOption.CUSTOM) }
  var sortAscending by remember { mutableStateOf(true) }
  var sortMenuExpanded by remember { mutableStateOf(false) }

  val watchlists = (watchlistsState as? UiState.Success)?.data.orEmpty()
  LaunchedEffect(watchlists) {
    if (watchlists.isNotEmpty() && watchlists.none { it.id == selectedWatchlistId }) {
      selectedWatchlistId = watchlists.first().id
    }
  }
  val activeWatchlist = watchlists.firstOrNull { it.id == selectedWatchlistId } ?: watchlists.firstOrNull()

  val allItems = (itemsState as? UiState.Success)?.data.orEmpty()
  val activeItems =
    remember(allItems, activeWatchlist?.id) {
      if (activeWatchlist == null) emptyList()
      else allItems.filter { it.watchlistId == activeWatchlist.id }
    }

  val localOrder = remember { mutableStateListOf<WatchlistItem>() }
  LaunchedEffect(activeItems) {
    localOrder.clear()
    localOrder.addAll(activeItems)
  }

  val displayedEntries =
    remember(localOrder.toList(), snapshot, sortOption, sortAscending) {
      val withQuotes =
        localOrder.map { item -> item to resolveQuoteForWatchlistItem(item, snapshot) }
      when (sortOption) {
        WatchlistSortOption.CUSTOM -> withQuotes
        WatchlistSortOption.TICKER_NAME ->
          withQuotes.sortedWith(
            if (sortAscending) compareBy<Pair<WatchlistItem, MarketQuote>> { it.first.symbol.uppercase(Locale.US) }
            else compareByDescending { it.first.symbol.uppercase(Locale.US) }
          )
        WatchlistSortOption.PRICE ->
          withQuotes.sortedWith(
            if (sortAscending) compareBy<Pair<WatchlistItem, MarketQuote>> { it.second.price }.thenBy { it.first.symbol }
            else compareByDescending<Pair<WatchlistItem, MarketQuote>> { it.second.price }.thenBy { it.first.symbol }
          )
        WatchlistSortOption.DAILY_CHANGE ->
          withQuotes.sortedWith(
            if (sortAscending) compareBy<Pair<WatchlistItem, MarketQuote>> { it.second.changePercent }.thenBy { it.first.symbol }
            else compareByDescending<Pair<WatchlistItem, MarketQuote>> { it.second.changePercent }.thenBy { it.first.symbol }
          )
      }
    }

  val activeSymbolsInList =
    remember(activeItems) { activeItems.map { it.symbol.uppercase() }.toSet() }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      if (activeWatchlist != null) {
        FloatingActionButton(
          onClick = { showAddSymbolDialog = true },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("add_symbol_fab"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.cd_add_symbol),
          )
        }
      }
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      TickerSearchBarSection(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        onClearQuery = onClearSearch,
        results = searchResults,
        isSearching = isSearching,
        existingSymbolsInTarget = activeSymbolsInList,
        addButtonLabel = "Add",
        onInspectTicker = onInspectTicker,
        onAddTicker = { ticker ->
          activeWatchlist?.let { target ->
            onAddSymbol(target.id, ticker.symbol, ticker.name, ticker.assetClass)
          } ?: run { showCreateWatchlistDialog = true }
        },
        searchFieldTag = "watchlists_search_input",
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { onOpenAlerts(null) },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f).testTag("watchlists_open_alerts_button"),
        ) {
          Icon(
            Icons.Rounded.NotificationsActive,
            contentDescription = stringResource(R.string.cd_alerts),
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Price Alerts", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
          onClick = onOpenPortfolio,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.weight(1f).testTag("watchlists_open_portfolio_button"),
        ) {
          Icon(
            Icons.Rounded.PieChart,
            contentDescription = stringResource(R.string.cd_portfolio),
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Portfolio", style = MaterialTheme.typography.labelLarge)
        }
      }

      // Watchlist Selector Row
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LazyRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(watchlists, key = { it.id }) { wl ->
            FilterChip(
              selected = wl.id == activeWatchlist?.id,
              onClick = { selectedWatchlistId = wl.id },
              label = {
                Text(
                  text = if (wl.pinnedToTop) "★ ${wl.name}" else wl.name,
                  style = MaterialTheme.typography.labelLarge,
                )
              },
              modifier = Modifier.testTag("watchlist_chip_${wl.id}"),
            )
          }
        }
        OutlinedButton(
          onClick = { showCreateWatchlistDialog = true },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("new_watchlist_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add_watchlist), modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("List", style = MaterialTheme.typography.labelLarge)
        }
      }

      if (activeWatchlist != null) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box {
                val isSorted = sortOption != WatchlistSortOption.CUSTOM
                FilterChip(
                  selected = isSorted,
                  onClick = { sortMenuExpanded = true },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Sort,
                      contentDescription = "Sort watchlist assets",
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  trailingIcon = {
                    if (isSorted) {
                      Icon(
                        imageVector =
                          if (sortAscending) Icons.Rounded.ArrowUpward
                          else Icons.Rounded.ArrowDownward,
                        contentDescription =
                          if (sortAscending) "Sorted ascending" else "Sorted descending",
                        modifier = Modifier.size(14.dp),
                      )
                    }
                  },
                  label = {
                    Text(
                      text =
                        if (isSorted) "Sort: ${sortOption.label}" else "Sort",
                      style = MaterialTheme.typography.labelLarge,
                    )
                  },
                  modifier = Modifier.testTag("watchlist_sort_menu_button"),
                )

                DropdownMenu(
                  expanded = sortMenuExpanded,
                  onDismissRequest = { sortMenuExpanded = false },
                ) {
                  WatchlistSortOption.entries.forEach { option ->
                    val isSelected = sortOption == option
                    DropdownMenuItem(
                      text = {
                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                          Text(
                            text =
                              when (option) {
                                WatchlistSortOption.CUSTOM -> "Custom Order"
                                WatchlistSortOption.TICKER_NAME -> "Ticker Name"
                                WatchlistSortOption.PRICE -> "Price"
                                WatchlistSortOption.DAILY_CHANGE -> "Daily Change %"
                              },
                            fontWeight =
                              if (isSelected) FontWeight.Bold else FontWeight.Normal,
                          )
                          if (isSelected && option != WatchlistSortOption.CUSTOM) {
                            Text(
                              text = if (sortAscending) "↑" else "↓",
                              style = MaterialTheme.typography.labelMedium,
                              color = MaterialTheme.colorScheme.primary,
                              fontWeight = FontWeight.Bold,
                            )
                          }
                        }
                      },
                      trailingIcon = {
                        if (isSelected) {
                          Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected sort option",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                          )
                        }
                      },
                      onClick = {
                        if (sortOption == option && option != WatchlistSortOption.CUSTOM) {
                          sortAscending = !sortAscending
                        } else {
                          sortOption = option
                          sortAscending = option.defaultAscending
                        }
                        sortMenuExpanded = false
                      },
                      modifier = Modifier.testTag("sort_option_${option.tagKey}"),
                    )
                    if (option == WatchlistSortOption.CUSTOM) {
                      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                  }
                }
              }

              if (sortOption != WatchlistSortOption.CUSTOM) {
                IconButton(
                  onClick = { sortAscending = !sortAscending },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("watchlist_sort_direction_button"),
                ) {
                  Icon(
                    imageVector =
                      if (sortAscending) Icons.Rounded.ArrowUpward
                      else Icons.Rounded.ArrowDownward,
                    contentDescription =
                      if (sortAscending) "Toggle descending order"
                      else "Toggle ascending order",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }

            Row {
              IconButton(
                onClick = { onToggleWatchlistPin(activeWatchlist) },
                modifier = Modifier.testTag("pin_watchlist_button"),
              ) {
                Icon(
                  imageVector =
                    if (activeWatchlist.pinnedToTop) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                  contentDescription = stringResource(R.string.cd_pin),
                  tint =
                    if (activeWatchlist.pinnedToTop) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { watchlistToRename = activeWatchlist },
                modifier = Modifier.testTag("rename_watchlist_button"),
              ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Rename watchlist")
              }
              if (watchlists.size > 1) {
                IconButton(
                  onClick = { onDeleteWatchlist(activeWatchlist) },
                  modifier = Modifier.testTag("delete_watchlist_button"),
                ) {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }

          val helperText =
            when (sortOption) {
              WatchlistSortOption.CUSTOM -> "Long-press & drag or use arrows to reorder"
              WatchlistSortOption.TICKER_NAME ->
                "Sorted by Ticker Name (${if (sortAscending) "A → Z" else "Z → A"})"
              WatchlistSortOption.PRICE ->
                "Sorted by Price (${if (sortAscending) "Low → High" else "High → Low"})"
              WatchlistSortOption.DAILY_CHANGE ->
                "Sorted by Daily Change % (${if (sortAscending) "Low → High" else "High → Low"})"
            }
          Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      when (watchlistsState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is UiState.Error -> {
          Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
              text = watchlistsState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        is UiState.Success -> {
          if (localOrder.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "No symbols in this watchlist yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Tap + to add Stocks, ETFs, Indices, Crypto, or Commodities.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            val rowHeightPx = with(LocalDensity.current) { 96.dp.toPx() }
            var draggedItemId by remember { mutableStateOf<String?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }

            val allowManualReorder = sortOption == WatchlistSortOption.CUSTOM
            LazyColumn(
              modifier = Modifier.fillMaxSize().testTag("watchlist_items_list"),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              itemsIndexed(displayedEntries, key = { _, (item, _) -> item.id }) { index, (item, quote) ->
                val isDragging = allowManualReorder && draggedItemId == item.id
                val dragModifier =
                  if (allowManualReorder) {
                    Modifier.pointerInput(item.id, localOrder.size) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = {
                          draggedItemId = item.id
                          dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                          change.consume()
                          dragOffsetY += dragAmount.y
                          val curIdx = localOrder.indexOfFirst { it.id == item.id }
                          if (curIdx != -1) {
                            if (dragOffsetY > rowHeightPx * 0.7f && curIdx < localOrder.lastIndex) {
                              localOrder.add(curIdx + 1, localOrder.removeAt(curIdx))
                              dragOffsetY -= rowHeightPx
                            } else if (dragOffsetY < -rowHeightPx * 0.7f && curIdx > 0) {
                              localOrder.add(curIdx - 1, localOrder.removeAt(curIdx))
                              dragOffsetY += rowHeightPx
                            }
                          }
                        },
                        onDragEnd = {
                          draggedItemId = null
                          dragOffsetY = 0f
                          onReorderItems(localOrder.map { it.id })
                        },
                        onDragCancel = {
                          draggedItemId = null
                          dragOffsetY = 0f
                        },
                      )
                    }
                  } else {
                    Modifier
                  }

                WatchlistItemCard(
                  item = item,
                  quote = quote,
                  manualReorderEnabled = allowManualReorder,
                  canMoveUp = allowManualReorder && index > 0,
                  canMoveDown = allowManualReorder && index < displayedEntries.lastIndex,
                  modifier =
                    Modifier.zIndex(if (isDragging) 2f else 0f)
                      .offset {
                        IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0)
                      }
                      .then(dragModifier),
                  onClick = { onOpenQuote(quote) },
                  onTogglePin = { onToggleItemPin(item) },
                  onMoveUp = {
                    if (allowManualReorder && index > 0) {
                      localOrder.add(index - 1, localOrder.removeAt(index))
                      onReorderItems(localOrder.map { it.id })
                    }
                  },
                  onMoveDown = {
                    if (allowManualReorder && index < localOrder.lastIndex) {
                      localOrder.add(index + 1, localOrder.removeAt(index))
                      onReorderItems(localOrder.map { it.id })
                    }
                  },
                  onSetAlert = { onOpenAlerts(item.symbol) },
                  onDelete = { onRemoveItem(item.id) },
                )
              }
            }
          }
        }
      }
    }
  }

  if (showCreateWatchlistDialog) {
    WatchlistNameDialog(
      title = "Create Watchlist",
      initialName = "",
      onDismiss = { showCreateWatchlistDialog = false },
      onConfirm = { name ->
        onCreateWatchlist(name)
        showCreateWatchlistDialog = false
      },
    )
  }

  watchlistToRename?.let { wl ->
    WatchlistNameDialog(
      title = "Rename Watchlist",
      initialName = wl.name,
      onDismiss = { watchlistToRename = null },
      onConfirm = { name ->
        onRenameWatchlist(wl.id, name)
        watchlistToRename = null
      },
    )
  }

  if (showAddSymbolDialog && activeWatchlist != null) {
    AddSymbolDialog(
      onDismiss = { showAddSymbolDialog = false },
      onConfirm = { symbol, name, assetClass ->
        onAddSymbol(activeWatchlist.id, symbol, name, assetClass)
        showAddSymbolDialog = false
      },
    )
  }
}

private fun resolveQuoteForWatchlistItem(
  item: WatchlistItem,
  snapshot: MarketCommandSnapshot?,
): MarketQuote =
  snapshot?.watchlistQuotes?.get(item.symbol)
    ?: (snapshot?.indices.orEmpty() + snapshot?.futures.orEmpty())
      .firstOrNull { it.symbol == item.symbol }
    ?: MarketQuote(
      symbol = item.symbol,
      displaySymbol = item.symbol,
      name = item.displayName,
      price = 100.0,
      change = 0.0,
      changePercent = 0.0,
      previousClose = 100.0,
      dayHigh = 101.0,
      dayLow = 99.0,
      open = 100.0,
      volumeLabel = "—",
      sparkline = listOf(100.0, 100.2, 100.1, 100.4),
      category = QuoteCategory.WATCHLIST,
    )

@Composable
private fun WatchlistItemCard(
  item: WatchlistItem,
  quote: MarketQuote,
  manualReorderEnabled: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
  onTogglePin: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onSetAlert: () -> Unit,
  onDelete: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val accentColor = if (quote.isPositive) marketColors.gain else marketColors.loss
  val pillBg = if (quote.isPositive) marketColors.gainSurface else marketColors.lossSurface
  val assetClass = AssetClass.fromWire(item.assetClass)

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("watchlist_item_${item.symbol}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (manualReorderEnabled) {
          Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }

        Column(modifier = Modifier.weight(1.2f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = item.symbol,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = assetClass.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        MiniSparklineChart(
          points = quote.sparkline,
          isPositive = quote.isPositive,
          modifier = Modifier.width(56.dp).height(26.dp),
        )

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = String.format(Locale.US, "%,.2f", quote.price),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(6.dp))
                .background(pillBg)
                .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = String.format(Locale.US, "%+.2f%%", quote.changePercent),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = accentColor,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onTogglePin, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = if (item.pinnedToTop) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
            contentDescription = stringResource(R.string.cd_pin),
            tint =
              if (item.pinnedToTop) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = "Move up",
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.ArrowDownward,
            contentDescription = "Move down",
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(onClick = onSetAlert, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = "Set price alert for ${item.symbol}",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun WatchlistNameDialog(
  title: String,
  initialName: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Watchlist Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("watchlist_name_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { if (name.isNotBlank()) onConfirm(name) },
        enabled = name.isNotBlank(),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun AddSymbolDialog(
  onDismiss: () -> Unit,
  onConfirm: (String, String, AssetClass) -> Unit,
) {
  var symbol by remember { mutableStateOf("") }
  var displayName by remember { mutableStateOf("") }
  var selectedClass by remember { mutableStateOf(AssetClass.STOCK) }

  val presets =
    listOf(
      Triple("TSLA", "Tesla Inc.", AssetClass.STOCK),
      Triple("AMZN", "Amazon.com Inc.", AssetClass.STOCK),
      Triple("IWM", "iShares Russell 2000 ETF", AssetClass.ETF),
      Triple("SOL-USD", "Solana USD", AssetClass.CRYPTO),
      Triple("SI=F", "Silver Futures", AssetClass.COMMODITY),
      Triple("^VIX", "CBOE Volatility Index", AssetClass.INDEX),
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Asset to Watchlist") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(presets) { (sym, name, cls) ->
            FilterChip(
              selected = symbol == sym,
              onClick = {
                symbol = sym
                displayName = name
                selectedClass = cls
              },
              label = { Text(sym) },
            )
          }
        }
        OutlinedTextField(
          value = symbol,
          onValueChange = { symbol = it.uppercase() },
          label = { Text("Ticker Symbol (e.g. AAPL, BTC-USD, GC=F)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("add_symbol_input"),
        )
        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Company / Asset Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(AssetClass.entries) { cls ->
            FilterChip(
              selected = selectedClass == cls,
              onClick = { selectedClass = cls },
              label = { Text(cls.label) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (symbol.isNotBlank()) {
            onConfirm(symbol.trim(), displayName.trim().ifBlank { symbol.trim() }, selectedClass)
          }
        },
        enabled = symbol.isNotBlank(),
        modifier = Modifier.testTag("confirm_add_symbol_button"),
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
