package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AssetClass(val wireValue: String, val label: String) {
  STOCK("STOCK", "Stock"),
  ETF("ETF", "ETF"),
  INDEX("INDEX", "Index"),
  CRYPTO("CRYPTO", "Crypto"),
  COMMODITY("COMMODITY", "Commodity");

  companion object {
    fun fromWire(value: String): AssetClass =
      entries.firstOrNull { it.wireValue == value } ?: STOCK
  }
}

@IgnoreExtraProperties
data class Watchlist(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val pinnedToTop: Boolean = false,
  val sortOrder: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WatchlistItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val watchlistId: String = "",
  val symbol: String = "",
  val displayName: String = "",
  val assetClass: String = AssetClass.STOCK.wireValue,
  val pinnedToTop: Boolean = false,
  val sortOrder: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class ThemeMode(val wireValue: String, val label: String) {
  MARKET_AUTO("MARKET_AUTO", "Market Hours (Auto)"),
  DARK("DARK", "OLED Dark"),
  LIGHT("LIGHT", "Light");

  companion object {
    fun fromWire(value: String): ThemeMode =
      entries.firstOrNull { it.wireValue == value } ?: MARKET_AUTO
  }
}

enum class RefreshInterval(
  val seconds: Int,
  val shortLabel: String,
  val label: String,
) {
  SEC_15(15, "15s", "15 seconds (Real-Time)"),
  SEC_30(30, "30s", "30 seconds (Balanced)"),
  SEC_60(60, "60s", "60 seconds (Standard)"),
  MIN_5(300, "5m", "5 minutes (Low Data)");

  companion object {
    fun fromSeconds(seconds: Int): RefreshInterval =
      entries.firstOrNull { it.seconds == seconds } ?: SEC_30
  }
}

@IgnoreExtraProperties
data class UserPreferencesDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val themeMode: String = ThemeMode.MARKET_AUTO.wireValue,
  val marketOpenMinutesEt: Long = 570L, // 09:30 ET
  val marketCloseMinutesEt: Long = 960L, // 16:00 ET
  val refreshIntervalSeconds: Long = 30L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
