package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.ThemeMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

@Immutable
data class MarketColors(
  val gain: Color,
  val gainSurface: Color,
  val loss: Color,
  val lossSurface: Color,
  val amber: Color,
  val amberSurface: Color,
  val neutral: Color,
  val isOledDark: Boolean,
)

val LocalMarketColors =
  staticCompositionLocalOf {
    MarketColors(
      gain = MarketGreen,
      gainSurface = MarketGreen.copy(alpha = 0.14f),
      loss = MarketRed,
      lossSurface = MarketRed.copy(alpha = 0.14f),
      amber = MarketAmber,
      amberSurface = MarketAmber.copy(alpha = 0.16f),
      neutral = OledTextSecondary,
      isOledDark = true,
    )
  }

private val OledDarkColorScheme =
  darkColorScheme(
    primary = MarketGreen,
    onPrimary = Color(0xFF001A01),
    primaryContainer = MarketGreenDeep,
    onPrimaryContainer = Color(0xFF8CFF91),
    secondary = MarketGreenBright,
    onSecondary = Color(0xFF001A01),
    secondaryContainer = Color(0xFF0D2818),
    onSecondaryContainer = Color(0xFF9DF8B6),
    tertiary = MarketAmber,
    onTertiary = Color(0xFF2B1700),
    background = OledBlack,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceContainer,
    onSurfaceVariant = OledTextSecondary,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = OledSurfaceLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceHigh,
    surfaceContainerHighest = Color(0xFF262D36),
    outline = OledBorder,
    outlineVariant = OledBorderSubtle,
    error = MarketRed,
    onError = Color.White,
  )

private val CrispLightColorScheme =
  lightColorScheme(
    primary = MarketGreen,
    onPrimary = Color.White,
    primaryContainer = MarketGreenMint,
    onPrimaryContainer = Color(0xFF003302),
    secondary = Color(0xFF00A804),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F9E8),
    onSecondaryContainer = Color(0xFF003302),
    tertiary = Color(0xFFE68A00),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDCE3EA),
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = MarketRed,
    onError = Color.White,
  )

fun isUsMarketOpenNow(
  openMinutesEt: Int,
  closeMinutesEt: Int,
  nowEt: ZonedDateTime = ZonedDateTime.now(ZoneId.of("America/New_York")),
): Boolean {
  val day = nowEt.dayOfWeek
  if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false
  val currentMinutes = nowEt.hour * 60 + nowEt.minute
  return if (openMinutesEt <= closeMinutesEt) {
    currentMinutes in openMinutesEt until closeMinutesEt
  } else {
    currentMinutes >= openMinutesEt || currentMinutes < closeMinutesEt
  }
}

fun resolveDarkTheme(
  themeMode: ThemeMode,
  openMinutesEt: Int,
  closeMinutesEt: Int,
  nowEt: ZonedDateTime = ZonedDateTime.now(ZoneId.of("America/New_York")),
): Boolean =
  when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.MARKET_AUTO -> !isUsMarketOpenNow(openMinutesEt, closeMinutesEt, nowEt)
  }

@Composable
fun MarketPulseTheme(
  darkTheme: Boolean,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) OledDarkColorScheme else CrispLightColorScheme
  val marketColors =
    if (darkTheme) {
      MarketColors(
        gain = MarketGreen,
        gainSurface = MarketGreen.copy(alpha = 0.16f),
        loss = MarketRed,
        lossSurface = MarketRed.copy(alpha = 0.16f),
        amber = MarketAmber,
        amberSurface = MarketAmber.copy(alpha = 0.16f),
        neutral = OledTextSecondary,
        isOledDark = true,
      )
    } else {
      MarketColors(
        gain = Color(0xFF00A804),
        gainSurface = Color(0xFF00C805).copy(alpha = 0.12f),
        loss = Color(0xFFE54500),
        lossSurface = Color(0xFFFF5000).copy(alpha = 0.12f),
        amber = Color(0xFFD97706),
        amberSurface = Color(0xFFFFA726).copy(alpha = 0.16f),
        neutral = LightTextSecondary,
        isOledDark = false,
      )
    }

  CompositionLocalProvider(LocalMarketColors provides marketColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      content = content,
    )
  }
}
