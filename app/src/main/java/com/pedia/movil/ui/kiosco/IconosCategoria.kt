package com.pedia.movil.ui.kiosco

import androidx.annotation.DrawableRes
import com.pedia.movil.R
import java.text.Normalizer

/** Minúsculas y sin tildes, para comparar nombres de categoría ("Menús" → "menus"). */
fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

// PedIA no guarda imagen de las categorías: se elige un dibujo según su nombre. El orden
// importa ("Menús de hamburguesa" es un menú). Si no encaja con nada, un plato con cubiertos.
private val dibujos = listOf(
    listOf("menu", "combo", "oferta", "promo") to R.drawable.ic_cat_menu,
    listOf("hambur", "burger", "bocadillo", "sandw", "wrap") to R.drawable.ic_cat_hamburguesa,
    listOf("pizza") to R.drawable.ic_cat_pizza,
    listOf("ensalada", "verde", "veggie") to R.drawable.ic_cat_ensalada,
    listOf("patata", "frit", "entrante", "complement", "acompan", "snack", "racion", "tapa") to R.drawable.ic_cat_patatas,
    listOf("postre", "helado", "dulce", "tarta", "pastel") to R.drawable.ic_cat_postre,
    listOf("cafe", "desayuno", "infusion", "te ") to R.drawable.ic_cat_cafe,
    listOf("bebida", "refresco", "agua", "cerveza", "vino", "zumo", "batido", "copa") to R.drawable.ic_cat_bebida,
)

@DrawableRes
fun iconoCategoria(nombre: String): Int {
    val n = normalizar(nombre) + " "
    return dibujos.firstOrNull { (claves, _) -> claves.any { it in n } }?.second ?: R.drawable.ic_comer_aqui
}
