package dev.groig.routing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CF),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFFB4492C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3B0900),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA0),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF7FBF4),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FBF4),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5EE),
    surfaceContainer = Color(0xFFEBEFE9),
    surfaceContainerHigh = Color(0xFFE5EAE3),
    surfaceContainerHighest = Color(0xFFE0E4DD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD6B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFA6F2CF),
    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF5E1603),
    secondaryContainer = Color(0xFF8F3317),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFF0C048),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA0),
    background = Color(0xFF101512),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B28),
    surfaceContainerHighest = Color(0xFF313632),
)

@Composable
fun RoutingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
