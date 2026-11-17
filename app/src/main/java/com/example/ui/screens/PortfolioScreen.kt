package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AssetClass
import com.example.data.model.BenchmarkComparison
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.model.PortfolioHolding
import com.example.data.model.PortfolioPositionSummary
import com.example.data.model.PortfolioSummary
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.UiState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PortfolioScreen(
  holdingsState: UiState<List<PortfolioHolding>>,
  portfolioSummary: PortfolioSummary?,
  snapshot: MarketCommandSnapshot?,
  onAddHolding: (String, String, AssetClass, Double, Double) -> Unit,
  onUpdateHolding: (String, String, AssetClass, Double, Double) -> Unit,
  onDeleteHolding: (String) -> Unit,
  onOpenQuote: (MarketQuote) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  var showAddDialog by remember { mutableStateOf(false) }
  var editingHolding by remember { mutableStateOf<PortfolioHolding?>(null) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_holding_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add portfolio holding")
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("portfolio_screen"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("portfolio_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Portfolio & Benchmarks",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Track daily P&L vs S&P 500, Nasdaq & Dow",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      when (holdingsState) {
        is UiState.Loading -> {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
        }
        is UiState.Error -> {
          item {
            Text(
              text = holdingsState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        is UiState.Success -> {
          if (portfolioSummary == null || portfolioSummary.positions.isEmpty()) {
            item {
              PortfolioEmptyStateCard(onAddFirst = { showAddDialog = true })
            }
          } else {
            item {
              PortfolioSummaryHeroCard(summary = portfolioSummary)
            }
            item {
              BenchmarkComparisonCard(
                portfolioDayPct = portfolioSummary.dayChangePercent,
                benchmarks = portfolioSummary.benchmarks,
              )
            }
            item {
              Text(
                text = "Holdings (${portfolioSummary.positions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
            items(portfolioSummary.positions, key = { it.holding.id }) { pos ->
              PortfolioPositionCard(
                position = pos,
                onClick = { onOpenQuote(pos.quote) },
                onEdit = { editingHolding = pos.holding },
                onDelete = { onDeleteHolding(pos.holding.id) },
              )
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    HoldingEditorDialog(
      existing = null,
      snapshot = snapshot,
      onDismiss = { showAddDialog = false },
      onConfirm = { symbol, name, assetClass, shares, avgCost ->
        onAddHolding(symbol, name, assetClass, shares, avgCost)
        showAddDialog = false
      },
    )
  }

  editingHolding?.let { holding ->
    HoldingEditorDialog(
      existing = holding,
      snapshot = snapshot,
      onDismiss = { editingHolding = null },
      onConfirm = { _, name, assetClass, shares, avgCost ->
        onUpdateHolding(holding.id, name, assetClass, shares, avgCost)
        editingHolding = null
      },
    )
  }
}

@Composable
private fun PortfolioEmptyStateCard(onAddFirst: () -> Unit) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().testTag("portfolio_empty_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.PieChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
      )
      Text(
        text = "Start Tracking Your Holdings",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "Enter your stock, ETF, crypto, or commodity positions with share count and cost basis to monitor daily performance vs S&P 500, Nasdaq, and Dow.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onAddFirst,
        modifier = Modifier.testTag("portfolio_add_first_button"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add Holding")
      }
    }
  }
}

@Composable
private fun PortfolioSummaryHeroCard(summary: PortfolioSummary) {
  val marketColors = LocalMarketColors.current
  val dayAccent = if (summary.dayChangeDollar >= 0.0) marketColors.gain else marketColors.loss
  val dayBg = if (summary.dayChangeDollar >= 0.0) marketColors.gainSurface else marketColors.lossSurface
  val totalAccent = if (summary.totalGainDollar >= 0.0) marketColors.gain else marketColors.loss

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().testTag("portfolio_summary_card"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = "TOTAL PORTFOLIO VALUE",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = String.format(Locale.US, "$%,.2f", summary.totalValue),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(shape = RoundedCornerShape(10.dp), color = dayBg, modifier = Modifier.weight(1f)) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "Today's P&L",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = String.format(
                Locale.US,
                "%+,.2f (%+.2f%%)",
                summary.dayChangeDollar,
                summary.dayChangePercent,
              ),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = dayAccent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "Total Return",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = String.format(
                Locale.US,
                "%+,.2f (%+.2f%%)",
                summary.totalGainDollar,
                summary.totalGainPercent,
              ),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = totalAccent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BenchmarkComparisonCard(
  portfolioDayPct: Double,
  benchmarks: List<BenchmarkComparison>,
) {
  val marketColors = LocalMarketColors.current
  val spxAlpha = benchmarks.firstOrNull { it.symbol == "SPX" }?.portfolioAlphaPercent

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().testTag("portfolio_benchmark_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Daily Benchmark Comparison",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Portfolio today vs major U.S. indices",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (spxAlpha != null) {
          val alphaPositive = spxAlpha >= 0.0
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (alphaPositive) marketColors.gainSurface else marketColors.lossSurface,
          ) {
            Text(
              text = String.format(Locale.US, "α vs SPX %+.2f%%", spxAlpha),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (alphaPositive) marketColors.gain else marketColors.loss,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            )
          }
        }
      }

      if (benchmarks.isNotEmpty()) {
        VicoBenchmarkBarChart(
          portfolioDayPct = portfolioDayPct,
          benchmarks = benchmarks,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          benchmarks.forEach { bm ->
            val alphaPos = bm.portfolioAlphaPercent >= 0.0
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.weight(1f),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = bm.symbol,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = String.format(Locale.US, "%+.2f%%", bm.indexChangePercent),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = String.format(Locale.US, "%+.2f%% α", bm.portfolioAlphaPercent),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (alphaPos) marketColors.gain else marketColors.loss,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VicoBenchmarkBarChart(
  portfolioDayPct: Double,
  benchmarks: List<BenchmarkComparison>,
) {
  val marketColors = LocalMarketColors.current
  val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val labels = remember(benchmarks) { listOf("PORT") + benchmarks.map { it.symbol } }
  val values = remember(portfolioDayPct, benchmarks) {
    listOf(portfolioDayPct) + benchmarks.map { it.indexChangePercent }
  }
  val modelProducer = remember { CartesianChartModelProducer() }

  LaunchedEffect(values) {
    modelProducer.runTransaction {
      columnSeries { series(values) }
    }
  }

  val positiveCol =
    rememberLineComponent(
      fill = fill(marketColors.gain),
      thickness = 22.dp,
      shape = CorneredShape.rounded(topLeftDp = 6f, topRightDp = 6f),
    )
  val negativeCol =
    rememberLineComponent(
      fill = fill(marketColors.loss),
      thickness = 22.dp,
      shape = CorneredShape.rounded(bottomLeftDp = 6f, bottomRightDp = 6f),
    )
  val columnProvider =
    remember(positiveCol, negativeCol) {
      object : ColumnCartesianLayer.ColumnProvider {
        override fun getColumn(
          entry: ColumnCartesianLayerModel.Entry,
          seriesIndex: Int,
          extraStore: ExtraStore,
        ): LineComponent = if (entry.y >= 0.0) positiveCol else negativeCol

        override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent =
          positiveCol
      }
    }

  val xFormatter = remember(labels) {
    CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.roundToInt()) ?: "" }
  }
  val yFormatter = remember {
    CartesianValueFormatter { _, value, _ -> String.format(Locale.US, "%+.1f%%", value) }
  }

  CartesianChartHost(
    chart =
      rememberCartesianChart(
        rememberColumnCartesianLayer(columnProvider = columnProvider),
        startAxis =
          VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 10.sp),
            valueFormatter = yFormatter,
          ),
        bottomAxis =
          HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(color = axisLabelColor, textSize = 11.sp),
            valueFormatter = xFormatter,
          ),
      ),
    modelProducer = modelProducer,
    scrollState = rememberVicoScrollState(scrollEnabled = false),
    modifier = Modifier.fillMaxWidth().height(165.dp).testTag("vico_portfolio_benchmark_chart"),
  )
}

@Composable
private fun PortfolioPositionCard(
  position: PortfolioPositionSummary,
  onClick: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val marketColors = LocalMarketColors.current
  val dayAccent = if (position.dayChangeDollar >= 0.0) marketColors.gain else marketColors.loss
  val totalAccent = if (position.totalReturnDollar >= 0.0) marketColors.gain else marketColors.loss

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("portfolio_holding_${position.holding.symbol}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = position.holding.symbol,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
              Text(
                text = String.format(Locale.US, "%.1f%%", position.weightPercent),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          Text(
            text = "${String.format(Locale.US, "%.2f", position.holding.shares)} sh @ ${String.format(Locale.US, "$%,.2f", position.holding.avgCostBasis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = String.format(Locale.US, "$%,.2f", position.marketValue),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = String.format(
              Locale.US,
              "Day %+,.2f (%+.2f%%)",
              position.dayChangeDollar,
              position.dayChangePercent,
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = dayAccent,
          )
        }
      }

      LinearProgressIndicator(
        progress = { (position.weightPercent / 100.0).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = String.format(
            Locale.US,
            "Total Return: %+,.2f (%+.2f%%)",
            position.totalReturnDollar,
            position.totalReturnPercent,
          ),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = totalAccent,
        )
        Row {
          IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit holding", modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = stringResource(R.string.cd_delete),
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HoldingEditorDialog(
  existing: PortfolioHolding?,
  snapshot: MarketCommandSnapshot?,
  onDismiss: () -> Unit,
  onConfirm: (String, String, AssetClass, Double, Double) -> Unit,
) {
  var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
  var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
  var assetClass by remember {
    mutableStateOf(
      existing?.assetClass?.let { AssetClass.fromWire(it) } ?: AssetClass.STOCK
    )
  }
  var sharesText by remember {
    mutableStateOf(existing?.shares?.let { String.format(Locale.US, "%.2f", it) } ?: "")
  }
  var costText by remember {
    mutableStateOf(existing?.avgCostBasis?.let { String.format(Locale.US, "%.2f", it) } ?: "")
  }

  val presets =
    listOf(
      Triple("SPY", "SPDR S&P 500 ETF", AssetClass.ETF),
      Triple("QQQ", "Invesco QQQ Trust", AssetClass.ETF),
      Triple("NVDA", "NVIDIA Corp.", AssetClass.STOCK),
      Triple("AAPL", "Apple Inc.", AssetClass.STOCK),
      Triple("MSFT", "Microsoft Corp.", AssetClass.STOCK),
      Triple("BTC-USD", "Bitcoin USD", AssetClass.CRYPTO),
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Add Portfolio Holding" else "Edit ${existing.symbol}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (existing == null) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presets) { (sym, name, cls) ->
              FilterChip(
                selected = symbol == sym,
                onClick = {
                  symbol = sym
                  displayName = name
                  assetClass = cls
                  val live = snapshot?.watchlistQuotes?.get(sym)?.price
                  if (costText.isBlank() && live != null) {
                    costText = String.format(Locale.US, "%.2f", live * 0.94)
                  }
                },
                label = { Text(sym) },
              )
            }
          }
          OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = { Text("Ticker Symbol") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("holding_symbol_input"),
          )
        }
        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Asset Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = sharesText,
          onValueChange = { sharesText = it },
          label = { Text("Shares / Units") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("holding_shares_input"),
        )
        OutlinedTextField(
          value = costText,
          onValueChange = { costText = it },
          label = { Text("Average Cost Basis ($)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("holding_cost_input"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(AssetClass.entries) { cls ->
            FilterChip(
              selected = assetClass == cls,
              onClick = { assetClass = cls },
              label = { Text(cls.label) },
            )
          }
        }
      }
    },
    confirmButton = {
      val parsedShares = sharesText.toDoubleOrNull()
      val parsedCost = costText.toDoubleOrNull()
      val valid =
        symbol.isNotBlank() &&
          parsedShares != null &&
          parsedShares > 0.0 &&
          parsedCost != null &&
          parsedCost >= 0.0
      Button(
        onClick = {
          if (valid && parsedShares != null && parsedCost != null) {
            onConfirm(
              symbol.trim(),
              displayName.trim().ifBlank { symbol.trim() },
              assetClass,
              parsedShares,
              parsedCost,
            )
          }
        },
        enabled = valid,
        modifier = Modifier.testTag("confirm_holding_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
