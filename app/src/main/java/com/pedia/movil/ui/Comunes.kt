package com.pedia.movil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.PIN_INICIAL
import com.pedia.movil.datos.Producto

/** Pide el PIN de ajustes. Con el PIN de fábrica se recuerda cuál es. */
@Composable
fun DialogoPin(ajustes: Ajustes, titulo: String = "PIN de ajustes", alAceptar: () -> Unit, alCancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error) Text("PIN incorrecto", color = MaterialTheme.colorScheme.error)
                if (ajustes.pin == PIN_INICIAL) {
                    Text(
                        "PIN de fábrica: $PIN_INICIAL. Cámbialo en Ajustes > Seguridad.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (pin == ajustes.pin) alAceptar() else error = true }) { Text("Entrar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoConfirmar(
    titulo: String,
    texto: String,
    textoSi: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    peligroso: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            Button(
                onClick = alConfirmar,
                colors = if (peligroso) ButtonDefaults.buttonColors(containerColor = Rojo) else ButtonDefaults.buttonColors(),
            ) { Text(textoSi) }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/** Foto del producto o, si no tiene, un recuadro de color con su inicial. */
@Composable
fun FotoProducto(url: String?, nombre: String, modifier: Modifier = Modifier) {
    val sinFoto: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(AzulSuave, Color(0xFFDBE7FF)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                nombre.take(1).uppercase(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = AzulPedia.copy(alpha = 0.6f),
            )
        }
    }
    Box(modifier) {
        if (url == null) sinFoto()
        else SubcomposeAsyncImage(
            model = url,
            contentDescription = nombre,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { sinFoto() },
            error = { sinFoto() },
        )
    }
}

@Composable
fun SelectorCantidad(cantidad: Int, alCambiar: (Int) -> Unit, minimo: Int = 1, tamano: Dp = 44.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotonRedondo("−", tamano, activo = cantidad > minimo) { alCambiar(cantidad - 1) }
        Text(
            cantidad.toString(),
            modifier = Modifier.width(tamano),
            textAlign = TextAlign.Center,
            fontSize = (tamano.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
        )
        BotonRedondo("+", tamano) { alCambiar(cantidad + 1) }
    }
}

@Composable
fun BotonRedondo(texto: String, tamano: Dp = 44.dp, activo: Boolean = true, alPulsar: () -> Unit) {
    FilledTonalButton(
        onClick = alPulsar,
        enabled = activo,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(tamano),
    ) { Text(texto, fontSize = (tamano.value * 0.5f).sp, fontWeight = FontWeight.Bold) }
}

// Ficha de un producto para elegir opciones ("sin queso"...), nota y cantidad antes de
// añadirlo. La usan el kiosko (al tocar un producto) y el camarero (pulsación larga).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoProducto(
    producto: Producto,
    urlFoto: String?,
    textoBoton: String,
    permitirNota: Boolean,
    alAnadir: (cantidad: Int, notas: String) -> Unit,
    alCerrar: () -> Unit,
) {
    var cantidad by remember { mutableIntStateOf(1) }
    var elegidas by remember { mutableStateOf(setOf<String>()) }
    var nota by remember { mutableStateOf("") }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 560.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (urlFoto != null) {
                    FotoProducto(urlFoto, producto.nombre, Modifier.fillMaxWidth().height(220.dp))
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(producto.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    producto.descripcion?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(euros(producto.precio), style = MaterialTheme.typography.titleLarge, color = AzulPedia, fontWeight = FontWeight.Bold)
                    if (producto.opciones.isNotEmpty()) {
                        Text("Personaliza", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            producto.opciones.forEach { opcion ->
                                FilterChip(
                                    selected = opcion in elegidas,
                                    onClick = { elegidas = if (opcion in elegidas) elegidas - opcion else elegidas + opcion },
                                    label = { Text(opcion, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp)) },
                                )
                            }
                        }
                    }
                    if (permitirNota) {
                        OutlinedTextField(
                            value = nota,
                            onValueChange = { nota = it.take(120) },
                            label = { Text("Nota para cocina") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        SelectorCantidad(cantidad, { cantidad = it.coerceIn(1, 99) }, tamano = 52.dp)
                        TextButton(onClick = alCerrar) { Text("Cancelar", fontSize = 18.sp) }
                    }
                    Button(
                        onClick = {
                            // Mismo formato que PedIA: opciones y nota en un texto separado por comas.
                            val notas = (producto.opciones.filter { it in elegidas } + listOfNotNull(nota.trim().ifBlank { null }))
                                .joinToString(", ")
                            alAnadir(cantidad, notas)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("$textoBoton · ${euros(producto.precio * cantidad)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Etiqueta pequeña de color (estado de un pedido, "Pagada"...). */
@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color) {
    Surface(color = fondo, shape = RoundedCornerShape(50)) {
        Text(
            texto,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun AvisoError(texto: String, alReintentar: (() -> Unit)? = null) {
    Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Rojo))
            Spacer(Modifier.width(12.dp))
            Text(texto, color = Color(0xFF991B1B), modifier = Modifier.weight(1f))
            if (alReintentar != null) TextButton(onClick = alReintentar) { Text("Reintentar") }
        }
    }
}
