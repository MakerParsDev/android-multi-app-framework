# Mucize Dualar — original-Arabic source status

Reviewed 2026-11-02 for [issue #501](https://github.com/MakerParsDev/android-multi-app-framework/issues/501). This is a source audit, not a grading guarantee, medical treatment or financial promise. Existing Latin readings must remain separate from original Arabic.

## Sources by class

- **Quran (11 records):** Exact cited Tanzil Uthmani verses; source edition and CC BY 3.0 notice are in verified_quran_verses.json.
- **Classical hadith (4 records):** Individually cited supplication excerpts in verified_hadith_supplications.json. The collection does not authenticate the legacy benefit statements.
- **Scholarly commentary (#11):** verified_scholarly_supplications.json holds a short public-domain prayer wording quoted in Ali al-Qari's *Mirqat al-Mafatih* [Book of Prayer commentary](https://www.islamweb.net/ar/library/content/79/2643/), around p. 987. The exact full Latin-era prayer expression appears in this work, but is **not thereby authenticated as a Prophetic hadith**. [Tirmidhi 3516](https://sunnah.com/tirmidhi:3516) contains only the shorter opening and grades its reported chain weak. No modern commentary/page layout is bundled.

## Still unverified original-Arabic entries

| Entry | Independent source evidence | Safe disposition |
| --- | --- | --- |
| **#5 — insomnia** | [Dorar](https://dorar.net/h/N824iK19?osoul=1) grades the related Zayd ibn Thabit report **very weak**; variants exist | Preserve Latin and unverified warning; no Prophetic or therapeutic claim |
| **#9 — gratitude** | [Ibn Majah 3801](https://sunnah.com/ibnmajah:3801) contains the praise phrase but the special-reward narration is **da'if** | Do not claim a verified special benefit or silently import a weak report as authenticated |
| **#15 — debt** | [Abu Dawud 1555](https://sunnah.com/abudawud:1555) resembles the legacy ending and is graded **da'if**; [Bukhari 6369](https://sunnah.com/bukhari:6369) is authentic **with a different ending** | Preserve variants; no silent Bukhari substitution or debt-relief guarantee |
| **#18 — memory** | [Islamweb](https://www.islamweb.net/ar/fatwa/print.php?id=57083) evaluates a related but *different* study prayer without transmitted chain | Original exact wording not reliably sourced; do not reconstruct Arabic or promise memory improvement |

Only show original Arabic with a source-specific, traceable wording witness and appropriate licensing. Classical literature is not automatically an authenticated hadith. Coverage advances from 15/20 on protected main to **16/20 only after** protected review and merge of the #11 commentary implementation. Four entries stay explicitly unverified.

These changes do not imply Play-signed delivery or human-spoken accessibility acceptance.
