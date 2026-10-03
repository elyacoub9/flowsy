package com.elyacoub9.flowsy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val Typography = Typography(
    // Big money numbers.
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 60.sp),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures so amounts line up. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
