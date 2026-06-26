package com.terminal.rootnode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.terminal.rootnode.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val JetBrainsMono: FontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("JetBrains Mono"),
        fontProvider = provider,
        weight = FontWeight.Normal
    ),
    Font(
        googleFont = GoogleFont("JetBrains Mono"),
        fontProvider = provider,
        weight = FontWeight.Medium
    ),
    Font(
        googleFont = GoogleFont("JetBrains Mono"),
        fontProvider = provider,
        weight = FontWeight.Bold
    )
)

// Fallback for previews / offline: system monospace
val TerminalFont: FontFamily = JetBrainsMono

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