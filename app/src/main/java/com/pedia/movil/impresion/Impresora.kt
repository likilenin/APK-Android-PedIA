package com.pedia.movil.impresion

import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.ErrorPedia
import com.pedia.movil.datos.Pedido
import com.pedia.movil.ui.euros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Impresora térmica de tickets por red (ESC/POS en el puerto 9100, el estándar de casi
// todas las impresoras de tickets con conexión de red o wifi).
object Impresora {
    suspend fun enviar(ip: String, puerto: Int, datos: ByteArray) = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, puerto), 3000)
                socket.soTimeout = 5000
                socket.getOutputStream().apply {
                    write(datos)
                    flush()
                }
            }
        } catch (e: IOException) {
            throw ErrorPedia("No se puede conectar con la impresora $ip:$puerto. ¿Está encendida y en la misma red?")
        }
    }

    suspend fun imprimir(ajustes: Ajustes, datos: ByteArray) {
        if (ajustes.impresoraIp.isBlank()) throw ErrorPedia("No hay impresora configurada (Ajustes > Kiosko).")
        enviar(ajustes.impresoraIp, ajustes.impresoraPuerto, datos)
    }
}

/** Construye un ticket ESC/POS con la página de códigos Windows-1252 (tildes, ñ y €). */
class TicketEscPos(private val columnas: Int) {
    private val salida = ByteArrayOutputStream()
    private val juego = Charset.forName("windows-1252")

    init {
        comando(0x1B, 0x40) // inicializar
        comando(0x1B, 0x74, 16) // página de códigos WPC1252
    }

    private fun comando(vararg bytes: Int) = salida.write(ByteArray(bytes.size) { bytes[it].toByte() })

    fun centrado() = apply { comando(0x1B, 0x61, 1) }
    fun izquierda() = apply { comando(0x1B, 0x61, 0) }
    fun negrita(si: Boolean) = apply { comando(0x1B, 0x45, if (si) 1 else 0) }

    /** 1 = normal, 2 = doble, 3 = triple (alto y ancho). */
    fun tamano(veces: Int) = apply {
        val n = (veces - 1).coerceIn(0, 7)
        comando(0x1D, 0x21, (n shl 4) or n)
    }

    fun texto(linea: String) = apply {
        // El formato de euros usa espacios no separables, que algunas impresoras pintan raro.
        salida.write(linea.replace(' ', ' ').replace(' ', ' ').toByteArray(juego))
        salida.write(0x0A)
    }

    fun separador() = texto("-".repeat(columnas))

    /** Texto a la izquierda e importe a la derecha; si no cabe, el texto se parte en varias líneas. */
    fun fila(izquierda: String, derecha: String, cols: Int = columnas) = apply {
        val hueco = (cols - derecha.length - 1).coerceAtLeast(8)
        val trozos = izquierda.chunked(hueco).ifEmpty { listOf("") }
        trozos.dropLast(1).forEach { texto(it) }
        texto(trozos.last().padEnd(hueco) + " " + derecha.padStart(cols - hueco - 1))
    }

    fun avanzar(lineas: Int) = apply { repeat(lineas) { salida.write(0x0A) } }
    fun cortar() = apply { comando(0x1D, 0x56, 66, 0) }

    fun bytes(): ByteArray = salida.toByteArray()
}

object Tickets {
    private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    private fun columnas(ajustes: Ajustes) = if (ajustes.anchoPapel == 58) 32 else 48

    /** Ticket que se lleva el cliente del kiosko: número de pedido bien grande, lo pedido y el total. */
    fun kiosco(ajustes: Ajustes, pedido: Pedido, nombreLocal: String, paraLlevar: Boolean): ByteArray {
        val cols = columnas(ajustes)
        val t = TicketEscPos(cols)
        t.centrado().negrita(true).tamano(2).texto(nombreLocal.take(cols / 2)).tamano(1).negrita(false)
        t.texto(LocalDateTime.now().format(formatoFecha))
        t.separador()
        t.texto("TU NÚMERO DE PEDIDO")
        t.negrita(true).tamano(4).texto((pedido.numeroDia ?: pedido.id).toString()).tamano(1)
        t.texto(if (paraLlevar) "PARA LLEVAR" else "PARA TOMAR AQUÍ").negrita(false)
        t.separador().izquierda()
        pedido.items.forEach { item ->
            t.fila("${item.cantidad} x ${item.productoNombre ?: "Producto"}", euros(item.precioUnitario * item.cantidad))
            item.notas?.takeIf { it.isNotBlank() }?.let { t.texto("   ($it)") }
        }
        t.separador()
        t.negrita(true).tamano(2).fila("TOTAL", euros(pedido.total), cols / 2).tamano(1).negrita(false)
        t.texto("IVA incluido")
        t.estadoPago(pedido)
        t.avanzar(1).centrado()
        ajustes.piePagina.lines().filter { it.isNotBlank() }.forEach { t.texto(it) }
        return t.avanzar(4).cortar().bytes()
    }

    fun prueba(ajustes: Ajustes): ByteArray {
        val t = TicketEscPos(columnas(ajustes))
        t.centrado().negrita(true).tamano(2).texto("PedIA Móvil").tamano(1).negrita(false)
        t.texto("Prueba de impresión").texto(LocalDateTime.now().format(formatoFecha))
        t.separador().izquierda().fila("Tildes y eñes: áéíóú ñ", euros(1250)).separador()
        return t.centrado().texto("Si lees esto bien, la impresora funciona.").avanzar(4).cortar().bytes()
    }

    private fun TicketEscPos.estadoPago(pedido: Pedido) {
        if (pedido.pagado) texto("PAGADO") else texto("PENDIENTE DE PAGO")
    }
}
