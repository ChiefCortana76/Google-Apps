package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.AppPreferences
import com.example.ui.navigation.MainNavTab

@Composable
fun BottomBarSettingsCard(
  preferences: AppPreferences,
  onSetBottomBarVisible: (Boolean) -> Unit,
  onMoveTab: (route: String, direction: Int) -> Unit,
  onSetTabEnabled: (route: String, enabled: Boolean) -> Unit,
  onResetDefaults: () -> Unit,
) {
  val orderedTabs = MainNavTab.normalizeOrder(preferences.bottomBarTabOrder)
  val visibleTabs =
    MainNavTab.resolveVisibleTabs(preferences.bottomBarTabOrder, preferences.hiddenBottomBarTabs)
  val barEffectivelyShown = preferences.bottomBarVisible && visibleTabs.isNotEmpty()
  val isCustomized =
    !preferences.bottomBarVisible ||
      orderedTabs.map { it.route } != MainNavTab.defaultRoutes ||
      preferences.hiddenBottomBarTabs.isNotEmpty()

  Card(
    shape = RoundedCornerShape(22.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.testTag("bottom_bar_settings_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
          ) {
            Icon(
              imageVector = Icons.Rounded.ViewCarousel,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp).size(20.dp),
            )
          }
          Column {
            Text(
              text = stringResource(R.string.bottom_bar_settings_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = stringResource(R.string.bottom_bar_settings_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(999.dp),
          color =
            if (barEffectivelyShown) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text =
              if (!preferences.bottomBarVisible) "Bar Off"
              else "${visibleTabs.size}/${orderedTabs.size} On",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color =
              if (barEffectivelyShown) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      // Master bar visibility switch
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Show Bottom Ribbon Bar",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Tap app title anytime to return Home when hidden",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = preferences.bottomBarVisible,
          onCheckedChange = onSetBottomBarVisible,
          modifier = Modifier.testTag("bottom_bar_visibility_switch"),
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Live mini ribbon preview
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .alpha(if (preferences.bottomBarVisible) 1f else 0.55f)
            .testTag("bottom_bar_preview_strip"),
      ) {
        if (!barEffectivelyShown) {
          Text(
            text =
              if (!preferences.bottomBarVisible) "Bottom ribbon is turned off"
              else "All tabs toggled off — ribbon bar hidden",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            visibleTabs.forEachIndexed { idx, tab ->
              Column(
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Icon(
                  imageVector = if (idx == 0) tab.selectedIcon else tab.unselectedIcon,
                  contentDescription = null,
                  tint =
                    if (idx == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = tab.label,
                  fontSize = 8.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Individual Tabs (Toggle & Order)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isCustomized) {
          TextButton(
            onClick = onResetDefaults,
            modifier = Modifier.testTag("reset_bottom_bar_button"),
          ) {
            Icon(
              Icons.Rounded.RestartAlt,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset")
          }
        }
      }

      orderedTabs.forEachIndexed { index, tab ->
        val enabled = tab.route !in preferences.hiddenBottomBarTabs
        val slotIndex = visibleTabs.indexOf(tab)
        val borderColor =
          if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, borderColor, RoundedCornerShape(14.dp))
              .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
              .alpha(if (enabled) 1f else 0.65f)
              .testTag("bottom_bar_row_${tab.route}")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = if (enabled) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint =
                  if (enabled) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Switch(
              checked = enabled,
              onCheckedChange = { checked -> onSetTabEnabled(tab.route, checked) },
              modifier = Modifier.testTag("nav_toggle_${tab.route}"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text =
                if (enabled) "Shown in ribbon • Slot #${slotIndex + 1}"
                else "Hidden from bottom ribbon",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 30.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { onMoveTab(tab.route, -1) },
                enabled = index > 0,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("nav_move_up_${tab.route}"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.ArrowUpward,
                  contentDescription = stringResource(R.string.cd_move_tab_up, tab.label),
                  modifier = Modifier.size(18.dp),
                )
              }
              IconButton(
                onClick = { onMoveTab(tab.route, 1) },
                enabled = index < orderedTabs.lastIndex,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("nav_move_down_${tab.route}"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.ArrowDownward,
                  contentDescription = stringResource(R.string.cd_move_tab_down, tab.label),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
