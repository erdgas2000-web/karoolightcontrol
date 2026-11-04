package io.lightprofiles.karoo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Built for a small screen read in daylight, at arm's length, often with
 * gloves on: black background, white text, one strong accent, and nothing in
 * the mid-greys that Material's defaults use for secondary text.
 */
private val Scheme = darkColorScheme(
    primary = Color(0xFFFFC400),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4A3800),
    onPrimaryContainer = Color(0xFFFFE082),
    secondary = Color(0xFFFFC400),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1C),
    // Material's default for this is a mid grey that disappears outdoors.
    onSurfaceVariant = Color(0xFFCFCFCF),
    outline = Color(0xFF6E6E6E),
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF000000),
)

private val Type = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LightProfilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Scheme.background),
            color = Scheme.background,
            content = content,
        )
    }
}
