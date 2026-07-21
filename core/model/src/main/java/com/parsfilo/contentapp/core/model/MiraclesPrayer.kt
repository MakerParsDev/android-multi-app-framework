package com.parsfilo.contentapp.core.model

/**
 * Mucize Dua modeli
 * mucizedualar flavor'ında kullanılır
 */
data class MiraclesPrayer(
    val duaIsim: String,
    val duaAciklama: String,
    val duaBesmele: String,
    val duaArapca: String,
    val duaLatinOkunus: String = ""
)
