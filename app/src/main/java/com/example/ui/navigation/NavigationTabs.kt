package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_MARKETS = "markets"
const val ROUTE_FUTURES = "futures"
const val ROUTE_WATCHLISTS = "watchlists"
const val ROUTE_CALENDAR = "calendar"
const val ROUTE_NEWS = "news"
const val ROUTE_BREADTH = "breadth"
const val ROUTE_PORTFOLIO = "portfolio"
const val ROUTE_ALERTS = "alerts"

enum class MainNavTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DASHBOARD(
    ROUTE_DASHBOARD,
    "Dashboard",
    Icons.Rounded.SpaceDashboard,
    Icons.Outlined.SpaceDashboard,
  ),
  MARKETS(
    ROUTE_MARKETS,
    "Markets",
    Icons.AutoMirrored.Rounded.ShowChart,
    Icons.AutoMirrored.Outlined.ShowChart,
  ),
  FUTURES(ROUTE_FUTURES, "Futures", Icons.Rounded.Bolt, Icons.Outlined.Bolt),
  WATCHLISTS(ROUTE_WATCHLISTS, "Watchlists", Icons.Rounded.Bookmarks, Icons.Outlined.Bookmarks),
  CALENDAR(ROUTE_CALENDAR, "Calendar", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
  NEWS(ROUTE_NEWS, "News", Icons.Rounded.Newspaper, Icons.Outlined.Newspaper),
  BREADTH(ROUTE_BREADTH, "Breadth", Icons.Rounded.GridView, Icons.Outlined.GridView);

  companion object {
    val defaultRoutes: List<String> = entries.map { it.route }

    fun fromRoute(route: String): MainNavTab? = entries.firstOrNull { it.route == route }

    fun normalizeOrder(savedRoutes: List<String>): List<MainNavTab> {
      val resolved = savedRoutes.mapNotNull { fromRoute(it) }.distinct()
      val missing = entries.filter { it !in resolved }
      return resolved + missing
    }

    fun resolveVisibleTabs(
      savedRoutes: List<String>,
      hiddenRoutes: Set<String>,
    ): List<MainNavTab> {
      val ordered = normalizeOrder(savedRoutes)
      return ordered.filter { it.route !in hiddenRoutes }
    }
  }
}
