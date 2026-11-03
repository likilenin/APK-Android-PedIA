package com.pedia.movil

import android.app.ActivityManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pedia.movil.ui.FondoOscuro
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Modo
import com.pedia.movil.ui.PantallaActualizacion
import com.pedia.movil.ui.PantallaAjustes
import com.pedia.movil.ui.PantallaInicio
import com.pedia.movil.ui.TemaPedia
import com.pedia.movil.ui.camarero.PantallaCamarero
import com.pedia.movil.ui.kiosco.PantallaKiosco

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Kiosko y comandero: la pantalla no se apaga mientras la app está abierta.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ajustes = Ajustes(applicationContext)
        setContent {
            TemaPedia {
                // Desde Android 15 la app se dibuja bajo las barras del sistema: se deja el
                // margen justo para que nada quede tapado (ni la barra de estado ni la de gestos).
                Box(Modifier.fillMaxSize().background(FondoOscuro).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AppPedia(ajustes, ::modoKiosco)
                }
            }
        }
    }

    /**
     * En el kiosko la app ocupa toda la pantalla (sin barras de Android) y, si está activado
     * en Ajustes, se fija para que los clientes no puedan salir de ella.
     */
    private fun modoKiosco(activo: Boolean, fijar: Boolean) {
        // Solo se pide fijar si no lo está ya: cada petición hace que Android vuelva a sacar su
        // aviso «App is pinned», que tapa la app hasta que alguien lo cierra.
        val fijada = getSystemService(ActivityManager::class.java)?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        runCatching {
            if (activo && fijar && !fijada) startLockTask()
            if (!(activo && fijar) && fijada) stopLockTask()
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (activo) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private enum class Pantalla { ACTUALIZACION, INICIO, AJUSTES, KIOSCO, CAMARERO }

@Composable
private fun AppPedia(ajustes: Ajustes, modoKiosco: (activo: Boolean, fijar: Boolean) -> Unit) {
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.ACTUALIZACION) }
    var volverDeAjustes by rememberSaveable { mutableStateOf(Pantalla.INICIO) }
    var actualizandoDesdeAjustes by rememberSaveable { mutableStateOf(false) }

    fun pantallaDelModo() = when {
        !ajustes.servidorConfigurado -> Pantalla.INICIO
        ajustes.modo == Modo.KIOSCO -> Pantalla.KIOSCO
        ajustes.modo == Modo.CAMARERO -> Pantalla.CAMARERO
        else -> Pantalla.INICIO
    }

    fun abrirAjustesDesde(origen: Pantalla) {
        volverDeAjustes = origen
        pantalla = Pantalla.AJUSTES
    }

    fun cambiarModo() {
        ajustes.modo = null
        pantalla = Pantalla.INICIO
    }

    LaunchedEffect(pantalla) {
        modoKiosco(pantalla == Pantalla.KIOSCO, ajustes.kioscoFijarPantalla)
    }

    when (pantalla) {
        Pantalla.ACTUALIZACION -> PantallaActualizacion(
            ajustes,
            alTerminar = {
                if (actualizandoDesdeAjustes) {
                    actualizandoDesdeAjustes = false
                    pantalla = Pantalla.AJUSTES
                } else {
                    // Cada vez que se abre la app se pregunta en qué modo se va a usar.
                    cambiarModo()
                }
            },
        )
        Pantalla.INICIO -> PantallaInicio(
            ajustes,
            alElegirModo = { modo ->
                ajustes.modo = modo
                // Primera vez: sin PedIA configurada se va directo a Ajustes para buscarla.
                if (!ajustes.servidorConfigurado) abrirAjustesDesde(Pantalla.INICIO) else pantalla = pantallaDelModo()
            },
            alAbrirAjustes = { abrirAjustesDesde(Pantalla.INICIO) },
        )
        Pantalla.AJUSTES -> PantallaAjustes(
            ajustes,
            alVolver = { pantalla = if (volverDeAjustes == Pantalla.INICIO) pantallaDelModo() else volverDeAjustes },
            alActualizar = {
                actualizandoDesdeAjustes = true
                pantalla = Pantalla.ACTUALIZACION
            },
            alCambiarModo = ::cambiarModo,
        )
        Pantalla.KIOSCO -> PantallaKiosco(
            ajustes,
            alAbrirAjustes = { abrirAjustesDesde(Pantalla.KIOSCO) },
            alCambiarModo = ::cambiarModo,
        )
        Pantalla.CAMARERO -> PantallaCamarero(
            ajustes,
            alAbrirAjustes = { abrirAjustesDesde(Pantalla.CAMARERO) },
            alCambiarModo = ::cambiarModo,
        )
    }
}
