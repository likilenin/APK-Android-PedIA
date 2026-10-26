package com.pedia.movil.ui.camarero

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.ItemPedido
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.Pedido
import com.pedia.movil.datos.Producto
import com.pedia.movil.datos.anadir
import com.pedia.movil.datos.cambiarCantidad
import com.pedia.movil.datos.mensajeUsuario
import kotlinx.coroutines.launch

class CamareroVM(private val ajustes: Ajustes) : ViewModel() {
    var carta by mutableStateOf<Carta?>(null)
        private set
    var errorCarta by mutableStateOf<String?>(null)
        private set

    /** Pedido abierto de cada mesa ocupada (el más reciente, como hace PedIA). */
    var pedidosPorMesa by mutableStateOf<Map<Int, Pedido>>(emptyMap())
        private set
    var conectado by mutableStateOf<Boolean?>(null)
        private set
    var errorConexion by mutableStateOf<String?>(null)
        private set

    var mesaAbierta by mutableStateOf<Int?>(null)
        private set

    /** Lo que se está apuntando para la mesa abierta y aún no se ha mandado a cocina. */
    val nuevas = mutableStateListOf<LineaCarrito>()
    var enviando by mutableStateOf(false)
        private set
    var ocupado by mutableStateOf(false)
        private set

    /** Mensaje corto para la barra inferior ("Comanda enviada"...). */
    var aviso by mutableStateOf<String?>(null)

    private val api get() = ajustes.api()

    init {
        cargarCarta()
    }

    fun cargarCarta() {
        viewModelScope.launch {
            try {
                carta = api.cargarCarta()
                errorCarta = null
            } catch (e: Exception) {
                errorCarta = e.mensajeUsuario()
            }
        }
    }

    suspend fun refrescarMesas() {
        try {
            val lista = api.pedidosDeMesaActivos()
            pedidosPorMesa = lista
                .mapNotNull { p -> p.mesaNumero?.trim()?.toIntOrNull()?.let { it to p } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, pedidos) -> pedidos.maxBy { it.creadoEn } }
            conectado = true
            errorConexion = null
            if (carta == null) cargarCarta()
        } catch (e: Exception) {
            conectado = false
            errorConexion = e.mensajeUsuario()
        }
    }

    fun pedidoDe(mesa: Int?) = mesa?.let { pedidosPorMesa[it] }

    fun abrirMesa(mesa: Int) {
        nuevas.clear()
        mesaAbierta = mesa
    }

    fun cerrarMesa() {
        nuevas.clear()
        mesaAbierta = null
    }

    fun anadir(producto: Producto, cantidad: Int = 1, notas: String = "") =
        nuevas.anadir(LineaCarrito(producto, cantidad, notas))

    fun cambiarCantidad(clave: String, delta: Int) = nuevas.cambiarCantidad(clave, delta)

    private fun actualizarPedido(pedido: Pedido) {
        val mesa = pedido.mesaNumero?.trim()?.toIntOrNull()
        val sinEste = pedidosPorMesa.filterValues { it.id != pedido.id }
        pedidosPorMesa = if (mesa != null && pedido.estado in listOf("pendiente", "aceptado", "en_preparacion", "listo"))
            sinEste + (mesa to pedido) else sinEste
    }

    // Manda a PedIA lo apuntado: si la mesa ya tiene pedido abierto se le añaden los
    // productos; si no, se crea el pedido de la mesa. Antes de crear uno nuevo se vuelve a
    // mirar si otro camarero acaba de abrir esa mesa, para no duplicar pedidos.
    fun enviarComanda() {
        val mesa = mesaAbierta ?: return
        if (nuevas.isEmpty() || enviando) return
        enviando = true
        viewModelScope.launch {
            try {
                refrescarMesas()
                val existente = pedidosPorMesa[mesa]
                if (existente == null) {
                    val notas = ajustes.nombreCamarero.takeIf { it.isNotBlank() }?.let { "Camarero: $it" }
                    actualizarPedido(api.crearPedido("mesa", mesa, notas, nuevas.toList()))
                    nuevas.clear()
                } else {
                    // Una a una, quitando de la lista lo que ya ha llegado: si algo falla a
                    // medias, lo que queda en pantalla es justo lo que falta por enviar.
                    while (nuevas.isNotEmpty()) {
                        actualizarPedido(api.anadirItem(existente.id, nuevas.first()))
                        nuevas.removeAt(0)
                    }
                }
                aviso = "✓ Comanda de la mesa $mesa enviada a cocina"
                mesaAbierta = null
            } catch (e: Exception) {
                aviso = "✗ No se ha enviado: ${e.mensajeUsuario()}"
            } finally {
                enviando = false
            }
        }
    }

    private fun accion(textoOk: String?, cerrar: Boolean = false, bloque: suspend () -> Pedido?) {
        if (ocupado) return
        ocupado = true
        viewModelScope.launch {
            try {
                bloque()?.let(::actualizarPedido)
                textoOk?.let { aviso = it }
                if (cerrar) cerrarMesa()
            } catch (e: Exception) {
                aviso = "✗ ${e.mensajeUsuario()}"
            } finally {
                ocupado = false
            }
        }
    }

    fun quitarUnaEnviada(pedido: Pedido, item: ItemPedido) = accion(null) {
        if (item.cantidad <= 1) api.eliminarItem(pedido.id, item.id)
        else api.cambiarCantidadItem(pedido.id, item.id, item.cantidad - 1)
    }

    fun eliminarEnviada(pedido: Pedido, item: ItemPedido) = accion(null) { api.eliminarItem(pedido.id, item.id) }

    fun cobrarEnEfectivo(pedido: Pedido) =
        accion("✓ Mesa ${pedido.mesaNumero} cobrada en efectivo") { api.marcarPagado(pedido.id) }

    fun liberarMesa(pedido: Pedido) =
        accion("✓ Mesa ${pedido.mesaNumero} libre", cerrar = true) { api.liberarMesa(pedido.id) }

    fun cambiarDeMesa(pedido: Pedido, nueva: Int) = accion("✓ Pedido pasado a la mesa $nueva", cerrar = true) {
        api.cambiarMesa(pedido.id, nueva)
    }

    fun imprimirCuenta(pedido: Pedido) = accion("✓ Ticket enviado a la impresora de PedIA") {
        api.reimprimir(pedido.id)
        null
    }
}
