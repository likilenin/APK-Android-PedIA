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

    // Con canal ("kiosco"), PedIA solo devuelve lo que ese canal puede pedir.
    suspend fun categorias(canal: String? = null): List<Categoria> =
        json.decodeFromString(peticion("GET", "/api/categorias" + (canal?.let { "?canal=$it" } ?: "")))

    suspend fun productos(canal: String? = null): List<Producto> =
        json.decodeFromString(peticion("GET", "/api/productos" + (canal?.let { "?canal=$it" } ?: "")))

    suspend fun configuracion(): Map<String, String> =
        json.decodeFromString<List<EntradaConfig>>(peticion("GET", "/api/config"))
            .associate { it.clave to (it.valor ?: "") }

    suspend fun cargarCarta(canal: String? = null): Carta = coroutineScope {
        val cats = async { categorias(canal) }
        val prods = async { productos(canal) }
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
            ivaGeneral = cfg["iva_porcentaje"]?.replace(',', '.')?.toDoubleOrNull() ?: 10.0,
        )
    }

    suspend fun pedido(id: Long): Pedido = json.decodeFromString(peticion("GET", "/api/pedidos/$id"))

    /** El cliente de un pedido retenido elige pagar en caja: la comanda ya puede ir a cocina. */
    suspend fun liberarCocina(pedidoId: Long, pagoPrevisto: String) {
        try {
            peticion("POST", "/api/pedidos/$pedidoId/liberar-cocina", buildJsonObject { put("pago_previsto", pagoPrevisto) })
        } catch (e: ErrorPedia) {
            // Una PedIA sin esta ruta ya imprimió la comanda al crear el pedido: no hay nada que liberar.
            if (e.codigoHttp != 404) throw e
        }
    }

    suspend fun eliminarPedido(pedidoId: Long) {
        peticion("DELETE", "/api/pedidos/$pedidoId")
    }

    /**
     * Enlace de cobro de PedIA (el mismo del QR del ticket): abre la página de PedIA que lleva a
     * Redsys. Falla si PedIA no puede cobrar online.
     */
    suspend fun urlPago(pedidoId: Long): String =
        json.parseToJsonElement(peticion("GET", "/api/pedidos/$pedidoId/pago-qr"))
            .jsonObject["url"]?.jsonPrimitive?.content
            ?: throw ErrorPedia("PedIA no ha dado el enlace de pago.")

    /**
     * Ticket del cliente tal como lo imprime PedIA (con su plantilla editada). Solo existe si PedIA
     * tiene GET /api/pedidos/:id/ticket (ver PROMPT-PEDIA.md); si no, devuelve null y la app usa su
     * copia de la plantilla por defecto de PedIA.
     */
    suspend fun ticketDePedia(pedidoId: Long): TicketRemoto? = try {
        json.decodeFromString<TicketRemoto>(peticion("GET", "/api/pedidos/$pedidoId/ticket"))
    } catch (e: ErrorPedia) {
        if (e.codigoHttp == 404) null else throw e
    }

    /** Pedidos de mesa abiertos (los mismos que PedIA marca como mesa ocupada). */
    suspend fun pedidosDeMesaActivos(): List<Pedido> =
        json.decodeFromString<List<Pedido>>(
            peticion("GET", "/api/pedidos?estados=${ESTADOS_ACTIVOS_MESA.joinToString(",")}")
        ).filter { it.tipo == "mesa" }

    private fun lineaJson(linea: LineaCarrito) = buildJsonObject {
        put("producto_id", linea.producto.id)
        put("cantidad", linea.cantidad)
        if (linea.notas.isNotBlank()) put("notas", linea.notas)
        // Menús: PedIA calcula el precio y el texto de la línea a partir de las opciones elegidas.
        if (linea.selecciones.isNotEmpty()) putJsonArray("selecciones") { linea.selecciones.forEach { add(it) } }
    }

    /**
     * [origen]: "kiosko" o "camarero". [pagoPrevisto]: "efectivo" o "tarjeta" (se ve en el tablero de PedIA).
     * [retenerCocina]: pedido pendiente de cobro online: la comanda no se imprime hasta que se pague o se
     * llame a [liberarCocina] (ver PROMPT-PEDIA.md; si PedIA aún no lo tiene, la imprime al crearlo).
     */
    suspend fun crearPedido(
        tipo: String,
        mesa: Int?,
        notas: String?,
        lineas: List<LineaCarrito>,
        origen: String,
        pagoPrevisto: String? = null,
        retenerCocina: Boolean = false,
    ): Pedido {
        val cuerpo = buildJsonObject {
            put("tipo", tipo)
            put("origen", origen)
            if (pagoPrevisto != null) put("pago_previsto", pagoPrevisto)
            if (retenerCocina) put("retener_cocina", true)
            if (mesa != null) put("mesa_numero", mesa.toString())
            if (!notas.isNullOrBlank()) put("notas", notas)
            putJsonArray("items") { lineas.forEach { add(lineaJson(it)) } }
        }
        return json.decodeFromString(peticion("POST", "/api/pedidos", cuerpo))
    }

    suspend fun anadirItem(pedidoId: Long, linea: LineaCarrito): Pedido =
        json.decodeFromString(peticion("POST", "/api/pedidos/$pedidoId/items", lineaJson(linea)))

    suspend fun cambiarCantidadItem(pedidoId: Long, itemId: Long, cantidad: Int): Pedido =
        json.decodeFromString(
            peticion("PATCH", "/api/pedidos/$pedidoId/items/$itemId", buildJsonObject { put("cantidad", cantidad) })
        )

    suspend fun cambiarNotasItem(pedidoId: Long, itemId: Long, notas: String): Pedido =
        json.decodeFromString(
            peticion("PATCH", "/api/pedidos/$pedidoId/items/$itemId", buildJsonObject { put("notas", notas) })
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
