package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Watchlist
import com.example.data.model.WatchlistItem
import com.example.data.repository.TickerSearchResult
import com.example.ui.theme.LocalMarketColors
import java.util.Locale

@Composable
fun TickerSearchBarSection(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearQuery: () -> Unit,
  results: List<TickerSearchResult>,
  isSearching: Boolean,
  existingSymbolsInTarget: Set<String>,
  addButtonLabel: String,
  onInspectTicker: (TickerSearchResult) -> Unit,
  onAddTicker: (TickerSearchResult) -> Unit,
  modifier: Modifier = Modifier,
  searchFieldTag: String = "ticker_search_input",
) {
  val focusManager = LocalFocusManager.current
  val marketColors = LocalMarketColors.current

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier = Modifier.fillMaxWidth().testTag(searchFieldTag),
      singleLine = true,
      placeholder = {
        Text(
          text = "Search stocks, ETFs, crypto (e.g. AAPL, NVDA, SPY)",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = "Search tickers",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      trailingIcon = {
        if (isSearching) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
          )
        } else if (query.isNotEmpty()) {
          IconButton(
            onClick = {
              onClearQuery()
              focusManager.clearFocus()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_ticker_search"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Clear search",
            )
          }
        }
      },
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.Characters,
          imeAction = ImeAction.Search,
        ),
      keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
      shape = RoundedCornerShape(16.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )

    AnimatedVisibility(visible = query.isNotBlank()) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier =
          Modifier.fillMaxWidth()
            .border(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant,
              RoundedCornerShape(18.dp),
            )
            .testTag("ticker_search_results_card"),
      ) {
        when {
          isSearching && results.isEmpty() -> {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Searching market tickers...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          results.isEmpty() -> {
            Text(
              text = "No matching tickers found for \"${query.trim()}\"",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
          else -> {
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            ) {
              items(results, key = { it.symbol }) { item ->
                val alreadyAdded = existingSymbolsInTarget.contains(item.symbol.uppercase())
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clickable {
                        focusManager.clearFocus()
                        onInspectTicker(item)
                      }
                      .padding(horizontal = 14.dp, vertical = 10.dp)
                      .testTag("search_result_${item.symbol}"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
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
                          text =
                            if (item.exchange.isNotBlank()) "${item.assetClass.label} • ${item.exchange}"
                            else item.assetClass.label,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                    }
                    Text(
                      text = item.name,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }

                  item.quote?.let { q ->
                    val accent = if (q.isPositive) marketColors.gain else marketColors.loss
                    Column(horizontalAlignment = Alignment.End) {
                      Text(
                        text = String.format(Locale.US, "%,.2f", q.price),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = String.format(Locale.US, "%+.2f%%", q.changePercent),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                      )
                    }
                  }

                  if (alreadyAdded) {
                    Surface(
                      shape = RoundedCornerShape(999.dp),
                      color = marketColors.gainSurface,
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.Check,
                          contentDescription = "Already in watchlist",
                          tint = marketColors.gain,
                          modifier = Modifier.size(14.dp),
                        )
                        Text(
                          text = "Added",
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.Bold,
                          color = marketColors.gain,
                        )
                      }
                    }
                  } else {
                    FilledTonalButton(
                      onClick = { onAddTicker(item) },
                      shape = RoundedCornerShape(999.dp),
                      modifier =
                        Modifier.minimumInteractiveComponentSize()
                          .testTag("add_search_result_${item.symbol}"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add ${item.symbol} to watchlist",
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = addButtonLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                      )
                    }
                  }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SelectWatchlistForTickerDialog(
  ticker: TickerSearchResult,
  watchlists: List<Watchlist>,
  watchlistItems: List<WatchlistItem>,
  onSelectWatchlist: (Watchlist) -> Unit,
  onDismiss: () -> Unit,
) {
  val marketColors = LocalMarketColors.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add ${ticker.symbol} to Watchlist") },
    text = {
      if (watchlists.isEmpty()) {
        Text(
          text = "Create a watchlist in the Watchlists tab first to save symbols.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = ticker.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          watchlists.forEach { wl ->
            val alreadyInList =
              watchlistItems.any {
                it.watchlistId == wl.id && it.symbol.equals(ticker.symbol, ignoreCase = true)
              }
            Surface(
              shape = RoundedCornerShape(14.dp),
              color =
                if (alreadyInList) marketColors.gainSurface
                else MaterialTheme.colorScheme.surfaceContainer,
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable(enabled = !alreadyInList) { onSelectWatchlist(wl) }
                  .testTag("pick_watchlist_${wl.id}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = wl.name,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                if (alreadyInList) {
                  Box(
                    modifier =
                      Modifier.clip(RoundedCornerShape(999.dp))
                        .background(marketColors.gain.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                  ) {
                    Text(
                      text = "In List",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = marketColors.gain,
                    )
                  }
                } else {
                  Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add to ${wl.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}
