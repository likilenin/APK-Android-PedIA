package com.pedia.movil.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pedia.movil.BuildConfig
import com.pedia.movil.actualizacion.Actualizador
import com.pedia.movil.datos.Ajustes
import com.pedia.movil.datos.PediaApi
import com.pedia.movil.datos.PediaEncontrada
import com.pedia.movil.datos.buscarPedia
import com.pedia.movil.datos.ipLocal
import com.pedia.movil.datos.mensajeUsuario
import com.pedia.movil.impresion.Impresora
import com.pedia.movil.impresion.Tickets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    ajustes: Ajustes,
    alVolver: () -> Unit,
    alBuscarActualizaciones: () -> Unit,
    alCambiarModo: () -> Unit,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var ip by remember { mutableStateOf(ajustes.servidorIp) }
    var puerto by remember { mutableStateOf(ajustes.servidorPuerto.toString()) }
    var camarero by remember { mutableStateOf(ajustes.nombreCamarero) }
    var impresoraIp by remember { mutableStateOf(ajustes.impresoraIp) }
    var impresoraPuerto by remember { mutableStateOf(ajustes.impresoraPuerto.toString()) }
    var papel by remember { mutableIntStateOf(ajustes.anchoPapel) }
    var pie by remember { mutableStateOf(ajustes.piePagina) }
    var inactividad by remember { mutableStateOf(ajustes.kioscoInactividadSeg.toString()) }
    var fijar by remember { mutableStateOf(ajustes.kioscoFijarPantalla) }
    var nuevoPin by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf(ajustes.repoActualizaciones) }

    var buscando by remember { mutableStateOf(false) }
    var progresoBusqueda by remember { mutableFloatStateOf(0f) }
    var encontradas by remember { mutableStateOf<List<PediaEncontrada>?>(null) }
    var resultadoConexion by remember { mutableStateOf<String?>(null) }
    var resultadoImpresora by remember { mutableStateOf<String?>(null) }
    var permisoInstalar by remember { mutableStateOf(Actualizador.puedeInstalar(contexto)) }
    var permisoReabrir by remember { mutableStateOf(Actualizador.puedeReabrirse(contexto)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permisoInstalar = Actualizador.puedeInstalar(contexto)
        permisoReabrir = Actualizador.puedeReabrirse(contexto)
    }

    fun guardar() {
        ajustes.servidorIp = ip
        puerto.toIntOrNull()?.let { ajustes.servidorPuerto = it }
        ajustes.nombreCamarero = camarero
        ajustes.impresoraIp = impresoraIp
        impresoraPuerto.toIntOrNull()?.let { ajustes.impresoraPuerto = it }
        ajustes.anchoPapel = papel
        ajustes.piePagina = pie
        inactividad.toIntOrNull()?.let { ajustes.kioscoInactividadSeg = it.coerceIn(15, 600) }
        ajustes.kioscoFijarPantalla = fijar
        if (nuevoPin.length >= 4) ajustes.pin = nuevoPin
        ajustes.repoActualizaciones = repo
    }

    fun guardarYVolver() {
        guardar()
        alVolver()
    }

    BackHandler { guardarYVolver() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = ::guardarYVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = { TextButton(onClick = ::guardarYVolver) { Text("Guardar") } },
            )
        },
    ) { relleno ->
        Column(
            Modifier
                .padding(relleno)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Seccion("Conexión con PedIA") {
                    Text(
                        "El ordenador con PedIA y este dispositivo tienen que estar en la misma wifi. " +
                            "Esta red: ${ipLocal(contexto) ?: "sin conexión"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            ip, { ip = it }, label = { Text("IP del ordenador de PedIA") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            puerto, { puerto = it.filter(Char::isDigit).take(5) }, label = { Text("Puerto") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = !buscando, onClick = {
                            buscando = true
                            encontradas = null
                            alcance.launch {
                                encontradas = buscarPedia(contexto, puerto.toIntOrNull() ?: 4000) { progresoBusqueda = it }
                                buscando = false
                            }
                        }) { Text("Buscar PedIA en la wifi") }
                        OutlinedButton(onClick = {
                            resultadoConexion = "Probando…"
                            alcance.launch {
                                resultadoConexion = try {
                                    val carta = PediaApi("http://${ip.trim()}:${puerto.toIntOrNull() ?: 4000}").cargarCarta()
                                    "✓ Conectado con ${carta.nombreLocal}: ${carta.productos.size} productos, ${carta.mesas} mesas."
                                } catch (e: Exception) {
                                    "✗ ${e.mensajeUsuario()}"
                                }
                            }
                        }) { Text("Probar conexión") }
                    }
                    if (buscando) {
                        LinearProgressIndicator(progress = { progresoBusqueda }, modifier = Modifier.fillMaxWidth())
                        Text("Buscando en la red…", style = MaterialTheme.typography.bodySmall)
                    }
                    encontradas?.let { lista ->
                        if (lista.isEmpty()) {
                            Text(
                                "No se ha encontrado PedIA. Comprueba que PedIA está abierta en el ordenador y que el " +
                                    "cortafuegos de Windows permite conexiones a PedIA en redes privadas.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        lista.forEach { pedia ->
                            Card(Modifier.fillMaxWidth().clickable { ip = pedia.ip; resultadoConexion = "Seleccionado ${pedia.ip}" }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pedia.nombreLocal ?: "PedIA", fontWeight = FontWeight.Bold)
                                        Text(pedia.ip, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(if (ip == pedia.ip) "✓ Elegido" else "Usar este", color = AzulPedia)
                                }
                            }
                        }
                    }
                    resultadoConexion?.let { Text(it) }
                }

                Seccion("Modo camarero") {
                    OutlinedTextField(
                        camarero, { camarero = it }, label = { Text("Nombre del camarero (sale en la comanda)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }

                Seccion("Modo kiosko") {
                    Text("Impresora de tickets por red (ESC/POS). Déjala vacía si el kiosko no imprime.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            impresoraIp, { impresoraIp = it }, label = { Text("IP de la impresora") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            impresoraPuerto, { impresoraPuerto = it.filter(Char::isDigit).take(5) }, label = { Text("Puerto") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp),
                        )
                    }
                    Text("Ancho del papel", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow {
                        listOf(58, 80).forEachIndexed { i, mm ->
                            SegmentedButton(
                                selected = papel == mm,
                                onClick = { papel = mm },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text("$mm mm") }
                        }
                    }
                    OutlinedTextField(
                        pie, { pie = it }, label = { Text("Texto al pie del ticket") }, minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = {
                        guardar()
                        resultadoImpresora = "Imprimiendo…"
                        alcance.launch {
                            resultadoImpresora = try {
                                Impresora.imprimir(ajustes, Tickets.prueba(ajustes))
                                "✓ Ticket de prueba enviado."
                            } catch (e: Exception) {
                                "✗ ${e.mensajeUsuario()}"
                            }
                        }
                    }) { Text("Imprimir ticket de prueba") }
                    resultadoImpresora?.let { Text(it) }
                    HorizontalDivider()
                    OutlinedTextField(
                        inactividad, { inactividad = it.filter(Char::isDigit).take(3) },
                        label = { Text("Segundos sin tocar antes de volver al inicio") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                    FilaInterruptor(
                        "Fijar la pantalla en modo kiosko",
                        "Impide que los clientes salgan de la app. Android pedirá confirmarlo la primera vez.",
                        fijar,
                    ) { fijar = it }
                }

                Seccion("Seguridad") {
                    OutlinedTextField(
                        nuevoPin, { nuevoPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Nuevo PIN de ajustes (mínimo 4 cifras)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                    )
                }

                Seccion("Actualizaciones") {
                    Text("Versión instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    OutlinedTextField(
                        repo, { repo = it }, label = { Text("Repositorio de GitHub (usuario/nombre)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    FilaPermiso(
                        "Instalar actualizaciones",
                        "Necesario para que la app se actualice sola.",
                        permisoInstalar,
                    ) { contexto.startActivity(Actualizador.intentPermisoInstalar(contexto)) }
                    FilaPermiso(
                        "Volver a abrirse tras actualizar",
                        "Permiso «Mostrar sobre otras apps». Sin él, tras actualizar hay que abrir la app a mano.",
                        permisoReabrir,
                    ) { contexto.startActivity(Actualizador.intentPermisoReabrir(contexto)) }
                    Button(onClick = { guardar(); alBuscarActualizaciones() }) { Text("Buscar actualizaciones ahora") }
                }

                Seccion("Modo de uso") {
                    OutlinedButton(onClick = { guardar(); alCambiarModo() }) { Text("Cambiar entre kiosko y camarero") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            contenido()
        }
    }
}

@Composable
private fun FilaInterruptor(titulo: String, texto: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Medium)
            Text(texto, style = MaterialTheme.typography.bodySmall)
        }
        Switch(valor, alCambiar)
    }
}

@Composable
private fun FilaPermiso(titulo: String, texto: String, concedido: Boolean, alPedir: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Medium)
            Text(texto, style = MaterialTheme.typography.bodySmall)
        }
        if (concedido) Text("✓ Concedido", color = Verde)
        else OutlinedButton(onClick = alPedir) { Text("Dar permiso") }
    }
}
