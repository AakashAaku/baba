package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BabaLightColorScheme = lightColorScheme(
    primary = TrustNavy,
    onPrimary = PureWhite,
    primaryContainer = ConnectionMintSoft,
    onPrimaryContainer = TrustNavyDark,
    secondary = ConnectionMintDeep,
    onSecondary = PureWhite,
    secondaryContainer = WarmGoldSoft,
    onSecondaryContainer = WarmGoldDeep,
    tertiary = WarmGold,
    onTertiary = TrustNavyDark,
    background = SoftWhite,
    onBackground = SlateInk,
    surface = PureWhite,
    onSurface = SlateInk,
    surfaceVariant = MistSurface,
    onSurfaceVariant = MutedSlate,
    outline = OutlineSubtle,
    error = AlertCoral,
    onError = PureWhite,
    errorContainer = AlertCoralSoft,
    onErrorContainer = AlertCoral
)

private val ColorErrorDark = androidx.compose.ui.graphics.Color(0xFFF28B82)

private val BabaDarkColorScheme = darkColorScheme(
    primary = ConnectionMint,
    onPrimary = TrustNavyDark,
    primaryContainer = TrustNavyLight,
    onPrimaryContainer = ConnectionMintSoft,
    secondary = WarmGold,
    onSecondary = TrustNavyDark,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = WarmGoldSoft,
    tertiary = ConnectionMint,
    onTertiary = TrustNavyDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextMuted,
    outline = TrustNavyLight,
    error = ColorErrorDark,
    onError = TrustNavyDark
)

val BabaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BabaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BabaDarkColorScheme else BabaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BabaTypography,
        shapes = BabaShapes,
        content = content
    )
}
