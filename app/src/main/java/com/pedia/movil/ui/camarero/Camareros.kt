package com.pedia.movil.ui.camarero

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.Camarero
import com.pedia.movil.ui.AvisoError
import com.pedia.movil.ui.FondoOscuro

private val COLORES_CAMARERO = listOf(
    Color(0xFFDB2777), Color(0xFF7C3AED), Color(0xFF0D9488), Color(0xFFEA580C),
    Color(0xFFCA8A04), Color(0xFF4F46E5), Color(0xFF16A34A), Color(0xFFDC2626),
)

/** Cada camarero tiene siempre el mismo color, para reconocer sus mesas de un vistazo. */
fun colorCamarero(id: Long?): Color = COLORES_CAMARERO[(((id ?: 0L) % COLORES_CAMARERO.size) + COLORES_CAMARERO.size).toInt() % COLORES_CAMARERO.size]

fun inicialCamarero(nombre: String?): String = nombre?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** Círculo con la inicial del camarero y su color. */
@Composable
fun InsigniaCamarero(id: Long?, nombre: String?, tamano: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(tamano)
            .clip(CircleShape)
            .background(colorCamarero(id))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(inicialCamarero(nombre), color = Color.White, fontWeight = FontWeight.Black, fontSize = (tamano.value * 0.5f).sp)
    }
}

/** «¿Quién eres?»: al entrar en el modo camarero, cada uno elige su nombre de la lista de PedIA. */
@Composable
fun PantallaElegirCamarero(vm: CamareroVM, ajustes: Ajustes, alAbrirAjustes: () -> Unit, alCambiarModo: () -> Unit) {
    val ultimo = ajustes.camareroId
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FondoOscuro, Color(0xFF1E3A8A))))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(88.dp))
            Text("¿Quién eres?", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(vm.carta?.nombreLocal, "Elige tu nombre para coger tus comandas").joinToString(" · "),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            when {
                vm.camareros.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.camareros, key = { it.id }) { c -> TarjetaCamarero(c, esUltimo = c.id == ultimo) { vm.elegirCamarero(c) } }
                }
                vm.errorCamareros != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AvisoError("No se puede ver la lista de camareros: ${vm.errorCamareros}")
                    Spacer(Modifier.height(8.dp))
                    Text("Se vuelve a intentar solo cada pocos segundos.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    TextButton(onClick = vm::seguirSinElegir) { Text("Entrar sin elegir camarero", color = Color.White) }
                }
                else -> CircularProgressIndicator(color = Color.White)
            }
        }
        TextButton(onClick = alCambiarModo, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("‹ Cambiar de modo", color = Color.White, fontSize = 16.sp)
        }
        IconButton(onClick = alAbrirAjustes, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TarjetaCamarero(camarero: Camarero, esUltimo: Boolean, alPulsar: () -> Unit) {
    Card(
        onClick = alPulsar,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = FondoOscuro),
        border = if (esUltimo) BorderStroke(3.dp, colorCamarero(camarero.id)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().height(150.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            InsigniaCamarero(camarero.id, camarero.nombre, tamano = 64.dp)
            Spacer(Modifier.height(10.dp))
            Text(camarero.nombre, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (esUltimo) Text("Último en entrar", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
    }
}

/** Elegir a quién pasar una comanda. */
@Composable
fun DialogoCamarero(titulo: String, camareros: List<Camarero>, actual: Long?, alElegir: (Camarero) -> Unit, alCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                camareros.forEach { c ->
                    OutlinedButton(
                        onClick = { alElegir(c) },
                        enabled = c.id != actual,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        InsigniaCamarero(c.id, c.nombre, tamano = 24.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(if (c.id == actual) "${c.nombre} (ahora)" else c.nombre, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
