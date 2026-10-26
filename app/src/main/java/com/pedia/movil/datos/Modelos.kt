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
) {
    val estaDisponible get() = disponible == 1
}

@Serializable
data class ItemPedido(
    val id: Long,
    @SerialName("producto_id") val productoId: Long,
    val cantidad: Int = 1,
    @SerialName("precio_unitario") val precioUnitario: Long = 0,
    val notas: String? = null,
    @SerialName("producto_nombre") val productoNombre: String? = null,
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
    val items: List<ItemPedido> = emptyList(),
) {
    val pagado get() = estadoPago == "pagado"
    val unidades get() = items.sumOf { it.cantidad }
}

@Serializable
data class EntradaConfig(
    val clave: String,
    val valor: String? = null,
)

/** Carta lista para mostrar: solo categorías activas con productos, en el orden de PedIA. */
data class Carta(
    val categorias: List<Categoria>,
    val productos: List<Producto>,
    val nombreLocal: String,
    val mesas: Int,
) {
    fun productosDe(categoriaId: Long?) = productos.filter { it.categoriaId == categoriaId }
}

/** Una línea del pedido que se está preparando en la tablet (aún sin enviar a PedIA). */
data class LineaCarrito(
    val producto: Producto,
    val cantidad: Int,
    val notas: String = "",
) {
    val clave get() = "${producto.id}|$notas"
    val subtotal get() = producto.precio * cantidad
}

/** Añade la línea al carrito, sumándola a otra igual (mismo producto y mismas notas) si ya existe. */
fun MutableList<LineaCarrito>.anadir(nueva: LineaCarrito) {
    val i = indexOfFirst { it.clave == nueva.clave }
    if (i >= 0) this[i] = this[i].copy(cantidad = this[i].cantidad + nueva.cantidad) else add(nueva)
}

/** Suma o resta unidades a una línea; si llega a cero, la quita. */
fun MutableList<LineaCarrito>.cambiarCantidad(clave: String, delta: Int) {
    val i = indexOfFirst { it.clave == clave }
    if (i < 0) return
    val cantidad = this[i].cantidad + delta
    if (cantidad <= 0) removeAt(i) else this[i] = this[i].copy(cantidad = cantidad)
}
