package com.pedia.movil.actualizacion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Recibe el resultado de la instalación lanzada por Actualizador.instalar. */
class ReceptorInstalacion : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // Android quiere que alguien confirme la actualización: se abre su diálogo.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmar != null) {
                    confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { contexto.startActivity(confirmar) }
                        .onFailure { EstadoInstalacion.error.value = "Android no ha dejado abrir el diálogo de instalación." }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> EstadoInstalacion.error.value = "Se ha cancelado la instalación."
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                EstadoInstalacion.error.value =
                    "La actualización no es compatible con la app instalada (¿firmada con otra clave?)."
            else -> EstadoInstalacion.error.value =
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No se ha podido instalar la actualización."
        }
    }
}

/**
 * Al instalarse una versión nueva, Android cierra la app. Esto la vuelve a abrir sola
 * (en Android 10 o superior solo si tiene el permiso "Mostrar sobre otras apps").
 */
class ReceptorAppActualizada : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val abrir = contexto.packageManager.getLaunchIntentForPackage(contexto.packageName) ?: return
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { contexto.startActivity(abrir) }
    }
}
