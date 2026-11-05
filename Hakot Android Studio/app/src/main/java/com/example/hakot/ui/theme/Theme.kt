package com.example.hakot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ProfessionalBlueColorScheme = darkColorScheme(
    primary = CivicBluePrimary,
    onPrimary = Color.White,
    primaryContainer = CivicBlueContainer,
    onPrimaryContainer = CivicBlueLight,
    secondary = EcoGreenPrimary,
    onSecondary = Color.Black,
    secondaryContainer = EcoGreenContainer,
    onSecondaryContainer = EcoGreenLight,
    tertiary = RecycleYellow,
    onTertiary = Color.Black,
    tertiaryContainer = RecycleYellowContainer,
    onTertiaryContainer = RecycleYellowLight,
    error = HazardousRed,
    onError = Color.White,
    errorContainer = HazardousRedContainer,
    onErrorContainer = HazardousRedLight,
    background = DarkBackground,
    onBackground = TextOnDarkPrimary,
    surface = DarkSurface,
    onSurface = TextOnDarkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextOnDarkSecondary,
    outline = DarkBorder
)

@Composable
fun HakotTheme(
    darkTheme: Boolean = true, // Professional True Black & Deep Blue Civic Theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProfessionalBlueColorScheme,
        typography = Typography,
        content = content
    )
}
