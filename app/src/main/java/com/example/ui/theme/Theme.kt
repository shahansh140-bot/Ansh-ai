package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

private val AnshShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AnshAiTheme(
    themeMode: ThemeMode = ThemeMode.ELECTRIC_DARK,
    content: @Composable () -> Unit
) {
    val bgColor = when (themeMode) {
        ThemeMode.ELECTRIC_DARK -> ObsidianBg
        ThemeMode.OLED_BLACK -> OledBlackBg
        ThemeMode.MIDNIGHT_INDIGO -> MidnightIndigoBg
    }

    val surfaceColor = when (themeMode) {
        ThemeMode.ELECTRIC_DARK -> MidnightSurface
        ThemeMode.OLED_BLACK -> Color(0xFF0E1017)
        ThemeMode.MIDNIGHT_INDIGO -> Color(0xFF141B33)
    }

    val colorScheme = darkColorScheme(
        primary = ElectricBlue,
        onPrimary = TextPrimary,
        primaryContainer = ElectricBlueDark,
        onPrimaryContainer = TextPrimary,
        secondary = NebulaPurple,
        onSecondary = TextPrimary,
        secondaryContainer = Color(0xFF2B1C4D),
        onSecondaryContainer = TextPrimary,
        tertiary = ElectricCyan,
        onTertiary = ObsidianBg,
        background = bgColor,
        onBackground = TextPrimary,
        surface = surfaceColor,
        onSurface = TextPrimary,
        surfaceVariant = MidnightSurfaceElevated,
        onSurfaceVariant = TextSecondary,
        outline = MidnightBorder,
        outlineVariant = Color(0xFF1F2338),
        error = ErrorCoral,
        onError = TextPrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnshTypography,
        shapes = AnshShapes,
        content = content
    )
}
