package com.pedia.movil.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mismos datos (y nombres de campo) que devuelve la API de PedIA. Los precios van en
// céntimos, como en PedIA.

@Serializable
data class Categoria(
    val id: Long,
    val nombre: String,
    val orden: Int = 0,
    val activo: Int = 1,
)

@Serializable
data class Producto(
    val id: Long,
    @SerialName("categoria_id") val categoriaId: Long,
    val nombre: String,
    val descripcion: String? = null,
    val precio: Long,
    @SerialName("foto_path") val fotoPath: String? = null,
    val disponible: Int = 1,
    val orden: Int = 0,
    val opciones: List<String> = emptyList(),
    /** Menús: grupos de opciones que hay que elegir al pedirlo ("Bebida", "Patatas"...). */
    val grupos: List<GrupoProducto> = emptyList(),
) {
    val estaDisponible get() = disponible == 1
    val esMenu get() = grupos.isNotEmpty()
}

@Serializable
data class GrupoProducto(
    val id: Long,
    val nombre: String,
    val obligatorio: Boolean = true,
    @SerialName("max_selecciones") val maxSelecciones: Int = 1,
    val opciones: List<OpcionGrupo> = emptyList(),
)

@Serializable
data class OpcionGrupo(
    val id: Long,
    val texto: String,
    /** Céntimos que suma al precio del menú (puede ser 0). */
    val suplemento: Long = 0,
    val agotada: Boolean = false,
    /** Producto de la carta al que corresponde la opción, si está enlazado en PedIA. */
    @SerialName("producto_id") val productoId: Long? = null,
)

@Serializable
data class ItemPedido(
    val id: Long,
    @SerialName("producto_id") val productoId: Long,
    val cantidad: Int = 1,
    @SerialName("precio_unitario") val precioUnitario: Long = 0,
    val notas: String? = null,
    @SerialName("producto_nombre") val productoNombre: String? = null,
    @SerialName("producto_iva_porcentaje") val productoIvaPorcentaje: Double? = null,
)

@Serializable
data class Pedido(
    val id: Long,
    val tipo: String,
    @SerialName("mesa_numero") val mesaNumero: String? = null,
    val estado: String = "pendiente",
    @SerialName("estado_pago") val estadoPago: String = "pendiente",
    val total: Long = 0,
    val notas: String? = null,
    @SerialName("numero_dia") val numeroDia: Int? = null,
    @SerialName("created_at") val creadoEn: String = "",
    /** 1 si PedIA tiene la comanda retenida hasta que se cobre. */
    @SerialName("cocina_retenida") val cocinaRetenida: Int = 0,
    val impresion: ImpresionPedido? = null,
    val items: List<ItemPedido> = emptyList(),
) {
    val pagado get() = estadoPago == "pagado"
    val unidades get() = items.sumOf { it.cantidad }
}

/** Respuesta de GET /api/pedidos/:id/ticket de PedIA (cuando lo tenga). */
@Serializable
data class TicketRemoto(
    val texto: String,
    @SerialName("qr_url") val qrUrl: String? = null,
    @SerialName("escpos_base64") val escposBase64: String? = null,
)

@Serializable
data class EntradaConfig(
    val clave: String,
    val valor: String? = null,
)

/** Una zona del local (Terraza, Salón, Barra) con sus mesas: [prefijo] y número ("T1", "T2"...). */
data class ZonaMesas(val nombre: String, val prefijo: String, val cantidad: Int) {
    val mesas get() = (1..cantidad).map { "$prefijo$it" }
}

/** Código de una mesa tal como lo guarda PedIA: sin espacios y en mayúsculas ("t 3" → "T3"). */
fun codigoMesa(texto: String?): String? = texto?.filterNot { it.isWhitespace() }?.uppercase()?.ifEmpty { null }

/** Carta lista para mostrar: solo categorías activas con productos, en el orden de PedIA. */
data class Carta(
    val categorias: List<Categoria>,
    val productos: List<Producto>,
    val nombreLocal: String,
    /** Zonas del local con sus mesas, en el orden en que se enseñan. */
    val zonas: List<ZonaMesas>,
    /** IVA general de PedIA (%), el que se usa en el ticket para los productos sin IVA propio. */
    val ivaGeneral: Double = 10.0,
) {
    fun productosDe(categoriaId: Long?) = productos.filter { it.categoriaId == categoriaId }

    /**
     * Ingredientes que se pueden quitar a una opción de menú ("Sin queso"...): los del producto de la
     * carta enlazado o, si no está enlazado, los del producto que se llama igual.
     */
    fun ingredientesDe(opcion: OpcionGrupo): List<String> {
        val nombre = opcion.texto.trim().lowercase()
        val producto = productos.firstOrNull { it.id == opcion.productoId }
            ?: productos.firstOrNull { it.nombre.trim().lowercase() == nombre }
        return producto?.opciones.orEmpty()
    }
}

/** Cómo fue la última impresión del ticket del pedido en la impresora de PedIA. */
@Serializable
data class ImpresionPedido(
    val estado: String? = null,
    @SerialName("ultimo_error") val ultimoError: String? = null,
)

/** Una línea del pedido que se está preparando en la tablet (aún sin enviar a PedIA). */
data class LineaCarrito(
    val producto: Producto,
    val cantidad: Int,
    val notas: String = "",
    /** Menús: ids de las opciones elegidas en cada grupo (PedIA calcula el precio con ellas). */
    val selecciones: List<Long> = emptyList(),
    /** Lo marcado en la ficha, para poder volver a abrirla: opciones del producto... */
    val opcionesMarcadas: Set<String> = emptySet(),
    /** ...e ingredientes quitados a cada opción de menú (id de la opción → "Sin queso"...). */
    val quitados: Map<Long, Set<String>> = emptyMap(),
    /** ...y lo que el camarero ha escrito con sus palabras ("sin pan"). Todo ello ya va dentro de [notas]. */
    val notaLibre: String = "",
) {
    /** Se puede abrir su ficha para cambiar lo elegido o quitar ingredientes. */
    val editable get() = producto.esMenu || producto.opciones.isNotEmpty()
    private val opcionesElegidas get() = producto.grupos.flatMap { it.opciones }.filter { it.id in selecciones }
    val suplemento get() = opcionesElegidas.sumOf { it.suplemento }
    val precioUnidad get() = producto.precio + suplemento
    /** Lo elegido del menú y las notas, para enseñarlo en pantalla ("Cocacola, Patatas deluxe, sin queso"). */
    val detalle get() = (opcionesElegidas.map { it.texto } + listOfNotNull(notas.ifBlank { null })).joinToString(", ")
    val clave get() = "${producto.id}|${selecciones.sorted().joinToString(",")}|$notas"
    val subtotal get() = precioUnidad * cantidad
}

/** Añade la línea al carrito, sumándola a otra igual (mismo producto y mismas notas) si ya existe. */
fun MutableList<LineaCarrito>.anadir(nueva: LineaCarrito) {
    val i = indexOfFirst { it.clave == nueva.clave }
    if (i >= 0) this[i] = this[i].copy(cantidad = this[i].cantidad + nueva.cantidad) else add(nueva)
}

/** Cambia una línea por su versión editada, en el mismo sitio; si ahora es igual a otra, se suman. */
fun MutableList<LineaCarrito>.sustituir(clave: String, nueva: LineaCarrito) {
    val i = indexOfFirst { it.clave == clave }
    if (i < 0) return
    val igual = indexOfFirst { it.clave == nueva.clave }
    if (igual >= 0 && igual != i) {
        this[igual] = this[igual].copy(cantidad = this[igual].cantidad + nueva.cantidad)
        removeAt(i)
    } else {
        this[i] = nueva
    }
}

/** Suma o resta unidades a una línea; si llega a cero, la quita. */
fun MutableList<LineaCarrito>.cambiarCantidad(clave: String, delta: Int) {
    val i = indexOfFirst { it.clave == clave }
    if (i < 0) return
    val cantidad = this[i].cantidad + delta
    if (cantidad <= 0) removeAt(i) else this[i] = this[i].copy(cantidad = cantidad)
}
