package com.parsfilo.contentapp.feature.quran.config

object QuranApiConfig {

    private const val QURAN_API_CDN = "https://cdn.jsdelivr.net/gh/fawazahmed0/quran-api@1"

    const val SURA_LIST_URL = "$QURAN_API_CDN/info.json"

    fun suraEditionUrl(editionId: String, suraNumber: Int): String =
        "$QURAN_API_CDN/editions/$editionId/$suraNumber.json"

    const val ARABIC_EDITION = "ara-quranacademy"
    const val LATIN_EDITION = "tur-latinalphabet"
    const val TURKISH_EDITION = "tur-diyanetisleri"
    const val ENGLISH_EDITION = "eng-abdelhaleem"
    const val GERMAN_EDITION = "deu-aburidamuhammad"

    private const val EVERYAYAH_BASE = "https://everyayah.com/data"
    private const val QURANICAUDIO_MIRROR = "https://mirrors.quranicaudio.com/everyayah"

    object Reciters {
        data class ReciterInfo(
            val id: String,
            val displayName: String,
            val folderName: String,
        )

        val DEFAULT = ReciterInfo(
            id = "alafasy_128",
            displayName = "Mishary Alafasy",
            folderName = "Alafasy_128kbps",
        )

        val ALL = listOf(
            DEFAULT,
            ReciterInfo(
                id = "minshawi_murattal_128",
                displayName = "Minshawi (Murattal)",
                folderName = "Minshawy_Murattal_128kbps",
            ),
            ReciterInfo(
                id = "husary_128",
                displayName = "Husary",
                folderName = "Husary_128kbps",
            ),
            ReciterInfo(
                id = "basfar_192",
                displayName = "Abdullah Basfar",
                folderName = "Abdullah_Basfar_192kbps",
            ),
            ReciterInfo(
                id = "sudais_192",
                displayName = "Abdurrahmaan As-Sudais",
                folderName = "Abdurrahmaan_As-Sudais_192kbps",
            ),
        )
    }

    fun ayahAudioUrl(reciterFolderName: String, suraNumber: Int, ayahNumber: Int): String {
        val fileName = "%03d%03d.mp3".format(suraNumber, ayahNumber)
        return "$EVERYAYAH_BASE/$reciterFolderName/$fileName"
    }

    fun ayahAudioMirrorUrl(reciterFolderName: String, suraNumber: Int, ayahNumber: Int): String {
        val fileName = "%03d%03d.mp3".format(suraNumber, ayahNumber)
        return "$QURANICAUDIO_MIRROR/$reciterFolderName/$fileName"
    }

    const val AUDIO_CACHE_DIR = "quran_audio"

    fun cachedAudioFileName(reciterId: String, suraNumber: Int, ayahNumber: Int): String =
        "${reciterId}_${suraNumber}_$ayahNumber.mp3"
}
