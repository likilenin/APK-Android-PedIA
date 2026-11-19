package com.pedia.movil.datos

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.Inet4Address

data class PediaEncontrada(val ip: String, val puerto: Int, val nombreLocal: String?)

/** IPv4 de este dispositivo en la red actual (la wifi), o null si no hay red. */
fun ipLocal(contexto: Context): String? {
    val cm = contexto.getSystemService(ConnectivityManager::class.java) ?: return null
    val enlace = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return enlace.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }?.hostAddress
}

suspend fun buscarPedia(contexto: Context, puerto: Int, alAvanzar: (Float) -> Unit): List<PediaEncontrada> =
    buscarPedia(contexto, listOf(puerto, 4000).distinct(), alAvanzar)

// Busca PedIA en la wifi: prueba /api/health en los puertos indicados de todas las
// direcciones de la red (x.x.x.1 a x.x.x.254). No hace falta configurar nada en PedIA.
suspend fun buscarPedia(contexto: Context, puertos: List<Int>, alAvanzar: (Float) -> Unit): List<PediaEncontrada> {
    val propia = ipLocal(contexto) ?: return emptyList()
    val prefijo = propia.substringBeforeLast('.')
    val limite = Semaphore(48)
    val total = 254 * puertos.size
    var hechas = 0
    return withContext(Dispatchers.IO) {
        coroutineScope {
            (1..254).flatMap { n -> puertos.map { puerto -> n to puerto } }.map { (n, puerto) ->
                async {
                    val ip = "$prefijo.$n"
                    val encontrada = limite.withPermit { if (ip == propia) null else sondear(ip, puerto) }
                    synchronized(limite) { hechas++; alAvanzar(hechas / total.toFloat()) }
                    encontrada
                }
            }.awaitAll().filterNotNull()
        }
    }
}

/**
 * ¿Hay un PedIA en esta dirección y puerto? Con el nombre de su local, si lo dice. [rapido]: esperar poco (al buscar
 * por toda la red); con una sola dirección que probar, como la del local guardado, se espera lo normal.
 */
suspend fun sondear(ip: String, puerto: Int, rapido: Boolean = true): PediaEncontrada? = withContext(Dispatchers.IO) {
    val cliente = if (rapido) PediaApi.httpRapido else PediaApi.http
    val ok = runCatching {
        cliente.newCall(Request.Builder().url("http://$ip:$puerto/api/health").build()).execute().use {
            it.isSuccessful && it.body?.string().orEmpty().contains("\"ok\"")
        }
    }.getOrDefault(false)
    if (!ok) return@withContext null
    val nombre = runCatching { PediaApi("http://$ip:$puerto").configuracion()["local_nombre"] }.getOrNull()
    PediaEncontrada(ip, puerto, nombre?.takeIf { it.isNotBlank() })
}
