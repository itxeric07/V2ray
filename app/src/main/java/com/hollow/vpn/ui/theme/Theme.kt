package com.hollow.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HollowLightColorScheme = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = Color.White,
    primaryContainer = SelectedMintCard,
    onPrimaryContainer = ForestEmeraldDark,
    secondary = EmeraldSwitchActive,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAED),
    onSecondaryContainer = ForestEmerald,
    tertiary = AuraMidTeal,
    background = SageBackgroundTop,
    onBackground = TextPrimaryDark,
    surface = PureWhiteCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = InputFieldBg,
    onSurfaceVariant = TextSecondaryMuted,
    outline = SubtleCardBorder,
    error = DisconnectCrimson,
    errorContainer = DisconnectPillBg,
    onErrorContainer = DisconnectCrimson
)

private val HollowDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8EAED),
    onPrimary = Color(0xFF202124),
    primaryContainer = Color(0xFF3C4043),
    onPrimaryContainer = Color(0xFFF1F3F4),
    secondary = Color(0xFFBDC1C6),
    onSecondary = Color(0xFF202124),
    secondaryContainer = Color(0xFF3C4043),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFF9AA0A6),
    background = Color(0xFF111214),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF202124),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF303134),
    onSurfaceVariant = Color(0xFFBDC1C6),
    outline = Color(0xFF5F6368),
    error = Color(0xFFF1F3F4),
    errorContainer = Color(0xFF3C4043),
    onErrorContainer = Color(0xFFF1F3F4)
)

@Composable
fun HollowVpnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) HollowDarkColorScheme else HollowLightColorScheme,
        typography = Typography,
        content = content
    )
}
