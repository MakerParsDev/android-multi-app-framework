# Mucize Dualar — original-Arabic source status

Reviewed 2026-11-02 for [issue #501](https://github.com/MakerParsDev/android-multi-app-framework/issues/501). This is a source audit, not a grading guarantee, medical treatment or financial promise. Existing Latin readings must remain separate from original Arabic.

## Sources by class

- **Quran (11 records):** Exact cited Tanzil Uthmani verses; source edition and CC BY 3.0 notice are in verified_quran_verses.json.
- **Classical hadith (4 records):** Individually cited supplication excerpts in verified_hadith_supplications.json. The collection does not authenticate the legacy benefit statements.
- **Weakly graded transmitted wording (#9 and #15, proposed):** attested_weak_hadith_supplications.json quotes only short, exact Arabic phrases from Ibn Majah 3801 (graded daif by Darussalam) and Abu Dawud 1555 (graded daif by Al-Albani). The UI explicitly warns of weak narration in TR/EN/DE; the historic phrases are not authenticated Prophetic reports, guaranteed reward/debt relief, or alternative Bukhari 6369 wording. All Latin records remain unchanged.
- **Scholarly commentary (#11):** verified_scholarly_supplications.json holds a short public-domain prayer wording quoted in Ali al-Qari's *Mirqat al-Mafatih* [Book of Prayer commentary](https://www.islamweb.net/ar/library/content/79/2643/), around p. 987. The exact full Latin-era prayer expression appears in this work, but is **not thereby authenticated as a Prophetic hadith**. [Tirmidhi 3516](https://sunnah.com/tirmidhi:3516) contains only the shorter opening and grades its reported chain weak. No modern commentary/page layout is bundled.

## Still unverified original-Arabic entries

| Entry | Independent source evidence | Safe disposition |
| --- | --- | --- |
| **#5 — insomnia** | [Dorar](https://dorar.net/h/N824iK19?osoul=1) grades the related Zayd ibn Thabit report **very weak**; variants exist | Preserve Latin and unverified warning; no Prophetic or therapeutic claim |
| **#18 — memory** | [Islamweb](https://www.islamweb.net/ar/fatwa/print.php?id=57083) evaluates a related but *different* study prayer without transmitted chain | Original exact wording not reliably sourced; do not reconstruct Arabic or promise memory improvement |

Only show original Arabic with a source-specific, traceable wording witness and appropriate licensing. Classical literature is not automatically an authenticated hadith. The previous #11 commentary tranche is already merged (16/20 on main before this proposal). This separate weak-narration wording tranche would bring traceable, correctly graded original Arabic to **18/20 only after** protected review and merge. The two still-unsourced entries (#5 and #18) must not be reconstructed or labeled Prophetic.

These changes do not imply Play-signed delivery or human-spoken accessibility acceptance.
