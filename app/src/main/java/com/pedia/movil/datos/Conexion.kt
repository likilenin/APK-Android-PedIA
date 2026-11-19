package com.pedia.movil.datos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface EstadoConexion {
    data class Buscando(val texto: String, val progreso: Float? = null) : EstadoConexion
    data class Conectado(val nombre: String?, val ip: String, val puerto: Int) : EstadoConexion

    /** Hay que elegir: se han encontrado varios locales (o no está el que se guardó). */
    data class ElegirLocal(val locales: List<PediaEncontrada>, val guardado: String?) : EstadoConexion
    data class Sin(val mensaje: String) : EstadoConexion
}

private const val SEGUNDOS_ENTRE_INTENTOS = 20L
private const val PUERTO_HABITUAL = 4000

/**
 * Conexión con el PedIA del local, al arrancar la app:
 *  1. Si ya hay un local guardado y responde en la wifi, se conecta a él sin preguntar nada.
 *  2. Si no responde (o en esa dirección hay otro local), se busca PedIA por la red. Si está el guardado con otra
 *     dirección, se usa; si no hay ninguno guardado y solo hay uno, se conecta; si hay varios, se pregunta con
 *     cuál (el elegido se guarda); si no hay ninguno, se sigue buscando cada pocos segundos.
 * Al conectar se cargan las cartas (kiosko y local).
 */
object Conexion {
    var estado by mutableStateOf<EstadoConexion>(EstadoConexion.Buscando("Conectando con PedIA…"))
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var trabajo: Job? = null

    /** (Re)inicia la conexión. Con la app ya conectada solo vuelve a comprobarla y a pedir las cartas. */
    fun arrancar(contexto: Context, ajustes: Ajustes) {
        trabajo?.cancel()
        val aplicacion = contexto.applicationContext
        trabajo = alcance.launch {
            while (isActive) {
                if (intentar(aplicacion, ajustes)) break
                delay(SEGUNDOS_ENTRE_INTENTOS * 1000)
            }
        }
    }

    /** El usuario ha elegido uno de los locales encontrados. */
    fun elegir(local: PediaEncontrada, ajustes: Ajustes) {
        trabajo?.cancel()
        conectar(local, ajustes)
    }

    /** True si ya se ha resuelto (conectado o a la espera de que se elija); false para volver a intentarlo. */
    private suspend fun intentar(contexto: Context, ajustes: Ajustes): Boolean {
        val guardado = ajustes.servidorNombre.takeIf { it.isNotBlank() }
        estado = EstadoConexion.Buscando("Conectando con PedIA…")

        // 1. El local guardado.
        if (ajustes.servidorIp.isNotBlank()) {
            val propio = sondear(ajustes.servidorIp, ajustes.servidorPuerto, rapido = false)
            if (propio != null && (guardado == null || propio.nombreLocal == null || mismoNombre(propio.nombreLocal, guardado))) {
                conectar(propio, ajustes)
                return true
            }
        }

        // 2. Buscarlo en la wifi.
        estado = EstadoConexion.Buscando("Buscando PedIA en la wifi…", 0f)
        val puertos = listOf(ajustes.servidorPuerto, PUERTO_HABITUAL).distinct()
        val halladas = buscarPedia(contexto, puertos) { estado = EstadoConexion.Buscando("Buscando PedIA en la wifi…", it) }
        val delGuardado = halladas.filter { guardado != null && mismoNombre(it.nombreLocal, guardado) }
        return when {
            halladas.isEmpty() -> {
                estado = EstadoConexion.Sin(
                    if (ajustes.servidorIp.isBlank()) "No se encuentra PedIA en la wifi. Comprueba que está abierta en el ordenador."
                    else "No se encuentra ${guardado ?: "PedIA"} en la wifi. Comprueba que el ordenador está encendido y PedIA abierta.",
                )
                false
            }
            delGuardado.size == 1 -> { conectar(delGuardado.first(), ajustes); true }
            halladas.size == 1 && (guardado == null || mismoNombre(halladas.first().nombreLocal, guardado)) -> {
                conectar(halladas.first(), ajustes)
                true
            }
            else -> {
                estado = EstadoConexion.ElegirLocal(halladas, guardado)
                true
            }
        }
    }

    private fun conectar(local: PediaEncontrada, ajustes: Ajustes) {
        val cambia = local.ip != ajustes.servidorIp || local.puerto != ajustes.servidorPuerto || !mismoNombre(local.nombreLocal, ajustes.servidorNombre.ifBlank { null })
        ajustes.servidorIp = local.ip
        ajustes.servidorPuerto = local.puerto
        local.nombreLocal?.let { ajustes.servidorNombre = it }
        estado = EstadoConexion.Conectado(local.nombreLocal, local.ip, local.puerto)
        if (cambia) Cartas.olvidar()
        Cartas.cargarTodas(ajustes, forzar = true)
    }

    private fun mismoNombre(a: String?, b: String?) = a != null && b != null && claveTexto(a) == claveTexto(b)
}
