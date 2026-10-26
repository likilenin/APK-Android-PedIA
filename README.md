# PedIA Móvil

App Android que trabaja como **esclava de PedIA** (el TPV de escritorio, que es el master) dentro
de la misma wifi. No usa internet para hablar con PedIA: llama directamente a la API que PedIA ya
abre en la red local (`http://<IP del ordenador>:4000`). Solo sale a internet para buscar
actualizaciones en GitHub.

Kotlin + Jetpack Compose. Android 8.0 o superior.

## Modos

**Kiosko** (autoservicio, tipo McDonald's / Burger King)
- Bienvenida → «Para tomar aquí» / «Para llevar» → carta con fotos por categorías → ficha del
  producto con opciones («sin queso»…) y cantidad → resumen → confirmar.
- El pedido entra en PedIA como pedido *para recoger*, con la nota `KIOSKO · Para llevar` o
  `KIOSKO · Para tomar aquí`, y PedIA lo imprime en cocina como cualquier otro.
- Pantalla final con el número de pedido en grande e impresión del ticket del cliente en una
  impresora térmica de red (ESC/POS, puerto 9100, 58 u 80 mm).
- Si nadie toca la pantalla durante un rato pregunta «¿Sigues ahí?» y vuelve al inicio.
- Pantalla completa (sin barras de Android) y fijada: los clientes no pueden salir de la app.
  Menú del encargado: **5 toques seguidos en la esquina superior izquierda** de la bienvenida + PIN.

**Camarero**
- Cuadrícula de mesas (el número de mesas sale de *Ajustes > Datos del local* de PedIA) que se
  refresca sola cada 4 s: libre, ocupada (importe, productos, minutos) o lista (verde).
- Al tocar una mesa: carta rápida con buscador. **Un toque añade 1**, pulsación larga abre
  opciones, nota y cantidad. Botón grande **ENVIAR A COCINA**.
- Si la mesa está libre crea su pedido en PedIA (con `Camarero: <nombre>` en la nota); si ya
  tiene uno abierto, le añade los productos.
- Opciones de la mesa: imprimir cuenta (en la impresora de PedIA), cobrar en efectivo, cambiar de
  mesa, liberar mesa, quitar productos ya enviados.

Los ajustes (conexión, impresora, PIN…) están protegidos con PIN. PIN de fábrica: `1234`.

## Puesta en marcha

1. Instalar el APK en la tablet o el móvil (permitir «instalar apps de origen desconocido»).
2. Abrir la app, elegir el modo. La primera vez lleva a Ajustes: **Buscar PedIA en la wifi** (o
   escribir la IP del ordenador de PedIA) y **Probar conexión**.
3. En el ordenador de PedIA, el cortafuegos de Windows tiene que permitir PedIA en *redes
   privadas* (Windows lo pregunta la primera vez que se abre PedIA). Si la búsqueda no la
   encuentra, es lo primero que hay que mirar.
4. Kiosko: poner la IP de la impresora de tickets y pulsar **Imprimir ticket de prueba**.

## Actualizaciones automáticas

Al abrirse, la app mira `https://github.com/<repo>/releases/latest/download/version.json`. Si
hay una versión más nueva, descarga `pedia-movil.apk` con una barra de progreso y la instala.

- La primera vez Android pide permiso para *instalar apps de esta fuente*: la app lleva a esa
  pantalla.
- Desde Android 12 se instala sin preguntar; en versiones anteriores Android muestra su diálogo
  «¿Actualizar esta app?».
- Para que vuelva a abrirse sola tras actualizar, dar el permiso «Mostrar sobre otras apps»
  (Ajustes > Actualizaciones). Sin él hay que abrirla a mano.
- El repositorio se configura en `gradle.properties` (`REPO_ACTUALIZACIONES`) y se puede cambiar
  desde Ajustes. **El repositorio tiene que ser público** (las tablets descargan sin usuario).

### Publicar una versión nueva

1. Cambiar el código.
2. En `gradle.properties`: subir `VERSION_CODE` en 1 y poner el nuevo `VERSION_NAME`.
3. Escribir las novedades en `novedades.txt` (salen en la pantalla de descarga).
4. Commit, etiqueta `v<VERSION_NAME>` y push con etiquetas:
   ```bash
   git tag v1.0.1
   git push origin main --tags
   ```
5. GitHub Actions (`.github/workflows/publicar.yml`) compila, firma y crea la release. Las tablets
   la instalan la próxima vez que se abra la app.

### Firma (importante)

Todas las versiones tienen que ir firmadas con **la misma clave**, o Android no deja instalar la
actualización encima. La clave está en `firma/` (fuera de git):

- `firma/pedia-movil.jks` y `firma/firma.properties`: para compilar en este PC.
- **Haz una copia de seguridad de la carpeta `firma/`** fuera de este ordenador. Si se pierde, las
  tablets ya instaladas no podrán actualizarse (habría que desinstalar e instalar de nuevo).
- En GitHub (Settings > Secrets and variables > Actions) hay que crear dos secretos con los valores
  de `firma/SECRETOS-GITHUB.txt`: `FIRMA_ALMACEN_BASE64` y `FIRMA_CLAVE`.

## Compilar en este PC

Herramientas en `C:\Users\antua\Android` (JDK 17, Android SDK). Desde la carpeta del proyecto:

```bash
JAVA_HOME=/c/Users/antua/Android/jdk-17 ./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

## Probar en este PC (tablet de pruebas)

Doble clic en **`Abrir tablet de pruebas.bat`**: arranca un PedIA simulado
(`herramientas/pedia-simulada`, puerto 4100, con carta, 12 mesas e impresora de tickets falsa en el
9100), abre el emulador Android (tablet 1280×800) e instala y abre la última versión compilada.
En la app, la conexión es IP `10.0.2.2` (el PC visto desde el emulador) y puerto `4100`.
Atajos del emulador: Ctrl+Retroceso = atrás, Ctrl+H = inicio.

## Estructura

```
app/src/main/java/com/pedia/movil/
  MainActivity.kt            Navegación entre pantallas
  datos/                     Ajustes del dispositivo, modelos y cliente de la API de PedIA,
                             búsqueda de PedIA en la wifi
  impresion/                 Tickets ESC/POS por red
  actualizacion/             Autoactualización desde GitHub
  ui/                        Pantallas: actualización, inicio, ajustes
  ui/kiosco/                 Modo kiosko
  ui/camarero/               Modo camarero
```

## Rutas de PedIA que usa

`GET /api/health`, `/api/categorias`, `/api/productos`, `/api/config`,
`/api/pedidos?estados=…`; `POST /api/pedidos`, `/api/pedidos/:id/items`,
`/api/pedidos/:id/marcar-pagado`, `/liberar-mesa`, `/reimprimir`;
`PATCH /api/pedidos/:id/mesa`, `/api/pedidos/:id/items/:itemId`;
`DELETE /api/pedidos/:id/items/:itemId`. No hace falta ningún cambio en PedIA.
