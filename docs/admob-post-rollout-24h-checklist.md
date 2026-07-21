# AdMob Post-Rollout 24-48h Checklist

Bu runbook, `2026-03-13` tarihinde canlıya alınan reklam refactor sonrası ilk 48 saatte aynı metrikleri aynı formatta okumak için hazırlanmıştır.

## Baseline

`2026-03-13` bugün-so-far baseline:

- Overall weighted show rate: `38.17%`
- Latest-only weighted show rate: `36.21%`
- Overall weighted match rate: `68.31%`
- Latest-only weighted match rate: `84.63%`

Hedefler:

- Overall weighted show rate: baseline + `4` puan
- Latest-only weighted show rate: baseline + `3` puan
- `Amenerrasulu`, `Yasin`, `Namaz Vakitleri` için interstitial show rate: en az `12%`
- `Amenerrasulu`, `Yasin`, `Namaz Vakitleri` için app open show rate: en az `8%`
- `app_open` tarafında `NOT_LOADED` suppress baskısında belirgin azalma

## Tek Komut

Windows / PowerShell:

```powershell
.\scripts\monetization\admob-post-rollout-check.ps1
```

Varsayılan token dosyası:

- `SECRET/ADMOB_KOTNROL/token.json`

İsteğe bağlı:

```powershell
.\scripts\monetization\admob-post-rollout-check.ps1 -Publisher "pub-3312485084079132"
```

Üretilen artefaktlar:

- `TEMP_OUT/admob_today_report.json`
- `TEMP_OUT/admob_today_latest_report.json`

## Karar Kuralları

### Sağlıklı işaretler

- Overall weighted show rate artıyorsa
- Latest-only weighted show rate artıyorsa
- Latest-only request başına gelir düşmüyorsa
- `Namaz Sureleri`, `İnşirah`, `Esma-ül Hüsna` gibi toparlanan uygulamalar trendi koruyorsa

### Kırmızı bayraklar

- Overall show rate baseline altında kalıyorsa
- Latest-only show rate `36.21%` altında kalıyorsa
- `Amenerrasulu` app open show rate `0-5%` bandında kalıyorsa
- `Yasin` interstitial show rate `5%` civarında kalıyorsa
- `Namaz Vakitleri` latest-only low performer listesinde kalmaya devam ediyorsa

## Elle Kontrol Edilecekler

Admin panel:

- `Ad Health > Today so far`
- `Ad Health > Today so far (live latest versions)`
- `Ad Health > Weekly diagnostics`
- `Format funnel` bloklarında:
  - `app_open`
  - `interstitial`
  - `native`

Android logcat:

- `timber_log` etiketi ile:
  - `show_intent`
  - `show_blocked`
  - `show_not_loaded`
  - `show_started`
  - `show_impression`
  - `show_dismissed`
  - `show_failed`

Özellikle takip edilecek suppress paterni:

- `NOT_LOADED`
- `COOLDOWN`
- `CONTENT_IN_PROGRESS`
- `SESSION_CAP`

## Önerilen Zamanlar

1. İlk kontrol: rollout sonrası `+24 saat`
2. İkinci kontrol: rollout sonrası `+48 saat`
3. Gerekirse üçüncü kontrol: ilk hafta sonu kapanışında weekly diagnostics ile

## Sonraki Adım Kriteri

### Eğer hedefler tutuyorsa

- RC değerleri korunur
- problemli 3 uygulama ayrıca izlenir

### Eğer hedefler tutmuyorsa

- Önce `app_open` ve `interstitial` format kırılımı tekrar incelenir
- Sonra `Amenerrasulu`, `Yasin`, `Namaz Vakitleri` için placement-level audit yapılır
- Gerekirse RC ile:
  - `ads_app_open_cooldown_ms`
  - `ads_app_open_resume_gap_ms`
  - `ads_interstitial_frequency_cap_ms`
  tekrar daraltılır veya gevşetilir
