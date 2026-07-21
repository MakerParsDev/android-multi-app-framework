# 30 Günlük Teknik Backlog (Operasyon + Gelir + Stabilite)

Bu plan, mevcut multi-flavor Android yapısını daha güvenli, ölçülebilir ve gelir odaklı hale getirmek için hazırlanmıştır.

## Hedef KPI'lar (30 gün sonunda)
- Crash-free users: `>= %99.5`
- Push registration success: `>= %98`
- Notification tap-to-open success: `>= %99`
- Ad request -> show dönüşümü: `+%10` iyileşme
- ARPDAU: kontrollü deneylerle `+%5-12` iyileşme

## Öncelik Seviyeleri
- `P0`: yayın/stabilite riski, hemen
- `P1`: gelir/operasyon etkisi yüksek
- `P2`: verimlilik ve kalite iyileştirmesi

---

## Hafta 1 (Gün 1-7) — Stabilite ve Release Güvenlik Bariyerleri

### 1) Migration Safety Net (P0)
- İş: Room migration testlerini tüm schema versiyonları için zorunlu hale getir.
- Dosyalar:
  - `core/database/src/test/java/.../AppDatabaseMigrationTest.kt`
  - `core/database/src/main/java/.../AppDatabase.kt`
- Kabul: CI’da migration test fail ederse publish pipeline bloklanır.

### 2) Release Readiness Gate (P0)
- İş: release/publish öncesi tek noktadan preflight script + Gradle task.
- Kontroller: keystore, push URL, play credentials, web client id fallback, flavor versions.
- Dosyalar:
  - `app/build.gradle.kts`
  - `.github/workflows/release.yml`
  - `scripts/final-verification.sh`
- Kabul: eksik parametreyle release job başlamadan fail-fast.

### 3) Crash Triage Otomasyonu (P1)
- İş: en çok etki eden crash’leri günlük raporlayan script.
- Çıktı: flavor bazlı top issue + kullanıcı etkisi.
- Dosyalar:
  - `scripts/` altında yeni `crash_daily_report.*`
- Kabul: günlük rapor artifact olarak CI’da üretilecek.

---

## Hafta 2 (Gün 8-14) — Operasyon Paneli ve Gözlemlenebilirlik

### 4) Flavor Health Dashboard Veri Katmanı (P1)
- İş: dashboard için ortak metrik şeması.
- Metrikler: crash-free, ANR, active devices, push success, ad show rate.
- Dosyalar:
  - `side-projects/firebase/functions/src/` (yeni aggregate endpoint)
  - `side-projects/admin-notifications/src/` (health tab)
- Kabul: son 24 saat ve 7 gün kırılımı listelenebilmeli.

### 5) Device Coverage SLA Alarmı (P1)
- İş: package bazlı aktif cihaz eşiği altına düşüşte alarm.
- Varsayılan: son 14 günde aktif cihaz `< N` ise uyarı.
- Dosyalar:
  - `side-projects/firebase/functions/src/deviceCoverageReport.ts`
- Kabul: admin panelde kırmızı uyarı + eksik package listesi.

### 6) Incident Mode (P2)
- İş: tek tıkla “gelir azalt, stabilite koru” RC profili.
- RC flags: banner/native kapat, interstitial cap yükselt, app-open soğutma artır.
- Dosyalar:
  - `feature/ads/.../AdsPolicyProvider.kt`
  - admin panelde “Incident mode” toggle
- Kabul: 5 dk içinde tüm uygulamalara etki eden güvenli profil.

---

## Hafta 3 (Gün 15-21) — Gelir Ölçümü ve A/B Deney Altyapısı

### 7) Monetization Funnel Raporu (P1)
- İş: format bazlı funnel çıkar (`request -> loaded -> show -> paid`).
- Kırılımlar: flavor, placement, app version.
- Dosyalar:
  - `feature/ads/src/main/java/.../AdRevenueLogger.kt`
  - backend aggregate job (functions)
- Kabul: no-fill ve show düşüşü hızlı teşhis edilebilir olmalı.

### 8) A/B Test Paketi v1 (P1)
- İş: Remote Config ile 2 kontrollü deney.
- Deneyler:
  - `ads_interstitial_frequency_cap_ms`: 150s vs 180s
  - `ads_rewarded_interstitial_max_per_session`: 2 vs 3
- Dosyalar:
  - `feature/ads/.../AdsPolicyProvider.kt`
  - RC template/docs
- Kabul: 7 gün sonunda ARPDAU + retention karşılaştırma raporu.

### 9) Placement Bazlı Gelir Doğrulama (P1)
- İş: mevcut placement key kullanımını ekran bazında doğrula, fallback kaçaklarını raporla.
- Dosyalar:
  - `app/src/main/java/.../AppNavigation.kt`
  - `app/src/*/res/values/ads.xml`
- Kabul: hangi ekranın hangi ad unit’i kullandığı net tablo halinde.

---

## Hafta 4 (Gün 22-30) — Ürünleştirme ve Süreç Sertleştirme

### 10) Push Campaign Templates (P2)
- İş: Cuma/Kandil/Bayram için hazır şablon + timezone-safe schedule.
- Dosyalar:
  - `side-projects/admin-notifications/src/App.tsx`
  - `side-projects/firebase/functions/src/dispatchNotifications.ts`
- Kabul: operatör 30 sn içinde kampanya açabilmeli.

### 11) QA Regression Checklists (P1)
- İş: ads, notifications, auth, migration için release checklist zorunlu hale getir.
- Dosyalar:
  - `docs/ADS_QURAN_RELEASE_QA_CHECKLIST.md`
  - `docs/CI_RELEASE_VALIDATION_PLAN.md`
- Kabul: release PR’ında checklist tamamlanmadan merge yok.

### 12) Haftalık Exec Özeti (P2)
- İş: otomatik kısa rapor (gelir, stabilite, riskler, aksiyonlar).
- Dosyalar:
  - `scripts/weekly_exec_summary.*`
- Kabul: her pazartesi artifact/mesaj olarak üretim.

---

## Uygulama Sırası (Net)
1. Migration + release gate
2. Health dashboard + coverage SLA
3. Funnel ölçüm + A/B deney
4. Şablon kampanyalar + süreç sertleştirme

## Riskler ve Kontrol
- A/B deneylerin retention düşürmesi: küçük cohort + rollback flag.
- Çok flavor’da drift: tek kaynak RC config + weekly drift raporu.
- Operasyon yükü: admin panelde tek ekran özet.

## “Definition of Done”
- Tüm P0/P1 backlog maddeleri merge edilmiş ve CI’dan geçmiş.
- Dashboard’da flavor bazlı sağlık görünürlüğü aktif.
- En az 1 A/B deneyi tamamlanmış ve karar raporu çıkarılmış.
- Release sürecinde manuel sürpriz/hata oranı belirgin düşmüş.
