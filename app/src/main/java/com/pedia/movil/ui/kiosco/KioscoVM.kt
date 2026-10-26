package com.pedia.movil.ui.kiosco

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.Pedido
import com.pedia.movil.datos.Producto
import com.pedia.movil.datos.anadir
import com.pedia.movil.datos.cambiarCantidad
import com.pedia.movil.datos.mensajeUsuario
import com.pedia.movil.impresion.Impresora
import com.pedia.movil.impresion.Tickets
import kotlinx.coroutines.launch

enum class PasoKiosco { BIENVENIDA, CARTA, RESUMEN, ENVIANDO, CONFIRMADO }

enum class EstadoImpresion { SIN_IMPRESORA, IMPRIMIENDO, IMPRESO, ERROR }

class KioscoVM(private val ajustes: Ajustes) : ViewModel() {
    var carta by mutableStateOf<Carta?>(null)
        private set
    var errorCarta by mutableStateOf<String?>(null)
        private set
    var paso by mutableStateOf(PasoKiosco.BIENVENIDA)
        private set
    var paraLlevar by mutableStateOf(false)
        private set
    var categoria by mutableStateOf<Long?>(null)
    val lineas = mutableStateListOf<LineaCarrito>()
    var errorEnvio by mutableStateOf<String?>(null)
    var pedido by mutableStateOf<Pedido?>(null)
        private set
    var impresion by mutableStateOf(EstadoImpresion.SIN_IMPRESORA)
        private set

    val total get() = lineas.sumOf { it.subtotal }
    val unidades get() = lineas.sumOf { it.cantidad }

    init {
        recargarCarta()
    }

    // La carta se vuelve a pedir a PedIA con cada cliente: así un producto que se
    // marca como agotado en el TPV desaparece del kiosko enseguida.
    fun recargarCarta() {
        viewModelScope.launch {
            try {
                val nueva = ajustes.api().cargarCarta()
                carta = nueva
                errorCarta = null
                if (nueva.categorias.none { it.id == categoria }) categoria = nueva.categorias.firstOrNull()?.id
            } catch (e: Exception) {
                errorCarta = e.mensajeUsuario()
            }
        }
    }

    fun empezar(llevar: Boolean) {
        paraLlevar = llevar
        lineas.clear()
        errorEnvio = null
        categoria = carta?.categorias?.firstOrNull()?.id
        paso = PasoKiosco.CARTA
        recargarCarta()
    }

    fun anadir(producto: Producto, cantidad: Int, notas: String) = lineas.anadir(LineaCarrito(producto, cantidad, notas))

    fun cambiarCantidad(clave: String, delta: Int) {
        lineas.cambiarCantidad(clave, delta)
        if (lineas.isEmpty() && paso == PasoKiosco.RESUMEN) paso = PasoKiosco.CARTA
    }

    fun verResumen() {
        if (lineas.isNotEmpty()) paso = PasoKiosco.RESUMEN
    }

    fun seguirPidiendo() {
        paso = PasoKiosco.CARTA
    }

    fun cancelar() {
        lineas.clear()
        errorEnvio = null
        paso = PasoKiosco.BIENVENIDA
        recargarCarta()
    }

    fun confirmar() {
        if (lineas.isEmpty() || paso == PasoKiosco.ENVIANDO) return
        paso = PasoKiosco.ENVIANDO
        errorEnvio = null
        viewModelScope.launch {
            try {
                // En PedIA entra como pedido "para recoger" en el tablero de cocina; la nota
                // indica que viene del kiosko y si es para tomar aquí o para llevar.
                val nota = "KIOSKO · " + if (paraLlevar) "Para llevar" else "Para tomar aquí"
                val creado = ajustes.api().crearPedido("recogida", null, nota, lineas.toList())
                pedido = creado
                lineas.clear()
                paso = PasoKiosco.CONFIRMADO
                imprimir()
            } catch (e: Exception) {
                errorEnvio = e.mensajeUsuario()
                paso = PasoKiosco.RESUMEN
            }
        }
    }

    fun imprimir() {
        val actual = pedido ?: return
        if (ajustes.impresoraIp.isBlank()) {
            impresion = EstadoImpresion.SIN_IMPRESORA
            return
        }
        impresion = EstadoImpresion.IMPRIMIENDO
        viewModelScope.launch {
            impresion = try {
                Impresora.imprimir(ajustes, Tickets.kiosco(ajustes, actual, carta?.nombreLocal ?: "PedIA", paraLlevar))
                EstadoImpresion.IMPRESO
            } catch (e: Exception) {
                EstadoImpresion.ERROR
            }
        }
    }

    fun terminar() {
        pedido = null
        impresion = EstadoImpresion.SIN_IMPRESORA
        paso = PasoKiosco.BIENVENIDA
        recargarCarta()
    }
}
