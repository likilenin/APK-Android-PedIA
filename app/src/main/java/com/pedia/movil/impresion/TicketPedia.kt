package com.pedia.movil.impresion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.pedia.movil.datos.Pedido
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Ticket del cliente con el diseño de PedIA: el texto de la plantilla ya con los datos, conservando
 * los marcadores [CENTRAR] [/CENTRAR] [NEGRITA] [/NEGRITA] y {{qr}} (donde va el QR de cobro).
 * [escpos] son los bytes exactos que daría PedIA, cuando los da ella.
 */
class TicketPedia(val texto: String, val qrUrl: String?, val escpos: ByteArray? = null)

// Copia de server/services/plantillaTicket.js de PedIA (plantilla por defecto y cálculo de datos).
// Se usa cuando PedIA todavía no ofrece GET /api/pedidos/:id/ticket; si lo ofrece, se usa su texto
// (que respeta la plantilla editada en el PC).
object PlantillaPedia {
    const val POR_DEFECTO = """[CENTRAR]================================
[NEGRITA]{{local}}[/NEGRITA]
[CENTRAR]===============================
Fecha: {{fecha}}
Hora: {{hora}}

Nº pedido: {{numero_pedido}}
Cliente: {{cliente}}
Telefono: {{telefono}}
Tipo: {{tipo}}
Direccion: {{direccion}}

------------------------------------------
[NEGRITA]PEDIDO:[/NEGRITA]
{{items}}

------------------------------------------
Base imponible: {{subtotal}}
IVA ({{iva_porcentaje}}%): {{iva}}
[NEGRITA]TOTAL: {{total}}[/NEGRITA]
================================

[CENTRAR]{{qr}}[/CENTRAR]

[CENTRAR]Gracias por su pedido
"""

    private const val PRODUCTO_VOZ = "Pedido telefónico (automático)"
    private val formatoFecha = DateTimeFormatter.ofPattern("d/M/yyyy")
    private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

    fun construir(pedido: Pedido, nombreLocal: String, ivaGeneral: Double, qrUrl: String?): TicketPedia {
        val texto = sustituir(POR_DEFECTO, datos(pedido, nombreLocal, ivaGeneral))
        return TicketPedia(texto, qrUrl)
    }

    /** "5,50 EUR", como formatearPrecio de PedIA. */
    fun precio(centimos: Long): String {
        val abs = kotlin.math.abs(centimos)
        return (if (centimos < 0) "-" else "") + "${abs / 100},${(abs % 100).toString().padStart(2, '0')} EUR"
    }

    /** Número como lo escribe JavaScript: 10 → "10", 10.5 → "10.5". */
    private fun numero(valor: Double): String =
        if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()

    private fun modificaciones(notas: String?): String =
        notas.orEmpty().split(Regex(",\\s*")).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") { " ($it)" }

    private fun tipo(pedido: Pedido) = when (pedido.tipo) {
        "mesa" -> pedido.mesaNumero?.let { "MESA $it" } ?: "LOCAL"
        "recogida" -> "RECOGIDA"
        else -> "DELIVERY"
    }

    private fun datos(pedido: Pedido, nombreLocal: String, ivaGeneral: Double): Map<String, String> {
        // created_at viene en UTC (SQLite); el ticket enseña la hora local.
        val fecha = runCatching {
            LocalDateTime.parse(pedido.creadoEn.trim().replace(' ', 'T')).atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }.getOrElse { LocalDateTime.now() }

        // Cada línea con el IVA propio del producto o, si no tiene, el general (igual que PedIA).
        val grupos = linkedMapOf<Double, DoubleArray>()
        for (item in pedido.items) {
            val tasa = item.productoIvaPorcentaje ?: ivaGeneral
            val total = (item.precioUnitario * item.cantidad).toDouble()
            val base = total / (1 + tasa / 100)
            val g = grupos.getOrPut(tasa) { DoubleArray(2) }
            g[0] += base
            g[1] += total - base
        }
        val base = grupos.values.sumOf { it[0] }
        val iva = grupos.values.sumOf { it[1] }
        val tasas = grupos.keys.toList()
        val ivaMostrado = when (tasas.size) {
            0 -> numero(ivaGeneral)
            1 -> numero(tasas[0])
            else -> "varios"
        }
        val desglose = if (tasas.size <= 1) "" else tasas.sorted().joinToString("\n") { t ->
            val g = grupos.getValue(t)
            "Base ${numero(t)}%: ${precio(g[0].roundToLong())} - IVA ${numero(t)}%: ${precio(g[1].roundToLong())}"
        }
        val items = pedido.items.joinToString("\n") { item ->
            if (item.productoNombre == PRODUCTO_VOZ) "x${item.cantidad} ${item.notas ?: PRODUCTO_VOZ}"
            else "x${item.cantidad} ${item.productoNombre ?: "Producto"}${modificaciones(item.notas)}"
        }
        return mapOf(
            "local" to nombreLocal,
            "fecha" to fecha.format(formatoFecha),
            "hora" to fecha.format(formatoHora),
            "numero_pedido" to (pedido.numeroDia ?: pedido.id).toString(),
            "cliente" to "-",
            "telefono" to "-",
            "tipo" to tipo(pedido),
            "direccion" to "-",
            "items" to items,
            "subtotal" to precio(base.roundToLong()),
            "iva_porcentaje" to ivaMostrado,
            "desglose_iva" to desglose,
            "iva" to precio(iva.roundToLong()),
            "total" to precio(pedido.total),
        )
    }

    // {{qr}} no se sustituye: es "aquí va el QR".
    private fun sustituir(plantilla: String, datos: Map<String, String>): String {
        var texto = plantilla
        // iva_porcentaje antes que iva (como en PedIA), para que {{iva}} no toque {{iva_porcentaje}}.
        for (clave in listOf("local", "fecha", "hora", "numero_pedido", "cliente", "telefono", "tipo", "direccion",
            "items", "subtotal", "iva_porcentaje", "desglose_iva", "iva", "total")) {
            texto = texto.replace(Regex("\\{\\{\\s*$clave\\s*\\}\\}"), Regex.escapeReplacement(datos.getValue(clave)))
        }
        return texto
    }
}

/** Trozos del ticket: texto, o un cambio de formato, o el sitio del QR. */
private val TOKEN = Regex("(\\[CENTRAR\\]|\\[/CENTRAR\\]|\\[NEGRITA\\]|\\[/NEGRITA\\]|\\[GRANDE\\]|\\[/GRANDE\\]|\\{\\{\\s*qr\\s*\\}\\})")

private fun trozos(texto: String): List<String> {
    val resultado = mutableListOf<String>()
    var desde = 0
    for (m in TOKEN.findAll(texto)) {
        if (m.range.first > desde) resultado += texto.substring(desde, m.range.first)
        resultado += m.value
        desde = m.range.last + 1
    }
    if (desde < texto.length) resultado += texto.substring(desde)
    return resultado
}

private fun esQr(trozo: String) = Regex("^\\{\\{\\s*qr\\s*\\}\\}$").matches(trozo)

// Mismos comandos que server/services/escpos.js de PedIA.
object EscPosPedia {
    private const val ESC = 0x1B
    private const val GS = 0x1D

    fun desde(ticket: TicketPedia): ByteArray = ticket.escpos ?: construir(ticket.texto, ticket.qrUrl)

    fun construir(texto: String, qrUrl: String?): ByteArray {
        val salida = ByteArrayOutputStream()
        fun bytes(vararg b: Int) = salida.write(ByteArray(b.size) { b[it].toByte() })
        bytes(ESC, 0x40) // iniciar
        bytes(ESC, 0x74, 0x02) // página de códigos CP850
        for (trozo in trozos(texto)) {
            when {
                trozo == "[CENTRAR]" -> bytes(ESC, 0x61, 1)
                trozo == "[/CENTRAR]" -> bytes(ESC, 0x61, 0)
                trozo == "[NEGRITA]" -> bytes(ESC, 0x45, 1)
                trozo == "[/NEGRITA]" -> bytes(ESC, 0x45, 0)
                trozo == "[GRANDE]" -> bytes(GS, 0x21, 0x11)
                trozo == "[/GRANDE]" -> bytes(GS, 0x21, 0x00)
                esQr(trozo) -> if (qrUrl != null) salida.write(qr(qrUrl))
                else -> salida.write(Cp850.codificar(trozo))
            }
        }
        bytes(ESC, 0x64, 4) // avanzar 4 líneas
        bytes(GS, 0x56, 0) // corte total
        return salida.toByteArray()
    }

    /** QR nativo de la impresora (GS ( k, modelo 2, módulo 6, corrección M), como PedIA. */
    private fun qr(datos: String): ByteArray {
        val contenido = datos.toByteArray(Charsets.UTF_8)
        val longitud = contenido.size + 3
        val salida = ByteArrayOutputStream()
        fun bytes(vararg b: Int) = salida.write(ByteArray(b.size) { b[it].toByte() })
        bytes(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)
        bytes(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 6)
        bytes(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 49)
        bytes(GS, 0x28, 0x6B, longitud and 0xFF, (longitud shr 8) and 0xFF, 0x31, 0x50, 0x30)
        salida.write(contenido)
        bytes(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)
        return salida.toByteArray()
    }
}

/** CP850 (lo que usa PedIA): ASCII tal cual y las letras del español en su posición; lo demás, "?". */
object Cp850 {
    private val tabla = mapOf(
        'Ç' to 0x80, 'ü' to 0x81, 'é' to 0x82, 'â' to 0x83, 'ä' to 0x84, 'à' to 0x85, 'ç' to 0x87, 'ê' to 0x88,
        'ë' to 0x89, 'è' to 0x8A, 'ï' to 0x8B, 'î' to 0x8C, 'ì' to 0x8D, 'Ä' to 0x8E, 'É' to 0x90, 'ô' to 0x93,
        'ö' to 0x94, 'ò' to 0x95, 'û' to 0x96, 'ù' to 0x97, 'Ö' to 0x99, 'Ü' to 0x9A, 'á' to 0xA0, 'í' to 0xA1,
        'ó' to 0xA2, 'ú' to 0xA3, 'ñ' to 0xA4, 'Ñ' to 0xA5, 'ª' to 0xA6, 'º' to 0xA7, '¿' to 0xA8, '¡' to 0xAD,
        'Á' to 0xB5, 'Â' to 0xB6, 'À' to 0xB7, 'Í' to 0xD6, 'Ó' to 0xE0, 'Ú' to 0xE9, '·' to 0xFA,
    )

    fun codificar(texto: String): ByteArray {
        val salida = ByteArrayOutputStream()
        for (c in texto.replace(' ', ' ').replace(' ', ' ')) {
            salida.write(
                when {
                    c.code < 0x80 -> c.code
                    else -> tabla[c] ?: '?'.code
                }
            )
        }
        return salida.toByteArray()
    }
}

/** Imagen de un código QR (en blanco y negro). */
fun bitmapQr(datos: String, lado: Int): Bitmap {
    val matriz = QRCodeWriter().encode(datos, BarcodeFormat.QR_CODE, lado, lado, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.RGB_565)
    for (y in 0 until lado) for (x in 0 until lado) bmp.setPixel(x, y, if (matriz[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    return bmp
}

// El mismo ticket en PDF, con el aspecto del papel térmico: letra de ancho fijo, centrado,
// negritas y el QR donde lo pone la plantilla.
object PdfTicket {
    private class Linea(val texto: String, val centrada: Boolean, val negrita: Boolean, val grande: Boolean = false, val qr: Boolean = false)

    fun escribir(ticket: TicketPedia, anchoPapelMm: Int, salida: OutputStream) {
        val columnas = if (anchoPapelMm == 58) 32 else 48
        val anchoPt = anchoPapelMm / 25.4f * 72f
        val margen = 8f
        val anchoUtil = anchoPt - 2 * margen
        val tamanoLetra = anchoUtil / columnas / 0.6f
        val altoLinea = tamanoLetra * 1.25f
        val ladoQr = minOf(anchoUtil * 0.62f, 140f)

        // Recorre la plantilla igual que la impresora: el centrado y la negrita siguen activos hasta
        // que otro marcador los cambia.
        val lineas = mutableListOf<Linea>()
        var centrado = false
        var negrita = false
        val actual = StringBuilder()
        var negritaEnLinea = false
        var grande = false
        var grandeEnLinea = false
        fun cerrarLinea() {
            val texto = actual.toString()
            // Las líneas largas se parten como lo haría la impresora (la letra grande ocupa el doble).
            if (texto.isEmpty()) lineas += Linea("", centrado, negritaEnLinea)
            else texto.chunked(if (grandeEnLinea) columnas / 2 else columnas).forEach { lineas += Linea(it, centrado, negritaEnLinea, grandeEnLinea) }
            actual.clear()
            negritaEnLinea = negrita
            grandeEnLinea = grande
        }
        for (trozo in trozos(ticket.texto)) {
            when {
                trozo == "[CENTRAR]" -> centrado = true
                trozo == "[/CENTRAR]" -> centrado = false
                trozo == "[NEGRITA]" -> { negrita = true; negritaEnLinea = true }
                trozo == "[/NEGRITA]" -> negrita = false
                trozo == "[GRANDE]" -> { grande = true; grandeEnLinea = true }
                trozo == "[/GRANDE]" -> grande = false
                esQr(trozo) -> if (ticket.qrUrl != null) {
                    if (actual.isNotEmpty()) cerrarLinea()
                    lineas += Linea("", true, false, qr = true)
                }
                else -> {
                    val partes = trozo.split('\n')
                    partes.forEachIndexed { i, parte ->
                        actual.append(parte)
                        if (i < partes.size - 1) cerrarLinea()
                    }
                }
            }
        }
        if (actual.isNotEmpty()) cerrarLinea()

        val alto = 2 * margen + lineas.sumOf { (if (it.qr) ladoQr + altoLinea else if (it.grande) altoLinea * 2 else altoLinea).toDouble() }.toFloat() + altoLinea * 3
        val documento = PdfDocument()
        val pagina = documento.startPage(PdfDocument.PageInfo.Builder(anchoPt.toInt() + 1, alto.toInt() + 1, 1).create())
        val lienzo: Canvas = pagina.canvas
        val normal = Paint().apply { typeface = Typeface.MONOSPACE; textSize = tamanoLetra; color = 0xFF000000.toInt(); isAntiAlias = true }
        val gruesa = Paint(normal).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val grandeNormal = Paint(normal).apply { textSize = tamanoLetra * 2 }
        val grandeGruesa = Paint(gruesa).apply { textSize = tamanoLetra * 2 }
        var y = margen + altoLinea
        for (linea in lineas) {
            if (linea.qr) {
                val qr = bitmapQr(ticket.qrUrl!!, 300)
                val x = (anchoPt - ladoQr) / 2
                lienzo.drawBitmap(qr, null, android.graphics.RectF(x, y - altoLinea / 2, x + ladoQr, y - altoLinea / 2 + ladoQr), null)
                y += ladoQr + altoLinea
                continue
            }
            val pintura = when {
                linea.grande && linea.negrita -> grandeGruesa
                linea.grande -> grandeNormal
                linea.negrita -> gruesa
                else -> normal
            }
            val ancho = pintura.measureText(linea.texto)
            val x = if (linea.centrada) (anchoPt - ancho) / 2 else margen
            if (linea.grande) y += altoLinea
            lienzo.drawText(linea.texto, x, y, pintura)
            y += altoLinea
        }
        documento.finishPage(pagina)
        documento.writeTo(salida)
        documento.close()
    }
}
