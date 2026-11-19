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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.GrupoProducto
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.OpcionGrupo
import com.pedia.movil.datos.PediaApi
import com.pedia.movil.datos.Producto
import com.pedia.movil.datos.claveTexto
import com.pedia.movil.datos.infoAlergenos
import com.pedia.movil.datos.ingredientesQuitables
import com.pedia.movil.datos.textoSin
import com.pedia.movil.ui.Acento
import com.pedia.movil.ui.AcentoSuave
import com.pedia.movil.ui.Crema
import com.pedia.movil.ui.DialogoAlergenos
import com.pedia.movil.ui.GrisTexto
import com.pedia.movil.ui.Rojo
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.Tinta
import com.pedia.movil.ui.esMovil
import com.pedia.movil.ui.euros
import kotlinx.coroutines.delay

// Carrusel de un producto, paso a paso y a pantalla completa, como el de los autoservicios y el de PedIA: cada
// grupo del menú es un paso ("Elige tu bebida"...); los pasos fijos son «Ingredientes» (quitar), «Añadir extra»
// (con suplemento, se pueden repetir) y «Opciones», con el nombre y el orden que el dueño ha puesto en PedIA
// (titulos_pasos); un grupo puede salir solo según lo elegido antes (solo_si). Al final, «Completa tu menú» en el
// kiosko y la nota para cocina con la cantidad en el camarero. La cabecera se queda quieta y el contenido de cada
// paso entra con un fundido y las tarjetas una tras otra.

enum class ModoAsistente { KIOSCO, CAMARERO }

private sealed interface PasoAsistente {
    data class Grupo(val grupo: GrupoProducto) : PasoAsistente
    data class IngredientesDeOpcion(val opcion: OpcionGrupo, val ingredientes: List<String>) : PasoAsistente
    data object Ingredientes : PasoAsistente
    data object Extras : PasoAsistente
    data object Opciones : PasoAsistente
    data object Completa : PasoAsistente
    data object Nota : PasoAsistente
}

private val PASOS_FIJOS = listOf("ingredientes", "extras", "opciones")

/** Un paso del orden del producto: el grupo número [grupo] o uno de los tres pasos fijos. */
private class ElementoPaso(val grupo: Int?, val fijo: String?)

/**
 * Orden de todos los pasos: `orden` es la lista de marcas de PedIA ("#" = el siguiente grupo y "ingredientes" |
 * "extras" | "opciones" = ese paso fijo). Marcas raras o repetidas se ignoran, los grupos que sobran van detrás y
 * los pasos fijos que no salgan, al final en su orden de siempre.
 */
private fun secuenciaDePasos(grupos: Int, orden: List<String>): List<ElementoPaso> {
    val salida = mutableListOf<ElementoPaso>()
    val usados = mutableSetOf<String>()
    var g = 0
    for (marca in orden) {
        if (marca == "#") {
            if (g < grupos) salida += ElementoPaso(g++, null)
        } else if (marca in PASOS_FIJOS && usados.add(marca)) {
            salida += ElementoPaso(null, marca)
        }
    }
    while (g < grupos) salida += ElementoPaso(g++, null)
    for (marca in PASOS_FIJOS) if (marca !in usados) salida += ElementoPaso(null, marca)
    return salida
}

/** Índices de los grupos activos: los que no tienen condición y los que cumplen su `solo_si` con lo elegido antes. */
private fun gruposActivos(grupos: List<GrupoProducto>, elegidas: Set<Long>): Set<Int> {
    val activos = mutableSetOf<Int>()
    grupos.forEachIndexed { i, g ->
        val condicion = g.soloSi
        if (condicion == null) {
            activos += i
        } else {
            val nombre = claveTexto(condicion.grupo)
            val opcion = claveTexto(condicion.opcion)
            val j = grupos.take(i).indexOfFirst { claveTexto(it.nombre) == nombre }
            if (j >= 0 && j in activos && grupos[j].opciones.any { it.id in elegidas && claveTexto(it.texto) == opcion }) activos += i
        }
    }
    return activos
}

@Composable
fun AsistenteProducto(
    producto: Producto,
    carta: Carta,
    api: PediaApi,
    /** La línea del pedido que se está editando; sin ella, se añade una nueva. */
    linea: LineaCarrito? = null,
    modo: ModoAsistente = ModoAsistente.KIOSCO,
    /** Lo que se ofrece en «Completa tu menú» (solo kiosko, y vacío al editar). */
    sugerencias: List<Producto> = emptyList(),
    /** Camarero, al editar varias unidades iguales: guarda los cambios solo en una de ellas. */
    alGuardarSoloUna: ((LineaCarrito) -> Unit)? = null,
    alTerminar: (principal: LineaCarrito, extras: List<LineaCarrito>) -> Unit,
    alCerrar: () -> Unit,
) {
    val camarero = modo == ModoAsistente.CAMARERO
    val grupos = producto.gruposVisibles
    val quitables = producto.ingredientesQuitables
    var menu by remember {
        mutableStateOf(grupos.associate { g -> g.id to g.opciones.map { it.id }.filter { it in linea?.selecciones.orEmpty() } })
    }
    var quitados by remember { mutableStateOf(linea?.quitados.orEmpty()) }
    // Ingredientes del propio producto que se quitan ("sin queso") y modificaciones marcadas ("poco hecha").
    var sinPropios by remember { mutableStateOf(quitables.filter { textoSin(it) in linea?.opcionesMarcadas.orEmpty() }.toSet()) }
    var elegidas by remember { mutableStateOf(producto.opciones.filter { it in linea?.opcionesMarcadas.orEmpty() }.toSet()) }
    var extras by remember { mutableStateOf(linea?.extras.orEmpty()) }
    var complementos by remember { mutableStateOf(mapOf<Long, Int>()) }
    var notaLibre by remember { mutableStateOf(linea?.notaLibre.orEmpty()) }
    var cantidad by remember { mutableIntStateOf(linea?.cantidad ?: 1) }
    var verAlergenos by remember { mutableStateOf(false) }
    var indice by remember { mutableIntStateOf(0) }
    var adelante by remember { mutableStateOf(true) }
    val movil = esMovil()

    // Lo elegido en los grupos que siguen activos (al cambiar una elección se olvidan los pasos que dependían de ella).
    val todasElegidas = menu.values.flatten().toSet()
    val activos = gruposActivos(grupos, todasElegidas)
    val seleccionadas = grupos.filterIndexed { i, _ -> i in activos }.flatMap { menu[it.id].orEmpty() }
    val faltan = grupos.filterIndexed { i, g -> i in activos && g.obligatorio && menu[g.id].isNullOrEmpty() }

    val pasos = buildList<PasoAsistente> {
        secuenciaDePasos(grupos.size, producto.titulosPasos.orden).forEach { e ->
            if (e.grupo != null) {
                if (e.grupo in activos) {
                    val g = grupos[e.grupo]
                    add(PasoAsistente.Grupo(g))
                    // Lo elegido que es un producto de la carta con ingredientes: se le pueden quitar.
                    g.opciones.filter { it.id in menu[g.id].orEmpty() }.forEach { o ->
                        val ingredientes = carta.ingredientesDe(o)
                        if (ingredientes.isNotEmpty()) add(PasoAsistente.IngredientesDeOpcion(o, ingredientes))
                    }
                }
            } else when (e.fijo) {
                "ingredientes" -> if (quitables.isNotEmpty()) add(PasoAsistente.Ingredientes)
                "extras" -> if (producto.extrasDisponibles.isNotEmpty()) add(PasoAsistente.Extras)
                "opciones" -> if (producto.opciones.isNotEmpty()) add(PasoAsistente.Opciones)
            }
        }
        if (camarero) add(PasoAsistente.Nota)
        else if (linea == null && sugerencias.isNotEmpty()) add(PasoAsistente.Completa)
    }
    val actual = pasos.getOrNull(indice.coerceIn(0, (pasos.size - 1).coerceAtLeast(0)))

    val extrasPrecio = extras.entries.sumOf { (t, n) -> (producto.extrasDisponibles.firstOrNull { it.texto == t }?.suplemento ?: 0L) * n }
    val precioUnidad = producto.precio + producto.grupos.flatMap { it.opciones }.filter { it.id in seleccionadas }.sumOf { it.suplemento } + extrasPrecio
    val precioComplementos = complementos.entries.sumOf { (id, n) -> (sugerencias.firstOrNull { it.id == id }?.precio ?: 0L) * n }
    val ultimo = indice >= pasos.lastIndex
    val puedeSeguir = actual !is PasoAsistente.Grupo || !actual.grupo.obligatorio || menu[actual.grupo.id].orEmpty().isNotEmpty()

    fun construir(unidades: Int): LineaCarrito {
        val notas = producto.opciones.filter { it in elegidas } + quitables.filter { it in sinPropios }.map(::textoSin) +
            notasDeIngredientes(producto, seleccionadas, quitados) + listOfNotNull(notaLibre.trim().ifBlank { null })
        return LineaCarrito(
            producto,
            unidades,
            notas.joinToString(", "),
            seleccionadas,
            elegidas + quitables.filter { it in sinPropios }.map(::textoSin),
            quitados.filterKeys { it in seleccionadas }.filterValues { it.isNotEmpty() },
            notaLibre.trim(),
            extras.filterValues { it > 0 },
        )
    }

    fun terminar() {
        val anadidos = complementos.filterValues { it > 0 }.mapNotNull { (id, n) -> sugerencias.firstOrNull { it.id == id }?.let { LineaCarrito(it, n) } }
        alTerminar(construir(if (camarero) cantidad else linea?.cantidad ?: 1), anadidos)
    }

    fun seguir() {
        if (!puedeSeguir) return
        if (ultimo) terminar() else { adelante = true; indice++ }
    }

    fun atras() {
        if (indice == 0) alCerrar() else { adelante = false; indice-- }
    }

    BackHandler(onBack = ::atras)

    // Un producto sin ningún paso (no debería pasar) se añade tal cual.
    if (actual == null) {
        LaunchedEffect(Unit) { terminar() }
        return
    }

    // Surface y no un simple fondo: así los toques no llegan a lo que queda debajo.
    Surface(color = Crema, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Cabecera fija: nombre y foto de lo que se está eligiendo.
            val (titulo, cabecera) = when (actual) {
                is PasoAsistente.IngredientesDeOpcion -> actual.opcion.texto to (carta.productoDe(actual.opcion) ?: producto)
                else -> producto.nombre to producto
            }
            Column(
                Modifier.fillMaxWidth().background(Color.White).padding(top = if (camarero) 12.dp else 20.dp, bottom = if (camarero) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(if (camarero) 96.dp else 0.dp))
                    Text(
                        titulo,
                        fontSize = if (movil) 24.sp else 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Tinta,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // El camarero puede mirar qué alérgenos lleva lo que está eligiendo.
                    if (camarero) {
                        TextButton(onClick = { verAlergenos = true }, modifier = Modifier.width(96.dp)) {
                            Text("Alérgenos", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Acento, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ImagenProducto(
                    api.urlFoto(cabecera.fotoPath),
                    carta.iconoDe(cabecera),
                    Modifier.height(if (camarero) (if (movil) 72.dp else 110.dp) else if (movil) 120.dp else 190.dp).aspectRatio(1.4f).clip(RoundedCornerShape(18.dp)),
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
                val paso = pasos.getOrNull(i) ?: actual
                Column(Modifier.fillMaxSize()) {
                    val (pregunta, ayuda) = textosDe(paso, producto)
                    Text(
                        pregunta,
                        fontSize = if (movil) 21.sp else 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Tinta,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = if (camarero) 12.dp else 18.dp, start = 16.dp, end = 16.dp),
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
                            is PasoAsistente.IngredientesDeOpcion -> itemsIndexed(paso.ingredientes, key = { _, t -> t }) { n, ingrediente ->
                                val suyos = quitados[paso.opcion.id].orEmpty()
                                TarjetaEleccion(
                                    titulo = ingrediente,
                                    detalle = null,
                                    urlFoto = null,
                                    icono = null,
                                    marcada = ingrediente in suyos,
                                    habilitada = true,
                                    quitar = true,
                                    modifier = Modifier.aparicion(n),
                                ) {
                                    quitados = quitados + (paso.opcion.id to if (ingrediente in suyos) suyos - ingrediente else suyos + ingrediente)
                                }
                            }
                            PasoAsistente.Ingredientes -> itemsIndexed(quitables, key = { _, t -> t }) { n, ingrediente ->
                                TarjetaEleccion(
                                    titulo = ingrediente,
                                    detalle = null,
                                    urlFoto = null,
                                    icono = null,
                                    marcada = ingrediente in sinPropios,
                                    habilitada = true,
                                    quitar = true,
                                    modifier = Modifier.aparicion(n),
                                ) {
                                    sinPropios = if (ingrediente in sinPropios) sinPropios - ingrediente else sinPropios + ingrediente
                                }
                            }
                            PasoAsistente.Extras -> itemsIndexed(producto.extrasDisponibles, key = { _, e -> e.texto }) { n, extra ->
                                TarjetaExtraTexto(
                                    texto = extra.texto,
                                    suplemento = extra.suplemento,
                                    veces = extras[extra.texto] ?: 0,
                                    modifier = Modifier.aparicion(n),
                                ) { extras = extras + (extra.texto to it.coerceIn(0, 10)) }
                            }
                            PasoAsistente.Opciones -> itemsIndexed(producto.opciones, key = { _, t -> t }) { n, opcion ->
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
                                    cantidad = complementos[sugerido.id] ?: 0,
                                    modifier = Modifier.aparicion(n),
                                ) { complementos = complementos + (sugerido.id to it.coerceIn(0, 20)) }
                            }
                            PasoAsistente.Nota -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        value = notaLibre,
                                        onValueChange = { notaLibre = it.take(200) },
                                        label = { Text("Qué pide el cliente") },
                                        placeholder = { Text("Sin pan, poco hecha…") },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Cantidad", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Tinta, modifier = Modifier.weight(1f))
                                        SelectorCantidad(cantidad, { cantidad = it.coerceIn(1, 99) }, tamano = 52.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = Color.White, shadowElevation = 12.dp) {
                Column {
                    // Camarero, editando varias unidades iguales: cambiar solo una de ellas.
                    if (camarero && ultimo && alGuardarSoloUna != null && linea != null && linea.cantidad > 1 && cantidad == linea.cantidad) {
                        OutlinedButton(
                            onClick = { alGuardarSoloUna(construir(1)) },
                            enabled = faltan.isEmpty(),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(2.dp, Acento),
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp).heightIn(min = 50.dp),
                        ) { Text("Guardar solo en 1 de las ${linea.cantidad}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tinta) }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = if (movil) 12.dp else 24.dp, vertical = if (movil) 10.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotonRedondoKiosco(Icons.AutoMirrored.Filled.ArrowBack, "Volver", ::atras)
                        Spacer(Modifier.width(12.dp))
                        // Camarero: con lo obligatorio ya elegido, se puede añadir sin pasar por el resto de pasos.
                        if (camarero && !ultimo && faltan.isEmpty()) {
                            OutlinedButton(
                                onClick = ::terminar,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(2.dp, Acento),
                                modifier = Modifier.weight(1f).heightIn(min = if (movil) 54.dp else 64.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    if (linea != null) "Guardar" else "Añadir ya",
                                    fontSize = if (movil) 16.sp else 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Tinta,
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
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
                            modifier = Modifier.weight(1f).heightIn(min = if (movil) 54.dp else 64.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                when {
                                    !puedeSeguir -> "Elige una opción"
                                    !ultimo -> "Continuar"
                                    linea != null -> "Guardar cambios · ${euros(precioUnidad * (if (camarero) cantidad else linea.cantidad))}"
                                    camarero -> "Añadir · ${euros(precioUnidad * cantidad)}"
                                    else -> "Añadir al pedido · ${euros(precioUnidad + precioComplementos)}"
                                },
                                fontSize = if (movil) 17.sp else 22.sp,
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
    if (verAlergenos) DialogoAlergenos(producto.nombre, infoAlergenos(producto, carta, seleccionadas)) { verAlergenos = false }
}

/** Título y ayuda de cada paso; los pasos fijos usan el nombre que el dueño les haya puesto en PedIA. */
private fun textosDe(paso: PasoAsistente, producto: Producto): Pair<String, String?> = when (paso) {
    is PasoAsistente.Grupo -> if (paso.grupo.maxSelecciones > 1 || !paso.grupo.obligatorio) {
        "¿Quieres añadir ${paso.grupo.nombre.lowercase()}?" to
            (if (paso.grupo.maxSelecciones > 1) "Puedes elegir hasta ${paso.grupo.maxSelecciones}" else "Es opcional")
    } else {
        // "Elige tu bebida", "Elige tus patatas".
        val nombre = paso.grupo.nombre.trim().lowercase()
        (if (nombre.endsWith("s")) "Elige tus $nombre" else "Elige tu $nombre") to null
    }
    is PasoAsistente.IngredientesDeOpcion -> "Personaliza tu ${paso.opcion.texto}" to "Toca lo que quieras quitar"
    PasoAsistente.Ingredientes -> (producto.titulosPasos.ingredientes ?: "Ingredientes") to "Toca lo que quieras quitar"
    PasoAsistente.Extras -> (producto.titulosPasos.extras ?: "Añadir extra") to "Cosas que no lleva, con suplemento; usa + para repetirlas"
    PasoAsistente.Opciones -> (producto.titulosPasos.opciones ?: "Opciones") to "Toca lo que quieras cambiar"
    PasoAsistente.Completa -> "Completa tu menú" to "¿Te apetece algo más? Añádelo ahora"
    PasoAsistente.Nota -> "¿Algo más para cocina?" to "Escribe lo que pide el cliente y la cantidad"
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

/**
 * Tarjeta de una elección: con foto (opciones de menú) o solo texto (ingredientes, cambios). Con [quitar], marcarla
 * es quitar algo: sale tachada y en rojo.
 */
@Composable
private fun TarjetaEleccion(
    titulo: String,
    detalle: String?,
    urlFoto: String?,
    @DrawableRes icono: Int?,
    marcada: Boolean,
    habilitada: Boolean,
    modifier: Modifier,
    quitar: Boolean = false,
    alPulsar: () -> Unit,
) {
    val color = if (quitar) Rojo else Acento
    Surface(
        onClick = alPulsar,
        enabled = habilitada,
        color = if (marcada) (if (quitar) Color(0xFFFEE2E2) else AcentoSuave) else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (marcada) 3.dp else 1.dp, if (marcada) color else Color(0xFFE5E7EB)),
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
                    textDecoration = if (quitar && marcada) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = if (icono == null) 44.dp else 0.dp).padding(top = if (icono == null) 10.dp else 0.dp),
                )
                detalle?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GrisTexto) }
            }
            if (marcada) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center,
                ) { Text(if (quitar) "✕" else "✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
        }
    }
}

/** «Añadir extra»: texto, suplemento y −/+ para pedirlo una o varias veces. */
@Composable
private fun TarjetaExtraTexto(texto: String, suplemento: Long, veces: Int, modifier: Modifier, alCambiar: (Int) -> Unit) {
    Surface(
        color = if (veces > 0) AcentoSuave else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (veces > 0) 3.dp else 1.dp, if (veces > 0) Acento else Color(0xFFE5E7EB)),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                texto,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Tinta,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(if (suplemento > 0) "+ ${euros(suplemento)}" else "Gratis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GrisTexto)
            Spacer(Modifier.height(6.dp))
            SelectorCantidad(veces, alCambiar, minimo = 0, tamano = 40.dp)
        }
    }
}

/** «Completa tu menú»: producto con su cantidad para añadirlo de una vez. */
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
