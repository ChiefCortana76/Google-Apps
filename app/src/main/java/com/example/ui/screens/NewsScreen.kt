package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MarketNewsArticle
import com.example.data.model.NewsCategory
import com.example.ui.components.MarketSwipeRefresh
import com.example.ui.theme.LocalMarketColors
import java.util.Locale

private val PRIMARY_NEWS_CATEGORIES =
  listOf(
    NewsCategory.ALL,
    NewsCategory.MARKETS,
    NewsCategory.ECONOMICS,
    NewsCategory.FEDERAL_RESERVE,
    NewsCategory.EARNINGS,
    NewsCategory.CRYPTO,
  )

@Composable
fun NewsScreen(
  articles: List<MarketNewsArticle>,
  isRefreshing: Boolean = false,
  onRefresh: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var selectedCategory by rememberSaveable { mutableStateOf(NewsCategory.ALL) }
  var expandedArticleId by rememberSaveable { mutableStateOf<String?>(null) }

  val visibleCategories =
    remember(articles) {
      val extraCategories =
        listOf(NewsCategory.TECHNOLOGY, NewsCategory.ENERGY).filter { cat ->
          articles.any { it.category == cat }
        }
      PRIMARY_NEWS_CATEGORIES + extraCategories
    }

  val countsByCategory =
    remember(articles) {
      NewsCategory.entries.associateWith { cat ->
        if (cat == NewsCategory.ALL) articles.size else articles.count { it.category == cat }
      }
    }

  val filteredArticles =
    remember(articles, selectedCategory) {
      if (selectedCategory == NewsCategory.ALL) articles
      else articles.filter { it.category == selectedCategory }
    }

  MarketSwipeRefresh(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    testTag = "news_swipe_refresh",
    modifier = modifier.fillMaxSize(),
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("news_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item(key = "news_header") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Market News Wire",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Markets, Economics, Fed, Earnings & Crypto headlines",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(999.dp),
              ) {
                Text(
                  text = "${filteredArticles.size} stories",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
              }
            }

            LazyRow(
              modifier = Modifier.fillMaxWidth().testTag("news_category_row"),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(visibleCategories, key = { it.name }) { category ->
                val count = countsByCategory[category] ?: 0
                val selected = selectedCategory == category
                FilterChip(
                  selected = selected,
                  onClick = { selectedCategory = category },
                  leadingIcon = {
                    Icon(
                      imageVector = categoryIcon(category),
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  label = {
                    Text(
                      text =
                        if (category == NewsCategory.ALL) "${category.label} ($count)"
                        else if (count > 0) "${category.label} · $count"
                        else category.label,
                      maxLines = 1,
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                  modifier = Modifier.testTag("news_category_${category.name.lowercase(Locale.US)}"),
                )
              }
            }
          }
        }

        if (filteredArticles.isEmpty()) {
          item(key = "news_empty") {
            Card(
              modifier = Modifier.fillMaxWidth().testTag("news_empty_state"),
              shape = RoundedCornerShape(20.dp),
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Newspaper,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(40.dp),
                )
                Text(
                  text = "No ${selectedCategory.label} headlines yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Pull down to refresh the wire or switch back to all feeds.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = { selectedCategory = NewsCategory.ALL },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Text("Show All Feeds")
                }
              }
            }
          }
        } else {
          items(filteredArticles, key = { it.id }) { article ->
            val isExpanded = expandedArticleId == article.id
            NewsArticleCard(
              article = article,
              isExpanded = isExpanded,
              onToggleExpand = {
                expandedArticleId = if (isExpanded) null else article.id
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NewsArticleCard(
  article: MarketNewsArticle,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val uriHandler = LocalUriHandler.current
  val badgeTint = categoryBadgeColor(article.category, marketColors.gain, marketColors.amber)

  Card(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onToggleExpand)
        .minimumInteractiveComponentSize()
        .testTag("news_card_${article.id}"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
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
          Surface(
            color = badgeTint.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = article.category.label.uppercase(Locale.US),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = badgeTint,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
          article.sentimentPositive?.let { positive ->
            Surface(
              color =
                (if (positive) marketColors.gain else marketColors.loss).copy(alpha = 0.12f),
              shape = RoundedCornerShape(8.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(6.dp)
                      .clip(CircleShape)
                      .background(if (positive) marketColors.gain else marketColors.loss)
                )
                Text(
                  text = if (positive) "Bullish" else "Bearish",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (positive) marketColors.gain else marketColors.loss,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }

        Text(
          text = "${article.source} • ${article.publishedAgo}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
        text = article.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Text(
        text = article.summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
      )

      AnimatedVisibility(visible = isExpanded && article.url.isNotBlank()) {
        Column {
          Spacer(modifier = Modifier.height(4.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = {
              runCatching { uriHandler.openUri(article.url) }
            },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Read Full Story")
          }
        }
      }
    }
  }
}

private fun categoryIcon(category: NewsCategory): ImageVector =
  when (category) {
    NewsCategory.ALL -> Icons.Rounded.Newspaper
    NewsCategory.MARKETS -> Icons.AutoMirrored.Rounded.TrendingUp
    NewsCategory.ECONOMICS -> Icons.Rounded.Public
    NewsCategory.FEDERAL_RESERVE -> Icons.Rounded.AccountBalance
    NewsCategory.EARNINGS -> Icons.Rounded.Assessment
    NewsCategory.CRYPTO -> Icons.Rounded.CurrencyBitcoin
    NewsCategory.TECHNOLOGY -> Icons.Rounded.Memory
    NewsCategory.ENERGY -> Icons.Rounded.OilBarrel
  }

@Composable
private fun categoryBadgeColor(
  category: NewsCategory,
  gainColor: Color,
  amberColor: Color,
): Color =
  when (category) {
    NewsCategory.MARKETS -> gainColor
    NewsCategory.FEDERAL_RESERVE -> amberColor
    NewsCategory.ECONOMICS -> MaterialTheme.colorScheme.primary
    NewsCategory.EARNINGS -> MaterialTheme.colorScheme.secondary
    NewsCategory.CRYPTO -> MaterialTheme.colorScheme.tertiary
    NewsCategory.TECHNOLOGY -> MaterialTheme.colorScheme.primary
    NewsCategory.ENERGY -> amberColor
    NewsCategory.ALL -> MaterialTheme.colorScheme.primary
  }
