package com.ifwqu.domainmail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable

/**
 * 设计令牌直接对齐 domainmail/public/style.css 的 :root。
 * 包括亮/暗两套，以及 README 里标注的手机端实测尺寸。
 */
object DmUi {
    val Blue = Color(0xFF2563EB)
    val BlueDark = Color(0xFF1D4ED8)
    val BlueSoft = Color(0xFFEDF4FF)
    val Star = Color(0xFFF0A92B)
    val Danger = Color(0xFFE5484D)
    val Ok = Color(0xFF22C34D)

    private val AvatarColors = listOf(
        Color(0xFF6B92E0), Color(0xFF7C6BD6), Color(0xFF3F9E8C),
        Color(0xFFD07A53), Color(0xFFC75B86), Color(0xFF4E8FCB),
        Color(0xFF8A7B4F), Color(0xFF5B7FB5)
    )

    fun avatarColor(key: String): Color =
        AvatarColors[abs(key.hashCode() % AvatarColors.size)]
}

data class DmPalette(
    val appBg: Color,
    val cardBg: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val muted2: Color,
    val line: Color,
    val lineLight: Color,
    val blue: Color,
    val blueSoft: Color,
    val onBlue: Color,
    val avatarFg: Color,
    val star: Color,
    val danger: Color
)

private val LightPalette = DmPalette(
    appBg = Color(0xFFF4F6F9),
    cardBg = Color(0xFFFFFFFF),
    ink = Color(0xFF202938),
    ink2 = Color(0xFF344054),
    muted = Color(0xFF71717A),
    muted2 = Color(0xFF7A8494),
    line = Color(0xFFE4E9F0),
    lineLight = Color(0xFFEDF0F4),
    blue = DmUi.Blue,
    blueSoft = DmUi.BlueSoft,
    onBlue = Color.White,
    avatarFg = Color(0xFF6B92E0),
    star = DmUi.Star,
    danger = DmUi.Danger
)

private val DarkPalette = DmPalette(
    appBg = Color(0xFF0F1117),
    cardBg = Color(0xFF161922),
    ink = Color(0xFFE7E9EE),
    ink2 = Color(0xFFC8CCD6),
    muted = Color(0xFF8B93A7),
    muted2 = Color(0xFF7D8598),
    line = Color(0xFF2A3040),
    lineLight = Color(0xFF232734),
    blue = Color(0xFF5B8CFF),
    blueSoft = Color(0xFF1C2740),
    onBlue = Color(0xFF0F1117),
    avatarFg = Color(0xFF9FB8F5),
    star = Color(0xFFF0A92B),
    danger = Color(0xFFE5484D)
)

val LocalDmPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun dm(): DmPalette = LocalDmPalette.current

@Composable
fun DmTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF5B8CFF),
            onPrimary = Color(0xFF0F1117),
            background = palette.appBg,
            surface = palette.cardBg,
            onBackground = palette.ink,
            onSurface = palette.ink,
            outline = palette.line
        )
    } else {
        lightColorScheme(
            primary = DmUi.Blue,
            onPrimary = Color.White,
            background = palette.appBg,
            surface = palette.cardBg,
            onBackground = palette.ink,
            onSurface = palette.ink,
            outline = palette.line
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDmPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun initialOf(label: String, address: String): String {
    val source = label.trim().ifBlank { address.trim() }
    val first = source.firstOrNull()?.uppercaseChar() ?: '?'
    return first.toString()
}

@Composable
fun DmAvatar(label: String, address: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(DmUi.avatarColor(address.ifBlank { label }), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialOf(label, address),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

/** 14px 圆角 + 1px 边框 + 微阴影，对应 Webmail 的主卡片。 */
@Composable
fun DmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = dm()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = palette.cardBg,
        contentColor = palette.ink,
        border = BorderStroke(1.dp, palette.line),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
            content()
        }
    }
}

@Composable
fun DmBadge(count: Int) {
    if (count <= 0) return
    val palette = dm()
    Box(
        modifier = Modifier
            .background(palette.blue, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = palette.onBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DmSectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = dm().muted,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}
