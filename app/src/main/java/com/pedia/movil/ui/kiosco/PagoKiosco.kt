package com.pedia.movil.ui.kiosco

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pedia.movil.R
import com.pedia.movil.impresion.bitmapQr
import com.pedia.movil.ui.Crema
import com.pedia.movil.ui.GrisTexto
import com.pedia.movil.ui.Rojo
import com.pedia.movil.ui.Tinta
import com.pedia.movil.ui.euros

// Pago del pedido del kiosko con la pasarela de Redsys de PedIA: tarjeta en la propia pantalla,
// QR para pagar con el móvil (tarjeta o Bizum) o en caja.

@Composable
fun ElegirPago(vm: KioscoVM) {
    Column(
        Modifier.fillMaxSize().background(Crema).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("¿Cómo quieres pagar?", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Tinta, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Total a pagar", fontSize = 20.sp, color = GrisTexto)
        Text(euros(vm.totalAPagar), fontSize = 52.sp, fontWeight = FontWeight.Black, color = Tinta)
        vm.errorPago?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Rojo, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tamano = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(150.dp)
            if (vm.pagoTarjeta) BotonPago(R.drawable.ic_pago_tarjeta, "Tarjeta", "Paga aquí con tu tarjeta", tamano, vm::pagarConTarjeta)
            if (vm.pagoMovil) BotonPago(R.drawable.ic_pago_movil, "Con el móvil", "Escanea un QR y paga con tarjeta o Bizum", tamano, vm::pagarConMovil)
            if (vm.pagoCaja) BotonPago(R.drawable.ic_pago_caja, "En caja", "Paga en el mostrador con tu número", tamano, vm::pagarEnCaja)
            if (vm.trabajando) CircularProgressIndicator(color = Tinta, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        }
        OutlinedButton(
            onClick = vm::volverAlPedido,
            enabled = !vm.trabajando,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, Color(0xFFD1D5DB)),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 72.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
        ) { Text("← Volver al pedido", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tinta) }
    }
}

@Composable
private fun BotonPago(@DrawableRes icono: Int, titulo: String, texto: String, modifier: Modifier, alPulsar: () -> Unit) {
    Surface(onClick = alPulsar, color = Color.White, shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, modifier = modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icono), contentDescription = null, tint = Tinta, modifier = Modifier.size(96.dp))
            Spacer(Modifier.width(24.dp))
            Column {
                Text(titulo, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Tinta)
                Text(texto, fontSize = 18.sp, color = GrisTexto)
            }
        }
    }
}

/** La pasarela de Redsys (la página de pago de PedIA) dentro del kiosko. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PagoConTarjeta(vm: KioscoVM) {
    val pedido = vm.pedido ?: return
    val url = vm.urlPagoTarjeta ?: return
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_pago_tarjeta), contentDescription = null, tint = Tinta, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pago con tarjeta", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Tinta)
                    Text("Total: ${euros(pedido.total)}", fontSize = 17.sp, color = GrisTexto)
                }
                OutlinedButton(
                    onClick = { vm.elegirOtraFormaDePago() },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Otra forma de pago", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tinta) }
            }
        }
        // Al volver de Redsys, PedIA lleva a /pago/ok o /pago/ko: así se sabe cómo ha ido.
        val terminado = remember(url) { booleanArrayOf(false) }
        fun revisar(direccion: String?) {
            if (direccion == null || terminado[0]) return
            when {
                "/pago/ok" in direccion -> { terminado[0] = true; vm.pasarelaTerminada(true) }
                "/pago/ko" in direccion -> { terminado[0] = true; vm.pasarelaTerminada(false) }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            revisar(request.url.toString())
                            return false
                        }

                        override fun onPageStarted(view: WebView, direccion: String?, favicon: Bitmap?) {
                            revisar(direccion)
                        }
                    }
                    // Evita la página de aviso del túnel gratuito (ngrok) si PedIA redirige por él.
                    loadUrl(url, mapOf("ngrok-skip-browser-warning" to "1"))
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/** QR de cobro de PedIA: el cliente paga en su móvil y el kiosko se entera solo. */
@Composable
fun PagoConMovil(vm: KioscoVM) {
    val pedido = vm.pedido ?: return
    val url = vm.urlPago ?: return
    val qr = remember(url) { bitmapQr(url, 600).asImageBitmap() }
    Column(
        Modifier.fillMaxSize().background(Crema).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Paga con tu móvil", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Tinta)
        Spacer(Modifier.height(8.dp))
        Text(
            "Escanea el código con la cámara y paga con tarjeta o Bizum",
            fontSize = 20.sp,
            color = GrisTexto,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(24.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp) {
            Image(qr, contentDescription = "Código QR de pago", modifier = Modifier.padding(24.dp).size(340.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(euros(pedido.total), fontSize = 44.sp, fontWeight = FontWeight.Black, color = Tinta)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Tinta, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Esperando el pago…", fontSize = 20.sp, color = Tinta)
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = { vm.elegirOtraFormaDePago() },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.heightIn(min = 64.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
        ) { Text("Elegir otra forma de pago", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Tinta) }
    }
}
