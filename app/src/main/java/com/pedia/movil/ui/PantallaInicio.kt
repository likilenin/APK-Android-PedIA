package com.pedia.movil.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedia.movil.BuildConfig
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Modo
import com.pedia.movil.datos.mensajeUsuario

@Composable
fun PantallaInicio(ajustes: Ajustes, alElegirModo: (Modo) -> Unit, alAbrirAjustes: () -> Unit) {
    var pedirPin by remember { mutableStateOf(false) }
    var conexion by remember { mutableStateOf<String?>(null) }
    var conectado by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        if (!ajustes.servidorConfigurado) {
            conectado = false
            conexion = "Falta configurar dónde está PedIA. Entra en Ajustes y pulsa «Buscar PedIA en la wifi»."
            return@LaunchedEffect
        }
        try {
            val carta = ajustes.api().cargarCarta()
            conectado = true
            conexion = "Conectado con ${carta.nombreLocal} (${ajustes.servidorIp})"
        } catch (e: Exception) {
            conectado = false
            conexion = e.mensajeUsuario()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoOscuro, Color(0xFF1E3A8A)))),
    ) {
        IconButton(onClick = { pedirPin = true }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 900.dp),
        ) {
            LogoPedia(tamano = 48, latido = false)
            Spacer(Modifier.height(12.dp))
            Text("¿Cómo vas a usar este dispositivo?", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ancho = maxWidth > 600.dp
                val kiosko: @Composable (Modifier) -> Unit = {
                    TarjetaModo("🍔", "Modo kiosko", "Los clientes piden solos en la pantalla, como en un autoservicio, y se llevan su ticket.", it) {
                        alElegirModo(Modo.KIOSCO)
                    }
                }
                val camarero: @Composable (Modifier) -> Unit = {
                    TarjetaModo("🧑‍🍳", "Modo camarero", "Ver las mesas, tomar nota y mandar la comanda a cocina en segundos.", it) {
                        alElegirModo(Modo.CAMARERO)
                    }
                }
                if (ancho) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        kiosko(Modifier.weight(1f))
                        camarero(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        kiosko(Modifier.fillMaxWidth())
                        camarero(Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            conexion?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape).background(
                            when (conectado) {
                                true -> Color(0xFF4ADE80)
                                false -> Color(0xFFF87171)
                                null -> Color.Gray
                            }
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(it, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Versión ${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
        }
    }

    if (pedirPin) {
        DialogoPin(ajustes, alAceptar = { pedirPin = false; alAbrirAjustes() }, alCancelar = { pedirPin = false })
    }
}

@Composable
private fun TarjetaModo(emoji: String, titulo: String, texto: String, modifier: Modifier, alPulsar: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = alPulsar),
    ) {
        Column(Modifier.padding(28.dp)) {
            Text(emoji, fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(titulo, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(texto, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
        }
    }
}
