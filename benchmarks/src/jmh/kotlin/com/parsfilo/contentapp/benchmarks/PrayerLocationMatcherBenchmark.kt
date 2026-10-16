package com.parsfilo.contentapp.benchmarks

import com.parsfilo.contentapp.feature.prayertimes.data.PrayerLocationMatcher
import com.parsfilo.contentapp.feature.prayertimes.model.PrayerCity
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
open class PrayerLocationMatcherBenchmark {
    private lateinit var cities: List<PrayerCity>

    @Setup
    fun setup() {
        val names =
            listOf(
                "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya",
                "Artvin", "Aydın", "Balıkesir", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur",
                "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli", "Diyarbakır", "Edirne",
                "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane",
                "Hakkari", "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir", "Kars", "Kastamonu",
                "Kayseri", "Kırklareli", "Kırşehir", "Kocaeli", "Konya", "Kütahya", "Malatya",
                "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş", "Nevşehir", "Niğde", "Ordu",
                "Rize", "Sakarya", "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat",
                "Trabzon", "Tunceli", "Şanlıurfa", "Uşak", "Van", "Yozgat", "Zonguldak", "Aksaray",
                "Bayburt", "Karaman", "Kırıkkale", "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır",
                "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce",
            )
        cities =
            names.mapIndexed { index, name ->
                PrayerCity(id = index + 1, countryId = 2, nameTr = name, nameEn = name)
            }
    }

    @Benchmark
    fun exactMatchLastCity(): PrayerCity? =
        PrayerLocationMatcher.bestMatch("DÜZCE", cities, { it.nameTr }, { it.nameEn })

    @Benchmark
    fun partialMatch(): PrayerCity? =
        PrayerLocationMatcher.bestMatch("sanliur", cities, { it.nameTr }, { it.nameEn })

    @Benchmark
    fun noMatch(): PrayerCity? =
        PrayerLocationMatcher.bestMatch("Berlin", cities, { it.nameTr }, { it.nameEn })
}
