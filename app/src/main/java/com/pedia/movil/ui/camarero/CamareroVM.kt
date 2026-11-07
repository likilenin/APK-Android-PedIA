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
import com.pedia.movil.datos.codigoMesa
import com.pedia.movil.datos.mensajeUsuario
import com.pedia.movil.datos.sustituir
import kotlinx.coroutines.launch

class CamareroVM(private val ajustes: Ajustes) : ViewModel() {
    var carta by mutableStateOf<Carta?>(null)
        private set
    var errorCarta by mutableStateOf<String?>(null)
        private set

    /** Pedido abierto de cada mesa ocupada (el más reciente, como hace PedIA). */
    var pedidosPorMesa by mutableStateOf<Map<String, Pedido>>(emptyMap())
        private set
    var conectado by mutableStateOf<Boolean?>(null)
        private set
    var errorConexion by mutableStateOf<String?>(null)
        private set

    var mesaAbierta by mutableStateOf<String?>(null)
        private set

    /** Zona que se está viendo en la pantalla de mesas (por su nombre: si PedIA quita una zona, no se salta a otra). */
    var zonaElegida by mutableStateOf<String?>(null)

    /** Lo que se está apuntando para la mesa abierta y aún no se ha mandado a cocina. */
    val nuevas = mutableStateListOf<LineaCarrito>()
    var enviando by mutableStateOf(false)
        private set
    var ocupado by mutableStateOf(false)
        private set

    /** Mensaje corto para la barra inferior ("Comanda enviada"...). */
    var aviso by mutableStateOf<String?>(null)

    private val api get() = ajustes.api()

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
                .mapNotNull { p -> codigoMesa(p.mesaNumero)?.let { it to p } }
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

    fun pedidoDe(mesa: String?) = mesa?.let { pedidosPorMesa[it] }

    fun abrirMesa(mesa: String) {
        nuevas.clear()
        mesaAbierta = mesa
    }

    fun cerrarMesa() {
        nuevas.clear()
        mesaAbierta = null
    }

    fun anadir(producto: Producto, cantidad: Int = 1, notas: String = "", selecciones: List<Long> = emptyList()) =
        nuevas.anadir(LineaCarrito(producto, cantidad, notas, selecciones))

    /** Añade la línea tal como sale de la ficha, con lo marcado, para poder volver a abrirla y editarla. */
    fun anadir(linea: LineaCarrito) = nuevas.anadir(linea)

    fun cambiarCantidad(clave: String, delta: Int) = nuevas.cambiarCantidad(clave, delta)

    /** Cambia una línea por enviar por su versión editada; con [soloUna], se separa una unidad con los cambios. */
    fun editarLinea(clave: String, nueva: LineaCarrito, soloUna: Boolean) {
        val linea = nuevas.firstOrNull { it.clave == clave } ?: return
        if (soloUna && linea.cantidad > 1) {
            nuevas.cambiarCantidad(clave, -1)
            nuevas.anadir(nueva.copy(cantidad = 1))
        } else {
            nuevas.sustituir(clave, nueva)
        }
    }

    /** Cambia lo que pide el cliente en una línea por enviar; con [soloUna], se separa una unidad con ese texto. */
    fun editarNota(clave: String, notas: String, soloUna: Boolean) {
        val linea = nuevas.firstOrNull { it.clave == clave } ?: return
        editarLinea(clave, linea.copy(notas = notas.trim()), soloUna)
    }

    private fun actualizarPedido(pedido: Pedido) {
        val mesa = codigoMesa(pedido.mesaNumero)
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
                    actualizarPedido(api.crearPedido("mesa", mesa, notas, nuevas.toList(), origen = "camarero"))
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

    fun editarNotaEnviada(pedido: Pedido, item: ItemPedido, notas: String) =
        accion("✓ Cambio guardado en PedIA. Avisa a cocina: no se vuelve a imprimir") {
            api.cambiarNotasItem(pedido.id, item.id, notas.trim())
        }

    fun cobrarEnEfectivo(pedido: Pedido) =
        accion("✓ Mesa ${pedido.mesaNumero} cobrada en efectivo") { api.marcarPagado(pedido.id) }

    fun cerrarComanda(pedido: Pedido) =
        accion("✓ Comanda de la mesa ${pedido.mesaNumero} cerrada: ticket impreso en PedIA", cerrar = true) {
            api.cerrarComanda(pedido.id)
            null
        }

    fun liberarMesa(pedido: Pedido) =
        accion("✓ Mesa ${pedido.mesaNumero} libre", cerrar = true) { api.liberarMesa(pedido.id) }

    fun cambiarDeMesa(pedido: Pedido, nueva: String) = accion("✓ Pedido pasado a la mesa $nueva", cerrar = true) {
        api.cambiarMesa(pedido.id, nueva)
    }
}
