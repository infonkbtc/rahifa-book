package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ReadingTheme

private val RahifaLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = BrassSecondary,
    onSecondary = Color.White,
    secondaryContainer = BrassContainer,
    onSecondaryContainer = OnBrassContainer,
    tertiary = SageTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = OnSageContainer,
    background = CreamBackground,
    onBackground = InkPrimary,
    surface = CreamSurface,
    onSurface = InkPrimary,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLow = Color(0xFFF6F1E8),
    surfaceContainer = CreamSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE9E0D1),
    outline = CreamOutline,
    outlineVariant = Color(0xFFE3D9C9),
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410E0B)
)

private val RahifaSepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = Color(0xFFFAF3E6),
    primaryContainer = SepiaPrimaryContainer,
    onPrimaryContainer = Color(0xFF3A120A),
    secondary = BrassSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9D2A8),
    onSecondaryContainer = OnBrassContainer,
    tertiary = SageTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2DEC7),
    onTertiaryContainer = OnSageContainer,
    background = SepiaBackground,
    onBackground = SepiaInkPrimary,
    surface = SepiaSurface,
    onSurface = SepiaInkPrimary,
    surfaceVariant = SepiaSurfaceVariant,
    onSurfaceVariant = SepiaInkSecondary,
    surfaceContainerLow = Color(0xFFEEE1C8),
    surfaceContainer = SepiaSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDFCCA9),
    outline = SepiaOutline,
    outlineVariant = Color(0xFFD8C5A6),
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410E0B)
)

private val RahifaDarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color(0xFF4D150A),
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = Color(0xFFFCE0D7),
    secondary = NightSecondary,
    onSecondary = Color(0xFF3D2800),
    secondaryContainer = NightSecondaryContainer,
    onSecondaryContainer = Color(0xFFFCE4B8),
    tertiary = Color(0xFFA0C9AE),
    onTertiary = Color(0xFF0E341F),
    tertiaryContainer = Color(0xFF284D37),
    onTertiaryContainer = Color(0xFFCBEAD5),
    background = NightBackground,
    onBackground = NightInkPrimary,
    surface = NightSurface,
    onSurface = NightInkPrimary,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightInkSecondary,
    surfaceContainerLow = Color(0xFF1F1B17),
    surfaceContainer = NightSurfaceVariant,
    surfaceContainerHigh = Color(0xFF322A24),
    outline = NightOutline,
    outlineVariant = Color(0xFF372F28),
    error = ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFF9DEDC)
)

val RahifaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RahifaBooksTheme(
    readingTheme: ReadingTheme = ReadingTheme.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (readingTheme) {
        ReadingTheme.LIGHT -> RahifaLightColorScheme
        ReadingTheme.DARK -> RahifaDarkColorScheme
        ReadingTheme.SEPIA -> RahifaSepiaColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val isLight = readingTheme != ReadingTheme.DARK
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RahifaTypography,
        shapes = RahifaShapes,
        content = content
    )
}
