package com.pedia.movil.ui.kiosco

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.pedia.movil.R
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.Producto
import com.pedia.movil.ui.AvisoError
import com.pedia.movil.ui.AzulPedia
import com.pedia.movil.ui.AzulSuave
import com.pedia.movil.ui.DialogoConfirmar
import com.pedia.movil.ui.DialogoPin
import com.pedia.movil.ui.DialogoProducto
import com.pedia.movil.ui.Etiqueta
import com.pedia.movil.ui.FondoOscuro
import com.pedia.movil.ui.FotoProducto
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.euros
import kotlinx.coroutines.delay

private const val SEGUNDOS_PANTALLA_GRACIAS = 20
private const val SEGUNDOS_SIGUES_AHI = 20

@Composable
fun PantallaKiosco(ajustes: Ajustes, alAbrirAjustes: () -> Unit, alCambiarModo: () -> Unit) {
    val vm: KioscoVM = viewModel { KioscoVM(ajustes) }
    var ultimoToque by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sigueAhi by remember { mutableStateOf(false) }
    var pedirPin by remember { mutableStateOf(false) }
    var menuAdmin by remember { mutableStateOf(false) }

    // En el kiosko el botón "atrás" no saca de la app: como mucho vuelve a la carta.
    BackHandler { if (vm.paso == PasoKiosco.RESUMEN) vm.seguirPidiendo() }

    // Si el cliente se va a mitad del pedido, se pregunta "¿Sigues ahí?" y luego se reinicia.
    LaunchedEffect(vm.paso) {
        ultimoToque = System.currentTimeMillis()
        while (vm.paso == PasoKiosco.CARTA || vm.paso == PasoKiosco.RESUMEN) {
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
            PasoKiosco.BIENVENIDA -> Bienvenida(vm, alToqueSecreto = { pedirPin = true })
            PasoKiosco.CARTA -> CartaKiosco(vm, ajustes)
            PasoKiosco.RESUMEN, PasoKiosco.ENVIANDO -> Resumen(vm)
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
            text = { Text("Tu pedido se borrará en $quedan segundos.", fontSize = 20.sp) },
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
private fun Bienvenida(vm: KioscoVM, alToqueSecreto: () -> Unit) {
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
            Text(
                carta?.nombreLocal ?: "Bienvenido",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            when {
                carta != null -> {
                    Text(
                        "Pide aquí y recoge en el mostrador",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    // Las dos opciones, quietas, una encima de otra y centradas en el espacio libre.
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tamano = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(180.dp)
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

        // Rincón secreto (arriba a la izquierda): 5 toques seguidos abren el menú del encargado.
        Box(
            Modifier
                .size(110.dp)
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
                        alToqueSecreto()
                    }
                },
        )
    }
}

@Composable
private fun BotonInicio(@DrawableRes icono: Int, texto: String, modifier: Modifier, alPulsar: () -> Unit) {
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
            Icon(painterResource(icono), contentDescription = null, tint = FondoOscuro, modifier = Modifier.size(124.dp))
            Spacer(Modifier.width(18.dp))
            Text(texto, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, color = FondoOscuro, maxLines = 1)
        }
    }
}

// ---------- Carta ----------

@Composable
private fun CartaKiosco(vm: KioscoVM, ajustes: Ajustes) {
    val carta = vm.carta ?: return
    var productoAbierto by remember { mutableStateOf<Producto?>(null) }
    var confirmarCancelar by remember { mutableStateOf(false) }
    val api = remember(ajustes.urlBase) { ajustes.api() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val ancho = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            Surface(shadowElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(carta.nombreLocal, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(14.dp))
                    Etiqueta(if (vm.paraLlevar) "Para llevar" else "Para tomar aquí", AzulSuave, AzulPedia)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { if (vm.lineas.isEmpty()) vm.cancelar() else confirmarCancelar = true }) {
                        Text("Cancelar pedido")
                    }
                }
            }
            if (!ancho) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(carta.categorias, key = { it.id }) { cat ->
                        BotonCategoria(cat.nombre, cat.id == vm.categoria, Modifier) { vm.categoria = cat.id }
                    }
                }
            }
            Row(Modifier.weight(1f)) {
                if (ancho) {
                    LazyColumn(
                        Modifier.width(230.dp).fillMaxHeight().background(Color.White),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(carta.categorias, key = { it.id }) { cat ->
                            BotonCategoria(cat.nombre, cat.id == vm.categoria, Modifier.fillMaxWidth()) { vm.categoria = cat.id }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (ancho) 210.dp else 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(carta.productosDe(vm.categoria), key = { it.id }) { producto ->
                        val enPedido = vm.lineas.filter { it.producto.id == producto.id }.sumOf { it.cantidad }
                        TarjetaProducto(producto, api.urlFoto(producto.fotoPath), enPedido) { productoAbierto = producto }
                    }
                }
            }
            if (vm.lineas.isNotEmpty()) BarraPedido(vm.unidades, vm.total, vm::verResumen)
        }
    }

    productoAbierto?.let { producto ->
        DialogoProducto(
            producto = producto,
            urlFoto = api.urlFoto(producto.fotoPath),
            textoBoton = "Añadir al pedido",
            permitirNota = false,
            alAnadir = { cantidad, notas -> vm.anadir(producto, cantidad, notas); productoAbierto = null },
            alCerrar = { productoAbierto = null },
        )
    }
    if (confirmarCancelar) {
        DialogoConfirmar(
            titulo = "¿Cancelar el pedido?",
            texto = "Se borrará todo lo que has elegido.",
            textoSi = "Sí, cancelar",
            peligroso = true,
            alConfirmar = { confirmarCancelar = false; vm.cancelar() },
            alCancelar = { confirmarCancelar = false },
        )
    }
}

@Composable
private fun BotonCategoria(nombre: String, elegida: Boolean, modifier: Modifier, alPulsar: () -> Unit) {
    Surface(
        onClick = alPulsar,
        color = if (elegida) AzulPedia else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (elegida) Color.White else FondoOscuro,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.heightIn(min = 60.dp),
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(nombre, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TarjetaProducto(producto: Producto, urlFoto: String?, enPedido: Int, alPulsar: () -> Unit) {
    val disponible = producto.estaDisponible
    Card(
        onClick = alPulsar,
        enabled = disponible,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, disabledContainerColor = Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (enPedido > 0) BorderStroke(3.dp, AzulPedia) else null,
    ) {
        Box {
            Column {
                FotoProducto(urlFoto, producto.nombre, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                Column(Modifier.padding(14.dp)) {
                    Text(
                        producto.nombre,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (disponible) FondoOscuro else Color.Gray,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (disponible) euros(producto.precio) else "Agotado",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (disponible) AzulPedia else Color.Gray,
                    )
                }
            }
            if (enPedido > 0) {
                Box(
                    Modifier.padding(10.dp).size(40.dp).clip(CircleShape).background(AzulPedia).align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) { Text(enPedido.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun BarraPedido(unidades: Int, total: Long, alVer: () -> Unit) {
    Surface(color = FondoOscuro, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mi pedido · $unidades ${if (unidades == 1) "producto" else "productos"}", color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
                Text(euros(total), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Button(
                onClick = alVer,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.heightIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
            ) { Text("Ver pedido y terminar  →", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------- Resumen ----------

@Composable
private fun Resumen(vm: KioscoVM) {
    val enviando = vm.paso == PasoKiosco.ENVIANDO
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 820.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tu pedido", fontSize = 36.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Etiqueta(if (vm.paraLlevar) "Para llevar" else "Para tomar aquí", AzulSuave, AzulPedia)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.lineas, key = { it.clave }) { linea -> FilaResumen(linea, enviando) { delta -> vm.cambiarCantidad(linea.clave, delta) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(euros(vm.total), fontSize = 40.sp, fontWeight = FontWeight.Black, color = AzulPedia)
            }
            Text("IVA incluido", color = Color.Gray)
            vm.errorEnvio?.let {
                Spacer(Modifier.height(12.dp))
                AvisoError("No se ha podido enviar el pedido. $it")
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = vm::seguirPidiendo,
                    enabled = !enviando,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                ) { Text("← Añadir más", fontSize = 20.sp) }
                Button(
                    onClick = vm::confirmar,
                    enabled = !enviando && vm.lineas.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1.4f).heightIn(min = 72.dp),
                ) {
                    if (enviando) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    else Text("Confirmar pedido", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilaResumen(linea: LineaCarrito, bloqueado: Boolean, alCambiar: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(linea.producto.nombre, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (linea.notas.isNotBlank()) Text(linea.notas, color = Color.Gray, fontSize = 16.sp)
                Text(euros(linea.producto.precio) + " / ud.", color = Color.Gray, fontSize = 14.sp)
            }
            if (!bloqueado) SelectorCantidad(linea.cantidad, { alCambiar(it - linea.cantidad) }, minimo = 0, tamano = 48.dp)
            Text(
                euros(linea.subtotal),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(110.dp),
            )
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
                EstadoImpresion.IMPRESO -> Text("Recoge tu ticket",color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                EstadoImpresion.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No se ha podido imprimir el ticket. Recuerda tu número.", color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = vm::imprimir) { Text("Reintentar impresión", color = Color.White, fontSize = 18.sp) }
                }
                EstadoImpresion.SIN_IMPRESORA -> Unit
            }
            Spacer(Modifier.height(12.dp))
            Text("Paga en caja indicando tu número.", color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, textAlign = TextAlign.Center)
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
