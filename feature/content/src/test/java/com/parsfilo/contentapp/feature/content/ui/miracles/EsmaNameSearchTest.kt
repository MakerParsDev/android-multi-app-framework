package com.parsfilo.contentapp.feature.content.ui.miracles

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.core.model.MiraclesPrayer
import org.junit.Test

class EsmaNameSearchTest {
    private fun prayer(latin: String, arabic: String = "", name: String = latin) = MiraclesPrayer(
        duaIsim = name,
        duaAciklama = "",
        duaBesmele = "",
        duaArapca = arabic,
        duaLatinOkunus = latin,
    )

    private val names = (1..99).map { index ->
        when (index) {
            1 -> prayer("er-Rahmân", "الرَّحْمَنُ")
            50 -> prayer("el-Mücîb", "الْمُجِيبُ")
            99 -> prayer("es-Sabûr", "الصَّبُورُ")
            else -> prayer("Name $index")
        }
    }

    @Test fun diacriticFreeSearchPreservesOriginalDetailIndex() {
        assertThat(findEsmaNames(names, "rahman").map { it.index }).containsExactly(0)
        assertThat(findEsmaNames(names, "sabur").map { it.index }).containsExactly(98)
        assertThat(findEsmaNames(names, "MUCIB").map { it.index }).containsExactly(49)
    }

    @Test fun ordinalSearchAndEmptyQueryKeepCanonicalOrder() {
        assertThat(findEsmaNames(names, "99").map { it.index }).containsExactly(98)
        assertThat(findEsmaNames(names, "1").map { it.index }).containsExactly(0)
        assertThat(findEsmaNames(names, "").map { it.index }).containsExactlyElementsIn(0..98).inOrder()
    }

    @Test fun arabicInputAndTurkishDottedIWork() {
        assertThat(findEsmaNames(names, "الرحمن").map { it.index }).containsExactly(0)
        assertThat(findEsmaNames(names, "MÜCİB").map { it.index }).containsExactly(49)
    }

    @Test fun unknownNameReturnsClearableEmptyList() {
        assertThat(findEsmaNames(names, "notfound-xyz")).isEmpty()
    }
}
