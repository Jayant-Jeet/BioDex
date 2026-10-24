package com.biodex.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biodex.R

object BioDexTheme {
    object Color {
        val Ink = androidx.compose.ui.graphics.Color(0xFF132D21)
        val Page = androidx.compose.ui.graphics.Color(0xFFF5F7EF)
        val Outer = androidx.compose.ui.graphics.Color(0xFFCBD8C6)
        val Forest = androidx.compose.ui.graphics.Color(0xFF173F2B)
        val ForestDeep = androidx.compose.ui.graphics.Color(0xFF0D2D1D)
        val TextMuted = androidx.compose.ui.graphics.Color(0xFF708075)
        val Border = androidx.compose.ui.graphics.Color(0xFFE0E6DC)
        val Amber = androidx.compose.ui.graphics.Color(0xFFE39438)
        val Gold = androidx.compose.ui.graphics.Color(0xFFF6CF4A)
        val SpecimenBackground = androidx.compose.ui.graphics.Color(0xFF201044)
        val SpecimenTop = androidx.compose.ui.graphics.Color(0xFF140C32)
        val SpecimenMiddle = androidx.compose.ui.graphics.Color(0xFF342064)
        val SpecimenPanel = androidx.compose.ui.graphics.Color(0xFF23114C)
        val SpecimenPanelHighlight = androidx.compose.ui.graphics.Color(0xFF3B1B70)
        val Common = androidx.compose.ui.graphics.Color(0xFFE0D7ED)
        val Uncommon = androidx.compose.ui.graphics.Color(0xFFA5F0C4)
        val Rare = androidx.compose.ui.graphics.Color(0xFFFFE491)
    }

    object Size {
        val Space2 = 2.dp
        val Space4 = 4.dp
        val Space6 = 6.dp
        val Space8 = 8.dp
        val Space10 = 10.dp
        val Space12 = 12.dp
        val Space14 = 14.dp
        val Space16 = 16.dp
        val Space18 = 18.dp
        val Space20 = 20.dp
        val Space24 = 24.dp
        val Space28 = 28.dp
        val Space32 = 32.dp
        val DashboardMaxWidth = 560.dp
        val PageHorizontalPadding = 16.dp
        val HeaderHorizontalPadding = 20.dp
        val CardGridGap = 11.dp
        val CardCornerRadius = 20.dp
        val CardImageHeight = 146.dp
        val SmallCornerRadius = 12.dp
        val MediumCornerRadius = 17.dp
        val LargeCornerRadius = 26.dp
        val SpecimenCornerRadius = 28.dp
        val BottomActionInset = 14.dp
    }

    object Typography {
        @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
        val Sans = FontFamily(
            listOf(400, 500, 600, 700).map { weight ->
                Font(
                    R.font.dm_sans,
                    FontWeight(weight),
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
                )
            },
        )
        val Serif = FontFamily(Font(R.font.dm_serif_regular))
        val SerifItalic = FontFamily(Font(R.font.dm_serif_italic, style = FontStyle.Italic))

        const val Caption = 8f
        const val Small = 10f
        const val Body = 12f
        const val BodyLarge = 14f
        const val CardTitle = 28f
        const val PageTitle = 42f
    }

    object Stroke {
        val Hairline = 1.dp
        val Standard = 1.5.dp
        val Emphasis = 2.dp
    }

    object Elevation {
        val Card = 18.dp
    }

    object Motion {
        const val PressDurationMillis = 140
        const val StandardDurationMillis = 180
        const val ImageDurationMillis = 300
        const val SplashDurationMillis = 1100
        const val StandardEasing = "ease"
        const val SplashEasing = "ease-in-out"
    }
}

object BColor {
    val Page = BioDexTheme.Color.Page
    val Outer = BioDexTheme.Color.Outer
    val Ink = BioDexTheme.Color.Ink
    val Forest = BioDexTheme.Color.Forest
    val Orange = BioDexTheme.Color.Amber
    val Orange2 = Color(0xFFD47C2B)
    val Amber = Color(0xFFEAA552)
    val Muted = Color(0xFF89978D)
    val Muted2 = BioDexTheme.Color.TextMuted
    val Border = BioDexTheme.Color.Border
}
