package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.MarketCommandSnapshot
import com.example.data.model.MarketQuote
import com.example.data.repository.MarketDataRepository
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

const val ACTION_REFRESH_WIDGET = "com.example.ACTION_REFRESH_WIDGET"

class MarketWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    // Immediate baseline render so the widget is never blank
    appWidgetIds.forEach { widgetId ->
      appWidgetManager.updateAppWidget(
        widgetId,
        MarketWidgetUpdater.buildDefaultMarketViews(context),
      )
    }
    refreshAllWidgetsAsync(context)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH_WIDGET) {
      refreshAllWidgetsAsync(context)
    }
  }

  private fun refreshAllWidgetsAsync(context: Context) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val snapshot =
          withTimeoutOrNull(12_000L) {
            MarketDataRepository().fetchFullSnapshot(emptyList())
          }
        if (snapshot != null) {
          MarketWidgetUpdater.updateAllWidgets(context.applicationContext, snapshot)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}

class FuturesWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.widget_futures)
      views.setTextViewText(R.id.widget_futures_title, "24/7 Overnight Futures")
      views.setTextViewText(
        R.id.widget_futures_body,
        "ES 6,012 (+0.44%) • NQ 21,084 (+0.72%)\nYM 44,015 (+0.24%) • RTY 2,378 • VX 15.45",
      )
      views.setOnClickPendingIntent(R.id.widget_futures_root, launchAppIntent(context))
      appWidgetManager.updateAppWidget(widgetId, views)
    }
  }
}

class EconomicEventWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.widget_economic)
      views.setTextViewText(R.id.widget_economic_title, "Next Economic Release")
      views.setTextViewText(R.id.widget_economic_event, "Syncing today's schedule...")
      views.setTextViewText(R.id.widget_economic_countdown, "Tap to open economic calendar")
      views.setOnClickPendingIntent(R.id.widget_economic_root, launchAppIntent(context))
      appWidgetManager.updateAppWidget(widgetId, views)
    }
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val snapshot =
          withTimeoutOrNull(12_000L) {
            MarketDataRepository().fetchFullSnapshot(emptyList(), forceCalendarRefresh = true)
          }
        if (snapshot != null) {
          MarketWidgetUpdater.updateAllWidgets(context.applicationContext, snapshot)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}

private fun launchAppIntent(context: Context): PendingIntent {
  val intent = Intent(context, MainActivity::class.java)
  return PendingIntent.getActivity(
    context,
    0,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

private fun refreshWidgetIntent(context: Context): PendingIntent {
  val intent =
    Intent(context, MarketWidgetProvider::class.java).apply {
      action = ACTION_REFRESH_WIDGET
    }
  return PendingIntent.getBroadcast(
    context,
    101,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )
}

object MarketWidgetUpdater {
  private val colorGain = Color.parseColor("#00C805")
  private val colorLoss = Color.parseColor("#FF453A")

  fun buildDefaultMarketViews(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_market)
    views.setTextViewText(R.id.widget_market_title, "THE MARKET • INDICES")
    views.setTextViewText(R.id.widget_market_updated, "Tap sync")
    views.setOnClickPendingIntent(R.id.widget_market_root, launchAppIntent(context))
    views.setOnClickPendingIntent(R.id.widget_market_refresh_btn, refreshWidgetIntent(context))
    return views
  }

  private fun bindIndexTile(
    views: RemoteViews,
    labelId: Int,
    priceId: Int,
    changeId: Int,
    fallbackLabel: String,
    quote: MarketQuote?,
  ) {
    views.setTextViewText(labelId, fallbackLabel)
    if (quote != null) {
      views.setTextViewText(priceId, String.format(Locale.US, "%,.2f", quote.price))
      views.setTextViewText(changeId, String.format(Locale.US, "%+.2f%%", quote.changePercent))
      views.setTextColor(changeId, if (quote.isPositive) colorGain else colorLoss)
    }
  }

  fun updateAllWidgets(context: Context, snapshot: MarketCommandSnapshot) {
    val manager = AppWidgetManager.getInstance(context)

    val marketIds =
      manager.getAppWidgetIds(ComponentName(context, MarketWidgetProvider::class.java))
    if (marketIds.isNotEmpty()) {
      val spx =
        snapshot.indices.firstOrNull {
          it.displaySymbol.equals("SPX", true) || it.symbol == "^GSPC"
        }
      val ndx =
        snapshot.indices.firstOrNull {
          it.displaySymbol.equals("NDX", true) || it.symbol == "^IXIC"
        }
      val dow =
        snapshot.indices.firstOrNull {
          it.displaySymbol.equals("DOW", true) || it.symbol == "^DJI"
        }

      marketIds.forEach { id ->
        val views = RemoteViews(context.packageName, R.layout.widget_market)
        views.setTextViewText(
          R.id.widget_market_title,
          if (snapshot.anyLiveFeedConnected) "THE MARKET • LIVE INDICES" else "THE MARKET • INDICES",
        )
        views.setTextViewText(R.id.widget_market_updated, snapshot.lastUpdatedLabel)

        bindIndexTile(
          views = views,
          labelId = R.id.widget_spx_label,
          priceId = R.id.widget_spx_price,
          changeId = R.id.widget_spx_change,
          fallbackLabel = "S&P 500",
          quote = spx,
        )
        bindIndexTile(
          views = views,
          labelId = R.id.widget_ndx_label,
          priceId = R.id.widget_ndx_price,
          changeId = R.id.widget_ndx_change,
          fallbackLabel = "NASDAQ",
          quote = ndx,
        )
        bindIndexTile(
          views = views,
          labelId = R.id.widget_dow_label,
          priceId = R.id.widget_dow_price,
          changeId = R.id.widget_dow_change,
          fallbackLabel = "DOW",
          quote = dow,
        )

        views.setOnClickPendingIntent(R.id.widget_market_root, launchAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_market_refresh_btn, refreshWidgetIntent(context))
        manager.updateAppWidget(id, views)
      }
    }

    val futuresIds =
      manager.getAppWidgetIds(ComponentName(context, FuturesWidgetProvider::class.java))
    if (futuresIds.isNotEmpty()) {
      val line =
        snapshot.futures.joinToString(" • ") {
          String.format(Locale.US, "%s %,.0f (%+.1f%%)", it.displaySymbol, it.price, it.changePercent)
        }
      futuresIds.forEach { id ->
        val views = RemoteViews(context.packageName, R.layout.widget_futures)
        views.setTextViewText(R.id.widget_futures_title, "Futures 24/7 • ${snapshot.lastUpdatedLabel}")
        views.setTextViewText(R.id.widget_futures_body, line)
        views.setOnClickPendingIntent(R.id.widget_futures_root, launchAppIntent(context))
        manager.updateAppWidget(id, views)
      }
    }

    val econIds =
      manager.getAppWidgetIds(ComponentName(context, EconomicEventWidgetProvider::class.java))
    if (econIds.isNotEmpty()) {
      val nextEvent =
        snapshot.economicEvents.firstOrNull { it.countdownMinutes != null }
          ?: snapshot.economicEvents.firstOrNull()
      econIds.forEach { id ->
        val views = RemoteViews(context.packageName, R.layout.widget_economic)
        views.setTextViewText(R.id.widget_economic_title, "Next Major Release")
        views.setTextViewText(
          R.id.widget_economic_event,
          nextEvent?.let { "${it.title} • ${it.timeEt}" } ?: "No scheduled releases",
        )
        views.setTextViewText(
          R.id.widget_economic_countdown,
          nextEvent?.let {
            val cd = it.countdownMinutes?.let { m -> "In ${m}m • " } ?: ""
            "${cd}Fcst ${it.forecast} • Prev ${it.previous}"
          } ?: "",
        )
        views.setOnClickPendingIntent(R.id.widget_economic_root, launchAppIntent(context))
        manager.updateAppWidget(id, views)
      }
    }
  }
}
