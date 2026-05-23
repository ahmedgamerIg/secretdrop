package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonPink,
    secondary = ElectricPurple,
    tertiary = CyberCyan,
    background = CosmicBackground,
    surface = CosmicSurface,
    surfaceVariant = CosmicSurfaceVariant,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = CosmicBackground,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextGray,
    outline = CosmicBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force gorgeous immersive dark cosmic mode
  dynamicColor: Boolean = false, // Disable dynamic content tinting to preserve intentional artist gradients
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
