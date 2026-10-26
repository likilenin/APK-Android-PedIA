package com.pedia.movil.ui

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

private val formatoEuros = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES"))

fun euros(centimos: Long): String = formatoEuros.format(centimos / 100.0)

/** Minutos desde una fecha de PedIA ("YYYY-MM-DD HH:MM:SS", en UTC como la guarda SQLite). */
fun minutosDesde(fechaUtc: String): Long? = runCatching {
    val instante = LocalDateTime.parse(fechaUtc.trim().replace(' ', 'T')).toInstant(ZoneOffset.UTC)
    Duration.between(instante, Instant.now()).toMinutes().coerceAtLeast(0)
}.getOrNull()

fun textoTiempo(minutos: Long): String = when {
    minutos < 1 -> "ahora"
    minutos < 60 -> "$minutos min"
    else -> "${minutos / 60} h ${minutos % 60} min"
}

fun textoEstado(estado: String): String = when (estado) {
    "pendiente" -> "Pendiente"
    "aceptado" -> "Aceptado"
    "en_preparacion" -> "En preparación"
    "listo" -> "Listo"
    "en_reparto" -> "En reparto"
    "entregado" -> "Entregado"
    "cancelado" -> "Cancelado"
    else -> estado
}
