package com.pedia.movil.datos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Una carta cargada de PedIA, con lo que haya pasado al pedirla. */
class EstadoCarta {
    var carta by mutableStateOf<Carta?>(null)
    var error by mutableStateOf<String?>(null)
    var cargando by mutableStateOf(false)
    internal var peticion = 0
}

/**
 * PedIA tiene una carta por canal de venta (qué se enseña y se puede pedir en cada sitio). La app usa dos, cada
 * una por separado: la del kiosko (lo que ven los clientes) y la del local (lo que pide el personal, canal «barra»).
 * Se cargan nada más arrancar la app y vuelven a pedirse al volver a ella.
 */
object Cartas {
    const val CANAL_KIOSCO = "kiosco"
    const val CANAL_LOCAL = "barra"

    val kiosco = EstadoCarta()
    val local = EstadoCarta()

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun cargarKiosco(ajustes: Ajustes, forzar: Boolean = false) = cargar(kiosco, ajustes, CANAL_KIOSCO, forzar)

    fun cargarLocal(ajustes: Ajustes, forzar: Boolean = false) = cargar(local, ajustes, CANAL_LOCAL, forzar)

    fun cargarTodas(ajustes: Ajustes, forzar: Boolean = false) {
        cargarKiosco(ajustes, forzar)
        cargarLocal(ajustes, forzar)
    }

    /** Al cambiar de local, lo cargado del anterior ya no vale. */
    fun olvidar() {
        kiosco.carta = null
        local.carta = null
    }

    private fun cargar(estado: EstadoCarta, ajustes: Ajustes, canal: String, forzar: Boolean) {
        if (ajustes.servidorIp.isBlank() || (estado.cargando && !forzar)) return
        val numero = ++estado.peticion
        estado.cargando = true
        alcance.launch {
            try {
                val nueva = ajustes.api().cargarCarta(canal)
                if (numero == estado.peticion) {
                    estado.carta = nueva
                    estado.error = null
                }
            } catch (e: Exception) {
                if (numero == estado.peticion) estado.error = e.mensajeUsuario()
            } finally {
                if (numero == estado.peticion) estado.cargando = false
            }
        }
    }
}
