package com.pedia.movil.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ErrorPedia(mensaje: String, val codigoHttp: Int = 0) : Exception(mensaje)

/** Mensaje para enseñar al usuario a partir de cualquier error. */
fun Throwable.mensajeUsuario(): String = when (this) {
    is ErrorPedia -> message ?: "Error al hablar con PedIA"
    else -> "Respuesta inesperada de PedIA (${this::class.simpleName}). ¿Está PedIA actualizada?"
}

private const val NOMBRE_PRODUCTO_VOZ = "Pedido telefónico (automático)"
private val ESTADOS_ACTIVOS_MESA = listOf("pendiente", "aceptado", "en_preparacion", "listo")

// Cliente de la API REST que PedIA ya expone en la red local (puerto 4000 por defecto).
// PedIA es el master: todo lo que se pide aquí se guarda allí, y PedIA lo imprime en
// cocina y lo muestra en su tablero igual que un pedido hecho en el propio TPV.
class PediaApi(private val urlBase: String) {

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** Para buscar PedIA por la red: hay que probar 254 direcciones, así que se espera poco a cada una. */
        val httpRapido: OkHttpClient = http.newBuilder()
            .connectTimeout(700, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()

        private val tipoJson = "application/json; charset=utf-8".toMediaType()
    }

    private suspend fun peticion(metodo: String, ruta: String, cuerpo: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            val body = when {
                cuerpo != null -> cuerpo.toString().toRequestBody(tipoJson)
                metodo == "POST" || metodo == "PATCH" || metodo == "PUT" -> "{}".toRequestBody(tipoJson)
                else -> null
            }
            val request = Request.Builder().url(urlBase + ruta).method(metodo, body).build()
            try {
                http.newCall(request).execute().use { respuesta ->
                    val texto = respuesta.body?.string().orEmpty()
                    if (!respuesta.isSuccessful) {
                        val error = runCatching {
                            json.parseToJsonElement(texto).jsonObject["error"]?.jsonPrimitive?.content
                        }.getOrNull()
                        throw ErrorPedia(
                            error ?: when (respuesta.code) {
                                423 -> "PedIA está bloqueada (licencia). Revisa PedIA en el ordenador."
                                404 -> "PedIA no encuentra lo que se ha pedido (¿se ha borrado?)."
                                else -> "PedIA ha respondido con un error (${respuesta.code})."
                            },
                            respuesta.code,
                        )
                    }
                    texto
                }
            } catch (e: IOException) {
                throw ErrorPedia(
                    "No hay conexión con PedIA ($urlBase). Comprueba que el ordenador está encendido, " +
                        "PedIA abierta y que estáis en la misma wifi."
                )
            }
        }

    suspend fun salud(): Boolean = peticion("GET", "/api/health").contains("ok")

    suspend fun categorias(): List<Categoria> = json.decodeFromString(peticion("GET", "/api/categorias"))

    suspend fun productos(): List<Producto> = json.decodeFromString(peticion("GET", "/api/productos"))

    suspend fun configuracion(): Map<String, String> =
        json.decodeFromString<List<EntradaConfig>>(peticion("GET", "/api/config"))
            .associate { it.clave to (it.valor ?: "") }

    suspend fun cargarCarta(): Carta = coroutineScope {
        val cats = async { categorias() }
        val prods = async { productos() }
        val config = async { runCatching { configuracion() }.getOrDefault(emptyMap()) }

        val activas = cats.await().filter { it.activo == 1 }.sortedWith(compareBy({ it.orden }, { it.id }))
        val idsActivas = activas.map { it.id }.toSet()
        val productos = prods.await()
            .filter { it.categoriaId in idsActivas && it.nombre != NOMBRE_PRODUCTO_VOZ }
            .sortedWith(compareBy({ it.orden }, { it.id }))
        val conProductos = productos.map { it.categoriaId }.toSet()
        val cfg = config.await()

        Carta(
            categorias = activas.filter { it.id in conProductos },
            productos = productos,
            nombreLocal = cfg["local_nombre"]?.takeIf { it.isNotBlank() } ?: "PedIA",
            mesas = cfg["mesas_cantidad"]?.toIntOrNull()?.takeIf { it > 0 } ?: 20,
        )
    }

    /** Pedidos de mesa abiertos (los mismos que PedIA marca como mesa ocupada). */
    suspend fun pedidosDeMesaActivos(): List<Pedido> =
        json.decodeFromString<List<Pedido>>(
            peticion("GET", "/api/pedidos?estados=${ESTADOS_ACTIVOS_MESA.joinToString(",")}")
        ).filter { it.tipo == "mesa" }

    suspend fun crearPedido(tipo: String, mesa: Int?, notas: String?, lineas: List<LineaCarrito>): Pedido {
        val cuerpo = buildJsonObject {
            put("tipo", tipo)
            if (mesa != null) put("mesa_numero", mesa.toString())
            if (!notas.isNullOrBlank()) put("notas", notas)
            putJsonArray("items") {
                lineas.forEach { linea ->
                    add(buildJsonObject {
                        put("producto_id", linea.producto.id)
                        put("cantidad", linea.cantidad)
                        if (linea.notas.isNotBlank()) put("notas", linea.notas)
                    })
                }
            }
        }
        return json.decodeFromString(peticion("POST", "/api/pedidos", cuerpo))
    }

    suspend fun anadirItem(pedidoId: Long, linea: LineaCarrito): Pedido {
        val cuerpo = buildJsonObject {
            put("producto_id", linea.producto.id)
            put("cantidad", linea.cantidad)
            if (linea.notas.isNotBlank()) put("notas", linea.notas)
        }
        return json.decodeFromString(peticion("POST", "/api/pedidos/$pedidoId/items", cuerpo))
    }

    suspend fun cambiarCantidadItem(pedidoId: Long, itemId: Long, cantidad: Int): Pedido =
        json.decodeFromString(
            peticion("PATCH", "/api/pedidos/$pedidoId/items/$itemId", buildJsonObject { put("cantidad", cantidad) })
        )

    suspend fun eliminarItem(pedidoId: Long, itemId: Long): Pedido =
        json.decodeFromString(peticion("DELETE", "/api/pedidos/$pedidoId/items/$itemId"))

    suspend fun cambiarMesa(pedidoId: Long, mesa: Int): Pedido =
        json.decodeFromString(
            peticion("PATCH", "/api/pedidos/$pedidoId/mesa", buildJsonObject { put("mesa_numero", mesa.toString()) })
        )

    /** Cobro en efectivo confirmado a mano (lo mismo que "Abrir caja" en PedIA). */
    suspend fun marcarPagado(pedidoId: Long): Pedido =
        json.decodeFromString(peticion("POST", "/api/pedidos/$pedidoId/marcar-pagado"))

    suspend fun liberarMesa(pedidoId: Long): Pedido =
        json.decodeFromString(peticion("POST", "/api/pedidos/$pedidoId/liberar-mesa"))

    /** Imprime de nuevo el ticket del pedido en la impresora de PedIA. */
    suspend fun reimprimir(pedidoId: Long) {
        peticion("POST", "/api/pedidos/$pedidoId/reimprimir")
    }

    fun urlFoto(ruta: String?): String? = when {
        ruta.isNullOrBlank() -> null
        ruta.startsWith("http") -> ruta
        else -> urlBase + (if (ruta.startsWith("/")) ruta else "/$ruta")
    }
}
