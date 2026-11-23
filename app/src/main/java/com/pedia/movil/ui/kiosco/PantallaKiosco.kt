package com.pedia.movil.ui.kiosco

import androidx.activity.compose.BackHandler
import com.pedia.movil.ui.Tinta
import com.pedia.movil.ui.GrisTexto
import com.pedia.movil.ui.AcentoSuave
import com.pedia.movil.ui.Acento
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedia.movil.R
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.ui.AzulPedia
import com.pedia.movil.ui.DialogoPin
import com.pedia.movil.ui.FondoOscuro
import com.pedia.movil.ui.esMovil
import com.pedia.movil.ui.euros
import kotlinx.coroutines.delay

private const val SEGUNDOS_PANTALLA_GRACIAS = 20
private const val SEGUNDOS_SIGUES_AHI = 20

/** Pasos en los que hay un cliente delante: si deja de tocar la pantalla, se le pregunta si sigue. */
private val PASOS_CON_CLIENTE = setOf(
    PasoKiosco.CARTA, PasoKiosco.RESUMEN, PasoKiosco.PAGO, PasoKiosco.PAGO_TARJETA, PasoKiosco.PAGO_MOVIL,
)

@Composable
fun PantallaKiosco(ajustes: Ajustes, alAbrirAjustes: () -> Unit, alCambiarModo: () -> Unit) {
    val contexto = LocalContext.current.applicationContext
    val vm: KioscoVM = viewModel { KioscoVM(ajustes, contexto) }
    var ultimoToque by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sigueAhi by remember { mutableStateOf(false) }
    var pedirPin by remember { mutableStateOf(false) }
    var menuAdmin by remember { mutableStateOf(false) }

    // Al abrir la app (o volver a ella) se pide de nuevo la carta a PedIA. Con un cliente pidiendo no se toca:
    // se recarga igualmente al empezar el siguiente pedido.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (vm.paso == PasoKiosco.BIENVENIDA) vm.recargarCarta() }

    // En el kiosko el botón "atrás" no saca de la app: como mucho vuelve a la carta.
    BackHandler {
        when (vm.paso) {
            PasoKiosco.RESUMEN -> vm.seguirPidiendo()
            PasoKiosco.PAGO -> vm.volverAlPedido()
            PasoKiosco.PAGO_TARJETA, PasoKiosco.PAGO_MOVIL -> vm.elegirOtraFormaDePago()
            else -> Unit
        }
    }

    // Si el cliente se va a mitad del pedido, se pregunta "¿Sigues ahí?" y luego se reinicia.
    LaunchedEffect(vm.paso) {
        ultimoToque = System.currentTimeMillis()
        while (vm.paso in PASOS_CON_CLIENTE) {
            delay(1000)
            if (!sigueAhi && System.currentTimeMillis() - ultimoToque > ajustes.kioscoInactividadSeg * 1000L) sigueAhi = true
        }
        sigueAhi = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        ultimoToque = System.currentTimeMillis()
                    }
                }
            },
    ) {
        // De una pantalla a otra, un fundido rápido (como en los autoservicios).
        AnimatedContent(
            targetState = if (vm.paso == PasoKiosco.ENVIANDO) PasoKiosco.RESUMEN else vm.paso,
            transitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
            label = "pasoKiosco",
        ) { paso ->
            when (paso) {
                PasoKiosco.BIENVENIDA -> Bienvenida(vm, alAbrirMenu = { pedirPin = true })
                PasoKiosco.CARTA -> CartaKiosco(vm, ajustes)
                PasoKiosco.RESUMEN, PasoKiosco.ENVIANDO -> ResumenKiosco(vm, ajustes)
                PasoKiosco.PAGO -> ElegirPago(vm)
                PasoKiosco.PAGO_TARJETA -> PagoConTarjeta(vm)
                PasoKiosco.PAGO_MOVIL -> PagoConMovil(vm)
                PasoKiosco.CONFIRMADO -> Confirmado(vm)
            }
        }
        Procesando(visible = vm.paso == PasoKiosco.ENVIANDO || (vm.paso == PasoKiosco.PAGO && vm.trabajando))
        Destello(vm.destello)
    }

    if (sigueAhi) {
        var quedan by remember { mutableIntStateOf(SEGUNDOS_SIGUES_AHI) }
        LaunchedEffect(Unit) {
            while (quedan > 0) {
                delay(1000)
                quedan--
            }
            sigueAhi = false
            vm.cancelar()
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("¿Sigues ahí?", fontSize = 28.sp) },
            text = { Text("Volveremos al inicio en $quedan segundos.", fontSize = 20.sp) },
            confirmButton = {
                Button(onClick = { ultimoToque = System.currentTimeMillis(); sigueAhi = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Sí, sigo pidiendo", fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { sigueAhi = false; vm.cancelar() }) { Text("Empezar de nuevo", fontSize = 18.sp) }
            },
        )
    }

    if (pedirPin) {
        DialogoPin(ajustes, alAceptar = { pedirPin = false; menuAdmin = true }, alCancelar = { pedirPin = false })
    }
    if (menuAdmin) {
        AlertDialog(
            onDismissRequest = { menuAdmin = false },
            title = { Text("Menú del encargado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { menuAdmin = false; vm.recargarCarta() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Recargar la carta de PedIA")
                    }
                    OutlinedButton(onClick = { menuAdmin = false; alAbrirAjustes() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ajustes")
                    }
                    OutlinedButton(onClick = { menuAdmin = false; alCambiarModo() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Salir del modo kiosko")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuAdmin = false }) { Text("Cerrar") } },
        )
    }
}

// ---------- Bienvenida ----------

@Composable
private fun Bienvenida(vm: KioscoVM, alAbrirMenu: () -> Unit) {
    var toques by remember { mutableIntStateOf(0) }
    var primerToque by remember { mutableLongStateOf(0L) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoOscuro, Color(0xFF1E3A8A), AzulPedia))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val carta = vm.carta
            val movil = esMovil()
            when {
                carta != null -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val arribaLogo = if (movil) 80.dp else 140.dp
                    val altoLogo = if (movil) 150.dp else 220.dp
                    val altoBoton = if (movil) 130.dp else 180.dp
                    val separacion = 28.dp
                    // Las dos opciones, centradas en la pantalla. En una pantalla tan baja que no caben ahí sin
                    // tapar el logo, justo debajo de él.
                    val arribaOpciones = maxOf((maxHeight - altoBoton * 2 - separacion) / 2, arribaLogo + altoLogo + 24.dp)
                    // Logo del local, algo separado del borde: ocupa todo el hueco que queda sobre las opciones
                    // (que no se mueven), sin pasarse de un máximo.
                    val altoLogoFinal = (arribaOpciones - arribaLogo - 20.dp).coerceIn(altoLogo, if (movil) 260.dp else 380.dp)
                    Image(
                        painterResource(R.drawable.logo_local),
                        contentDescription = "Logo del local",
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = arribaLogo).fillMaxWidth().height(altoLogoFinal),
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(top = arribaOpciones),
                        verticalArrangement = Arrangement.spacedBy(separacion),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tamano = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(altoBoton)
                        // Misma letra en las dos opciones: la que haga falta para que la más larga quepa en una línea.
                        var letra by remember { mutableFloatStateOf(if (movil) 30f else 46f) }
                        val menos = { letra = maxOf(letra * 0.92f, 12f) }
                        BotonInicio(R.drawable.ic_comer_aqui, "Para tomar aquí", tamano, letra, menos) { vm.empezar(llevar = false) }
                        BotonInicio(R.drawable.ic_para_llevar, "Para llevar", tamano, letra, menos) { vm.empezar(llevar = true) }
                    }
                }
                vm.errorCarta != null -> {
                    Text(
                        "Este kiosko no está disponible ahora mismo.\nPor favor, pide en el mostrador.",
                        color = Color.White,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(vm.errorCarta ?: "", color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                    TextButton(onClick = vm::recargarCarta) { Text("Reintentar", color = Color.White) }
                }
                else -> CircularProgressIndicator(color = Color.White)
            }
        }

        // Botón oculto del encargado (ajustes, salir del kiosko): 5 toques seguidos en la esquina
        // superior izquierda, y después el PIN.
        Box(
            Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val ahora = System.currentTimeMillis()
                    if (ahora - primerToque > 4000) {
                        primerToque = ahora
                        toques = 0
                    }
                    toques++
                    if (toques >= 5) {
                        toques = 0
                        alAbrirMenu()
                    }
                },
        )
    }
}

@Composable
private fun BotonInicio(@DrawableRes icono: Int, texto: String, modifier: Modifier, letra: Float, alNoCabe: () -> Unit, alPulsar: () -> Unit) {
    val movil = esMovil()
    Surface(
        onClick = alPulsar,
        color = Color.White,
        shape = RoundedCornerShape(36.dp),
        shadowElevation = 12.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(icono), contentDescription = null, tint = FondoOscuro, modifier = Modifier.size((if (movil) 84.dp else 124.dp) * maxOf(letra / (if (movil) 30f else 46f), 0.6f)))
            Spacer(Modifier.width(18.dp))
            Text(
                texto,
                fontSize = letra.sp,
                fontWeight = FontWeight.Black,
                color = FondoOscuro,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { if (it.didOverflowWidth) alNoCabe() },
            )
        }
    }
}

// ---------- Transiciones ----------

/** Al terminar de elegir un producto, el logo del local llena la pantalla un momento y se desvanece. */
@Composable
private fun Destello(contador: Int) {
    val inicial = remember { contador }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(contador) {
        if (contador != inicial) {
            visible = true
            delay(650)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(420))) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.logo_local), contentDescription = null, modifier = Modifier.height(180.dp))
        }
    }
}

/** Mientras PedIA crea el pedido o prepara el cobro: pantalla de espera con el logo. */
@Composable
private fun Procesando(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
        Column(
            Modifier.fillMaxSize().background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.logo_local), contentDescription = null, modifier = Modifier.height(140.dp))
            Spacer(Modifier.height(28.dp))
            Text("Procesando tu pedido", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Tinta)
            Spacer(Modifier.height(8.dp))
            Text("Un momento, por favor", fontSize = 19.sp, color = GrisTexto)
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = Acento, strokeWidth = 4.dp, modifier = Modifier.size(52.dp))
        }
    }
}

// ---------- Pedido realizado ----------

@Composable
private fun Confirmado(vm: KioscoVM) {
    val pedido = vm.pedido ?: return
    val movil = esMovil()
    var quedan by remember { mutableIntStateOf(SEGUNDOS_PANTALLA_GRACIAS) }
    LaunchedEffect(pedido.id) {
        while (quedan > 0) {
            delay(1000)
            quedan--
        }
        vm.terminar()
    }
    // Los tres pasos que le quedan al cliente, como en los autoservicios.
    val pasos = if (pedido.pagado) {
        listOf(
            R.drawable.ic_pago_tarjeta to "Pedido pagado",
            R.drawable.ic_comer_aqui to "Espera a oír tu número",
            R.drawable.ic_cat_hamburguesa to "¡Lo estamos preparando!",
        )
    } else {
        listOf(
            R.drawable.ic_pago_caja to "Pasa por el mostrador",
            R.drawable.ic_pago_tarjeta to "Paga tu pedido en el mostrador",
            R.drawable.ic_cat_hamburguesa to "¡Empezamos a prepararlo!",
        )
    }
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo_local), contentDescription = null, modifier = Modifier.height(if (movil) 90.dp else 120.dp))
        Spacer(Modifier.height(14.dp))
        Text("¡Pedido realizado!", fontSize = if (movil) 32.sp else 42.sp, fontWeight = FontWeight.Black, color = Tinta)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pasos.forEachIndexed { i, (icono, texto) -> PasoFinal(i + 1, icono, texto, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(24.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = BorderStroke(2.dp, Color(0xFFE5E7EB))) {
            Column(Modifier.padding(horizontal = 56.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Número de pedido", fontSize = 20.sp, color = GrisTexto)
                Text(
                    (pedido.numeroDia ?: pedido.id).toString(),
                    fontSize = if (movil) 72.sp else 96.sp,
                    lineHeight = if (movil) 78.sp else 100.sp,
                    fontWeight = FontWeight.Black,
                    color = Acento,
                )
                Text("${if (vm.paraLlevar) "Para llevar" else "Para tomar aquí"} · ${euros(pedido.total)}", fontSize = 18.sp, color = Tinta)
            }
        }
        Spacer(Modifier.height(22.dp))
        Recibo(vm.impresion, alReintentar = vm::imprimir)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = vm::terminar,
            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.White),
            shape = RoundedCornerShape(50),
            modifier = Modifier.heightIn(min = 64.dp),
            contentPadding = PaddingValues(horizontal = 44.dp),
        ) { Text("Terminar ($quedan)", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PasoFinal(numero: Int, @DrawableRes icono: Int, texto: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(Modifier.size(if (esMovil()) 64.dp else 80.dp).clip(CircleShape).background(AcentoSuave), contentAlignment = Alignment.Center) {
                Icon(painterResource(icono), contentDescription = null, tint = Tinta, modifier = Modifier.size(if (esMovil()) 38.dp else 48.dp))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(Acento),
                contentAlignment = Alignment.Center,
            ) { Text(numero.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(texto, fontSize = 16.sp, color = Tinta, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

/** La ranura de la impresora: mientras imprime, una espera; al terminar, el recibo sale hacia abajo. */
@Composable
private fun Recibo(estado: EstadoImpresion, alReintentar: () -> Unit) {
    if (estado == EstadoImpresion.SIN_IMPRESORA) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(300.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Tinta))
        when (estado) {
            EstadoImpresion.IMPRIMIENDO -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Acento, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Imprimiendo tu recibo…", fontSize = 20.sp, color = Tinta)
            }
            EstadoImpresion.IMPRESO -> {
                val sale = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visibleState = sale, enter = expandVertically(tween(700), expandFrom = Alignment.Top) + fadeIn(tween(300))) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.width(260.dp),
                    ) {
                        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.ic_pago_caja), contentDescription = null, tint = Tinta, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Coge tu recibo, gracias", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tinta)
                        }
                    }
                }
            }
            EstadoImpresion.ERROR -> Column(Modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se ha podido imprimir el recibo. Recuerda tu número.", fontSize = 18.sp, color = Tinta, textAlign = TextAlign.Center)
                TextButton(onClick = alReintentar) { Text("Reintentar impresión", fontSize = 18.sp, color = Acento) }
            }
            EstadoImpresion.SIN_IMPRESORA -> Unit
        }
    }
}
