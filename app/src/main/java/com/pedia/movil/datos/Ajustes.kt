package com.pedia.movil.datos

import android.content.Context
import com.pedia.movil.BuildConfig
import com.pedia.movil.impresion.TipoImpresora

enum class Modo { KIOSCO, CAMARERO }

const val PIN_INICIAL = "1234"

// Ajustes de este dispositivo (no se comparten con PedIA): dónde está PedIA en la wifi,
// en qué modo arranca la app, la impresora del kiosko y el PIN que protege los ajustes.
class Ajustes(contexto: Context) {
    private val prefs = contexto.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private fun texto(clave: String, defecto: String = "") = prefs.getString(clave, defecto) ?: defecto
    private fun guardarTexto(clave: String, valor: String) = prefs.edit().putString(clave, valor).apply()
    private fun guardarEntero(clave: String, valor: Int) = prefs.edit().putInt(clave, valor).apply()
    private fun guardarSiNo(clave: String, valor: Boolean) = prefs.edit().putBoolean(clave, valor).apply()

    var servidorIp: String
        get() = texto("servidor_ip")
        set(v) = guardarTexto("servidor_ip", v.trim())

    var servidorPuerto: Int
        get() = prefs.getInt("servidor_puerto", 4000)
        set(v) = guardarEntero("servidor_puerto", v)

    var modo: Modo?
        get() = Modo.entries.firstOrNull { it.name == texto("modo") }
        set(v) = guardarTexto("modo", v?.name ?: "")

    var pin: String
        get() = texto("pin", PIN_INICIAL)
        set(v) = guardarTexto("pin", v)

    var nombreCamarero: String
        get() = texto("nombre_camarero")
        set(v) = guardarTexto("nombre_camarero", v.trim())

    var impresoraIp: String
        get() = texto("impresora_ip")
        set(v) = guardarTexto("impresora_ip", v.trim())

    var impresoraPuerto: Int
        get() = prefs.getInt("impresora_puerto", 9100)
        set(v) = guardarEntero("impresora_puerto", v)

    /** Ancho del papel en mm: 58 u 80. */
    var anchoPapel: Int
        get() = prefs.getInt("ancho_papel", 80)
        set(v) = guardarEntero("ancho_papel", v)

    /** Dónde sale el ticket. Si nunca se ha elegido, red si ya había IP de impresora (versiones anteriores). */
    var impresoraTipo: TipoImpresora
        get() = TipoImpresora.entries.firstOrNull { it.name == texto("impresora_tipo") }
            ?: if (impresoraIp.isNotBlank()) TipoImpresora.RED else TipoImpresora.NINGUNA
        set(v) = guardarTexto("impresora_tipo", v.name)

    var bluetoothDireccion: String
        get() = texto("bluetooth_direccion")
        set(v) = guardarTexto("bluetooth_direccion", v)

    var bluetoothNombre: String
        get() = texto("bluetooth_nombre")
        set(v) = guardarTexto("bluetooth_nombre", v)

    /** Imprimir el ticket del cliente al hacer el pedido en el kiosko. */
    var imprimirAlPedir: Boolean
        get() = prefs.getBoolean("imprimir_al_pedir", true)
        set(v) = guardarSiNo("imprimir_al_pedir", v)

    /** Último ticket guardado en PDF (para abrirlo desde Ajustes). */
    var ultimoPdf: String
        get() = texto("ultimo_pdf")
        set(v) = guardarTexto("ultimo_pdf", v)

    // Formas de pago que ofrece el kiosko al terminar el pedido.
    var pagoTarjeta: Boolean
        get() = prefs.getBoolean("pago_tarjeta", true)
        set(v) = guardarSiNo("pago_tarjeta", v)

    var pagoMovil: Boolean
        get() = prefs.getBoolean("pago_movil", true)
        set(v) = guardarSiNo("pago_movil", v)

    var pagoCaja: Boolean
        get() = prefs.getBoolean("pago_caja", true)
        set(v) = guardarSiNo("pago_caja", v)

    // Desactivado por defecto: mientras se prueba, la fijación de Android bloquea la tablet
    // y su aviso tapa la app. Se activa en Ajustes cuando la tablet va al local.
    var kioscoFijarPantalla: Boolean
        get() = prefs.getBoolean("kiosco_fijar_pantalla", false)
        set(v) = guardarSiNo("kiosco_fijar_pantalla", v)

    /** Segundos sin tocar la pantalla del kiosko antes de preguntar "¿Sigues ahí?". */
    var kioscoInactividadSeg: Int
        get() = prefs.getInt("kiosco_inactividad", 60)
        set(v) = guardarEntero("kiosco_inactividad", v)

    var repoActualizaciones: String
        get() = texto("repo_actualizaciones", BuildConfig.REPO_ACTUALIZACIONES)
        set(v) = guardarTexto("repo_actualizaciones", v.trim())

    val servidorConfigurado get() = servidorIp.isNotBlank()
    val urlBase get() = "http://$servidorIp:$servidorPuerto"

    fun api() = PediaApi(urlBase)
}
