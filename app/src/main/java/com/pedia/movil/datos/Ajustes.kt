package com.pedia.movil.datos

import android.content.Context
import com.pedia.movil.BuildConfig

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

    var piePagina: String
        get() = texto("pie_pagina", "¡Gracias por tu pedido!\nPaga en caja indicando tu número.")
        set(v) = guardarTexto("pie_pagina", v)

    var kioscoFijarPantalla: Boolean
        get() = prefs.getBoolean("kiosco_fijar_pantalla", true)
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
