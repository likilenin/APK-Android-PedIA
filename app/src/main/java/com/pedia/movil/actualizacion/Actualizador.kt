package com.pedia.movil.actualizacion

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.pedia.movil.BuildConfig
import com.pedia.movil.datos.PediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Lo que describe el version.json publicado junto al APK en cada release de GitHub. */
@Serializable
data class VersionPublicada(
    val versionCode: Int,
    val versionName: String,
    val notas: String = "",
)

/** Resultado que el sistema comunica al terminar (o fallar) la instalación. */
object EstadoInstalacion {
    val error = MutableStateFlow<String?>(null)
}

// Actualizaciones desde las releases de GitHub. Se usan los enlaces fijos
// ".../releases/latest/download/<archivo>" (no la API de GitHub): no tienen límite de
// peticiones y siempre apuntan a la última versión publicada.
object Actualizador {
    const val NOMBRE_APK = "pedia-movil.apk"
    const val NOMBRE_VERSION = "version.json"

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val formatoRepo = Regex("""[\w.-]+/[\w.-]+|https?://\S+""")

    // "usuario/repo" = releases de GitHub. También vale una URL directa a una carpeta con
    // version.json y el APK (para pruebas o para servir las actualizaciones desde la red local).
    private fun urlDescarga(repo: String, archivo: String) =
        if (repo.startsWith("http")) "${repo.trimEnd('/')}/$archivo"
        else "https://github.com/$repo/releases/latest/download/$archivo"

    /** Devuelve la versión publicada si es más nueva que la instalada; null si no hay o no se puede saber. */
    suspend fun buscar(repo: String): VersionPublicada? = withContext(Dispatchers.IO) {
        if (!formatoRepo.matches(repo)) return@withContext null
        runCatching {
            val peticion = Request.Builder().url(urlDescarga(repo, NOMBRE_VERSION)).header("Cache-Control", "no-cache").build()
            http.newCall(peticion).execute().use { respuesta ->
                if (!respuesta.isSuccessful) null
                else PediaApi.json.decodeFromString<VersionPublicada>(respuesta.body?.string().orEmpty())
            }
        }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Descarga el APK al almacenamiento interno informando del progreso (bytes descargados, total o -1). */
    suspend fun descargar(contexto: Context, repo: String, alProgreso: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val destino = File(contexto.cacheDir, "actualizacion.apk")
            destino.delete()
            val respuesta = http.newCall(Request.Builder().url(urlDescarga(repo, NOMBRE_APK)).build()).execute()
            respuesta.use {
                if (!respuesta.isSuccessful) throw IOException("GitHub ha respondido ${respuesta.code}")
                val cuerpo = respuesta.body ?: throw IOException("Descarga vacía")
                val total = cuerpo.contentLength()
                var descargado = 0L
                var ultimoAviso = 0L
                cuerpo.byteStream().use { entrada ->
                    destino.outputStream().use { salida ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val leidos = entrada.read(buffer)
                            if (leidos < 0) break
                            salida.write(buffer, 0, leidos)
                            descargado += leidos
                            val ahora = System.currentTimeMillis()
                            if (ahora - ultimoAviso > 80) {
                                ultimoAviso = ahora
                                alProgreso(descargado, total)
                            }
                        }
                    }
                }
                alProgreso(descargado, total)
                if (total > 0 && descargado != total) throw IOException("La descarga se ha cortado")
            }
            destino
        }

    fun puedeInstalar(contexto: Context) = contexto.packageManager.canRequestPackageInstalls()

    fun intentPermisoInstalar(contexto: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${contexto.packageName}"))

    fun puedeReabrirse(contexto: Context) = Settings.canDrawOverlays(contexto)

    fun intentPermisoReabrir(contexto: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${contexto.packageName}"))

    // Instala con PackageInstaller. Desde Android 12, al actualizarse a sí misma no hace
    // falta que nadie confirme; en versiones anteriores Android muestra su diálogo de
    // "¿Actualizar esta app?" (ver ReceptorInstalacion).
    fun instalar(contexto: Context, apk: File) {
        EstadoInstalacion.error.value = null
        val instalador = contexto.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(contexto.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val idSesion = instalador.createSession(parametros)
        instalador.openSession(idSesion).use { sesion ->
            sesion.openWrite(NOMBRE_APK, 0, apk.length()).use { salida ->
                apk.inputStream().use { it.copyTo(salida) }
                sesion.fsync(salida)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val aviso = PendingIntent.getBroadcast(
                contexto, idSesion, Intent(contexto, ReceptorInstalacion::class.java), flags
            )
            sesion.commit(aviso.intentSender)
        }
    }
}
