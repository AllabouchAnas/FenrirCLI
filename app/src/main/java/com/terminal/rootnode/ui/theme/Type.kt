package com.terminal.rootnode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.terminal.rootnode.R

val JetBrainsMono: FontFamily = FontFamily(
    Font(resId = R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
    Font(resId = R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
    Font(resId = R.font.jetbrains_mono_bold, weight = FontWeight.Bold)
)

val TerminalFont: FontFamily = JetBrainsMono

// Pre-defined static text styles to optimize recomposition performance
val TerminalNormalTextStyle = TextStyle(
    fontFamily = TerminalFont,
    fontSize = 13.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.2.sp
)

val TerminalBrailleTextStyle = TextStyle(
    fontFamily = TerminalFont,
    fontSize = 6.5.sp,
    lineHeight = 7.sp,
    letterSpacing = 0.sp
)

val TerminalInfoTextStyle = TextStyle(
    fontFamily = TerminalFont,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    titleLarge = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TerminalFont,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.3.sp
    )
)