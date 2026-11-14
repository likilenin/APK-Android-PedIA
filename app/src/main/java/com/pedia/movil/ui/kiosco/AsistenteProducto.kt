package com.pedia.movil.ui.kiosco

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.GrupoProducto
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.OpcionGrupo
import com.pedia.movil.datos.PediaApi
import com.pedia.movil.datos.Producto
import com.pedia.movil.ui.Acento
import com.pedia.movil.ui.AcentoSuave
import com.pedia.movil.ui.Crema
import com.pedia.movil.ui.GrisTexto
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.Tinta
import com.pedia.movil.ui.esMovil
import com.pedia.movil.ui.euros
import kotlinx.coroutines.delay

// Asistente de un producto, paso a paso y a pantalla completa, como en los autoservicios de las
// hamburgueserías: cada grupo del menú es un paso ("Elige tu bebida"...), después se quitan
// ingredientes a lo elegido, se personaliza el producto y, al final, "Completa tu menú". La cabecera
// se queda quieta y el contenido de cada paso entra con un fundido y las tarjetas una tras otra.

private sealed interface PasoAsistente {
    data class Grupo(val grupo: GrupoProducto) : PasoAsistente
    data class Ingredientes(val opcion: OpcionGrupo, val ingredientes: List<String>) : PasoAsistente
    data object Personaliza : PasoAsistente
    data object Completa : PasoAsistente
}

/** ¿Hace falta el asistente? Un producto sin nada que elegir se añade directamente. */
val Producto.tieneAsistente get() = esMenu || opciones.isNotEmpty()

@Composable
fun AsistenteProducto(
    producto: Producto,
    carta: Carta,
    api: PediaApi,
    /** La línea del pedido que se está editando; sin ella, se añade una nueva. */
    linea: LineaCarrito? = null,
    /** Lo que se ofrece en "Completa tu menú" (vacío al editar). */
    sugerencias: List<Producto> = emptyList(),
    alTerminar: (principal: LineaCarrito, extras: List<LineaCarrito>) -> Unit,
    alCerrar: () -> Unit,
) {
    var menu by remember {
        mutableStateOf(
            producto.grupos.associate { g -> g.id to g.opciones.map { it.id }.filter { it in linea?.selecciones.orEmpty() } },
        )
    }
    var quitados by remember { mutableStateOf(linea?.quitados.orEmpty()) }
    var elegidas by remember { mutableStateOf(linea?.opcionesMarcadas.orEmpty()) }
    var extras by remember { mutableStateOf(mapOf<Long, Int>()) }
    var indice by remember { mutableIntStateOf(0) }
    var adelante by remember { mutableStateOf(true) }
    val movil = esMovil()

    // Los pasos dependen de lo elegido: quitar ingredientes solo aparece para lo que se ha elegido.
    val pasos = buildList {
        producto.grupos.forEach { g ->
            add(PasoAsistente.Grupo(g))
            g.opciones.filter { it.id in menu[g.id].orEmpty() }.forEach { o ->
                val ingredientes = carta.ingredientesDe(o)
                if (ingredientes.isNotEmpty()) add(PasoAsistente.Ingredientes(o, ingredientes))
            }
        }
        if (producto.opciones.isNotEmpty()) add(PasoAsistente.Personaliza)
        if (linea == null && sugerencias.isNotEmpty()) add(PasoAsistente.Completa)
    }
    val actual = pasos[indice.coerceIn(pasos.indices)]
    val seleccionadas = menu.values.flatten()
    val precioUnidad = producto.precio + producto.grupos.flatMap { it.opciones }.filter { it.id in seleccionadas }.sumOf { it.suplemento }
    val precioExtras = extras.entries.sumOf { (id, n) -> (sugerencias.firstOrNull { it.id == id }?.precio ?: 0L) * n }
    val ultimo = indice >= pasos.lastIndex
    val puedeSeguir = actual !is PasoAsistente.Grupo || !actual.grupo.obligatorio || menu[actual.grupo.id].orEmpty().isNotEmpty()

    fun terminar() {
        val notas = producto.opciones.filter { it in elegidas } + notasDeIngredientes(producto, seleccionadas, quitados)
        val principal = LineaCarrito(
            producto,
            linea?.cantidad ?: 1,
            notas.joinToString(", "),
            seleccionadas,
            elegidas,
            quitados.filterKeys { it in seleccionadas }.filterValues { it.isNotEmpty() },
        )
        val anadidos = extras.filterValues { it > 0 }.mapNotNull { (id, n) -> sugerencias.firstOrNull { it.id == id }?.let { LineaCarrito(it, n) } }
        alTerminar(principal, anadidos)
    }

    fun seguir() {
        if (!puedeSeguir) return
        if (ultimo) terminar() else { adelante = true; indice++ }
    }

    fun atras() {
        if (indice == 0) alCerrar() else { adelante = false; indice-- }
    }

    BackHandler(onBack = ::atras)

    // Surface y no un simple fondo: así los toques no llegan a la carta que queda debajo.
    Surface(color = Crema, modifier = Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
        // Cabecera fija: nombre y foto de lo que se está eligiendo.
        val (titulo, cabecera) = when (actual) {
            is PasoAsistente.Ingredientes -> actual.opcion.texto to (carta.productoDe(actual.opcion) ?: producto)
            else -> producto.nombre to producto
        }
        Column(
            Modifier.fillMaxWidth().background(Color.White).padding(top = 20.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(titulo, fontSize = if (movil) 26.sp else 32.sp, fontWeight = FontWeight.Black, color = Tinta, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            ImagenProducto(
                api.urlFoto(cabecera.fotoPath),
                carta.iconoDe(cabecera),
                Modifier.height(if (movil) 120.dp else 190.dp).aspectRatio(1.4f).clip(RoundedCornerShape(18.dp)),
                tamanoIcono = 0.6f,
            )
        }
        AnimatedContent(
            targetState = indice,
            transitionSpec = {
                val desde = if (adelante) 1 else -1
                (fadeIn(tween(260, delayMillis = 80)) + slideInHorizontally(tween(300)) { desde * it / 6 }) togetherWith fadeOut(tween(120))
            },
            label = "paso",
            modifier = Modifier.weight(1f),
        ) { i ->
            val paso = pasos[i.coerceIn(pasos.indices)]
            Column(Modifier.fillMaxSize()) {
                val (pregunta, ayuda) = textosDe(paso)
                Text(
                    pregunta,
                    fontSize = if (movil) 22.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Tinta,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 16.dp, end = 16.dp),
                )
                if (ayuda != null) {
                    // En "Completa tu menú" el mensaje va más grande: es lo que invita a añadir algo más.
                    val destacada = paso is PasoAsistente.Completa
                    Text(
                        ayuda,
                        fontSize = if (destacada) (if (movil) 19.sp else 22.sp) else 16.sp,
                        fontWeight = if (destacada) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (destacada) Tinta else GrisTexto,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 16.dp, end = 16.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (movil) 135.dp else 175.dp),
                    contentPadding = PaddingValues(if (movil) 12.dp else 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    when (paso) {
                        is PasoAsistente.Grupo -> itemsIndexed(paso.grupo.opciones, key = { _, o -> o.id }) { n, opcion ->
                            val elegidasGrupo = menu[paso.grupo.id].orEmpty()
                            val marcada = opcion.id in elegidasGrupo
                            val suyo = carta.productoDe(opcion)
                            TarjetaEleccion(
                                titulo = opcion.texto,
                                detalle = when {
                                    opcion.agotada -> "Agotado"
                                    opcion.suplemento > 0 -> "+ " + euros(opcion.suplemento)
                                    else -> null
                                },
                                urlFoto = suyo?.let { api.urlFoto(it.fotoPath) },
                                icono = carta.iconoDe(suyo ?: producto),
                                marcada = marcada,
                                habilitada = !opcion.agotada,
                                modifier = Modifier.aparicion(n),
                            ) {
                                val nuevas = when {
                                    marcada -> elegidasGrupo - opcion.id
                                    paso.grupo.maxSelecciones <= 1 -> listOf(opcion.id)
                                    elegidasGrupo.size < paso.grupo.maxSelecciones -> elegidasGrupo + opcion.id
                                    else -> elegidasGrupo
                                }
                                menu = menu + (paso.grupo.id to nuevas)
                            }
                        }
                        is PasoAsistente.Ingredientes -> itemsIndexed(paso.ingredientes, key = { _, t -> t }) { n, ingrediente ->
                            val suyos = quitados[paso.opcion.id].orEmpty()
                            TarjetaEleccion(
                                titulo = ingrediente,
                                detalle = null,
                                urlFoto = null,
                                icono = null,
                                marcada = ingrediente in suyos,
                                habilitada = true,
                                modifier = Modifier.aparicion(n),
                            ) {
                                quitados = quitados + (paso.opcion.id to if (ingrediente in suyos) suyos - ingrediente else suyos + ingrediente)
                            }
                        }
                        PasoAsistente.Personaliza -> itemsIndexed(producto.opciones, key = { _, t -> t }) { n, opcion ->
                            TarjetaEleccion(
                                titulo = opcion,
                                detalle = null,
                                urlFoto = null,
                                icono = null,
                                marcada = opcion in elegidas,
                                habilitada = true,
                                modifier = Modifier.aparicion(n),
                            ) {
                                elegidas = if (opcion in elegidas) elegidas - opcion else elegidas + opcion
                            }
                        }
                        PasoAsistente.Completa -> itemsIndexed(sugerencias, key = { _, p -> p.id }) { n, sugerido ->
                            TarjetaExtra(
                                producto = sugerido,
                                urlFoto = api.urlFoto(sugerido.fotoPath),
                                icono = carta.iconoDe(sugerido),
                                cantidad = extras[sugerido.id] ?: 0,
                                modifier = Modifier.aparicion(n),
                            ) { extras = extras + (sugerido.id to it.coerceIn(0, 20)) }
                        }
                    }
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 12.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (movil) 12.dp else 24.dp, vertical = if (movil) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonRedondoKiosco(Icons.AutoMirrored.Filled.ArrowBack, "Volver", ::atras)
                Spacer(Modifier.width(14.dp))
                Button(
                    onClick = ::seguir,
                    enabled = puedeSeguir,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Acento,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFEDEDED),
                        disabledContentColor = Color(0xFF71717A),
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).heightIn(min = if (movil) 58.dp else 68.dp),
                ) {
                    Text(
                        when {
                            !puedeSeguir -> "Elige una opción"
                            !ultimo -> "Continuar"
                            linea != null -> "Guardar cambios · ${euros(precioUnidad * linea.cantidad)}"
                            else -> "Añadir al pedido · ${euros(precioUnidad + precioExtras)}"
                        },
                        fontSize = if (movil) 18.sp else 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } }
}

private fun textosDe(paso: PasoAsistente): Pair<String, String?> = when (paso) {
    is PasoAsistente.Grupo -> if (paso.grupo.maxSelecciones > 1 || !paso.grupo.obligatorio) {
        "¿Quieres añadir ${paso.grupo.nombre.lowercase()}?" to
            (if (paso.grupo.maxSelecciones > 1) "Puedes elegir hasta ${paso.grupo.maxSelecciones}" else "Es opcional")
    } else {
        // "Elige tu bebida", "Elige tus patatas".
        val nombre = paso.grupo.nombre.trim().lowercase()
        (if (nombre.endsWith("s")) "Elige tus $nombre" else "Elige tu $nombre") to null
    }
    is PasoAsistente.Ingredientes -> "Personaliza tu ${paso.opcion.texto}" to "Toca lo que quieras quitar"
    PasoAsistente.Personaliza -> "Personaliza tu pedido" to "Toca lo que quieras cambiar"
    PasoAsistente.Completa -> "Completa tu menú" to "¿Te apetece algo más? Añádelo ahora"
}

/** Las tarjetas de cada paso entran una detrás de otra, subiendo un poco y apareciendo. */
@Composable
private fun Modifier.aparicion(indice: Int): Modifier {
    val avance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(indice.coerceAtMost(12) * 45L)
        avance.animateTo(1f, tween(280))
    }
    return graphicsLayer {
        alpha = avance.value
        translationY = (1f - avance.value) * 40.dp.toPx()
    }
}

/** Tarjeta de una elección: con foto (opciones de menú) o solo texto (ingredientes, cambios). */
@Composable
private fun TarjetaEleccion(
    titulo: String,
    detalle: String?,
    urlFoto: String?,
    @DrawableRes icono: Int?,
    marcada: Boolean,
    habilitada: Boolean,
    modifier: Modifier,
    alPulsar: () -> Unit,
) {
    Surface(
        onClick = alPulsar,
        enabled = habilitada,
        color = if (marcada) AcentoSuave else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (marcada) 3.dp else 1.dp, if (marcada) Acento else Color(0xFFE5E7EB)),
        shadowElevation = if (marcada) 4.dp else 1.dp,
        modifier = modifier,
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (icono != null) {
                    ImagenProducto(urlFoto, icono, Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)), tamanoIcono = 0.55f)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    titulo,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (habilitada) Tinta else GrisTexto,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = if (icono == null) 44.dp else 0.dp).padding(top = if (icono == null) 10.dp else 0.dp),
                )
                detalle?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GrisTexto) }
            }
            if (marcada) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Acento),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
        }
    }
}

/** "Completa tu menú": producto con su cantidad para añadirlo de una vez. */
@Composable
private fun TarjetaExtra(producto: Producto, urlFoto: String?, @DrawableRes icono: Int, cantidad: Int, modifier: Modifier, alCambiar: (Int) -> Unit) {
    Surface(
        color = if (cantidad > 0) AcentoSuave else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (cantidad > 0) 3.dp else 1.dp, if (cantidad > 0) Acento else Color(0xFFE5E7EB)),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ImagenProducto(urlFoto, icono, Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)), tamanoIcono = 0.55f)
            Spacer(Modifier.height(8.dp))
            Text(producto.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tinta, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(euros(producto.precio), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Tinta)
            Spacer(Modifier.height(6.dp))
            SelectorCantidad(cantidad, alCambiar, minimo = 0, tamano = 40.dp)
        }
    }
}

/** Botón redondo de la barra de abajo (volver, inicio), como en los autoservicios. */
@Composable
fun BotonRedondoKiosco(icono: ImageVector, descripcion: String, alPulsar: () -> Unit) {
    Surface(
        onClick = alPulsar,
        color = Color.White,
        shape = CircleShape,
        border = BorderStroke(2.dp, Color(0xFFD1D5DB)),
        modifier = Modifier.size(if (esMovil()) 54.dp else 62.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icono, descripcion, tint = Tinta, modifier = Modifier.size(30.dp)) }
    }
}
