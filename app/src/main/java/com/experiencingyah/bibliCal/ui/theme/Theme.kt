package com.experiencingyah.bibliCal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class BibliCalExtendedColors(
    val outlineSoft: Color,
    val success: Color,
    val surfaceElevated: Color,
    val sabbath: Color,
)

val LocalBibliCalColors = staticCompositionLocalOf {
    BibliCalExtendedColors(
        outlineSoft = BibliCalColors.OutlineSoftLight,
        success = BibliCalColors.SuccessLight,
        surfaceElevated = BibliCalColors.SurfaceElevatedLight,
        sabbath = BibliCalColors.SabbathLight,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = BibliCalColors.AccentOnDark,
    onPrimary = BibliCalColors.SurfaceDark,
    primaryContainer = BibliCalColors.AccentContainerDark,
    onPrimaryContainer = BibliCalColors.AccentOnContainerDark,
    secondary = BibliCalColors.AccentOnDark,
    onSecondary = BibliCalColors.SurfaceDark,
    secondaryContainer = BibliCalColors.AccentContainerDark,
    onSecondaryContainer = BibliCalColors.AccentOnContainerDark,
    background = BibliCalColors.SurfaceDark,
    onBackground = BibliCalColors.OnSurfaceDark,
    surface = BibliCalColors.SurfaceDark,
    onSurface = BibliCalColors.OnSurfaceDark,
    surfaceVariant = BibliCalColors.SurfaceVariantDark,
    onSurfaceVariant = BibliCalColors.OnSurfaceVariantDark,
    surfaceBright = BibliCalColors.SurfaceHighestDark,
    surfaceDim = BibliCalColors.SurfaceLowestDark,
    surfaceContainerLowest = BibliCalColors.SurfaceLowestDark,
    surfaceContainerLow = BibliCalColors.SurfaceDark,
    surfaceContainer = BibliCalColors.SurfaceElevatedDark,
    surfaceContainerHigh = BibliCalColors.SurfaceHighDark,
    surfaceContainerHighest = BibliCalColors.SurfaceHighestDark,
    inverseSurface = BibliCalColors.SurfaceElevatedLight,
    inverseOnSurface = BibliCalColors.OnSurfaceLight,
    outline = BibliCalColors.OutlineSoftDark,
    outlineVariant = BibliCalColors.OutlineSoftDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BibliCalColors.Accent,
    onPrimary = Color.White,
    primaryContainer = BibliCalColors.AccentContainerLight,
    onPrimaryContainer = BibliCalColors.AccentOnContainerLight,
    secondary = BibliCalColors.AccentDark,
    onSecondary = Color.White,
    secondaryContainer = BibliCalColors.AccentContainerLight,
    onSecondaryContainer = BibliCalColors.AccentOnContainerLight,
    background = BibliCalColors.SurfaceLight,
    onBackground = BibliCalColors.OnSurfaceLight,
    surface = BibliCalColors.SurfaceLight,
    onSurface = BibliCalColors.OnSurfaceLight,
    surfaceVariant = BibliCalColors.SurfaceVariantLight,
    onSurfaceVariant = BibliCalColors.OnSurfaceVariantLight,
    surfaceBright = BibliCalColors.SurfaceElevatedLight,
    surfaceDim = BibliCalColors.SurfaceVariantLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = BibliCalColors.SurfaceElevatedLight,
    surfaceContainer = BibliCalColors.SurfaceElevatedLight,
    surfaceContainerHigh = BibliCalColors.SurfaceVariantLight,
    surfaceContainerHighest = BibliCalColors.SurfaceVariantLight,
    inverseSurface = BibliCalColors.SurfaceDark,
    inverseOnSurface = BibliCalColors.OnSurfaceDark,
    outline = BibliCalColors.OutlineSoftLight,
    outlineVariant = BibliCalColors.OutlineSoftLight,
)

@Composable
fun BibliCalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) {
        BibliCalExtendedColors(
            outlineSoft = BibliCalColors.OutlineSoftDark,
            success = BibliCalColors.SuccessDark,
            surfaceElevated = BibliCalColors.SurfaceElevatedDark,
            sabbath = BibliCalColors.SabbathDark,
        )
    } else {
        BibliCalExtendedColors(
            outlineSoft = BibliCalColors.OutlineSoftLight,
            success = BibliCalColors.SuccessLight,
            surfaceElevated = BibliCalColors.SurfaceElevatedLight,
            sabbath = BibliCalColors.SabbathLight,
        )
    }
    CompositionLocalProvider(LocalBibliCalColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

object BibliCalThemeTokens {
    val colors: BibliCalExtendedColors
        @Composable
        get() = LocalBibliCalColors.current
}
