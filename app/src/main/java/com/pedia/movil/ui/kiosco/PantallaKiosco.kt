package com.pedia.movil.ui.kiosco

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
        when (vm.paso) {
            PasoKiosco.BIENVENIDA -> Bienvenida(vm, alAbrirMenu = { pedirPin = true })
            PasoKiosco.CARTA -> CartaKiosco(vm, ajustes)
            PasoKiosco.RESUMEN, PasoKiosco.ENVIANDO -> ResumenKiosco(vm, ajustes)
            PasoKiosco.PAGO -> ElegirPago(vm)
            PasoKiosco.PAGO_TARJETA -> PagoConTarjeta(vm)
            PasoKiosco.PAGO_MOVIL -> PagoConMovil(vm)
            PasoKiosco.CONFIRMADO -> Confirmado(vm)
        }
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
                carta != null -> {
                    Text(
                        "Pide aquí y recoge en el mostrador",
                        color = Color.White,
                        fontSize = if (movil) 28.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    // Las dos opciones, quietas, una encima de otra y centradas en el espacio libre.
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tamano = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(if (movil) 130.dp else 180.dp)
                        BotonInicio(R.drawable.ic_comer_aqui, "Para tomar aquí", tamano) { vm.empezar(llevar = false) }
                        BotonInicio(R.drawable.ic_para_llevar, "Para llevar", tamano) { vm.empezar(llevar = true) }
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
private fun BotonInicio(@DrawableRes icono: Int, texto: String, modifier: Modifier, alPulsar: () -> Unit) {
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
            Icon(painterResource(icono), contentDescription = null, tint = FondoOscuro, modifier = Modifier.size(if (movil) 84.dp else 124.dp))
            Spacer(Modifier.width(18.dp))
            Text(texto, fontSize = if (movil) 30.sp else 46.sp, fontWeight = FontWeight.Black, color = FondoOscuro, maxLines = 1)
        }
    }
}

// ---------- Confirmado ----------

@Composable
private fun Confirmado(vm: KioscoVM) {
    val pedido = vm.pedido ?: return
    var quedan by remember { mutableIntStateOf(SEGUNDOS_PANTALLA_GRACIAS) }
    LaunchedEffect(pedido.id) {
        while (quedan > 0) {
            delay(1000)
            quedan--
        }
        vm.terminar()
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF065F46), Color(0xFF16A34A)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("¡Gracias!", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("Tu número de pedido es", color = Color.White.copy(alpha = 0.85f), fontSize = 26.sp)
            Text(
                (pedido.numeroDia ?: pedido.id).toString(),
                color = Color.White,
                fontSize = 180.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 190.sp,
            )
            Text(
                "${if (vm.paraLlevar) "Para llevar" else "Para tomar aquí"} · ${euros(pedido.total)}",
                color = Color.White,
                fontSize = 24.sp,
            )
            Spacer(Modifier.height(24.dp))
            when (vm.impresion) {
                EstadoImpresion.IMPRIMIENDO -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Imprimiendo tu ticket…", color = Color.White, fontSize = 22.sp)
                }
                EstadoImpresion.IMPRESO -> Text("Recoge tu ticket", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                EstadoImpresion.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No se ha podido imprimir el ticket. Recuerda tu número.", color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = vm::imprimir) { Text("Reintentar impresión", color = Color.White, fontSize = 18.sp) }
                }
                EstadoImpresion.SIN_IMPRESORA -> Unit
            }
            Spacer(Modifier.height(12.dp))
            if (pedido.pagado) {
                Text("✓ Pagado", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Te avisaremos cuando esté listo.", color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, textAlign = TextAlign.Center)
            } else {
                Text("Paga en caja indicando tu número.", color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = vm::terminar,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF065F46)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.heightIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 40.dp),
            ) { Text("Terminar ($quedan)", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
