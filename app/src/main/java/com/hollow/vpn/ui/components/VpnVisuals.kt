package com.hollow.vpn.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hollow.vpn.ui.theme.AuraCoreGreen
import com.hollow.vpn.ui.theme.AuraInnerEmerald
import com.hollow.vpn.ui.theme.AuraMidTeal
import com.hollow.vpn.ui.theme.AuraOuterMint
import com.hollow.vpn.ui.theme.ForestEmerald
import com.hollow.vpn.ui.theme.PureWhiteCard
import com.hollow.vpn.ui.theme.SageBackgroundBottom
import com.hollow.vpn.ui.theme.SageBackgroundTop
import com.hollow.vpn.ui.theme.SageBlobLight
import com.hollow.vpn.ui.theme.SageBlobMedium
import com.hollow.vpn.ui.theme.SignalInactiveBar
import com.hollow.vpn.ui.theme.SignalLowPingGreen
import com.hollow.vpn.ui.theme.SignalMedPingAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-bleed atmospheric sage/mint background with organic curved pastel shapes
 * matching the exact aesthetic in the 5-screen reference screenshot.
 */
@Composable
fun SageOrganicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SageBackgroundTop, SageBackgroundBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-right soft organic sage wave
            drawCircle(
                color = SageBlobLight.copy(alpha = 0.55f),
                radius = w * 0.48f,
                center = Offset(w * 0.92f, h * 0.12f)
            )
            drawCircle(
                color = SageBlobMedium.copy(alpha = 0.32f),
                radius = w * 0.36f,
                center = Offset(w * 0.10f, h * 0.36f)
            )
            // Bottom-left & bottom-right soft mint organic hills
            drawCircle(
                color = SageBlobMedium.copy(alpha = 0.45f),
                radius = w * 0.55f,
                center = Offset(w * 0.22f, h * 0.94f)
            )
            drawCircle(
                color = SageBlobLight.copy(alpha = 0.50f),
                radius = w * 0.45f,
                center = Offset(w * 0.88f, h * 0.86f)
            )
        }
        content()
    }
}

/**
 * Custom 3D-styled Green Paper Plane Squircle Logo from Screen 1 ("Hollow VPN").
 */
@Composable
fun HollowVpnLogoCard(
    modifier: Modifier = Modifier,
    cardSize: Dp = 88.dp
) {
    Surface(
        modifier = modifier
            .size(cardSize)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = ForestEmerald.copy(alpha = 0.18f),
                spotColor = ForestEmerald.copy(alpha = 0.22f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = PureWhiteCard
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            val w = size.width
            val h = size.height

            // Left/Upper Wing (neutral gradient)
            val upperWing = Path().apply {
                moveTo(w * 0.08f, h * 0.48f)
                lineTo(w * 0.90f, h * 0.14f)
                lineTo(w * 0.50f, h * 0.56f)
                close()
            }
            drawPath(
                path = upperWing,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFDADCE0), Color(0xFF80868B)),
                    start = Offset(0f, h * 0.4f),
                    end = Offset(w * 0.9f, h * 0.14f)
                )
            )

            // Center Fold (deep neutral shadow)
            val centerFold = Path().apply {
                moveTo(w * 0.50f, h * 0.56f)
                lineTo(w * 0.90f, h * 0.14f)
                lineTo(w * 0.42f, h * 0.78f)
                close()
            }
            drawPath(
                path = centerFold,
                color = Color(0xFF303134)
            )

            // Right/Lower Wing (neutral gradient)
            val lowerWing = Path().apply {
                moveTo(w * 0.50f, h * 0.56f)
                lineTo(w * 0.90f, h * 0.14f)
                lineTo(w * 0.68f, h * 0.88f)
                close()
            }
            drawPath(
                path = lowerWing,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF9AA0A6), Color(0xFF3C4043)),
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.85f, h * 0.88f)
                )
            )
        }
    }
}

/**
 * Multi-layered translucent emerald/teal organic petal wave visualizer with central white
 * circular power button from Screen 3 ("Connected").
 */
@Composable
fun OrganicEmeraldPowerOrb(
    isConnected: Boolean,
    isConnecting: Boolean,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_wave_transition")
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slow_rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier.size(226.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseRadius = size.minDimension * 0.42f * (if (isConnected) pulseScale else 0.95f)

            val alphaMultiplier = if (isConnected) 1f else if (isConnecting) 0.75f else 0.42f

            // Layer 1: Outer soft mint organic wave petals
            rotate(degrees = slowRotation * 0.5f, pivot = Offset(cx, cy)) {
                drawOrganicWaveBlob(
                    center = Offset(cx, cy - 6f),
                    baseRadius = baseRadius * 1.08f,
                    lobeCount = 5,
                    wobble = 0.13f,
                    color = AuraOuterMint.copy(alpha = 0.38f * alphaMultiplier)
                )
            }

            // Layer 2: Mid-outer teal wave
            rotate(degrees = -slowRotation * 0.7f + 35f, pivot = Offset(cx, cy)) {
                drawOrganicWaveBlob(
                    center = Offset(cx + 4f, cy + 4f),
                    baseRadius = baseRadius * 0.96f,
                    lobeCount = 6,
                    wobble = 0.11f,
                    color = AuraMidTeal.copy(alpha = 0.42f * alphaMultiplier)
                )
            }

            // Layer 3: Mid-inner emerald wave
            rotate(degrees = slowRotation + 72f, pivot = Offset(cx, cy)) {
                drawOrganicWaveBlob(
                    center = Offset(cx - 3f, cy + 3f),
                    baseRadius = baseRadius * 0.84f,
                    lobeCount = 5,
                    wobble = 0.10f,
                    color = AuraInnerEmerald.copy(alpha = 0.52f * alphaMultiplier)
                )
            }

            // Layer 4: Core deep green ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuraCoreGreen.copy(alpha = 0.78f * alphaMultiplier),
                        AuraInnerEmerald.copy(alpha = 0.25f * alphaMultiplier)
                    ),
                    center = Offset(cx, cy),
                    radius = baseRadius * 0.76f
                ),
                radius = baseRadius * 0.74f,
                center = Offset(cx, cy)
            )
        }

        // Center crisp white circular power button
        Surface(
            modifier = Modifier
                .size(86.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = ForestEmerald.copy(alpha = 0.25f),
                    spotColor = ForestEmerald.copy(alpha = 0.30f)
                )
                .clip(CircleShape)
                .clickable(onClick = onToggleClick)
                .testTag("hero_power_button"),
            shape = CircleShape,
            color = PureWhiteCard
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (isConnected) "Disconnect VPN" else "Connect VPN",
                    tint = if (isConnected) ForestEmerald else ForestEmerald.copy(alpha = 0.55f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawOrganicWaveBlob(
    center: Offset,
    baseRadius: Float,
    lobeCount: Int,
    wobble: Float,
    color: Color
) {
    val path = Path()
    val steps = 72
    for (i in 0..steps) {
        val angle = (i.toFloat() / steps) * (2.0 * Math.PI)
        val r = baseRadius * (1f + wobble * sin(lobeCount * angle).toFloat() + (wobble * 0.45f) * cos((lobeCount - 2) * angle).toFloat())
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    drawPath(path = path, color = color)
}

/**
 * 3-bar cellular/latency signal indicator matching Screen 2 ("42 ms", "96 ms", etc.).
 */
@Composable
fun SignalStrengthBars(
    pingMs: Int,
    modifier: Modifier = Modifier
) {
    val isLowPing = pingMs < 90
    val activeColor = if (isLowPing) SignalLowPingGreen else SignalMedPingAmber
    val activeBars = when {
        pingMs < 75 -> 3
        pingMs < 130 -> 2
        else -> 2
    }

    Row(
        modifier = modifier
            .height(14.dp)
            .width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(6.dp, 10.dp, 14.dp)
        heights.forEachIndexed { index, barHeight ->
            val barColor = if (index < activeBars) activeColor else SignalInactiveBar
            Box(
                modifier = Modifier
                    .width(3.2.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
        }
    }
}

/**
 * Crisp circular country flag icon drawn with vector Canvas so every country in Screen 2 & 3
 * renders as a true circular flag badge (DE, SG, US, NL, GB, CA, FR, JP, IN, AU, CH, KR, SE, BR, FI).
 */
@Composable
fun CircularCountryFlag(
    countryCode: String,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        val w = this.size.width
        val h = this.size.height
        when (countryCode.uppercase()) {
            "DE" -> {
                // Germany: Black, Red, Gold horizontal stripes
                drawRect(color = Color(0xFF1A1A1A), topLeft = Offset(0f, 0f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFFD92B2B), topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFFF4C430), topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
            }
            "SG" -> {
                // Singapore: Red top, White bottom + crescent hint
                drawRect(color = Color(0xFFE52535), topLeft = Offset(0f, 0f), size = Size(w, h / 2f))
                drawRect(color = Color(0xFFFFFFFF), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
                drawCircle(color = Color.White, radius = w * 0.14f, center = Offset(w * 0.34f, h * 0.26f))
                drawCircle(color = Color(0xFFE52535), radius = w * 0.11f, center = Offset(w * 0.39f, h * 0.26f))
            }
            "US" -> {
                // United States: Red/White stripes + Blue canton
                val stripeCount = 7
                val stripeH = h / stripeCount
                for (i in 0 until stripeCount) {
                    drawRect(
                        color = if (i % 2 == 0) Color(0xFFD12B38) else Color.White,
                        topLeft = Offset(0f, i * stripeH),
                        size = Size(w, stripeH)
                    )
                }
                drawRect(color = Color(0xFF1E3A8A), topLeft = Offset(0f, 0f), size = Size(w * 0.52f, h * 0.54f))
            }
            "NL" -> {
                // Netherlands: Red, White, Blue horizontal
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(0f, 0f), size = Size(w, h / 3f))
                drawRect(color = Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFF1D4ED8), topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
            }
            "GB" -> {
                // United Kingdom: Navy background + White & Red crosses
                drawRect(color = Color(0xFF0A2A66), size = Size(w, h))
                drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.22f)
                drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.22f)
                drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.10f)
                drawLine(Color(0xFFC8102E), Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.10f)
                drawRect(Color.White, topLeft = Offset(0f, h * 0.36f), size = Size(w, h * 0.28f))
                drawRect(Color.White, topLeft = Offset(w * 0.36f, 0f), size = Size(w * 0.28f, h))
                drawRect(Color(0xFFC8102E), topLeft = Offset(0f, h * 0.42f), size = Size(w, h * 0.16f))
                drawRect(Color(0xFFC8102E), topLeft = Offset(w * 0.42f, 0f), size = Size(w * 0.16f, h))
            }
            "CA" -> {
                // Canada: Red sides, White center with red maple emblem
                drawRect(color = Color(0xFFD81E28), topLeft = Offset(0f, 0f), size = Size(w * 0.26f, h))
                drawRect(color = Color.White, topLeft = Offset(w * 0.26f, 0f), size = Size(w * 0.48f, h))
                drawRect(color = Color(0xFFD81E28), topLeft = Offset(w * 0.74f, 0f), size = Size(w * 0.26f, h))
                drawCircle(color = Color(0xFFD81E28), radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
            }
            "FR" -> {
                // France: Blue, White, Red vertical
                drawRect(color = Color(0xFF1D428A), topLeft = Offset(0f, 0f), size = Size(w / 3f, h))
                drawRect(color = Color.White, topLeft = Offset(w / 3f, 0f), size = Size(w / 3f, h))
                drawRect(color = Color(0xFFE02B38), topLeft = Offset(2f * w / 3f, 0f), size = Size(w / 3f, h))
            }
            "JP" -> {
                // Japan: White background with crimson sun disc
                drawRect(color = Color.White, size = Size(w, h))
                drawCircle(color = Color(0xFFBC002D), radius = w * 0.28f, center = Offset(w / 2f, h / 2f))
            }
            "IN" -> {
                // India: Saffron, White, Green + Navy wheel
                drawRect(color = Color(0xFFFF9933), topLeft = Offset(0f, 0f), size = Size(w, h / 3f))
                drawRect(color = Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFF138808), topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
                drawCircle(color = Color(0xFF000080), radius = w * 0.11f, center = Offset(w / 2f, h / 2f))
            }
            "AU" -> {
                // Australia: Royal blue with Southern Cross stars & Union Jack corner
                drawRect(color = Color(0xFF0A2A66), size = Size(w, h))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(0f, h * 0.18f), size = Size(w * 0.48f, h * 0.12f))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(w * 0.18f, 0f), size = Size(w * 0.12f, h * 0.48f))
                drawCircle(color = Color.White, radius = w * 0.06f, center = Offset(w * 0.25f, h * 0.72f))
                drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.72f, h * 0.32f))
                drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.72f, h * 0.68f))
                drawCircle(color = Color.White, radius = w * 0.05f, center = Offset(w * 0.58f, h * 0.50f))
            }
            "CH" -> {
                // Switzerland
                drawRect(color = Color(0xFFD52B1E), size = Size(w, h))
                drawRect(color = Color.White, topLeft = Offset(w * 0.24f, h * 0.41f), size = Size(w * 0.52f, h * 0.18f))
                drawRect(color = Color.White, topLeft = Offset(w * 0.41f, h * 0.24f), size = Size(w * 0.18f, h * 0.52f))
            }
            "SE" -> {
                // Sweden
                drawRect(color = Color(0xFF006AA7), size = Size(w, h))
                drawRect(color = Color(0xFFFECC02), topLeft = Offset(0f, h * 0.40f), size = Size(w, h * 0.20f))
                drawRect(color = Color(0xFFFECC02), topLeft = Offset(w * 0.32f, 0f), size = Size(w * 0.18f, h))
            }
            "BR" -> {
                // Brazil
                drawRect(color = Color(0xFF009C3B), size = Size(w, h))
                drawCircle(color = Color(0xFFFFDF00), radius = w * 0.30f, center = Offset(w / 2f, h / 2f))
                drawCircle(color = Color(0xFF002776), radius = w * 0.18f, center = Offset(w / 2f, h / 2f))
            }
            "FI" -> {
                // Finland: White with Blue Nordic cross
                drawRect(color = Color.White, size = Size(w, h))
                drawRect(color = Color(0xFF003580), topLeft = Offset(0f, h * 0.38f), size = Size(w, h * 0.24f))
                drawRect(color = Color(0xFF003580), topLeft = Offset(w * 0.30f, 0f), size = Size(w * 0.22f, h))
            }
            "RU" -> {
                // Russia: White, Blue, Red horizontal
                drawRect(color = Color.White, topLeft = Offset(0f, 0f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFF0039A6), topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFFD52B1E), topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
            }
            "PL" -> {
                // Poland: White top, Red bottom
                drawRect(color = Color.White, topLeft = Offset(0f, 0f), size = Size(w, h / 2f))
                drawRect(color = Color(0xFFDC143C), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
            }
            "AT" -> {
                // Austria: Red, White, Red horizontal
                drawRect(color = Color(0xFFED2939), topLeft = Offset(0f, 0f), size = Size(w, h / 3f))
                drawRect(color = Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                drawRect(color = Color(0xFFED2939), topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
            }
            "IT" -> {
                // Italy: Green, White, Red vertical
                drawRect(color = Color(0xFF009246), topLeft = Offset(0f, 0f), size = Size(w / 3f, h))
                drawRect(color = Color.White, topLeft = Offset(w / 3f, 0f), size = Size(w / 3f, h))
                drawRect(color = Color(0xFFCE2B37), topLeft = Offset(2f * w / 3f, 0f), size = Size(w / 3f, h))
            }
            "TR", "HK" -> {
                // Turkey / Hong Kong: Crimson Red with White center emblem
                drawRect(color = Color(0xFFE30A17), size = Size(w, h))
                drawCircle(color = Color.White, radius = w * 0.22f, center = Offset(w * 0.46f, h * 0.5f))
                drawCircle(color = Color(0xFFE30A17), radius = w * 0.17f, center = Offset(w * 0.52f, h * 0.5f))
            }
            "UA" -> {
                // Ukraine: Blue top, Yellow bottom
                drawRect(color = Color(0xFF0057B7), topLeft = Offset(0f, 0f), size = Size(w, h / 2f))
                drawRect(color = Color(0xFFFFD700), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
            }
            "KR" -> {
                // South Korea: White with Red/Blue Taegeuk circle
                drawRect(color = Color.White, size = Size(w, h))
                drawCircle(color = Color(0xFFCD2E3A), radius = w * 0.26f, center = Offset(w / 2f, h / 2f))
                drawArc(
                    color = Color(0xFF0047A0),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.24f, h * 0.24f),
                    size = Size(w * 0.52f, h * 0.52f)
                )
            }
            else -> {
                // Clean European / Global tricolor fallback
                drawRect(color = Color(0xFF1E3A8A), topLeft = Offset(0f, 0f), size = Size(w, h / 3f))
                drawRect(color = Color.White, topLeft = Offset(0f, h / 3f), size = Size(w, h / 3f))
                drawRect(color = ForestEmerald, topLeft = Offset(0f, 2f * h / 3f), size = Size(w, h / 3f))
            }
        }
        // Subtle inner rim for light flags like Japan/Singapore
        drawCircle(
            color = Color.Black.copy(alpha = 0.08f),
            radius = (w / 2f) - 0.5f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = 1f)
        )
    }
}

/**
 * Smooth rotating circular progress indicator matching Screen 1 ("Fetching servers...").
 */
@Composable
fun FetchingSpinnerRing(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val sweepRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 3.6.dp.toPx()
        // Track circle
        drawCircle(
            color = Color(0xFFD5E2DC),
            style = Stroke(width = strokeWidth)
        )
        // Rotating dark emerald arc
        rotate(degrees = sweepRotation) {
            drawArc(
                color = ForestEmerald,
                startAngle = -90f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
