package com.mattzilla30.mbooru.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

/** Share the complete BGSU palette with native controls, including the black night theme. */
@Suppress(
    "LongMethod",
    "FunctionNaming",
) // Bridge every Material role together so native and Compose colors stay aligned.
@Composable
fun MBooruTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val night = isSystemInDarkTheme()
    val colors =
        remember(context, configuration, night) {
            val base = if (night) darkColorScheme() else lightColorScheme()
            fun color(attr: Int, fallback: Color) =
                Color(MaterialColors.getColor(context, attr, fallback.toArgb()))
            base.copy(
                primary = color(MaterialR.attr.colorPrimary, base.primary),
                onPrimary = color(MaterialR.attr.colorOnPrimary, base.onPrimary),
                primaryContainer =
                    color(MaterialR.attr.colorPrimaryContainer, base.primaryContainer),
                onPrimaryContainer =
                    color(MaterialR.attr.colorOnPrimaryContainer, base.onPrimaryContainer),
                secondary = color(MaterialR.attr.colorSecondary, base.secondary),
                onSecondary = color(MaterialR.attr.colorOnSecondary, base.onSecondary),
                secondaryContainer =
                    color(MaterialR.attr.colorSecondaryContainer, base.secondaryContainer),
                onSecondaryContainer =
                    color(MaterialR.attr.colorOnSecondaryContainer, base.onSecondaryContainer),
                tertiary = color(MaterialR.attr.colorTertiary, base.tertiary),
                onTertiary = color(MaterialR.attr.colorOnTertiary, base.onTertiary),
                tertiaryContainer =
                    color(MaterialR.attr.colorTertiaryContainer, base.tertiaryContainer),
                onTertiaryContainer =
                    color(MaterialR.attr.colorOnTertiaryContainer, base.onTertiaryContainer),
                background = color(android.R.attr.colorBackground, base.background),
                onBackground = color(MaterialR.attr.colorOnBackground, base.onBackground),
                surface = color(MaterialR.attr.colorSurface, base.surface),
                onSurface = color(MaterialR.attr.colorOnSurface, base.onSurface),
                surfaceVariant = color(MaterialR.attr.colorSurfaceVariant, base.surfaceVariant),
                onSurfaceVariant =
                    color(MaterialR.attr.colorOnSurfaceVariant, base.onSurfaceVariant),
                surfaceTint = color(MaterialR.attr.colorPrimary, base.surfaceTint),
                surfaceDim = color(MaterialR.attr.colorSurfaceDim, base.surfaceDim),
                surfaceBright = color(MaterialR.attr.colorSurfaceBright, base.surfaceBright),
                surfaceContainerLowest =
                    color(MaterialR.attr.colorSurfaceContainerLowest, base.surfaceContainerLowest),
                surfaceContainerLow =
                    color(MaterialR.attr.colorSurfaceContainerLow, base.surfaceContainerLow),
                surfaceContainer =
                    color(MaterialR.attr.colorSurfaceContainer, base.surfaceContainer),
                surfaceContainerHigh =
                    color(MaterialR.attr.colorSurfaceContainerHigh, base.surfaceContainerHigh),
                surfaceContainerHighest =
                    color(
                        MaterialR.attr.colorSurfaceContainerHighest,
                        base.surfaceContainerHighest,
                    ),
                inverseSurface = color(MaterialR.attr.colorSurfaceInverse, base.inverseSurface),
                inverseOnSurface =
                    color(MaterialR.attr.colorOnSurfaceInverse, base.inverseOnSurface),
                inversePrimary = color(MaterialR.attr.colorPrimaryInverse, base.inversePrimary),
                outline = color(MaterialR.attr.colorOutline, base.outline),
                outlineVariant = color(MaterialR.attr.colorOutlineVariant, base.outlineVariant),
                error = color(MaterialR.attr.colorError, base.error),
                onError = color(MaterialR.attr.colorOnError, base.onError),
                errorContainer = color(MaterialR.attr.colorErrorContainer, base.errorContainer),
                onErrorContainer =
                    color(MaterialR.attr.colorOnErrorContainer, base.onErrorContainer),
            )
        }
    MaterialTheme(colorScheme = colors, typography = SettingsTypography, content = content)
}

private val SettingsTypography =
    Typography(
        bodyLarge =
            TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium =
            TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
    )

/** The preference layout's existing dimensions, shared by subsequent migrated screens. */
object PreferenceSpacing {
    val horizontal = 16.dp
    val vertical = 12.dp
    val iconSlot = 56.dp
    val minimumHeight = 64.dp
}
