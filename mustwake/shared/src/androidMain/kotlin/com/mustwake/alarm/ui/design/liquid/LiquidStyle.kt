package com.mustwake.alarm.ui.design.liquid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Constants extracted 1:1 from Kyant0/AndroidLiquidGlass (`kmp` branch, catalog sources).
 * Keep values identical to the reference; see docs/appearance.md for the source mapping.
 */
object LiquidStyle {
    // Colors (reference: LiquidBottomTabs / LiquidToggle / LiquidButton / DialogContent).
    val AccentLight = Color(0xFF0088FF)
    val AccentDark = Color(0xFF0091FF)
    val ContainerLight = Color(0xFFFAFAFA).copy(alpha = 0.4f)
    val ContainerDark = Color(0xFF121212).copy(alpha = 0.4f)
    val DialogContainerLight = Color(0xFFFAFAFA).copy(alpha = 0.6f)
    val DialogContainerDark = Color(0xFF121212).copy(alpha = 0.4f)
    val DialogDimLight = Color(0xFF29293A).copy(alpha = 0.23f)
    val DialogDimDark = Color(0xFF121212).copy(alpha = 0.56f)
    val ToggleTrackLight = Color(0xFF787878).copy(alpha = 0.2f)
    val ToggleTrackDark = Color(0xFF787880).copy(alpha = 0.36f)
    val ToggleAccentLight = Color(0xFF34C759)
    val ToggleAccentDark = Color(0xFF30D158)

    // Type scale (reference: HomeContent / DialogContent / BottomTabsContent / ButtonsContent).
    val Display: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
    val DialogTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium)
    val BodyLarge: TextStyle = TextStyle(fontSize = 17.sp)
    val Body: TextStyle = TextStyle(fontSize = 16.sp)
    val Label: TextStyle = TextStyle(fontSize = 15.sp)
    val LabelMedium: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val Caption: TextStyle = TextStyle(fontSize = 12.sp)

    // Geometry (reference: LiquidBottomTabs / LiquidButton / DialogContent).
    val BarSideMargin: Dp = 36.dp
    val BarOuterHeight: Dp = 64.dp
    val BarInnerHeight: Dp = 56.dp
    val BarInnerPadding: Dp = 4.dp
    val TabIconSize: Dp = 28.dp
    val TabIconLabelGap: Dp = 2.dp
    val ButtonHeight: Dp = 48.dp
    val ButtonHorizontalPadding: Dp = 16.dp
    val ButtonContentGap: Dp = 8.dp
    val DialogMargin: Dp = 40.dp
    val DialogCorner: Dp = 48.dp
}

@Composable
internal fun liquidAccent(): Color =
    if (isSystemInDarkTheme()) LiquidStyle.AccentDark else LiquidStyle.AccentLight

@Composable
internal fun liquidContainer(): Color =
    if (isSystemInDarkTheme()) LiquidStyle.ContainerDark else LiquidStyle.ContainerLight

@Composable
internal fun isLiquidLightTheme(): Boolean = !isSystemInDarkTheme()
