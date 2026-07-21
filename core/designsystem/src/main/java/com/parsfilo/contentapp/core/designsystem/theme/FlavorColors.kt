package com.parsfilo.contentapp.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Her flavor için premium renk kimliği.
 *
 * primary        → Ana buton, FAB, aktif nav, başlık rengi
 * primaryDark    → primary'nin koyu tonu (header gradient başlangıcı)
 * primaryDeep    → En derin ton (gradient bitiş / overlay)
 * secondary      → Vurgu, fiyat rozeti, highlight
 * background     → Ekran arka planı (artık saf beyaz değil — hafif tinted)
 * surface        → Kart yüzeyi (hafif tinted)
 * onPrimary      → primary üzerindeki metin/ikon
 * onSurface      → surface üzerindeki metin
 * onBackground   → background üzerindeki metin
 * surfaceVariant → İkincil kart, chip, input arka planı
 * outline        → Border, divider
 * gold           → Altın vurgu (Arapça başlık süsleme, rozet)
 */
data class FlavorColorTokens(
    val primary: Color,
    val primaryDark: Color,
    val primaryDeep: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color = Color(0xFFFFF8EE),
    val onSurface: Color = Color(0xFF1A100C),
    val onBackground: Color = Color(0xFF1A100C),
    val surfaceVariant: Color,
    val outline: Color,
    val gold: Color = Color(0xFFC09030),
)

object FlavorColors {
    fun forFlavor(flavorName: String): FlavorColorTokens = when (flavorName) {
        // ── Ramazan / Vakitler ───────────────────────────────────────────────

        "namazvakitleri" -> FlavorColorTokens(
            // Zengin sıcak orman yeşili + koyu kehribar — namaz huzuru
            primary = Color(0xFF1C3E22),
            primaryDark = Color(0xFF122818),
            primaryDeep = Color(0xFF081408),
            secondary = Color(0xFF8C6610),
            background = Color(0xFFE8EEE0),
            surface = Color(0xFFF0F4E8),
            surfaceVariant = Color(0xFFD2DFCA),
            outline = Color(0xFF9CB88C),
            gold = Color(0xFF8C6610),
        )

        // ── Sesli Sureler ────────────────────────────────────────────────────

        "yasinsuresi" -> FlavorColorTokens(
            // Derin koyu zeytin + antik kehribar — köklü kudsiyet
            primary = Color(0xFF2E3C10),
            primaryDark = Color(0xFF1E280A),
            primaryDeep = Color(0xFF0E1404),
            secondary = Color(0xFF8E6208),
            background = Color(0xFFEAE8D4),
            surface = Color(0xFFF2EEE2),
            surfaceVariant = Color(0xFFD8D4BC),
            outline = Color(0xFFB2A880),
            gold = Color(0xFF8E6208),
        )

        "fetihsuresi" -> FlavorColorTokens(
            // Sıcak derin lacivert + kehribar bronz — zafer ve onur
            primary = Color(0xFF1C2A70),
            primaryDark = Color(0xFF101A50),
            primaryDeep = Color(0xFF080C2E),
            secondary = Color(0xFFA87020),
            background = Color(0xFFEAE8DC),
            surface = Color(0xFFF2F0E4),
            surfaceVariant = Color(0xFFD8D4C0),
            outline = Color(0xFFB0A88C),
            gold = Color(0xFFA87020),
        )

        "amenerrasulu" -> FlavorColorTokens(
            // Zengin koyu bordo + antik altın — derin iman
            primary = Color(0xFF5E1428),
            primaryDark = Color(0xFF420E1C),
            primaryDeep = Color(0xFF280810),
            secondary = Color(0xFFA87830),
            background = Color(0xFFEEE4DC),
            surface = Color(0xFFF6EEE6),
            onSurface = Color(0xFF280E14),
            onBackground = Color(0xFF280E14),
            surfaceVariant = Color(0xFFE2CEC4),
            outline = Color(0xFFC8A898),
            gold = Color(0xFFA87830),
        )

        "ayetelkursi" -> FlavorColorTokens(
            // Sıcak derin mor + kehribar altın — Kürsî yüceliği
            primary = Color(0xFF261258),
            primaryDark = Color(0xFF1A0C3C),
            primaryDeep = Color(0xFF0C0622),
            secondary = Color(0xFF8A6018),
            background = Color(0xFFEAE4DC),
            surface = Color(0xFFF2EEE2),
            surfaceVariant = Color(0xFFDCD0C0),
            outline = Color(0xFFB8A890),
            gold = Color(0xFF8A6018),
        )

        "esmaulhusna" -> FlavorColorTokens(
            // Zengin patlıcan moru + eski altın — 99 ismin ihtişamı
            primary = Color(0xFF4E1262),
            primaryDark = Color(0xFF360C44),
            primaryDeep = Color(0xFF1E0628),
            secondary = Color(0xFFAC7818),
            background = Color(0xFFEEE8D8),
            surface = Color(0xFFF6F0E4),
            surfaceVariant = Color(0xFFE0D4BC),
            outline = Color(0xFFC0A878),
            gold = Color(0xFFAC7818),
        )

        "kenzularsduasi" -> FlavorColorTokens(
            // Sıcak derin navy-teal + terracotta — Arş'ın hazinesi
            primary = Color(0xFF1C3248),
            primaryDark = Color(0xFF102030),
            primaryDeep = Color(0xFF081018),
            secondary = Color(0xFFA83820),
            background = Color(0xFFE8E4D8),
            surface = Color(0xFFF0EAE0),
            surfaceVariant = Color(0xFFD8D0C0),
            outline = Color(0xFFB0A08C),
            gold = Color(0xFFC09040),
        )

        "insirahsuresi" -> FlavorColorTokens(
            // Sıcak derin teal + sıcak menekşe — ferahlık ve genişlik
            primary = Color(0xFF1E4A54),
            primaryDark = Color(0xFF123038),
            primaryDeep = Color(0xFF081820),
            secondary = Color(0xFF825892),
            background = Color(0xFFE4EEE8),
            surface = Color(0xFFEEF4F0),
            surfaceVariant = Color(0xFFCCD8CE),
            outline = Color(0xFF9AB4A8),
            gold = Color(0xFF9A8040),
        )

        "ismiazamduasi" -> FlavorColorTokens(
            // Zengin sıcak indigo + koyu bal kehribarı — yüce isim
            primary = Color(0xFF361060),
            primaryDark = Color(0xFF240840),
            primaryDeep = Color(0xFF120424),
            secondary = Color(0xFF9E6A10),
            background = Color(0xFFEAE4D8),
            surface = Color(0xFFF2EEE2),
            surfaceVariant = Color(0xFFE0D4C0),
            outline = Color(0xFFC0A882),
            gold = Color(0xFF9E6A10),
        )

        "vakiasuresi" -> FlavorColorTokens(
            // Derin yanık toprak + koyu kehribar kor — kıyamet ağırlığı
            primary = Color(0xFF6E2010),
            primaryDark = Color(0xFF4E1608),
            primaryDeep = Color(0xFF2C0C04),
            secondary = Color(0xFFC45C18),
            background = Color(0xFFF0E8DC),
            surface = Color(0xFFF8F0E4),
            surfaceVariant = Color(0xFFEED8C4),
            outline = Color(0xFFD4B090),
            gold = Color(0xFFC45C18),
        )

        "namazsurelerivedualarsesli" -> FlavorColorTokens(
            // Sıcak derin lacivert + kehribar altın — namaz bütünlüğü
            primary = Color(0xFF202E5C),
            primaryDark = Color(0xFF141E42),
            primaryDeep = Color(0xFF080E26),
            secondary = Color(0xFFA46818),
            background = Color(0xFFEAE8DC),
            surface = Color(0xFFF2F0E4),
            surfaceVariant = Color(0xFFD8D4C0),
            outline = Color(0xFFB0A888),
            gold = Color(0xFFA46818),
        )

        // ── Araçlar ──────────────────────────────────────────────────────────

        "kuran_kerim" -> FlavorColorTokens(
            // Zengin mushaf yeşili + parşömen kehribarı — Kuran'ın ruhu
            primary = Color(0xFF1E4028),
            primaryDark = Color(0xFF122818),
            primaryDeep = Color(0xFF081408),
            secondary = Color(0xFF8E5C10),
            background = Color(0xFFE8EEE0),
            surface = Color(0xFFF2F4E8),
            surfaceVariant = Color(0xFFD0DFCA),
            outline = Color(0xFF9CBA90),
            gold = Color(0xFF8E5C10),
        )

        "kible" -> FlavorColorTokens(
            // Derin terracotta + zengin bakır — pusulada yön ve kök
            primary = Color(0xFF7C2C0A),
            primaryDark = Color(0xFF561E06),
            primaryDeep = Color(0xFF300E02),
            secondary = Color(0xFFCC7018),
            background = Color(0xFFF0E8DC),
            surface = Color(0xFFF8F0E4),
            surfaceVariant = Color(0xFFEEDAD0),
            outline = Color(0xFFD4B088),
            gold = Color(0xFFCC7018),
        )

        "mucizedualar" -> FlavorColorTokens(
            // Zengin koyu kırmızı + sıcak altın — mucizevi atmosfer
            primary = Color(0xFF7E1030),
            primaryDark = Color(0xFF580A22),
            primaryDeep = Color(0xFF320510),
            secondary = Color(0xFFC07818),
            background = Color(0xFFF0E4E8),
            surface = Color(0xFFF8EEF0),
            surfaceVariant = Color(0xFFEED4D8),
            outline = Color(0xFFD4A8B0),
            gold = Color(0xFFC07818),
        )

        "zikirmatik" -> FlavorColorTokens(
            // Sıcak koyu lacivert + zengin yeşil teal — meditasyon aracı
            primary = Color(0xFF1E2432),
            primaryDark = Color(0xFF121820),
            primaryDeep = Color(0xFF070A10),
            secondary = Color(0xFF1E8A60),
            background = Color(0xFFE4ECE8),
            surface = Color(0xFFEEF4F0),
            surfaceVariant = Color(0xFFC8DCD4),
            outline = Color(0xFF88B0A0),
            gold = Color(0xFF1E8A60),
        )

        "bereketduasi" -> FlavorColorTokens(
            // Zengin sıcak orman zeytin + bal kehribarı — bereket ve bolluk
            primary = Color(0xFF2C4A28),
            primaryDark = Color(0xFF1C321A),
            primaryDeep = Color(0xFF0C180C),
            secondary = Color(0xFF9C5E10),
            background = Color(0xFFE8EEE0),
            surface = Color(0xFFF2F4E8),
            surfaceVariant = Color(0xFFD2DEC8),
            outline = Color(0xFF9EBC8A),
            gold = Color(0xFF9C5E10),
        )

        "nazarayeti" -> FlavorColorTokens(
            // Sıcak derin kobalt + sıcak teal — göz değmez bariyer
            primary = Color(0xFF1E2462),
            primaryDark = Color(0xFF131844),
            primaryDeep = Color(0xFF080B26),
            secondary = Color(0xFF285E7A),
            background = Color(0xFFE8E6DC),
            surface = Color(0xFFF2F0E4),
            surfaceVariant = Color(0xFFD4CCBC),
            outline = Color(0xFFA09888),
            gold = Color(0xFF6888B0),
        )

        else -> FlavorColorTokens(
            primary = Color(0xFF1E2870),
            primaryDark = Color(0xFF121860),
            primaryDeep = Color(0xFF080C30),
            secondary = Color(0xFF8C6018),
            background = Color(0xFFEAE8DC),
            surface = Color(0xFFF2F0E4),
            surfaceVariant = Color(0xFFD8D4C0),
            outline = Color(0xFFB0A888),
        )
    }
}
