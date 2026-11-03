package com.pedia.movil.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Colores de PedIA (mismo azul que el TPV de escritorio).
val AzulPedia = Color(0xFF2F6FED)
val AzulPediaOscuro = Color(0xFF2557C4)
val AzulSuave = Color(0xFFEAF1FF)
val FondoOscuro = Color(0xFF0F172A)
val Verde = Color(0xFF16A34A)
val VerdeSuave = Color(0xFFDCFCE7)
val Naranja = Color(0xFFEA580C)
val NaranjaSuave = Color(0xFFFFEDD5)
val Rojo = Color(0xFFDC2626)

// Kiosko (estilo autoservicio de comida rápida): fondo crema, tarjetas blancas, acento ámbar.
val Ambar = Color(0xFFFFB300)
val AmbarSuave = Color(0xFFFFF4D6)
val Tinta = Color(0xFF1C1C1E)
val Crema = Color(0xFFF6F2EA)
val GrisFoto = Color(0xFFF2F2F2)
val GrisTexto = Color(0xFF6B7280)

private val colores = lightColorScheme(
    primary = AzulPedia,
    onPrimary = Color.White,
    primaryContainer = AzulSuave,
    onPrimaryContainer = AzulPediaOscuro,
    secondary = Color(0xFF475569),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    error = Rojo,
)

/** La app está pensada para tablet: en un móvil se reduce todo (letras y botones) en esta proporción. */
private const val ESCALA_MOVIL = 0.8f

@Composable
fun esMovil() = LocalConfiguration.current.smallestScreenWidthDp < 600

@Composable
fun TemaPedia(contenido: @Composable () -> Unit) {
    val base = LocalDensity.current
    // Las pantallas tienen tamaños medidos: la letra grande del sistema no debe descuadrarlas.
    val densidad = Density(base.density * (if (esMovil()) ESCALA_MOVIL else 1f), minOf(base.fontScale, 1f))
    CompositionLocalProvider(LocalDensity provides densidad) {
        MaterialTheme(colorScheme = colores, content = contenido)
    }
}

/** Ventana de ficha a casi toda la pantalla. Android la abre sin la escala de TemaPedia: aquí se le vuelve a poner. */
@Composable
fun VentanaPedia(alCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    val densidad = LocalDensity.current
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides densidad, content = contenido)
    }
}
