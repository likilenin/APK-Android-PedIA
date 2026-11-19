package com.pedia.movil.datos

import java.text.Normalizer

// Ingredientes y alérgenos de los productos, tal como los guarda PedIA: los ingredientes se pueden quitar al
// tomar el pedido ("sin queso", no cambia el precio) y los alérgenos son los 14 de declaración obligatoria de
// la UE, por clave (las mismas que usa el servidor).

data class Alergeno(val clave: String, val nombre: String, val icono: String)

val ALERGENOS = listOf(
    Alergeno("gluten", "Gluten", "🌾"),
    Alergeno("crustaceos", "Crustáceos", "🦐"),
    Alergeno("huevo", "Huevo", "🥚"),
    Alergeno("pescado", "Pescado", "🐟"),
    Alergeno("cacahuetes", "Cacahuetes", "🥜"),
    Alergeno("soja", "Soja", "🫘"),
    Alergeno("lacteos", "Lácteos", "🥛"),
    Alergeno("frutos_cascara", "Frutos de cáscara", "🌰"),
    Alergeno("apio", "Apio", "🥬"),
    Alergeno("mostaza", "Mostaza", "🟡"),
    Alergeno("sesamo", "Sésamo", "⚪"),
    Alergeno("sulfitos", "Sulfitos", "🍷"),
    Alergeno("altramuces", "Altramuces", "🌼"),
    Alergeno("moluscos", "Moluscos", "🐚"),
)

/** Lo que se guarda en la nota de la línea cuando se quita un ingrediente: "sin queso". No cambia el precio. */
fun textoSin(ingrediente: String) = "sin ${ingrediente.trim().lowercase()}"

/** Sin mayúsculas, tildes ni espacios sobrantes: "  Lácteos " y "lacteos" son lo mismo. */
fun claveTexto(texto: String) =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

/**
 * Lo que se puede quitar de un producto: sus ingredientes menos uno que se llame igual que el propio producto
 * (a veces la carta trae «Hamburguesa de pollo» como primer ingrediente de la «Hamburguesa de pollo»: quitar el
 * producto entero no es quitar un ingrediente). Igual que en PedIA.
 */
val Producto.ingredientesQuitables: List<String>
    get() = ingredientes.filter { claveTexto(it) != claveTexto(nombre) }

/** Un producto (o una opción de menú que es un producto de la carta) con lo que lleva. */
class ParteAlergenos(val nombre: String, val ingredientes: List<String>, val alergenos: List<Alergeno>)

/** Lo que se enseña al pulsar "Ver alérgenos": una parte por cada producto que interviene. */
class InfoAlergenos(val partes: List<ParteAlergenos>, val sinRevisar: Boolean)

/**
 * Ingredientes y alérgenos de [producto] y, si es un menú, de lo elegido en él ([seleccionadas]: ids de las
 * opciones) que corresponde a un producto de la carta. PedIA marca con "revisados" los alérgenos que el dueño
 * ha comprobado: si falta alguno, se avisa de que lo cargado puede estar incompleto.
 */
fun infoAlergenos(producto: Producto, carta: Carta?, seleccionadas: List<Long>): InfoAlergenos {
    fun nombres(claves: List<String>) = claves.mapNotNull { c -> ALERGENOS.firstOrNull { it.clave == c } }
    val partes = mutableListOf<ParteAlergenos>()
    var sinRevisar = false
    val propio = ParteAlergenos(producto.nombre, producto.ingredientes, nombres(producto.alergenos))
    val delMenu = producto.grupos.flatMap { it.opciones }.filter { it.id in seleccionadas }.mapNotNull { o ->
        carta?.productoDe(o)?.let { o to it }
    }
    // El menú en sí casi nunca lleva datos: solo se enseña si los tiene o si no hay nada más que enseñar.
    if (propio.ingredientes.isNotEmpty() || propio.alergenos.isNotEmpty() || delMenu.isEmpty()) {
        partes += propio
        if (producto.alergenosRevisados == 0) sinRevisar = true
    }
    delMenu.forEach { (opcion, suyo) ->
        partes += ParteAlergenos(opcion.texto, suyo.ingredientes, nombres(suyo.alergenos))
        if (suyo.alergenosRevisados == 0) sinRevisar = true
    }
    return InfoAlergenos(partes, sinRevisar)
}
