package com.monday8am.edgelab.explorer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.monday8am.edgelab.explorer.R

val bodyFontFamily =
    FontFamily(
        Font(resId = R.font.jetbrains_mono, weight = FontWeight.Normal),
        Font(resId = R.font.jetbrains_mono, weight = FontWeight.Medium),
        Font(resId = R.font.jetbrains_mono, weight = FontWeight.Bold),
    )

val displayFontFamily =
    FontFamily(
        Font(resId = R.font.crimson_pro, weight = FontWeight.Normal),
        Font(resId = R.font.crimson_pro, weight = FontWeight.Medium),
        Font(resId = R.font.crimson_pro, weight = FontWeight.Bold),
    )

val baseline = Typography()

val AppTypography =
    Typography(
        displayLarge =
            baseline.displayLarge.copy(
                fontFamily = displayFontFamily,
                fontSize = 64.sp,
                lineHeight = 72.sp,
            ),
        displayMedium =
            baseline.displayMedium.copy(
                fontFamily = displayFontFamily,
                fontSize = 52.sp,
                lineHeight = 60.sp,
            ),
        displaySmall =
            baseline.displaySmall.copy(
                fontFamily = displayFontFamily,
                fontSize = 44.sp,
                lineHeight = 52.sp,
            ),
        headlineLarge =
            baseline.headlineLarge.copy(
                fontFamily = displayFontFamily,
                fontSize = 40.sp,
                lineHeight = 48.sp,
            ),
        headlineMedium =
            baseline.headlineMedium.copy(
                fontFamily = displayFontFamily,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineSmall =
            baseline.headlineSmall.copy(
                fontFamily = displayFontFamily,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily),
        titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily),
        titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
        labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
        labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
        labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
    )
