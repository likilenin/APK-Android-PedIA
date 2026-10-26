package com.pedia.movil.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pedia.movil.BuildConfig
import com.pedia.movil.actualizacion.Actualizador
import com.pedia.movil.actualizacion.EstadoInstalacion
import com.pedia.movil.actualizacion.VersionPublicada
import com.pedia.movil.datos.Ajustes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

private sealed interface EstadoAct {
    data object Buscando : EstadoAct
    data class SinPermiso(val version: VersionPublicada) : EstadoAct
    data class Descargando(val version: VersionPublicada, val bytes: Long, val total: Long, val velocidad: Long) : EstadoAct
    data class Instalando(val version: VersionPublicada) : EstadoAct
    data class Fallo(val mensaje: String) : EstadoAct
}

private fun megas(bytes: Long) = String.format(Locale.forLanguageTag("es-ES"), "%.1f MB", bytes / 1_048_576.0)

// Primera pantalla al abrir la app: busca una versión nueva en GitHub y, si la hay, la
// descarga (con barra de progreso) y la instala. Sin internet o sin versión nueva, pasa
// directamente a la app.
@Composable
fun PantallaActualizacion(ajustes: Ajustes, alTerminar: () -> Unit) {
    val contexto = LocalContext.current
    var estado by remember { mutableStateOf<EstadoAct>(EstadoAct.Buscando) }
    var intento by remember { mutableIntStateOf(0) }
    val errorInstalacion by EstadoInstalacion.error.collectAsState()

    LaunchedEffect(intento) {
        estado = EstadoAct.Buscando
        val version = Actualizador.buscar(ajustes.repoActualizaciones)
        if (version == null) {
            alTerminar()
            return@LaunchedEffect
        }
        if (!Actualizador.puedeInstalar(contexto)) {
            estado = EstadoAct.SinPermiso(version)
            return@LaunchedEffect
        }
        try {
            val inicio = System.currentTimeMillis()
            val apk = Actualizador.descargar(contexto, ajustes.repoActualizaciones) { bytes, total ->
                val segundos = ((System.currentTimeMillis() - inicio) / 1000.0).coerceAtLeast(0.2)
                estado = EstadoAct.Descargando(version, bytes, total, (bytes / segundos).toLong())
            }
            estado = EstadoAct.Instalando(version)
            Actualizador.instalar(contexto, apk)
            // Si todo va bien, Android cierra la app al instalar. Si no pasa nada en un rato,
            // no se deja la pantalla bloqueada: se ofrece reintentar o seguir.
            delay(45_000)
            if (estado is EstadoAct.Instalando) {
                estado = EstadoAct.Fallo("La instalación no ha terminado. Si Android ha mostrado un aviso, revísalo.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado = EstadoAct.Fallo("No se ha podido descargar la actualización: ${e.message ?: "error de red"}")
        }
    }

    LaunchedEffect(errorInstalacion) {
        errorInstalacion?.let { estado = EstadoAct.Fallo(it) }
    }

    // Al volver de los ajustes de Android tras dar el permiso, se sigue solo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (estado is EstadoAct.SinPermiso && Actualizador.puedeInstalar(contexto)) intento++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoOscuro, Color(0xFF1E3A8A)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).widthIn(max = 520.dp),
        ) {
            LogoPedia()
            Spacer(Modifier.height(32.dp))
            AnimatedContent(
                targetState = estado,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "estado",
            ) { actual ->
                when (actual) {
                    EstadoAct.Buscando -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Buscando actualizaciones…", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
                    }

                    is EstadoAct.SinPermiso -> TarjetaActualizacion {
                        Titulo("Hay una versión nueva: ${actual.version.versionName}")
                        Text(
                            "Para instalarla sola, PedIA Móvil necesita permiso para instalar apps. " +
                                "Pulsa «Dar permiso», activa «Permitir de esta fuente» y vuelve atrás.",
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { contexto.startActivity(Actualizador.intentPermisoInstalar(contexto)) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Dar permiso", fontSize = 18.sp) }
                        OutlinedButton(onClick = alTerminar, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Ahora no", color = Color.White)
                        }
                    }

                    is EstadoAct.Descargando -> TarjetaActualizacion {
                        Titulo("Descargando la versión ${actual.version.versionName}")
                        val fraccion = if (actual.total > 0) actual.bytes.toFloat() / actual.total else 0f
                        val animada by animateFloatAsState(fraccion, tween(200), label = "progreso")
                        Text(
                            if (actual.total > 0) "${(fraccion * 100).toInt()} %" else megas(actual.bytes),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (actual.total > 0) {
                            LinearProgressIndicator(
                                progress = { animada },
                                modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)),
                                color = Color(0xFF60A5FA),
                                trackColor = Color.White.copy(alpha = 0.15f),
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)),
                                color = Color(0xFF60A5FA),
                                trackColor = Color.White.copy(alpha = 0.15f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (actual.total > 0) "${megas(actual.bytes)} de ${megas(actual.total)}" else "Descargando…",
                                color = Color.White.copy(alpha = 0.75f),
                            )
                            Text("${megas(actual.velocidad)}/s", color = Color.White.copy(alpha = 0.75f))
                        }
                        Notas(actual.version)
                    }

                    is EstadoAct.Instalando -> TarjetaActualizacion {
                        Titulo("Instalando la versión ${actual.version.versionName}")
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)),
                            color = Color(0xFF4ADE80),
                            trackColor = Color.White.copy(alpha = 0.15f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "La app se cerrará y volverá a abrirse con la nueva versión. Si Android pregunta, pulsa «Actualizar».",
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }

                    is EstadoAct.Fallo -> TarjetaActualizacion {
                        Titulo("No se ha podido actualizar")
                        Text(actual.mensaje, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { EstadoInstalacion.error.value = null; intento++ },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Reintentar", fontSize = 18.sp) }
                        OutlinedButton(
                            onClick = { EstadoInstalacion.error.value = null; alTerminar() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Continuar con la versión actual", color = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Versión instalada ${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun TarjetaActualizacion(contenido: @Composable () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { contenido() }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun Notas(version: VersionPublicada) {
    if (version.notas.isBlank()) return
    Spacer(Modifier.height(20.dp))
    Text("Novedades", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
    Text(version.notas, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

/** Logo de PedIA dibujado con texto (P azul + "edIA"), con un ligero latido. */
@Composable
fun LogoPedia(tamano: Int = 56, latido: Boolean = true) {
    val transicion = rememberInfiniteTransition(label = "latido")
    val escala by transicion.animateFloat(
        initialValue = 1f,
        targetValue = if (latido) 1.04f else 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "escala",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.scale(escala)) {
        Box(
            Modifier.size((tamano * 1.15f).dp).clip(RoundedCornerShape((tamano / 3).dp)).background(AzulPedia),
            contentAlignment = Alignment.Center,
        ) {
            Text("P", color = Color.White, fontSize = (tamano * 0.8f).sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.size((tamano / 5).dp))
        Text("PedIA", color = Color.White, fontSize = (tamano * 0.85f).sp, fontWeight = FontWeight.Black)
    }
}
