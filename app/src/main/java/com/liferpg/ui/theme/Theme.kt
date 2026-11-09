package com.liferpg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Fundo = Color(0xFF080808)
val Superficie = Color(0xFF141414)
val SuperficieAlta = Color(0xFF202020)
val Neon = Color(0xFFF5F5F5)
val NeonRoxo = Color(0xFFBDBDBD)
val DouradoLoot = Color(0xFFEAEAEA)
val VerdeXp = Color(0xFFF5F5F5)
val VermelhoPerigo = Color(0xFFF87171)
val TextoPrimario = Color(0xFFF5F5F5)
val TextoSecundario = Color(0xFFA0A0A0)
val Borda = Color(0xFF2B2B2B)

private val EsquemaEscuro = darkColorScheme(
    primary = Neon,
    onPrimary = Color.Black,
    secondary = NeonRoxo,
    onSecondary = Color.Black,
    tertiary = DouradoLoot,
    onTertiary = Color.Black,
    background = Fundo,
    onBackground = TextoPrimario,
    surface = Superficie,
    onSurface = TextoPrimario,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoSecundario,
    error = VermelhoPerigo,
    outline = Borda,
)

private val TipografiaJogo = Typography(
    displayLarge = TextStyle(
        fontSize = 44.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Default,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.5.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
    ),
)

@Composable
fun LifeRpgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaEscuro,
        typography = TipografiaJogo,
        content = content,
    )
}
