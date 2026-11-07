package com.pedia.movil.ui.kiosco

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.ErrorPedia
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.Pedido
import com.pedia.movil.datos.Producto
import com.pedia.movil.datos.anadir
import com.pedia.movil.datos.cambiarCantidad
import com.pedia.movil.datos.mensajeUsuario
import com.pedia.movil.datos.sustituir
import com.pedia.movil.impresion.Impresora
import com.pedia.movil.impresion.PlantillaPedia
import com.pedia.movil.impresion.TicketPedia
import com.pedia.movil.impresion.TipoImpresora
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PasoKiosco { BIENVENIDA, CARTA, RESUMEN, ENVIANDO, PAGO, PAGO_TARJETA, PAGO_MOVIL, CONFIRMADO }

enum class EstadoImpresion { SIN_IMPRESORA, IMPRIMIENDO, IMPRESO, ERROR }

private const val MS_SONDEO_PAGO = 2500L

class KioscoVM(private val ajustes: Ajustes, private val contexto: Context) : ViewModel() {
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

    /** Enlace de cobro de PedIA del pedido en curso (null si PedIA no puede cobrar online). */
    var urlPago by mutableStateOf<String?>(null)
        private set
    var errorPago by mutableStateOf<String?>(null)
        private set
    private var sondeo: Job? = null

    val total get() = lineas.sumOf { it.subtotal }
    val unidades get() = lineas.sumOf { it.cantidad }
    /** Importe a pagar: el del pedido si ya está creado o, si no, el de lo que hay en el carrito. */
    val totalAPagar get() = pedido?.total ?: total
    val pagoTarjeta get() = ajustes.pagoTarjeta
    val pagoMovil get() = ajustes.pagoMovil
    val pagoCaja get() = ajustes.pagoCaja || (!pagoTarjeta && !pagoMovil)

    /** Se está creando el pedido o pidiendo el enlace de cobro tras elegir cómo pagar. */
    var trabajando by mutableStateOf(false)
        private set

    /** Lo que llevaba el pedido ya creado, por si el cliente vuelve atrás para cambiarlo. */
    private var lineasDelPedido = emptyList<LineaCarrito>()

    /** La pasarela de Redsys se abre por la red local (sin pasar por internet ni avisos del túnel). */
    val urlPagoTarjeta get() = pedido?.let { "${ajustes.urlBase}/pago/${it.id}/tarjeta" }

    private val api get() = ajustes.api()

    // La carta se vuelve a pedir a PedIA con cada cliente: así un producto que se
    // marca como agotado en el TPV desaparece del kiosko enseguida.
    fun recargarCarta() {
        viewModelScope.launch {
            try {
                val nueva = api.cargarCarta(canal = "kiosco")
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

    /** Aviso "añadido a tu pedido" que se enseña un momento tras añadir algo (como en los kioskos). */
    var avisoAnadido by mutableStateOf<String?>(null)
    var avisoId by mutableIntStateOf(0)
        private set

    fun anadir(linea: LineaCarrito) {
        lineas.anadir(linea)
        avisoAnadido = "Añadido a tu pedido: " + (if (linea.cantidad > 1) "${linea.cantidad} × " else "") + linea.producto.nombre
        avisoId++
    }

    fun editarLinea(clave: String, nueva: LineaCarrito) = lineas.sustituir(clave, nueva)

    /**
     * "¿Te apetece algo más?": productos disponibles que no están ya en el pedido, primero los
     * de bebidas, postres y complementos (lo que suele ofrecer un autoservicio al final).
     */
    fun sugerencias(): List<Producto> {
        val c = carta ?: return emptyList()
        val enPedido = lineas.map { it.producto.id }.toSet()
        val claves = listOf("bebida", "refresco", "postre", "helado", "patata", "complement", "acompan")
        val preferidas = c.categorias.filter { cat -> claves.any { it in normalizar(cat.nombre) } }.map { it.id }.toSet()
        return c.productos
            .filter { it.estaDisponible && it.id !in enPedido }
            .sortedBy { if (it.categoriaId in preferidas) 0 else 1 }
    }

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

    /**
     * El cliente se va o empieza de nuevo. Un pedido suyo aún sin pagar y con la comanda retenida no ha
     * llegado a cocina: se borra de PedIA para que no quede un pedido fantasma en "Local".
     */
    fun cancelar() {
        val abandonado = pedido
        reiniciar()
        if (abandonado != null && abandonado.cocinaRetenida == 1) {
            viewModelScope.launch {
                val leido = runCatching { api.pedido(abandonado.id) }.getOrNull() ?: return@launch
                if (!leido.pagado) runCatching { api.eliminarPedido(abandonado.id) }
            }
        }
    }

    private fun reiniciar() {
        pararSondeo()
        lineas.clear()
        errorEnvio = null
        pedido = null
        urlPago = null
        errorPago = null
        trabajando = false
        impresion = EstadoImpresion.SIN_IMPRESORA
        paso = PasoKiosco.BIENVENIDA
        recargarCarta()
    }

    // "Finalizar pedido" todavía no crea nada ni imprime: solo lleva a elegir cómo pagar. El pedido se
    // crea al elegirlo y el ticket sale cuando está pagado o cuando el cliente elige pagar en caja.
    fun confirmar() {
        if (lineas.isEmpty() || paso == PasoKiosco.ENVIANDO) return
        errorEnvio = null
        errorPago = null
        if (ajustes.pagoTarjeta || ajustes.pagoMovil) {
            paso = PasoKiosco.PAGO
            return
        }
        // Solo se paga en caja: no hay nada que preguntar.
        paso = PasoKiosco.ENVIANDO
        viewModelScope.launch {
            try {
                crearPedido("efectivo", retenerCocina = false)
                paso = PasoKiosco.CONFIRMADO
                imprimir()
            } catch (e: Exception) {
                errorEnvio = e.mensajeUsuario()
                paso = PasoKiosco.RESUMEN
            }
        }
    }

    // Vuelve a "Mi pedido" para ver o cambiar lo pedido. Si ya se había creado para cobrarlo con
    // tarjeta (y sigue sin pagar), se quita de PedIA y lo pedido vuelve al carrito, para no duplicarlo.
    fun volverAlPedido() {
        if (trabajando) return
        errorPago = null
        val actual = pedido
        if (actual == null) {
            paso = PasoKiosco.RESUMEN
            return
        }
        pararSondeo()
        trabajando = true
        viewModelScope.launch {
            try {
                val leido = api.pedido(actual.id)
                if (leido.pagado) {
                    // Ya lo ha pagado (por ejemplo, con el móvil): no se puede deshacer.
                    pedido = leido
                    paso = PasoKiosco.CONFIRMADO
                    imprimir()
                    return@launch
                }
                if (leido.cocinaRetenida != 1) {
                    // Su comanda ya ha salido en cocina: no se quita, solo queda pagarlo.
                    errorPago = "Tu pedido ya está en cocina y no se puede cambiar. Elige cómo pagarlo."
                    return@launch
                }
                api.eliminarPedido(actual.id)
                restaurarCarrito()
            } catch (e: Exception) {
                // Si PedIA ya no lo tiene (lo han borrado desde el TPV), no hay nada que quitar.
                if (e is ErrorPedia && e.codigoHttp == 404) restaurarCarrito() else errorPago = e.mensajeUsuario()
            } finally {
                trabajando = false
            }
        }
    }

    private fun restaurarCarrito() {
        pedido = null
        urlPago = null
        lineas.clear()
        lineas.addAll(lineasDelPedido)
        paso = PasoKiosco.RESUMEN
    }

    // Va a la pestaña "Local" de PedIA. La nota indica que viene del kiosko y si es para llevar.
    private suspend fun crearPedido(pagoPrevisto: String, retenerCocina: Boolean): Pedido {
        val nota = "KIOSKO · " + if (paraLlevar) "Para llevar" else "Para tomar aquí"
        val creado = api.crearPedido("mesa", null, nota, lineas.toList(), "kiosko", pagoPrevisto, retenerCocina)
        pedido = creado
        lineasDelPedido = lineas.toList()
        lineas.clear()
        return creado
    }

    fun pagarEnCaja() {
        if (trabajando) return
        pararSondeo()
        trabajando = true
        errorPago = null
        viewModelScope.launch {
            try {
                val actual = pedido
                if (actual == null) crearPedido("efectivo", retenerCocina = false)
                else api.liberarCocina(actual.id, "efectivo")
                paso = PasoKiosco.CONFIRMADO
                imprimir()
            } catch (e: Exception) {
                errorPago = e.mensajeUsuario()
            } finally {
                trabajando = false
            }
        }
    }

    fun pagarConTarjeta() = iniciarCobro(PasoKiosco.PAGO_TARJETA)

    fun pagarConMovil() = iniciarCobro(PasoKiosco.PAGO_MOVIL)

    private fun iniciarCobro(destino: PasoKiosco) {
        if (trabajando) return
        trabajando = true
        errorPago = null
        viewModelScope.launch {
            try {
                val actual = pedido ?: crearPedido("tarjeta", retenerCocina = true)
                urlPago = urlPago ?: api.urlPago(actual.id)
                paso = destino
                vigilarPago()
            } catch (e: Exception) {
                errorPago = e.mensajeUsuario()
            } finally {
                trabajando = false
            }
        }
    }

    fun elegirOtraFormaDePago(motivo: String? = null) {
        pararSondeo()
        errorPago = motivo
        paso = PasoKiosco.PAGO
    }

    /** La pasarela ha vuelto a /pago/ok o /pago/ko de PedIA. */
    fun pasarelaTerminada(ok: Boolean) {
        if (!ok) {
            elegirOtraFormaDePago("El pago no se ha completado. Puedes intentarlo de nuevo o elegir otra forma de pago.")
            return
        }
        // Redsys avisa a PedIA por su lado; el sondeo confirmará el cobro en cuanto PedIA lo apunte.
        viewModelScope.launch { comprobarPago() }
    }

    // Pregunta a PedIA cada poco si el pedido ya está pagado (Redsys avisa a PedIA, no a la tablet).
    private fun vigilarPago() {
        pararSondeo()
        sondeo = viewModelScope.launch {
            while (isActive) {
                delay(MS_SONDEO_PAGO)
                comprobarPago()
            }
        }
    }

    private suspend fun comprobarPago() {
        val actual = pedido ?: return
        val leido = runCatching { api.pedido(actual.id) }.getOrNull() ?: return
        if (leido.pagado && paso != PasoKiosco.CONFIRMADO) {
            pararSondeo()
            pedido = leido
            paso = PasoKiosco.CONFIRMADO
            imprimir()
        }
    }

    private fun pararSondeo() {
        sondeo?.cancel()
        sondeo = null
    }

    fun imprimir() {
        val actual = pedido ?: return
        if (ajustes.impresoraTipo == TipoImpresora.NINGUNA || !ajustes.imprimirAlPedir) {
            impresion = EstadoImpresion.SIN_IMPRESORA
            return
        }
        impresion = EstadoImpresion.IMPRIMIENDO
        viewModelScope.launch {
            impresion = try {
                Impresora.imprimir(contexto, ajustes, ticketDe(actual))
                EstadoImpresion.IMPRESO
            } catch (e: Exception) {
                EstadoImpresion.ERROR
            }
        }
    }

    // El ticket del cliente de PedIA: el que da la propia PedIA si ya sabe darlo (respeta su
    // plantilla editada) o, si no, la copia de su plantilla por defecto con el QR de cobro.
    private suspend fun ticketDe(p: Pedido): TicketPedia {
        runCatching { api.ticketDePedia(p.id) }.getOrNull()?.let { remoto ->
            return TicketPedia(remoto.texto, remoto.qrUrl, remoto.escposBase64?.let { Base64.decode(it, Base64.DEFAULT) })
        }
        val qr = if (p.pagado) null else urlPago ?: runCatching { api.urlPago(p.id) }.getOrNull()
        val c = carta
        return PlantillaPedia.construir(p, c?.nombreLocal ?: "", c?.ivaGeneral ?: 10.0, qr)
    }

    fun terminar() = reiniciar()

    override fun onCleared() {
        pararSondeo()
    }
}
