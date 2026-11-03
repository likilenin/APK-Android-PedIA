package com.pedia.movil.impresion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.ErrorPedia
import com.pedia.movil.datos.ItemPedido
import com.pedia.movil.datos.Pedido
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class TipoImpresora { NINGUNA, RED, BLUETOOTH, PDF }

data class ImpresoraBluetooth(val nombre: String, val direccion: String)

// Dónde sale el ticket del kiosko: impresora térmica por red (ESC/POS, puerto 9100), impresora
// térmica Bluetooth ya emparejada en Android, o un PDF en Descargas/PedIA.
object Impresora {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Imprime el ticket donde diga Ajustes. Devuelve un texto corto con el resultado. */
    suspend fun imprimir(contexto: Context, ajustes: Ajustes, ticket: TicketPedia): String =
        when (ajustes.impresoraTipo) {
            TipoImpresora.NINGUNA -> throw ErrorPedia("No hay impresora configurada (Ajustes > Impresora de tickets).")
            TipoImpresora.RED -> {
                if (ajustes.impresoraIp.isBlank()) throw ErrorPedia("Falta la IP de la impresora de red.")
                enviarRed(ajustes.impresoraIp, ajustes.impresoraPuerto, EscPosPedia.desde(ticket))
                "Ticket enviado a ${ajustes.impresoraIp}"
            }
            TipoImpresora.BLUETOOTH -> {
                if (ajustes.bluetoothDireccion.isBlank()) throw ErrorPedia("Falta elegir la impresora Bluetooth.")
                enviarBluetooth(contexto, ajustes.bluetoothDireccion, EscPosPedia.desde(ticket))
                "Ticket enviado a ${ajustes.bluetoothNombre.ifBlank { ajustes.bluetoothDireccion }}"
            }
            TipoImpresora.PDF -> {
                val uri = guardarPdf(contexto, ticket, ajustes.anchoPapel)
                ajustes.ultimoPdf = uri.toString()
                "Ticket guardado en PDF (Descargas/PedIA)"
            }
        }

    private suspend fun enviarRed(ip: String, puerto: Int, datos: ByteArray) = withContext(Dispatchers.IO) {
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

    fun tienePermisoBluetooth(contexto: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Impresoras (y demás aparatos) ya emparejados en los ajustes de Bluetooth de Android. */
    @SuppressLint("MissingPermission")
    fun bluetoothEmparejadas(contexto: Context): List<ImpresoraBluetooth> {
        if (!tienePermisoBluetooth(contexto)) throw ErrorPedia("Falta el permiso de Bluetooth.")
        val adaptador = contexto.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw ErrorPedia("Este dispositivo no tiene Bluetooth.")
        if (!adaptador.isEnabled) throw ErrorPedia("El Bluetooth está apagado. Enciéndelo en los ajustes de Android.")
        return adaptador.bondedDevices.map { ImpresoraBluetooth(it.name ?: it.address, it.address) }.sortedBy { it.nombre }
    }

    @SuppressLint("MissingPermission")
    private suspend fun enviarBluetooth(contexto: Context, direccion: String, datos: ByteArray) = withContext(Dispatchers.IO) {
        if (!tienePermisoBluetooth(contexto)) throw ErrorPedia("Falta el permiso de Bluetooth (Ajustes > Impresora de tickets).")
        val adaptador = contexto.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw ErrorPedia("Este dispositivo no tiene Bluetooth.")
        if (!adaptador.isEnabled) throw ErrorPedia("El Bluetooth está apagado.")
        try {
            adaptador.cancelDiscovery()
            adaptador.getRemoteDevice(direccion).createRfcommSocketToServiceRecord(SPP).use { socket ->
                socket.connect()
                socket.outputStream.write(datos)
                socket.outputStream.flush()
                // Algunas impresoras pierden el final si se corta la conexión enseguida.
                delay(400)
            }
        } catch (e: IOException) {
            throw ErrorPedia("No se puede conectar con la impresora Bluetooth. ¿Está encendida y cerca?")
        }
    }

    /** Guarda el ticket en PDF: en Descargas/PedIA (Android 10+) o en la carpeta de la app (8 y 9). */
    private suspend fun guardarPdf(contexto: Context, ticket: TicketPedia, anchoMm: Int): Uri = withContext(Dispatchers.IO) {
        val nombre = "ticket-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val valores = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, nombre)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PedIA")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contexto.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                    ?: throw IOException("Android no deja crear el archivo")
                resolver.openOutputStream(uri)!!.use { PdfTicket.escribir(ticket, anchoMm, it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                uri
            } else {
                val carpeta = File(contexto.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Tickets").apply { mkdirs() }
                val archivo = File(carpeta, nombre)
                archivo.outputStream().use { PdfTicket.escribir(ticket, anchoMm, it) }
                Uri.fromFile(archivo)
            }
        } catch (e: IOException) {
            throw ErrorPedia("No se ha podido guardar el PDF: ${e.message}")
        }
    }

    /** Ticket de ejemplo con el diseño de PedIA, para probar la impresora desde Ajustes. */
    fun ticketPrueba(nombreLocal: String): TicketPedia {
        val ahoraUtc = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val pedido = Pedido(
            id = 0,
            tipo = "recogida",
            estado = "pendiente",
            estadoPago = "pagado",
            total = 1700,
            numeroDia = 0,
            creadoEn = ahoraUtc,
            items = listOf(
                ItemPedido(1, 1, 2, 550, "sin cebolla, poco hecha", "Prueba: bocadillo"),
                ItemPedido(2, 2, 1, 600, null, "Prueba: menú con tildes y eñes (áéíóú ñ)"),
            ),
        )
        return PlantillaPedia.construir(pedido, nombreLocal.ifBlank { "PRUEBA DE IMPRESIÓN" }, 10.0, null)
    }
}
