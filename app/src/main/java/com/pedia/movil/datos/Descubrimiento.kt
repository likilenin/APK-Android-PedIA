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

data class PediaEncontrada(val ip: String, val nombreLocal: String?)

/** IPv4 de este dispositivo en la red actual (la wifi), o null si no hay red. */
fun ipLocal(contexto: Context): String? {
    val cm = contexto.getSystemService(ConnectivityManager::class.java) ?: return null
    val enlace = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return enlace.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }?.hostAddress
}

// Busca PedIA en la wifi: prueba /api/health en el puerto indicado de todas las
// direcciones de la red (x.x.x.1 a x.x.x.254). No hace falta configurar nada en PedIA.
suspend fun buscarPedia(contexto: Context, puerto: Int, alAvanzar: (Float) -> Unit): List<PediaEncontrada> {
    val propia = ipLocal(contexto) ?: return emptyList()
    val prefijo = propia.substringBeforeLast('.')
    val limite = Semaphore(48)
    var hechas = 0
    return withContext(Dispatchers.IO) {
        coroutineScope {
            (1..254).map { n ->
                async {
                    val ip = "$prefijo.$n"
                    val encontrada = limite.withPermit { if (ip == propia) null else probar(ip, puerto) }
                    synchronized(limite) { hechas++; alAvanzar(hechas / 254f) }
                    encontrada
                }
            }.awaitAll().filterNotNull()
        }
    }
}

private suspend fun probar(ip: String, puerto: Int): PediaEncontrada? {
    val ok = runCatching {
        PediaApi.httpRapido.newCall(Request.Builder().url("http://$ip:$puerto/api/health").build()).execute().use {
            it.isSuccessful && it.body?.string().orEmpty().contains("\"ok\"")
        }
    }.getOrDefault(false)
    if (!ok) return null
    val nombre = runCatching { PediaApi("http://$ip:$puerto").configuracion()["local_nombre"] }.getOrNull()
    return PediaEncontrada(ip, nombre?.takeIf { it.isNotBlank() })
}
