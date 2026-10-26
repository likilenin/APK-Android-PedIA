import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firma: en este PC sale de firma/firma.properties (fuera de git); en GitHub Actions, de
// los secretos del repositorio. Siempre la misma clave: si cambia, Android no deja
// instalar la actualización encima de la versión anterior.
val firma = Properties().apply {
    val archivo = rootProject.file("firma/firma.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
fun valorFirma(clave: String, variableEntorno: String): String? =
    firma.getProperty(clave) ?: System.getenv(variableEntorno)

android {
    namespace = "com.pedia.movil"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pedia.movil"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.property("VERSION_CODE") as String).toInt()
        versionName = project.property("VERSION_NAME") as String
        buildConfigField("String", "REPO_ACTUALIZACIONES", "\"${project.property("REPO_ACTUALIZACIONES")}\"")
    }

    signingConfigs {
        create("release") {
            val archivo = valorFirma("storeFile", "FIRMA_ARCHIVO")
            if (archivo != null) {
                storeFile = rootProject.file(archivo)
                storePassword = valorFirma("storePassword", "FIRMA_CLAVE_ALMACEN")
                keyAlias = valorFirma("keyAlias", "FIRMA_ALIAS")
                keyPassword = valorFirma("keyPassword", "FIRMA_CLAVE")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
