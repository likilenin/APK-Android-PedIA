package com.pedia.movil.ui.kiosco

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.Tinta
import com.pedia.movil.ui.Verde
import com.pedia.movil.ui.esMovil
import com.pedia.movil.ui.euros
import kotlinx.coroutines.delay

// Carta del kiosko con la distribución de los autoservicios de las hamburgueserías: categorías con foto
// en una franja arriba, productos en cuadrícula y abajo la barra con inicio, la bolsa del pedido y
// "Finalizar pedido". Un producto con algo que elegir se abre en su asistente paso a paso.

fun Carta.iconoDe(producto: Producto) =
    iconoCategoria(categorias.firstOrNull { it.id == producto.categoriaId }?.nombre.orEmpty())

@Composable
fun CartaKiosco(vm: KioscoVM, ajustes: Ajustes) {
    val carta = vm.carta ?: return
    val api = remember(ajustes.urlBase) { ajustes.api() }
    var productoAbierto by remember { mutableStateOf<Producto?>(null) }
    var confirmarCancelar by remember { mutableStateOf(false) }
    val categoria = carta.categorias.firstOrNull { it.id == vm.categoria } ?: carta.categorias.firstOrNull()
    val movil = esMovil()

    fun abrir(producto: Producto) {
        if (producto.tieneAsistente) productoAbierto = producto else vm.anadir(LineaCarrito(producto, 1))
    }

    Box(Modifier.fillMaxSize().background(Crema)) {
        Column(Modifier.fillMaxSize()) {
            FranjaCategorias(carta, api, categoria?.id) { vm.categoria = it }
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoria?.nombre.orEmpty(),
                    fontSize = if (movil) 24.sp else 30.sp,
                    fontWeight = FontWeight.Black,
                    color = Tinta,
                    modifier = Modifier.weight(1f),
                )
                EtiquetaLugar(vm.paraLlevar)
            }
            // Al cambiar de categoría, los productos se funden con los nuevos.
            AnimatedContent(
                targetState = categoria?.id,
                transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                label = "categoria",
                modifier = Modifier.weight(1f),
            ) { idCategoria ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (movil) 140.dp else 190.dp),
                    contentPadding = PaddingValues(if (movil) 12.dp else 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(carta.productosDe(idCategoria), key = { it.id }) { producto ->
                        TarjetaProductoKiosco(
                            producto = producto,
                            urlFoto = api.urlFoto(producto.fotoPath),
                            icono = carta.iconoDe(producto),
                            enPedido = vm.lineas.filter { it.producto.id == producto.id }.sumOf { it.cantidad },
                        ) { abrir(producto) }
                    }
                }
            }
            BarraKiosco(
                unidades = vm.unidades,
                total = vm.total,
                alInicio = { if (vm.lineas.isEmpty()) vm.cancelar() else confirmarCancelar = true },
                alAtras = null,
                alVerPedido = vm::verResumen,
                textoBoton = "Finalizar pedido",
                alBoton = vm::verResumen,
            )
        }
        AvisoAnadido(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = if (movil) 96.dp else 110.dp))
        // El asistente del producto sube desde abajo, a pantalla completa.
        AnimatedContent(
            targetState = productoAbierto,
            transitionSpec = { (slideInVertically(tween(320)) { it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(180)) },
            label = "asistente",
        ) { producto ->
            if (producto != null) {
                AsistenteProducto(
                    producto = producto,
                    carta = carta,
                    api = api,
                    sugerencias = vm.complementos(producto),
                    alTerminar = { principal, extras -> productoAbierto = null; vm.anadirDesdeAsistente(principal, extras) },
                    alCerrar = { productoAbierto = null },
                )
            }
        }
    }

    if (confirmarCancelar) {
        DialogoCancelar(alConfirmar = { confirmarCancelar = false; vm.cancelar() }, alCancelar = { confirmarCancelar = false })
    }
}

@Composable
private fun DialogoCancelar(alConfirmar: () -> Unit, alCancelar: () -> Unit) {
    DialogoConfirmar(
        titulo = "¿Volver al inicio?",
        texto = "Se borrará todo lo que has elegido.",
        textoSi = "Sí, empezar de nuevo",
        peligroso = true,
        alConfirmar = alConfirmar,
        alCancelar = alCancelar,
    )
}

/** Categorías en una franja arriba, cada una con la foto de uno de sus productos (o su dibujo). */
@Composable
private fun FranjaCategorias(carta: Carta, api: PediaApi, elegida: Long?, alElegir: (Long) -> Unit) {
    val movil = esMovil()
    Surface(color = Color.White, shadowElevation = 3.dp) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(carta.categorias, key = { it.id }) { cat ->
                val marcada = cat.id == elegida
                val conFoto = carta.productosDe(cat.id).firstOrNull { it.fotoPath != null }
                Surface(
                    onClick = { alElegir(cat.id) },
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(if (marcada) 3.dp else 1.dp, if (marcada) Acento else Color(0xFFE5E7EB)),
                    modifier = Modifier.width(if (movil) 108.dp else 140.dp),
                ) {
                    Column {
                        ImagenProducto(
                            conFoto?.let { api.urlFoto(it.fotoPath) },
                            iconoCategoria(cat.nombre),
                            Modifier.fillMaxWidth().height(if (movil) 64.dp else 84.dp),
                            tamanoIcono = 0.7f,
                        )
                        Text(
                            cat.nombre,
                            fontSize = if (movil) 13.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (marcada) Color.White else Tinta,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().background(if (marcada) Acento else Color.White).padding(vertical = 6.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
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

/** Producto de la carta: foto, nombre y precio. Al tocarlo se abre su asistente o se añade. */
@Composable
private fun TarjetaProductoKiosco(
    producto: Producto,
    urlFoto: String?,
    @DrawableRes icono: Int,
    enPedido: Int,
    alPulsar: () -> Unit,
) {
    val disponible = producto.estaDisponible
    Surface(
        onClick = alPulsar,
        enabled = disponible,
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        border = if (enPedido > 0) BorderStroke(3.dp, Acento) else null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                ImagenProducto(urlFoto, icono, Modifier.fillMaxWidth().aspectRatio(1.25f))
                if (!disponible) {
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Text("AGOTADO", fontWeight = FontWeight.Black, color = GrisTexto, fontSize = 18.sp)
                    }
                }
                if (enPedido > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Acento),
                        contentAlignment = Alignment.Center,
                    ) { Text(enPedido.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
                }
            }
            Text(
                producto.nombre,
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (disponible) Tinta else GrisTexto,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp),
            )
            Text(
                euros(producto.precio),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (disponible) GrisTexto else Color(0xFFB4B4B8),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
        }
    }
}

/** Barra de abajo: volver, inicio, la bolsa con lo pedido y el botón para seguir. */
@Composable
private fun BarraKiosco(
    unidades: Int,
    total: Long,
    alInicio: () -> Unit,
    alAtras: (() -> Unit)?,
    /** En la carta, la bolsa es un botón "Ver mi pedido" para revisar o cambiar lo pedido. */
    alVerPedido: (() -> Unit)? = null,
    textoBoton: String,
    cargando: Boolean = false,
    alBoton: () -> Unit,
) {
    val movil = esMovil()
    Surface(color = Color.White, shadowElevation = 16.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (movil) 10.dp else 18.dp, vertical = if (movil) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (alAtras != null) {
                BotonRedondoKiosco(Icons.AutoMirrored.Filled.ArrowBack, "Volver", alAtras)
                Spacer(Modifier.width(10.dp))
            }
            BotonRedondoKiosco(Icons.Filled.Home, "Volver al inicio", alInicio)
            Spacer(Modifier.weight(1f))
            if (alVerPedido != null) {
                OutlinedButton(
                    onClick = alVerPedido,
                    enabled = unidades > 0,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(2.dp, if (unidades > 0) Acento else Color(0xFFE5E7EB)),
                    modifier = Modifier.heightIn(min = if (movil) 54.dp else 64.dp),
                    contentPadding = PaddingValues(horizontal = if (movil) 10.dp else 18.dp),
                ) {
                    Bolsa(unidades)
                    Spacer(Modifier.width(6.dp))
                    Text(if (movil) "Mi pedido" else "Ver mi pedido", fontSize = if (movil) 15.sp else 18.sp, fontWeight = FontWeight.Bold, color = Tinta, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(euros(total), color = Tinta, fontSize = if (movil) 16.sp else 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            } else {
                Bolsa(unidades)
                Spacer(Modifier.width(8.dp))
                Text(euros(total), color = Tinta, fontSize = if (movil) 19.sp else 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Spacer(Modifier.width(if (movil) 8.dp else 16.dp))
            Button(
                onClick = alBoton,
                enabled = unidades > 0 && !cargando,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Acento,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFEDEDED),
                    disabledContentColor = Color(0xFFA1A1AA),
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.heightIn(min = if (movil) 54.dp else 64.dp),
                contentPadding = PaddingValues(horizontal = if (movil) 16.dp else 28.dp),
            ) {
                if (cargando) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                else Text(textoBoton, fontSize = if (movil) 17.sp else 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

/** La bolsa del pedido con el número de productos encima. */
@Composable
private fun Bolsa(unidades: Int) {
    val movil = esMovil()
    Box {
        Icon(Icons.Filled.ShoppingCart, contentDescription = "Mi pedido", tint = Tinta, modifier = Modifier.padding(top = 6.dp, end = 8.dp).size(if (movil) 28.dp else 36.dp))
        if (unidades > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Acento),
                contentAlignment = Alignment.Center,
            ) { Text(unidades.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        }
    }
}

/** Aviso verde "… agregado a tu pedido" que sube un momento sobre la barra, como en los autoservicios. */
@Composable
private fun AvisoAnadido(vm: KioscoVM, modifier: Modifier) {
    LaunchedEffect(vm.avisoId) {
        if (vm.avisoAnadido != null) {
            delay(2600)
            vm.avisoAnadido = null
        }
    }
    AnimatedVisibility(
        visible = vm.avisoAnadido != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Surface(color = Verde, shape = RoundedCornerShape(14.dp), shadowElevation = 8.dp, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Text(
                vm.avisoAnadido ?: "",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
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

// ---------- Revisa tu pedido ----------

@Composable
fun ResumenKiosco(vm: KioscoVM, ajustes: Ajustes) {
    val carta = vm.carta ?: return
    val api: PediaApi = remember(ajustes.urlBase) { ajustes.api() }
    val enviando = vm.paso == PasoKiosco.ENVIANDO
    var productoAbierto by remember { mutableStateOf<Producto?>(null) }
    var lineaEditada by remember { mutableStateOf<LineaCarrito?>(null) }
    var confirmarCancelar by remember { mutableStateOf(false) }
    val sugerencias = vm.sugerencias()
    val movil = esMovil()

    Box(Modifier.fillMaxSize().background(Crema)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Text(
                    "Revisa tu pedido",
                    fontSize = if (movil) 26.sp else 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Tinta,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            }
            // El pedido y el cambio de lugar ocupan todo el ancho; lo que se puede añadir, en cuadrícula debajo.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (movil) 130.dp else 170.dp),
                contentPadding = PaddingValues(if (movil) 12.dp else 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TarjetaPedido(vm, carta, api, bloqueado = enviando, alEditar = { lineaEditada = it })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilaLugar(vm.paraLlevar, habilitada = !enviando, alCambiar = vm::cambiarLugar)
                }
                if (sugerencias.isNotEmpty() && !enviando) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("¿Te apetece algo más?", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Tinta, modifier = Modifier.padding(top = 12.dp))
                    }
                    items(sugerencias, key = { it.id }) { producto ->
                        Sugerencia(producto, api.urlFoto(producto.fotoPath), carta.iconoDe(producto)) {
                            if (producto.tieneAsistente) productoAbierto = producto else vm.anadir(LineaCarrito(producto, 1))
                        }
                    }
                }
            }
            vm.errorEnvio?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { AvisoError("No se ha podido enviar el pedido. $it") }
            }
            BarraKiosco(
                unidades = vm.unidades,
                total = vm.total,
                alInicio = { confirmarCancelar = true },
                alAtras = vm::seguirPidiendo,
                textoBoton = "Proceder al pago",
                cargando = enviando,
                alBoton = vm::confirmar,
            )
        }
        AvisoAnadido(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = if (movil) 96.dp else 110.dp))
        AnimatedContent(
            targetState = productoAbierto to lineaEditada,
            transitionSpec = { (slideInVertically(tween(320)) { it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(180)) },
            label = "asistente",
        ) { (producto, linea) ->
            when {
                linea != null -> AsistenteProducto(
                    producto = linea.producto,
                    carta = carta,
                    api = api,
                    linea = linea,
                    alTerminar = { principal, _ -> vm.editarLinea(linea.clave, principal); lineaEditada = null },
                    alCerrar = { lineaEditada = null },
                )
                producto != null -> AsistenteProducto(
                    producto = producto,
                    carta = carta,
                    api = api,
                    sugerencias = vm.complementos(producto),
                    alTerminar = { principal, extras -> productoAbierto = null; vm.anadirDesdeAsistente(principal, extras) },
                    alCerrar = { productoAbierto = null },
                )
            }
        }
    }

    if (confirmarCancelar) {
        DialogoCancelar(alConfirmar = { confirmarCancelar = false; vm.cancelar() }, alCancelar = { confirmarCancelar = false })
    }
}

/** Todo lo pedido en una tarjeta: cada producto con su cantidad, lo elegido y el total. */
@Composable
private fun TarjetaPedido(vm: KioscoVM, carta: Carta, api: PediaApi, bloqueado: Boolean, alEditar: (LineaCarrito) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(if (esMovil()) 12.dp else 18.dp)) {
            vm.lineas.forEachIndexed { i, linea ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFEFEFF1))
                LineaRevisa(
                    linea = linea,
                    urlFoto = api.urlFoto(linea.producto.fotoPath),
                    icono = carta.iconoDe(linea.producto),
                    bloqueado = bloqueado,
                    alEditar = { alEditar(linea) },
                    alCambiar = { delta -> vm.cambiarCantidad(linea.clave, delta) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFEFEFF1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tinta)
                    Text("IVA incluido", fontSize = 14.sp, color = GrisTexto)
                }
                Text(euros(vm.total), fontSize = if (esMovil()) 28.sp else 34.sp, fontWeight = FontWeight.Black, color = Acento)
            }
        }
    }
}

/** Una línea de "Revisa tu pedido": foto con la cantidad debajo y "Editar"; a la derecha, lo elegido. */
@Composable
private fun LineaRevisa(
    linea: LineaCarrito,
    urlFoto: String?,
    @DrawableRes icono: Int,
    bloqueado: Boolean,
    alEditar: () -> Unit,
    alCambiar: (Int) -> Unit,
) {
    val movil = esMovil()
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (movil) 120.dp else 150.dp)) {
            ImagenProducto(urlFoto, icono, Modifier.size(if (movil) 72.dp else 92.dp).clip(RoundedCornerShape(14.dp)), tamanoIcono = 0.6f)
            if (!bloqueado) {
                Spacer(Modifier.height(8.dp))
                SelectorCantidad(linea.cantidad, { alCambiar(it - linea.cantidad) }, minimo = 0, tamano = if (movil) 38.dp else 44.dp)
                if (linea.editable) {
                    TextButton(onClick = alEditar) { Text("Editar", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Acento) }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(linea.producto.nombre, fontSize = if (movil) 18.sp else 20.sp, fontWeight = FontWeight.ExtraBold, color = Tinta, modifier = Modifier.weight(1f))
                Text(euros(linea.subtotal), fontSize = if (movil) 18.sp else 20.sp, fontWeight = FontWeight.Black, color = Tinta)
            }
            // Lo elegido y lo quitado, una cosa por línea.
            linea.detalle.split(", ").filter { it.isNotBlank() }.forEach {
                Text(it, fontSize = 15.sp, color = GrisTexto)
            }
            if (linea.cantidad > 1) Text("${euros(linea.precioUnidad)} / ud.", fontSize = 14.sp, color = GrisTexto)
        }
    }
}

/** "¿Lo quieres para llevar?": se puede cambiar aquí sin volver a empezar. */
@Composable
private fun FilaLugar(paraLlevar: Boolean, habilitada: Boolean, alCambiar: (Boolean) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_para_llevar), contentDescription = null, tint = Tinta, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("¿Lo quieres para llevar?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tinta)
                Text(if (paraLlevar) "Ahora: para llevar" else "Ahora: para tomar aquí", fontSize = 15.sp, color = GrisTexto)
            }
            Switch(checked = paraLlevar, onCheckedChange = alCambiar, enabled = habilitada)
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
