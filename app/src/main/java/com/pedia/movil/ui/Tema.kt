package com.pedia.movil.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun TemaPedia(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, content = contenido)
}
