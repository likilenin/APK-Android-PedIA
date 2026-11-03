package com.pedia.movil.ui.camarero

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.ItemPedido
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.Pedido
import com.pedia.movil.datos.Producto
import com.pedia.movil.ui.AvisoError
import com.pedia.movil.ui.AzulPedia
import com.pedia.movil.ui.AzulSuave
import com.pedia.movil.ui.DialogoConfirmar
import com.pedia.movil.ui.DialogoProducto
import com.pedia.movil.ui.Etiqueta
import com.pedia.movil.ui.FondoOscuro
import com.pedia.movil.ui.Naranja
import com.pedia.movil.ui.NaranjaSuave
import com.pedia.movil.ui.Rojo
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.Verde
import com.pedia.movil.ui.VerdeSuave
import com.pedia.movil.ui.euros
import com.pedia.movil.ui.kiosco.FichaProductoKiosco
import com.pedia.movil.ui.kiosco.iconoCategoria
import com.pedia.movil.ui.minutosDesde
import com.pedia.movil.ui.textoEstado
import com.pedia.movil.ui.textoTiempo
import kotlinx.coroutines.delay

private const val SEGUNDOS_REFRESCO = 4L

@Composable
fun PantallaCamarero(ajustes: Ajustes, alAbrirAjustes: () -> Unit, alCambiarModo: () -> Unit) {
    val vm: CamareroVM = viewModel { CamareroVM(ajustes) }
    val avisos = remember { SnackbarHostState() }
    val ciclo = LocalLifecycleOwner.current.lifecycle

    // Las mesas se refrescan solas mientras la pantalla está encendida: así se ve al
    // momento lo que piden otros camareros o el propio TPV.
    LaunchedEffect(Unit) {
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refrescarMesas()
                delay(SEGUNDOS_REFRESCO * 1000)
            }
        }
    }
    LaunchedEffect(vm.aviso) {
        vm.aviso?.let {
            avisos.showSnackbar(it)
            vm.aviso = null
        }
    }

    val mesa = vm.mesaAbierta
    if (mesa == null) {
        PantallaMesas(vm, ajustes, avisos, alAbrirAjustes, alCambiarModo)
    } else {
        PantallaComanda(vm, ajustes, mesa, avisos)
    }
}

// ---------- Mesas ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaMesas(
    vm: CamareroVM,
    ajustes: Ajustes,
    avisos: SnackbarHostState,
    alAbrirAjustes: () -> Unit,
    alCambiarModo: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val carta = vm.carta
    val mesas = carta?.mesas ?: 20

    Scaffold(
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FondoOscuro, titleContentColor = Color.White, actionIconContentColor = Color.White),
                title = {
                    Column {
                        Text("Mesas", fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(carta?.nombreLocal, ajustes.nombreCamarero.ifBlank { null }).joinToString(" · "),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                },
                actions = {
                    PuntoConexion(vm.conectado)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Recargar carta") }, onClick = { menu = false; vm.cargarCarta() })
                            DropdownMenuItem(text = { Text("Ajustes") }, onClick = { menu = false; alAbrirAjustes() })
                            DropdownMenuItem(text = { Text("Cambiar de modo") }, onClick = { menu = false; alCambiarModo() })
                        }
                    }
                },
            )
        },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            vm.errorConexion?.let { Box(Modifier.padding(12.dp)) { AvisoError(it) } }
            vm.errorCarta?.let { if (carta == null) Box(Modifier.padding(12.dp)) { AvisoError(it, vm::cargarCarta) } }
            Leyenda(vm.pedidosPorMesa.size, mesas)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items((1..mesas).toList(), key = { it }) { numero ->
                    TarjetaMesa(numero, vm.pedidoDe(numero)) { vm.abrirMesa(numero) }
                }
            }
        }
    }
}

@Composable
private fun PuntoConexion(conectado: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(
                when (conectado) {
                    true -> Color(0xFF4ADE80)
                    false -> Color(0xFFF87171)
                    null -> Color.Gray
                }
            )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            when (conectado) {
                true -> "PedIA"
                false -> "Sin conexión"
                null -> "…"
            },
            fontSize = 14.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun Leyenda(ocupadas: Int, total: Int) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$ocupadas ocupadas · ${total - ocupadas} libres", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Etiqueta("Libre", MaterialTheme.colorScheme.surfaceVariant, Color.DarkGray)
        Spacer(Modifier.width(6.dp))
        Etiqueta("Ocupada", AzulSuave, AzulPedia)
        Spacer(Modifier.width(6.dp))
        Etiqueta("Listo", VerdeSuave, Verde)
    }
}

@Composable
private fun TarjetaMesa(numero: Int, pedido: Pedido?, alPulsar: () -> Unit) {
    val listo = pedido?.estado == "listo"
    val (fondo, texto) = when {
        pedido == null -> Color.White to FondoOscuro
        listo -> Verde to Color.White
        else -> AzulPedia to Color.White
    }
    Card(
        onClick = alPulsar,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = texto),
        elevation = CardDefaults.cardElevation(defaultElevation = if (pedido == null) 1.dp else 4.dp),
        modifier = Modifier.height(132.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()) {
            Text("Mesa", fontSize = 14.sp, color = texto.copy(alpha = 0.7f))
            Text(numero.toString(), fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp)
            if (pedido == null) {
                Text("Libre", fontSize = 14.sp, color = texto.copy(alpha = 0.5f))
            } else {
                Text(euros(pedido.total), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val minutos = minutosDesde(pedido.creadoEn)
                Text(
                    listOfNotNull(
                        "${pedido.unidades} prod.",
                        minutos?.let(::textoTiempo),
                        if (pedido.pagado) "Pagada" else null,
                    ).joinToString(" · "),
                    fontSize = 13.sp,
                    color = texto.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------- Comanda de una mesa ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaComanda(vm: CamareroVM, ajustes: Ajustes, mesa: Int, avisos: SnackbarHostState) {
    val pedido = vm.pedidoDe(mesa)
    var salirSinEnviar by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<String?>(null) }
    var cambiarMesa by remember { mutableStateOf(false) }

    fun volver() {
        if (vm.nuevas.isNotEmpty()) salirSinEnviar = true else vm.cerrarMesa()
    }
    BackHandler(onBack = ::volver)

    Scaffold(
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FondoOscuro,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = { IconButton(onClick = ::volver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mesas") } },
                title = {
                    Column {
                        Text("Mesa $mesa", fontWeight = FontWeight.Bold)
                        Text(
                            pedido?.let { "Pedido nº ${it.numeroDia ?: it.id} · ${textoEstado(it.estado)}" + if (it.pagado) " · Pagada" else "" }
                                ?: "Mesa libre: nueva comanda",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                },
                actions = {
                    if (vm.ocupado) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    if (pedido != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opciones de la mesa") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!pedido.pagado) {
                                DropdownMenuItem(text = { Text("Cobrar en efectivo") }, onClick = { menu = false; confirmar = "cobrar" })
                            }
                            DropdownMenuItem(text = { Text("Cambiar de mesa") }, onClick = { menu = false; cambiarMesa = true })
                        }
                    }
                },
            )
        },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (pedido != null) {
                BarraMesa(
                    pedido,
                    habilitada = !vm.ocupado,
                    alCerrarComanda = {
                        // Lo que aún no se ha enviado no saldría en el ticket final.
                        if (vm.nuevas.isEmpty()) confirmar = "cerrar"
                        else vm.aviso = "Primero envía a cocina lo apuntado o quítalo de la comanda"
                    },
                    alLiberar = { confirmar = "liberar" },
                )
            }
            var pestana by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = pestana) {
                Tab(pestana == 0, { pestana = 0 }, text = { Text("Carta", fontSize = 16.sp) })
                Tab(pestana == 1, { pestana = 1 }, text = {
                    val n = vm.nuevas.sumOf { it.cantidad }
                    Text(if (n > 0) "Comanda ($n)" else "Comanda", fontSize = 16.sp)
                })
            }
            Box(Modifier.weight(1f)) {
                if (pestana == 0) CartaRapida(vm, ajustes, Modifier.fillMaxSize())
                else PanelComanda(vm, ajustes, pedido)
            }
            BarraEnviar(vm)
        }
    }

    if (salirSinEnviar) {
        DialogoConfirmar(
            titulo = "¿Salir sin enviar?",
            texto = "Hay productos apuntados que no se han mandado a cocina. Si sales, se pierden.",
            textoSi = "Salir sin enviar",
            peligroso = true,
            alConfirmar = { salirSinEnviar = false; vm.cerrarMesa() },
            alCancelar = { salirSinEnviar = false },
        )
    }
    if (pedido != null) when (confirmar) {
        "cobrar" -> DialogoConfirmar(
            titulo = "Cobrar mesa $mesa",
            texto = "Se marcará como cobrada en efectivo ${euros(pedido.total)} (igual que «Abrir caja» en PedIA).",
            textoSi = "Cobrado",
            alConfirmar = { confirmar = null; vm.cobrarEnEfectivo(pedido) },
            alCancelar = { confirmar = null },
        )
        "cerrar" -> DialogoConfirmar(
            titulo = "Cerrar comanda de la mesa $mesa",
            texto = "PedIA imprimirá el ticket final para el cliente (${euros(pedido.total)}).",
            textoSi = "Cerrar comanda",
            alConfirmar = { confirmar = null; vm.cerrarComanda(pedido) },
            alCancelar = { confirmar = null },
        )
        "liberar" -> DialogoConfirmar(
            titulo = "Liberar mesa $mesa",
            texto = "La mesa quedará libre. " +
                (if (pedido.pagado) "" else "Aún no está cobrada (${euros(pedido.total)}). ") +
                "El pedido no se borra: sigue en PedIA.",
            textoSi = "Liberar",
            alConfirmar = { confirmar = null; vm.liberarMesa(pedido) },
            alCancelar = { confirmar = null },
        )
    }
    if (cambiarMesa && pedido != null) {
        DialogoCambiarMesa(
            actual = mesa,
            ocupadas = vm.pedidosPorMesa.keys,
            alElegir = { nueva -> cambiarMesa = false; vm.cambiarDeMesa(pedido, nueva) },
            alCancelar = { cambiarMesa = false },
        )
    }
}

/** Total de la mesa ocupada y sus botones de cerrar comanda y liberar, a la vista nada más abrirla. */
@Composable
private fun BarraMesa(pedido: Pedido, habilitada: Boolean, alCerrarComanda: () -> Unit, alLiberar: () -> Unit) {
    Surface(shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(euros(pedido.total), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = alLiberar,
                enabled = habilitada,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("Liberar mesa", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = alCerrarComanda,
                enabled = habilitada,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("Cerrar comanda", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartaRapida(vm: CamareroVM, ajustes: Ajustes, modifier: Modifier) {
    val carta = vm.carta
    if (carta == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            vm.errorCarta?.let { Box(Modifier.padding(16.dp)) { AvisoError(it, vm::cargarCarta) } } ?: CircularProgressIndicator()
        }
        return
    }
    var categoria by remember { mutableStateOf(carta.categorias.firstOrNull()?.id) }
    var busqueda by remember { mutableStateOf("") }
    var detalle by remember { mutableStateOf<Producto?>(null) }
    val vibrar = LocalHapticFeedback.current
    val api = remember(ajustes.urlBase) { ajustes.api() }

    val productos = if (busqueda.isBlank()) carta.productosDe(categoria)
    else carta.productos.filter { it.nombre.contains(busqueda.trim(), ignoreCase = true) }

    Column(modifier) {
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            placeholder = { Text("Buscar producto…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (busqueda.isNotEmpty()) IconButton(onClick = { busqueda = "" }) { Icon(Icons.Filled.Clear, "Borrar") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
        if (busqueda.isBlank()) {
            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(carta.categorias, key = { it.id }) { cat ->
                    FilterChip(
                        selected = cat.id == categoria,
                        onClick = { categoria = cat.id },
                        label = { Text(cat.nombre, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp)) },
                    )
                }
            }
        } else Spacer(Modifier.height(12.dp))
        Text(
            "Toca para añadir · Mantén pulsado para opciones y notas",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(productos, key = { it.id }) { producto ->
                val cuenta = vm.nuevas.filter { it.producto.id == producto.id }.sumOf { it.cantidad }
                val disponible = producto.estaDisponible
                Surface(
                    color = when {
                        !disponible -> Color(0xFFF1F5F9)
                        cuenta > 0 -> AzulSuave
                        else -> Color.White
                    },
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = if (disponible) 2.dp else 0.dp,
                    modifier = Modifier
                        .heightIn(min = 92.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            enabled = disponible,
                            onClick = {
                                vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Un menú hay que elegirlo (bebida, patatas...): abre su ficha.
                                if (producto.esMenu) detalle = producto else vm.anadir(producto)
                            },
                            onLongClick = {
                                vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                                detalle = producto
                            },
                        ),
                ) {
                    Box {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                producto.nombre,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = if (disponible) FondoOscuro else Color.Gray,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(if (disponible) euros(producto.precio) else "Agotado", color = if (disponible) AzulPedia else Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        if (cuenta > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(AzulPedia),
                                contentAlignment = Alignment.Center,
                            ) { Text(cuenta.toString(), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    detalle?.let { producto ->
        if (producto.esMenu) {
            FichaProductoKiosco(
                producto = producto,
                carta = carta,
                urlFoto = api.urlFoto(producto.fotoPath),
                icono = iconoCategoria(carta.categorias.firstOrNull { it.id == producto.categoriaId }?.nombre.orEmpty()),
                conNotaLibre = true,
                alAnadir = { linea -> vm.anadir(linea); detalle = null },
                alCerrar = { detalle = null },
            )
        } else {
            DialogoProducto(
                producto = producto,
                urlFoto = api.urlFoto(producto.fotoPath),
                textoBoton = "Añadir",
                permitirNota = true,
                alAnadir = { cantidad, notas -> vm.anadir(producto, cantidad, notas); detalle = null },
                alCerrar = { detalle = null },
            )
        }
    }
}

@Composable
private fun PanelComanda(vm: CamareroVM, ajustes: Ajustes, pedido: Pedido?) {
    var itemElegido by remember { mutableStateOf<ItemPedido?>(null) }
    var lineaEditada by remember { mutableStateOf<LineaCarrito?>(null) }
    var itemEditado by remember { mutableStateOf<ItemPedido?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Para enviar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Naranja)
            }
            if (vm.nuevas.isEmpty()) item {
                Text("Toca productos de la carta para apuntarlos.", color = Color.Gray)
            }
            items(vm.nuevas, key = { "n" + it.clave }) { linea ->
                FilaNueva(linea, alEditar = { lineaEditada = linea }, alCambiar = { vm.cambiarCantidad(linea.clave, it) })
            }
            if (pedido != null && pedido.items.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ya en cocina", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(euros(pedido.total), fontWeight = FontWeight.Bold)
                    }
                }
                items(pedido.items, key = { "e" + it.id }) { item ->
                    Surface(
                        onClick = { itemElegido = item },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${item.cantidad}×", fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.productoNombre ?: "Producto")
                                item.notas?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = Color.Gray) }
                            }
                            Text(euros(item.precioUnitario * item.cantidad), color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }

    lineaEditada?.let { linea ->
        val carta = vm.carta
        if (linea.producto.esMenu && carta != null) {
            // Un menú se edita en su ficha: se puede cambiar lo elegido (la bebida, las patatas...), no solo el texto.
            FichaProductoKiosco(
                producto = linea.producto,
                carta = carta,
                urlFoto = ajustes.api().urlFoto(linea.producto.fotoPath),
                icono = iconoCategoria(carta.categorias.firstOrNull { it.id == linea.producto.categoriaId }?.nombre.orEmpty()),
                linea = linea,
                conNotaLibre = true,
                alGuardarSoloUna = { vm.editarLinea(linea.clave, it, soloUna = true); lineaEditada = null },
                alAnadir = { vm.editarLinea(linea.clave, it, soloUna = false); lineaEditada = null },
                alCerrar = { lineaEditada = null },
            )
        } else {
            DialogoNota(
                nombre = linea.producto.nombre,
                notasIniciales = linea.notas,
                opciones = linea.producto.opciones,
                unidades = linea.cantidad,
                aviso = null,
                alGuardar = { notas, soloUna -> vm.editarNota(linea.clave, notas, soloUna); lineaEditada = null },
                alCancelar = { lineaEditada = null },
            )
        }
    }
    val editado = itemEditado
    if (editado != null && pedido != null) {
        DialogoNota(
            nombre = editado.productoNombre ?: "Producto",
            notasIniciales = editado.notas.orEmpty(),
            opciones = vm.carta?.productos?.firstOrNull { it.id == editado.productoId }?.opciones.orEmpty(),
            unidades = 1,
            aviso = "Este producto ya está en cocina: el cambio se guarda en PedIA, pero no se vuelve a imprimir. Avisa a cocina.",
            alGuardar = { notas, _ -> vm.editarNotaEnviada(pedido, editado, notas); itemEditado = null },
            alCancelar = { itemEditado = null },
        )
    }

    val elegido = itemElegido
    if (elegido != null && pedido != null) {
        AlertDialog(
            onDismissRequest = { itemElegido = null },
            title = { Text("${elegido.cantidad}× ${elegido.productoNombre ?: "Producto"}") },
            text = { Text("Este producto ya está enviado a cocina. ¿Qué quieres hacer?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { itemElegido = null; itemEditado = elegido }) { Text("Editar") }
                    if (elegido.cantidad > 1) {
                        TextButton(onClick = { itemElegido = null; vm.quitarUnaEnviada(pedido, elegido) }) { Text("Quitar una unidad") }
                    }
                    TextButton(onClick = { itemElegido = null; vm.eliminarEnviada(pedido, elegido) }) { Text("Eliminar la línea", color = Rojo) }
                    TextButton(onClick = { itemElegido = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

@Composable
private fun FilaNueva(linea: LineaCarrito, alEditar: () -> Unit, alCambiar: (Int) -> Unit) {
    Surface(color = NaranjaSuave, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(linea.producto.nombre, fontWeight = FontWeight.Bold)
                if (linea.detalle.isNotBlank()) Text(linea.detalle, fontSize = 13.sp, color = Color.DarkGray)
                Text(euros(linea.subtotal), fontSize = 13.sp, color = Color.DarkGray)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = alEditar,
                modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { Text("Editar", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            SelectorCantidad(linea.cantidad, { alCambiar(it - linea.cantidad) }, minimo = 0, tamano = 40.dp)
        }
    }
}

/**
 * Lo que pide el cliente en un producto, con sus palabras: «sin pan» aunque en la carta de PedIA no exista.
 * Las opciones del producto, si tiene, son atajos que añaden o quitan su texto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoNota(
    nombre: String,
    notasIniciales: String,
    opciones: List<String>,
    unidades: Int,
    aviso: String?,
    alGuardar: (notas: String, soloUna: Boolean) -> Unit,
    alCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf(notasIniciales) }
    val partes = texto.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(nombre) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                aviso?.let { Text(it, color = Naranja, fontSize = 14.sp) }
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(200) },
                    label = { Text("Qué pide el cliente") },
                    placeholder = { Text("Sin pan, sin cebolla, poco hecha…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (opciones.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        opciones.forEach { opcion ->
                            val marcada = partes.any { it.equals(opcion, ignoreCase = true) }
                            FilterChip(
                                selected = marcada,
                                onClick = {
                                    texto = (if (marcada) partes.filterNot { it.equals(opcion, ignoreCase = true) } else partes + opcion)
                                        .joinToString(", ")
                                },
                                label = { Text(opcion, fontSize = 14.sp) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (unidades > 1) {
                    Button(onClick = { alGuardar(texto, true) }) { Text("Guardar solo en 1 de las $unidades") }
                    OutlinedButton(onClick = { alGuardar(texto, false) }) { Text("Guardar en las $unidades") }
                } else {
                    Button(onClick = { alGuardar(texto, false) }) { Text("Guardar") }
                }
                TextButton(onClick = alCancelar) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun BarraEnviar(vm: CamareroVM) {
    val unidades = vm.nuevas.sumOf { it.cantidad }
    Surface(shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (unidades == 0) "Nada por enviar" else "$unidades por enviar", fontWeight = FontWeight.Medium)
                if (unidades > 0) Text(euros(vm.nuevas.sumOf { it.subtotal }), color = Color.Gray)
            }
            Button(
                onClick = vm::enviarComanda,
                enabled = unidades > 0 && !vm.enviando,
                colors = ButtonDefaults.buttonColors(containerColor = Verde),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.heightIn(min = 60.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                if (vm.enviando) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                else Text("ENVIAR A COCINA", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun DialogoCambiarMesa(actual: Int, ocupadas: Set<Int>, alElegir: (Int) -> Unit, alCancelar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val numero = texto.toIntOrNull()
    val valida = numero != null && numero > 0 && numero != actual && numero !in ocupadas
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Pasar la mesa $actual a…") },
        text = {
            Column {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.filter(Char::isDigit).take(3) },
                    label = { Text("Número de mesa") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (numero != null && numero in ocupadas && numero != actual) {
                    Text("La mesa $numero está ocupada.", color = Rojo, textAlign = TextAlign.Start)
                }
            }
        },
        confirmButton = { Button(enabled = valida, onClick = { numero?.let(alElegir) }) { Text("Cambiar") } },
        dismissButton = { OutlinedButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
