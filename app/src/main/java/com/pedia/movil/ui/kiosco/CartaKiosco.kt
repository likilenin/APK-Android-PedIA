package com.pedia.movil.ui.kiosco

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.pedia.movil.R
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.PediaApi
import com.pedia.movil.datos.Producto
import com.pedia.movil.ui.Acento
import com.pedia.movil.ui.AcentoSuave
import com.pedia.movil.ui.AvisoError
import com.pedia.movil.ui.Crema
import com.pedia.movil.ui.DialogoConfirmar
import com.pedia.movil.ui.GrisFoto
import com.pedia.movil.ui.GrisTexto
import com.pedia.movil.ui.Rojo
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.Tinta
import com.pedia.movil.ui.VentanaPedia
import com.pedia.movil.ui.Verde
import com.pedia.movil.ui.esMovil
import com.pedia.movil.ui.euros
import kotlinx.coroutines.delay

// Carta del kiosko con la distribución de los autoservicios de comida rápida: categorías con
// dibujo en una columna a la izquierda, productos en tarjetas blancas sobre fondo crema y la
// barra del pedido siempre visible abajo.

private fun Carta.iconoDe(producto: Producto) =
    iconoCategoria(categorias.firstOrNull { it.id == producto.categoriaId }?.nombre.orEmpty())

@Composable
fun CartaKiosco(vm: KioscoVM, ajustes: Ajustes) {
    val carta = vm.carta ?: return
    val api = remember(ajustes.urlBase) { ajustes.api() }
    var productoAbierto by remember { mutableStateOf<Producto?>(null) }
    var confirmarCancelar by remember { mutableStateOf(false) }
    val categoria = carta.categorias.firstOrNull { it.id == vm.categoria } ?: carta.categorias.firstOrNull()
    val movil = esMovil()

    Box(Modifier.fillMaxSize().background(Crema)) {
        Column(Modifier.fillMaxSize()) {
            CabeceraKiosco(vm.paraLlevar)
            Row(Modifier.weight(1f)) {
                // Categorías
                LazyColumn(
                    Modifier.width(if (movil) 114.dp else 150.dp).fillMaxHeight().background(Color.White),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(carta.categorias, key = { it.id }) { cat ->
                        ElementoCategoria(cat.nombre, iconoCategoria(cat.nombre), cat.id == categoria?.id) { vm.categoria = cat.id }
                    }
                }
                VerticalDivider(color = Color(0xFFE7E2D8))
                // Productos de la categoría elegida
                Column(Modifier.weight(1f)) {
                    Text(
                        categoria?.nombre.orEmpty(),
                        fontSize = if (movil) 26.sp else 34.sp,
                        fontWeight = FontWeight.Black,
                        color = Tinta,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (movil) 135.dp else 180.dp),
                        contentPadding = PaddingValues(if (movil) 10.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(carta.productosDe(categoria?.id), key = { it.id }) { producto ->
                            TarjetaProductoKiosco(
                                producto = producto,
                                urlFoto = api.urlFoto(producto.fotoPath),
                                icono = carta.iconoDe(producto),
                                enPedido = vm.lineas.filter { it.producto.id == producto.id }.sumOf { it.cantidad },
                                alAbrir = { productoAbierto = producto },
                                // Sin opciones que elegir, el "+" lo añade directamente.
                                alAnadirRapido = {
                                    if (producto.opciones.isEmpty() && !producto.esMenu) vm.anadir(LineaCarrito(producto, 1)) else productoAbierto = producto
                                },
                            )
                        }
                    }
                }
            }
            BarraPedidoKiosco(
                unidades = vm.unidades,
                total = vm.total,
                alCancelar = { if (vm.lineas.isEmpty()) vm.cancelar() else confirmarCancelar = true },
                alVerPedido = vm::verResumen,
            )
        }
        AvisoAnadido(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    productoAbierto?.let { producto ->
        FichaProductoKiosco(
            producto = producto,
            carta = carta,
            urlFoto = api.urlFoto(producto.fotoPath),
            icono = carta.iconoDe(producto),
            alAnadir = { vm.anadir(it); productoAbierto = null },
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
private fun CabeceraKiosco(paraLlevar: Boolean) {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtiquetaLugar(paraLlevar)
        }
    }
}

@Composable
fun EtiquetaLugar(paraLlevar: Boolean) {
    Surface(color = AcentoSuave, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (paraLlevar) R.drawable.ic_para_llevar else R.drawable.ic_comer_aqui),
                contentDescription = null,
                tint = Tinta,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (paraLlevar) "Para llevar" else "Para tomar aquí", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Tinta)
        }
    }
}

@Composable
private fun ElementoCategoria(nombre: String, @DrawableRes icono: Int, elegida: Boolean, alPulsar: () -> Unit) {
    val movil = esMovil()
    Surface(onClick = alPulsar, color = if (elegida) AcentoSuave else Color.White) {
        Row(Modifier.fillMaxWidth().height(if (movil) 96.dp else ALTO_CATEGORIA)) {
            // Marca de la categoría elegida, como en los autoservicios.
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (elegida) Acento else Color.Transparent))
            Column(
                Modifier.weight(1f).padding(vertical = if (movil) 8.dp else 12.dp, horizontal = if (movil) 2.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(icono), contentDescription = null, tint = Tinta, modifier = Modifier.size(if (movil) 40.dp else 52.dp))
                Spacer(Modifier.height(if (movil) 4.dp else 6.dp))
                Text(
                    nombre,
                    fontSize = if (movil) 13.sp else 15.sp,
                    lineHeight = if (movil) 15.sp else 17.sp,
                    fontWeight = if (elegida) FontWeight.Black else FontWeight.SemiBold,
                    color = Tinta,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ALTO_CATEGORIA: Dp = 116.dp

/** Foto del producto o, si no tiene, el dibujo de su categoría sobre gris claro. */
@Composable
fun ImagenProducto(url: String?, @DrawableRes icono: Int, modifier: Modifier, tamanoIcono: Float = 0.5f) {
    val sinFoto: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(GrisFoto), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icono),
                contentDescription = null,
                tint = Color(0xFFB4B4B8),
                modifier = Modifier.fillMaxSize(tamanoIcono),
            )
        }
    }
    Box(modifier) {
        if (url == null) sinFoto()
        else SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { sinFoto() },
            error = { sinFoto() },
        )
    }
}

@Composable
private fun TarjetaProductoKiosco(
    producto: Producto,
    urlFoto: String?,
    @DrawableRes icono: Int,
    enPedido: Int,
    alAbrir: () -> Unit,
    alAnadirRapido: () -> Unit,
) {
    val disponible = producto.estaDisponible
    Surface(
        onClick = alAbrir,
        enabled = disponible,
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        border = if (enPedido > 0) BorderStroke(3.dp, Acento) else null,
    ) {
        Column {
            Box {
                ImagenProducto(urlFoto, icono, Modifier.fillMaxWidth().aspectRatio(1.2f))
                if (!disponible) {
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Text("AGOTADO", fontWeight = FontWeight.Black, color = GrisTexto, fontSize = 18.sp)
                    }
                }
                if (enPedido > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(38.dp).clip(CircleShape).background(Acento),
                        contentAlignment = Alignment.Center,
                    ) { Text(enPedido.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                }
            }
            Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)) {
                Text(
                    producto.nombre,
                    fontSize = 18.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (disponible) Tinta else GrisTexto,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        euros(producto.precio),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = if (disponible) Tinta else GrisTexto,
                        modifier = Modifier.weight(1f),
                    )
                    if (disponible) {
                        Surface(onClick = alAnadirRapido, color = Acento, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarraPedidoKiosco(unidades: Int, total: Long, alCancelar: () -> Unit, alVerPedido: () -> Unit) {
    val movil = esMovil()
    Surface(color = Color.White, shadowElevation = 16.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (movil) 10.dp else 16.dp, vertical = if (movil) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = alCancelar,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, Color(0xFFE5E7EB)),
                modifier = Modifier.heightIn(min = if (movil) 56.dp else 64.dp),
                contentPadding = PaddingValues(horizontal = if (movil) 12.dp else 24.dp),
            ) { Text(if (movil) "Cancelar" else "Cancelar pedido", color = Rojo, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(horizontal = if (movil) 10.dp else 16.dp)) {
                Text(
                    when {
                        unidades == 0 -> "Tu pedido está vacío"
                        movil -> "$unidades ${if (unidades == 1) "producto" else "productos"}"
                        else -> "Mi pedido · $unidades ${if (unidades == 1) "producto" else "productos"}"
                    },
                    color = GrisTexto,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
                Text(euros(total), color = Tinta, fontSize = if (movil) 24.sp else 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Button(
                onClick = alVerPedido,
                enabled = unidades > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Acento,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFEDEDED),
                    disabledContentColor = Color(0xFFA1A1AA),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = if (movil) 56.dp else 64.dp),
                contentPadding = PaddingValues(horizontal = if (movil) 14.dp else 26.dp),
            ) { Text("Ver mi pedido", fontSize = if (movil) 17.sp else 20.sp, fontWeight = FontWeight.Black, maxLines = 1) }
        }
    }
}

@Composable
private fun AvisoAnadido(vm: KioscoVM, modifier: Modifier) {
    LaunchedEffect(vm.avisoId) {
        if (vm.avisoAnadido != null) {
            delay(2200)
            vm.avisoAnadido = null
        }
    }
    AnimatedVisibility(
        visible = vm.avisoAnadido != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(color = Verde, shape = RoundedCornerShape(50), shadowElevation = 8.dp) {
            Text(
                "✓  " + (vm.avisoAnadido ?: ""),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp).widthIn(max = 620.dp),
            )
        }
    }
}

// ---------- Ficha del producto ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FichaProductoKiosco(
    producto: Producto,
    carta: Carta,
    urlFoto: String?,
    @DrawableRes icono: Int,
    /** La línea del pedido que se está editando; sin ella, la ficha añade una nueva. */
    linea: LineaCarrito? = null,
    alAnadir: (LineaCarrito) -> Unit,
    alCerrar: () -> Unit,
) {
    var cantidad by remember { mutableIntStateOf(linea?.cantidad ?: 1) }
    var elegidas by remember { mutableStateOf(linea?.opcionesMarcadas.orEmpty()) }
    // Menús: opciones elegidas en cada grupo (id del grupo → ids de las opciones).
    var menu by remember {
        mutableStateOf(
            linea?.let { l -> producto.grupos.associate { g -> g.id to g.opciones.map { it.id }.filter { it in l.selecciones } } }.orEmpty(),
        )
    }
    // Lo quitado a cada opción elegida ("Sin queso"...) y qué desplegables están abiertos.
    var quitados by remember { mutableStateOf(linea?.quitados.orEmpty()) }
    var abiertos by remember { mutableStateOf(linea?.selecciones.orEmpty().toSet()) }
    val seleccionadas = menu.values.flatten()
    val faltan = producto.grupos.filter { it.obligatorio && menu[it.id].isNullOrEmpty() }
    val precioUnidad = producto.precio + producto.grupos.flatMap { it.opciones }.filter { it.id in seleccionadas }.sumOf { it.suplemento }
    val movil = esMovil()

    VentanaPedia(alCerrar) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 680.dp).heightIn(max = 1100.dp),
        ) {
            Column {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Box {
                        ImagenProducto(urlFoto, icono, Modifier.fillMaxWidth().height(if (movil) 170.dp else 300.dp), tamanoIcono = 0.55f)
                        Surface(
                            onClick = alCerrar,
                            color = Color.White,
                            shape = CircleShape,
                            shadowElevation = 4.dp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(52.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text("✕", fontSize = 24.sp, color = Tinta) } }
                    }
                    Column(Modifier.padding(if (movil) 18.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(producto.nombre, fontSize = if (movil) 26.sp else 32.sp, lineHeight = if (movil) 30.sp else 36.sp, fontWeight = FontWeight.Black, color = Tinta)
                        producto.descripcion?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 18.sp, color = GrisTexto)
                        }
                        Text(euros(producto.precio), fontSize = if (movil) 24.sp else 28.sp, fontWeight = FontWeight.Black, color = Tinta)
                        producto.grupos.forEach { grupo ->
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(grupo.nombre, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Tinta)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    (if (grupo.maxSelecciones <= 1) "Elige 1" else "Elige hasta ${grupo.maxSelecciones}") +
                                        if (grupo.obligatorio) " · Obligatorio" else " · Opcional",
                                    fontSize = 15.sp,
                                    color = if (grupo in faltan) Rojo else GrisTexto,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                grupo.opciones.forEach { opcion ->
                                    val actuales = menu[grupo.id].orEmpty()
                                    val marcada = opcion.id in actuales
                                    CasillaOpcion(
                                        texto = opcion.texto,
                                        extra = when {
                                            opcion.agotada -> "Agotado"
                                            opcion.suplemento > 0 -> "+ " + euros(opcion.suplemento)
                                            else -> null
                                        },
                                        marcada = marcada,
                                        habilitada = !opcion.agotada,
                                    ) {
                                        val nuevas = when {
                                            marcada -> actuales - opcion.id
                                            grupo.maxSelecciones <= 1 -> listOf(opcion.id)
                                            actuales.size < grupo.maxSelecciones -> actuales + opcion.id
                                            else -> actuales
                                        }
                                        menu = menu + (grupo.id to nuevas)
                                        // Al elegir algo con ingredientes editables, su desplegable se abre solo.
                                        if (!marcada && opcion.id in nuevas) abiertos = abiertos + opcion.id
                                    }
                                }
                            }
                            grupo.opciones.filter { it.id in menu[grupo.id].orEmpty() }.forEach { opcion ->
                                val ingredientes = carta.ingredientesDe(opcion)
                                if (ingredientes.isNotEmpty()) {
                                    DesplegableIngredientes(
                                        nombre = opcion.texto,
                                        ingredientes = ingredientes,
                                        quitados = quitados[opcion.id].orEmpty(),
                                        abierto = opcion.id in abiertos,
                                        alAbrirCerrar = {
                                            abiertos = if (opcion.id in abiertos) abiertos - opcion.id else abiertos + opcion.id
                                        },
                                        alCambiar = { ingrediente ->
                                            val actuales = quitados[opcion.id].orEmpty()
                                            quitados = quitados + (opcion.id to if (ingrediente in actuales) actuales - ingrediente else actuales + ingrediente)
                                        },
                                    )
                                }
                            }
                        }
                        if (producto.opciones.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Personaliza tu producto", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Tinta)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                producto.opciones.forEach { opcion ->
                                    val marcada = opcion in elegidas
                                    CasillaOpcion(opcion, null, marcada, true) {
                                        elegidas = if (marcada) elegidas - opcion else elegidas + opcion
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cantidad", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Tinta, modifier = Modifier.weight(1f))
                            SelectorCantidad(cantidad, { cantidad = it.coerceIn(1, 99) }, tamano = 56.dp)
                        }
                    }
                }
                Surface(color = Color.White, shadowElevation = 12.dp) {
                    Row(Modifier.fillMaxWidth().padding(if (movil) 12.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(if (movil) 10.dp else 14.dp)) {
                        OutlinedButton(
                            onClick = alCerrar,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier.weight(1f).heightIn(min = if (movil) 60.dp else 72.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) { Text("Cancelar", fontSize = if (movil) 17.sp else 20.sp, fontWeight = FontWeight.Bold, color = Tinta, maxLines = 1) }
                        Button(
                            onClick = {
                                val notas = producto.opciones.filter { it in elegidas } + notasDeIngredientes(producto, seleccionadas, quitados)
                                alAnadir(
                                    LineaCarrito(
                                        producto,
                                        cantidad,
                                        notas.joinToString(", "),
                                        seleccionadas,
                                        elegidas,
                                        quitados.filterKeys { it in seleccionadas }.filterValues { it.isNotEmpty() },
                                    ),
                                )
                            },
                            enabled = faltan.isEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Acento,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFEDEDED),
                                disabledContentColor = Color(0xFF71717A),
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(if (movil) 2.4f else 2f).heightIn(min = if (movil) 60.dp else 72.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                when {
                                    faltan.isNotEmpty() -> "Elige: ${faltan.first().nombre}"
                                    linea != null -> "Guardar cambios · ${euros(precioUnidad * cantidad)}"
                                    else -> "Añadir al pedido · ${euros(precioUnidad * cantidad)}"
                                },
                                fontSize = if (movil) 17.sp else 20.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Notas de lo quitado en las opciones elegidas (lo de una opción que ya no está elegida no cuenta). Si
 * se ha editado más de una, cada nota dice de cuál es, para que en cocina no haya dudas.
 */
fun notasDeIngredientes(producto: Producto, seleccionadas: List<Long>, quitados: Map<Long, Set<String>>): List<String> {
    val editadas = producto.grupos.flatMap { it.opciones }
        .filter { it.id in seleccionadas && !quitados[it.id].isNullOrEmpty() }
    return editadas.flatMap { opcion ->
        quitados.getValue(opcion.id).map { if (editadas.size > 1) "${opcion.texto}: $it" else it }
    }
}

/** Desplegable bajo una opción de menú para quitarle ingredientes ("Sin queso", "Sin cebolla"...). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesplegableIngredientes(
    nombre: String,
    ingredientes: List<String>,
    quitados: Set<String>,
    abierto: Boolean,
    alAbrirCerrar: () -> Unit,
    alCambiar: (String) -> Unit,
) {
    Surface(
        color = Color(0xFFFAFAFA),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, Acento),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(onClick = alAbrirCerrar, color = Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Personalizar $nombre", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Tinta)
                        Text(
                            if (quitados.isEmpty()) "Toca lo que quieras quitar" else ingredientes.filter { it in quitados }.joinToString(", "),
                            fontSize = 15.sp,
                            color = GrisTexto,
                        )
                    }
                    Text(if (abierto) "▲" else "▼", fontSize = 18.sp, color = Tinta)
                }
            }
            AnimatedVisibility(visible = abierto) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                ) {
                    ingredientes.forEach { ingrediente ->
                        CasillaOpcion(ingrediente, null, ingrediente in quitados, true) { alCambiar(ingrediente) }
                    }
                }
            }
        }
    }
}

/** Casilla marcable de la ficha (opción de menú o "sin cebolla"), con precio extra si lo tiene. */
@Composable
private fun CasillaOpcion(texto: String, extra: String?, marcada: Boolean, habilitada: Boolean, alPulsar: () -> Unit) {
    Surface(
        onClick = alPulsar,
        enabled = habilitada,
        color = if (marcada) AcentoSuave else if (habilitada) Color.White else Color(0xFFF4F4F5),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, if (marcada) Acento else Color(0xFFE5E7EB)),
    ) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp)
                    .background(if (marcada) Acento else Color.White, CircleShape)
                    .border(2.dp, if (marcada) Acento else Color(0xFFD1D5DB), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (marcada) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White) }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (habilitada) Tinta else GrisTexto)
                extra?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GrisTexto) }
            }
        }
    }
}

// ---------- Mi pedido ----------

@Composable
fun ResumenKiosco(vm: KioscoVM, ajustes: Ajustes) {
    val carta = vm.carta ?: return
    val api: PediaApi = remember(ajustes.urlBase) { ajustes.api() }
    val enviando = vm.paso == PasoKiosco.ENVIANDO
    var productoAbierto by remember { mutableStateOf<Producto?>(null) }
    var lineaEditada by remember { mutableStateOf<LineaCarrito?>(null) }
    val sugerencias = vm.sugerencias()
    val movil = esMovil()

    Box(Modifier.fillMaxSize().background(Crema)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mi pedido", fontSize = if (movil) 28.sp else 36.sp, fontWeight = FontWeight.Black, color = Tinta, modifier = Modifier.weight(1f))
                    EtiquetaLugar(vm.paraLlevar)
                }
            }
            // Las líneas del pedido ocupan todo el ancho; lo que se puede añadir, en cuadrícula debajo.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (movil) 130.dp else 170.dp),
                contentPadding = PaddingValues(if (movil) 12.dp else 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(vm.lineas, key = { it.clave }, span = { GridItemSpan(maxLineSpan) }) { linea ->
                    FilaPedido(
                        linea = linea,
                        urlFoto = api.urlFoto(linea.producto.fotoPath),
                        icono = carta.iconoDe(linea.producto),
                        bloqueado = enviando,
                        alEditar = { lineaEditada = linea },
                        alCambiar = { delta -> vm.cambiarCantidad(linea.clave, delta) },
                    )
                }
                if (sugerencias.isNotEmpty() && !enviando) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "¿Te apetece algo más?",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Tinta,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(sugerencias, key = { it.id }) { producto ->
                        Sugerencia(producto, api.urlFoto(producto.fotoPath), carta.iconoDe(producto)) { productoAbierto = producto }
                    }
                }
            }
            Surface(color = Color.White, shadowElevation = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(if (movil) 12.dp else 20.dp)) {
                    vm.errorEnvio?.let {
                        AvisoError("No se ha podido enviar el pedido. $it")
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tinta)
                            Text("IVA incluido", fontSize = 15.sp, color = GrisTexto)
                        }
                        Text(euros(vm.total), fontSize = if (movil) 30.sp else 40.sp, fontWeight = FontWeight.Black, color = Tinta)
                    }
                    Spacer(Modifier.height(if (movil) 10.dp else 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(
                            onClick = vm::seguirPidiendo,
                            enabled = !enviando,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier.weight(1f).heightIn(min = if (movil) 60.dp else 76.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Seguir pidiendo", fontSize = if (movil) 17.sp else 20.sp, fontWeight = FontWeight.Bold, color = Tinta, maxLines = 1) }
                        Button(
                            onClick = vm::confirmar,
                            enabled = !enviando && vm.lineas.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1.4f).heightIn(min = if (movil) 60.dp else 76.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            if (enviando) CircularProgressIndicator(color = Tinta, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                            else Text("Finalizar pedido", fontSize = if (movil) 18.sp else 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        }
                    }
                }
            }
        }
        AvisoAnadido(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 200.dp))
    }

    productoAbierto?.let { producto ->
        FichaProductoKiosco(
            producto = producto,
            carta = carta,
            urlFoto = api.urlFoto(producto.fotoPath),
            icono = carta.iconoDe(producto),
            alAnadir = { vm.anadir(it); productoAbierto = null },
            alCerrar = { productoAbierto = null },
        )
    }
    lineaEditada?.let { linea ->
        FichaProductoKiosco(
            producto = linea.producto,
            carta = carta,
            urlFoto = api.urlFoto(linea.producto.fotoPath),
            icono = carta.iconoDe(linea.producto),
            linea = linea,
            alAnadir = { vm.editarLinea(linea.clave, it); lineaEditada = null },
            alCerrar = { lineaEditada = null },
        )
    }
}

@Composable
private fun FilaPedido(
    linea: LineaCarrito,
    urlFoto: String?,
    @DrawableRes icono: Int,
    bloqueado: Boolean,
    alEditar: () -> Unit,
    alCambiar: (Int) -> Unit,
) {
    if (esMovil()) {
        FilaPedidoMovil(linea, urlFoto, icono, bloqueado, alEditar, alCambiar)
        return
    }
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ImagenProducto(urlFoto, icono, Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)), tamanoIcono = 0.6f)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(linea.producto.nombre, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Tinta)
                if (linea.detalle.isNotBlank()) Text(linea.detalle, fontSize = 16.sp, color = GrisTexto)
                Text(euros(linea.precioUnidad) + " / ud.", fontSize = 15.sp, color = GrisTexto)
                if (linea.editable && !bloqueado) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = alEditar,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Acento),
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) { Text("Editar", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tinta) }
                }
            }
            if (!bloqueado) SelectorCantidad(linea.cantidad, { alCambiar(it - linea.cantidad) }, minimo = 0, tamano = 48.dp)
            Text(
                euros(linea.subtotal),
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                color = Tinta,
                textAlign = TextAlign.End,
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

/** En un móvil no cabe todo en una fila: nombre y precio arriba; editar y cantidad, debajo. */
@Composable
private fun FilaPedidoMovil(
    linea: LineaCarrito,
    urlFoto: String?,
    @DrawableRes icono: Int,
    bloqueado: Boolean,
    alEditar: () -> Unit,
    alCambiar: (Int) -> Unit,
) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(12.dp)) {
            Row {
                ImagenProducto(urlFoto, icono, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), tamanoIcono = 0.6f)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(linea.producto.nombre, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Tinta)
                    if (linea.detalle.isNotBlank()) Text(linea.detalle, fontSize = 15.sp, color = GrisTexto)
                }
                Spacer(Modifier.width(8.dp))
                Text(euros(linea.subtotal), fontSize = 19.sp, fontWeight = FontWeight.Black, color = Tinta)
            }
            if (!bloqueado) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (linea.editable) {
                        OutlinedButton(
                            onClick = alEditar,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(2.dp, Acento),
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) { Text("Editar", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tinta) }
                    }
                    Spacer(Modifier.weight(1f))
                    SelectorCantidad(linea.cantidad, { alCambiar(it - linea.cantidad) }, minimo = 0, tamano = 48.dp)
                }
            }
        }
    }
}

@Composable
private fun Sugerencia(producto: Producto, urlFoto: String?, @DrawableRes icono: Int, alPulsar: () -> Unit) {
    Surface(onClick = alPulsar, color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
        Column {
            ImagenProducto(urlFoto, icono, Modifier.fillMaxWidth().height(110.dp))
            Column(Modifier.padding(12.dp)) {
                Text(
                    producto.nombre,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Tinta,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(euros(producto.precio), fontSize = 17.sp, fontWeight = FontWeight.Black, color = Tinta, modifier = Modifier.weight(1f))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Acento), contentAlignment = Alignment.Center) {
                        Text("+", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
    }
}
