package com.pedia.movil.ui.camarero

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedia.movil.datos.Carta
import com.pedia.movil.datos.LineaCarrito
import com.pedia.movil.datos.Producto
import com.pedia.movil.ui.AzulPedia
import com.pedia.movil.ui.FondoOscuro
import com.pedia.movil.ui.Rojo
import com.pedia.movil.ui.SelectorCantidad
import com.pedia.movil.ui.VentanaPedia
import com.pedia.movil.ui.euros
import com.pedia.movil.ui.kiosco.notasDeIngredientes

// Ficha de un menú para el camarero: sin foto ni adornos, sube desde abajo para tenerla a mano y
// cada elección es un toque. El botón de añadir queda siempre a la vista, aunque la lista sea larga.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FichaMenuCamarero(
    producto: Producto,
    carta: Carta,
    /** La línea de la comanda que se está editando; sin ella, la ficha añade una nueva. */
    linea: LineaCarrito? = null,
    /** Al editar varias unidades: guarda los cambios solo en una de ellas. */
    alGuardarSoloUna: ((LineaCarrito) -> Unit)? = null,
    alGuardar: (LineaCarrito) -> Unit,
    alCerrar: () -> Unit,
) {
    var cantidad by remember { mutableIntStateOf(linea?.cantidad ?: 1) }
    var elegidas by remember { mutableStateOf(linea?.opcionesMarcadas.orEmpty()) }
    // Opciones elegidas en cada grupo (id del grupo → ids de las opciones).
    var menu by remember {
        mutableStateOf(
            linea?.let { l -> producto.grupos.associate { g -> g.id to g.opciones.map { it.id }.filter { it in l.selecciones } } }.orEmpty(),
        )
    }
    var quitados by remember { mutableStateOf(linea?.quitados.orEmpty()) }
    var notaLibre by remember { mutableStateOf(linea?.notaLibre.orEmpty()) }
    val seleccionadas = menu.values.flatten()
    val faltan = producto.grupos.filter { it.obligatorio && menu[it.id].isNullOrEmpty() }
    val precioUnidad = producto.precio + producto.grupos.flatMap { it.opciones }.filter { it.id in seleccionadas }.sumOf { it.suplemento }

    fun lineaElegida(unidades: Int): LineaCarrito {
        val notas = producto.opciones.filter { it in elegidas } + notasDeIngredientes(producto, seleccionadas, quitados) +
            listOfNotNull(notaLibre.trim().ifBlank { null })
        return LineaCarrito(
            producto,
            unidades,
            notas.joinToString(", "),
            seleccionadas,
            elegidas,
            quitados.filterKeys { it in seleccionadas }.filterValues { it.isNotEmpty() },
            notaLibre.trim(),
        )
    }

    VentanaPedia(alCerrar, Alignment.BottomCenter) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        producto.nombre,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FondoOscuro,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = alCerrar) { Icon(Icons.Filled.Clear, "Cerrar") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    producto.grupos.forEach { grupo ->
                        val actuales = menu[grupo.id].orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(grupo.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FondoOscuro)
                                if (grupo in faltan) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Elige una", fontSize = 13.sp, color = Rojo)
                                } else if (grupo.maxSelecciones > 1) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Hasta ${grupo.maxSelecciones}", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                grupo.opciones.forEach { opcion ->
                                    val marcada = opcion.id in actuales
                                    OpcionRapida(
                                        texto = opcion.texto,
                                        extra = when {
                                            opcion.agotada -> "Agotado"
                                            opcion.suplemento > 0 -> "+" + euros(opcion.suplemento)
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
                                    }
                                }
                            }
                            // Lo que se puede quitar de lo elegido, a la vista y sin desplegar nada.
                            grupo.opciones.filter { it.id in actuales }.forEach { opcion ->
                                val ingredientes = carta.ingredientesDe(opcion)
                                if (ingredientes.isNotEmpty()) {
                                    Text("Cambios en ${opcion.texto}", fontSize = 13.sp, color = Color.Gray)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val quitadas = quitados[opcion.id].orEmpty()
                                        ingredientes.forEach { ingrediente ->
                                            OpcionRapida(ingrediente, null, ingrediente in quitadas, true) {
                                                quitados = quitados + (opcion.id to if (ingrediente in quitadas) quitadas - ingrediente else quitadas + ingrediente)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (producto.opciones.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cambios", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FondoOscuro)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                producto.opciones.forEach { opcion ->
                                    val marcada = opcion in elegidas
                                    OpcionRapida(opcion, null, marcada, true) {
                                        elegidas = if (marcada) elegidas - opcion else elegidas + opcion
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = notaLibre,
                        onValueChange = { notaLibre = it.take(200) },
                        label = { Text("Qué pide el cliente") },
                        placeholder = { Text("Sin pan, poco hecha…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (alGuardarSoloUna != null && linea != null && linea.cantidad > 1) {
                            OutlinedButton(
                                onClick = { alGuardarSoloUna(lineaElegida(1)) },
                                enabled = faltan.isEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Guardar solo en 1 de las ${linea.cantidad}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectorCantidad(cantidad, { cantidad = it.coerceIn(1, 99) }, tamano = 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { alGuardar(lineaElegida(cantidad)) },
                                enabled = faltan.isEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            ) {
                                Text(
                                    when {
                                        faltan.isNotEmpty() -> "Falta: ${faltan.first().nombre}"
                                        linea != null -> "Guardar · ${euros(precioUnidad * cantidad)}"
                                        else -> "Añadir · ${euros(precioUnidad * cantidad)}"
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
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
}

/** Una opción de un toque: azul cuando está elegida. */
@Composable
private fun OpcionRapida(texto: String, extra: String?, marcada: Boolean, habilitada: Boolean, alPulsar: () -> Unit) {
    val color = when {
        marcada -> Color.White
        habilitada -> FondoOscuro
        else -> Color.Gray
    }
    Surface(
        onClick = alPulsar,
        enabled = habilitada,
        color = when {
            marcada -> AzulPedia
            habilitada -> Color.White
            else -> Color(0xFFF1F5F9)
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (marcada) AzulPedia else Color(0xFFCBD5E1)),
    ) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (extra != null) {
                Spacer(Modifier.width(6.dp))
                Text(extra, fontSize = 13.sp, color = color.copy(alpha = 0.8f))
            }
        }
    }
}
