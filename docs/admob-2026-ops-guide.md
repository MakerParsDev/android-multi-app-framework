# AdMob Operasyon Rehberi (Şubat 2026)

Bu doküman, projedeki AdMob alarm sonuçlarını teknik olarak yorumlamak ve hızlı aksiyon almak için hazırlandı.

## Resmi API kapsamı (2026-02)

## Stabil `v1`
- `accounts.get`
- `accounts.apps.list`
- `accounts.adUnits.list`
- `accounts.networkReport.generate`

## Not
- Portföy şu anda **yalnız AdMob** çalışıyor.
- Üçüncü taraf mediation kullanılmıyor.
- Bu yüzden operasyonel raporlarda `networkReport` temel kaynak kabul edilmeli.

Referans:
- https://developers.google.com/admob/api/v1/get-started
- https://developers.google.com/admob/api/reference/rest/v1/accounts.networkReport/generate
- https://developers.google.com/admob/api/reference/rest/v1/accounts.apps/list
- https://developers.google.com/admob/api/reference/rest/v1/accounts.adUnits/list

## Alarmların olası kök nedenleri

1. `rewarded request var ama gelir yok`
- Reklam yüklense bile kullanıcı gösterimi/impression oluşmuyor olabilir.
- Show rate düşük olabilir (`impressions / matched_requests`).
- Yanlış zamanlama: ad hazır olmadan `show` çağrısı.
- Rewarded ad unit ID doğru olsa bile trafik düşük kaliteli segmentte eCPM çok düşük kalabilir.

2. eCPM düşüşleri (`>= %30`)
- Ülke karışımı değişimi (TR dışı düşük CPM trafiği artışı).
- Tek ağ bağımlılığı nedeniyle açık artırma rekabeti sınırlı kalabilir.
- Session içi gösterim sıklığı/frequency cap nedeniyle düşük değerli gösterimler.
- App version fragmentation nedeniyle farklı davranışların aynı ad unit üzerinde karışması.

3. Düşük fill rate
- Bazı ad unit’lerde talep yoğun ama match düşük (özellikle native/interstitial).
- GDPR ülkelerinde consent funnel eksikliği.
- Tek ağ bağımlılığı (yalnız AdMob Network) nedeniyle açık artırma rekabeti düşük.

## Bu repoda yapılan teknik iyileştirmeler

1. Rewarded delivery görünürlüğü artırıldı:
- `feature/ads/.../RewardedAdManager.kt`
- `feature/ads/.../RewardedInterstitialAdManager.kt`
- `onPaidEvent` ve `onAdImpression` logları eklendi.
- `responseInfo.responseId` ve adapter bilgisi loglanıyor.

2. Rewarded istek israfı azaltıldı:
- `app/.../monetization/AdOrchestrator.kt`
- Uygulama açılışındaki zorunlu rewarded preload kaldırıldı.

3. Rewarded gösterim akışı düzeltildi:
- `app/.../ui/RewardsViewModel.kt`
- `app/.../ui/RewardsScreen.kt`
- Reklam hazır değilse yükle + bekle + göster akışı kullanılıyor.

4. `SECRET/ADMOB_KOTNROL` araçları güncellendi:
- `admob_checker.py`
  - fill + show rate birlikte alarm
  - rewarded anomaly sınıflandırması
  - parametreli eşikler
  - `inventory` check (v1 + v1beta görünümü)
  - token cache varsayılanı repo dışı
  - `admob_debug.py`
  - OAuth tabanlı hızlı API erişim testi

5. Resmi privacy standardına göre global ad request konfigürasyonu eklendi:
- `feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt`
- UMP tarafındaki `setTagForUnderAgeOfConsent(false)` bilgisi, ad isteklerine de
  `MobileAds.setRequestConfiguration(...)` ile yansıtılıyor.
- Bu, Google dokümantasyonundaki TFUA notuyla uyumlu:
  UMP etiketi tek başına ad isteği tarafına otomatik taşınmaz.

6. Consent sonucu gelmeden banner ad request gönderimi kapatıldı:
- `feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdsConsentRuntimeState.kt`
- `feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/BannerAd.kt`
- `feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt`
- Varsayılan durum `canRequestAds=false`; UMP olumlu sonuç vermeden banner yüklenmez.

7. Privacy options sonrası rıza değişikliği aynı oturumda uygulanıyor:
- `feature/settings/.../SettingsScreen.kt` form kapanış callback'i
- `app/.../MainActivity.kt` -> `AdOrchestrator.refreshConsent(...)`
- `app/.../monetization/AdOrchestrator.kt` consent false ise preload adlar temizlenir
- Böylece kullanıcı rızayı geri çektiğinde app restart beklenmeden ad istekleri durur.

## Resmi örneklerle eşleme (GoogleAds Android Examples)

Bu repo AdMob ad unit (`ca-app-pub-...`) kullandığı için yükleme tarafında
`AdRequest.Builder()` ile devam eder; Ad Manager örneklerindeki
`AdManagerAdRequest` yalnız GAM envanteri içindir.

- APIDemo:
  `kotlin/advanced/APIDemo/.../MainActivity.kt`
  ve `.../snippets/RequestConfigurationSnippets.kt`
  → `MobileAds.getRequestConfiguration().toBuilder()` + `setRequestConfiguration`.
- AdManager örnekleri (AppOpen/Banner/Interstitial/Rewarded/Native):
  UMP gather + privacy options + request configuration + sonra SDK init pattern’i.
- Bizdeki karşılığı:
  `feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt`
  ve
  `feature/settings/src/main/java/com/parsfilo/contentapp/feature/settings/ui/SettingsScreen.kt`.

## AdMob panelinde yapılanlar (bu oturum)

1. `Uyumlulaştırma > Uyumlulaştırma grupları` içinde iki ödüllü grupte aktif A/B testleri kapatıldı:
- `OdulluUyumlulastirma`
- `OdulluGecisUyumlulastirma`

2. Kontrol sonucu:
- Her iki grupta da artık `A/B testi yok` görünüyor.
- Gruplar listesinde durum `Hazır`.

3. `Policy Center` kontrolü:
- `Verse of Kursi Audio and ... (com.parsfilo.ayetelkursi)` için
  `Kısıtlanmış reklam sunumu` uyarısı yalnızca eski sürümler için görünüyor.

4. `Gizlilik ve mesajlaşma` kontrolü:
- Avrupa mesajı `etkin`.
- ABD eyalet mesajı `etkin`.

## Senin manuel tamamlaman gerekenler (AdMob legal/onay)

AdMob panelinde teknik olarak otomasyona uygun olmayan, hesap sahibinin onaylaması gereken adımlar kaldı:

1. `Uyumlulaştırma > OdulluUyumlulastirma` ve `OdulluGecisUyumlulastirma` içinde
`Reklam kaynağı ekle` adımından:
- `Meta Audience Network`: iş ortaklığı sözleşmesi/onay akışını tamamla.
- `Liftoff Monetize`: `Onayla ve kabul et` adımını tamamla.

2. Bu onaylar sonrası aynı ekranlardan kaynakları gruba ekle ve `Kaydet`.

3. `Engelleme kontrolleri > AB kullanıcı izni` tarafında eklenen partnerlerin
reklam teknolojisi sağlayıcısı olarak listelendiğini doğrula.

## Operasyon komutları

```bash
python SECRET/ADMOB_KOTNROL/admob_debug.py --client-secret SECRET/ADMOB_KOTNROL/client_secret.json --publisher pub-XXXX --check-v1beta

python SECRET/ADMOB_KOTNROL/admob_checker.py --client-secret SECRET/ADMOB_KOTNROL/client_secret.json --publisher pub-XXXX

python SECRET/ADMOB_KOTNROL/admob_checker.py --client-secret SECRET/ADMOB_KOTNROL/client_secret.json --publisher pub-XXXX --check inventory
```

## GDPR / UMP kontrol referansları
- https://developers.google.com/admob/android/privacy
- https://developers.google.com/admob/android/privacy/gdpr
- https://developers.google.com/admob/android/privacy/options
- https://developers.google.com/admob/privacy/consent-groups/sync-consent-across-apps

## Uyum durumu (kod + panel)

Koddan tamamlananlar:
- UMP consent her açılışta çağrılıyor.
- Consent yoksa ad SDK initialize akışı bloke.
- Privacy options form (gerekli ülkelerde) Settings ekranından açılabiliyor.
- TFUA bilgisi `RequestConfiguration` ile ad request katmanına uygulanıyor.
- Banner ad request'leri, consent sonucu gelene kadar gönderilmiyor.

AdMob panelde manuel zorunlu olanlar:
- `Privacy & messaging` altında GDPR ve US States mesajlarını oluşturup yayınlamak.
- Her mesaj tipinde hedef ülkeler/eyaletler ve dil kapsamını doğru seçmek.
- Test cihazlarında UMP formunun görünüp kapanabildiğini doğrulamak.

## Rıza grubu (Consent Group) uyarıları — yorum ve aksiyon

AdMob `Privacy & messaging > Rıza grubu` ekranındaki iki uyarı tipi farklı kaynaktan gelir:

1. `İlgilenilmesi gerekiyor`
- Genelde ilgili uygulama için publish edilmiş UMP mesajı / uygun dağıtım yöntemi / eligibility kontrolü eksik olduğu anlamına gelir.
- Bu uyarı kod ile tek başına kapanmaz; AdMob panelde mesaj yayını ve uygulama kapsamı doğrulanmalıdır.

2. `UMP SDK ile kullanıcı kimliklerini sağlamadığınız sürece...`
- Bu uyarı uygulama tarafıyla ilgilidir.
- Cross-app consent sync için UMP request parametrelerine bir senkronizasyon kimliği verilmelidir.
- Bu repoda çözüm: `setConsentSyncId(...)` + Android `App Set ID` (Play Services App Set API).

### Tüm uygulamalar için rıza grubu rollout checklist (Şubat 2026)

1. Android uygulamaların UMP sürümünü güncel tut (`UMP SDK 4.x`).
2. UMP request akışında `setConsentSyncId(...)` çağrısını etkinleştir (bu repo: App Set ID tabanlı).
3. AdMob `Privacy & messaging` içinde:
- GDPR mesajı published
- US states mesajı published
- Hedef bölge/dil kapsamı doğrulanmış
4. Rıza grubuna eklenecek tüm uygulamalarda `İlgilenilmesi gerekiyor` uyarısını tek tek temizle.
5. Gizlilik politikasında rıza grubundaki tüm uygulamaları ve consent paylaşımı/senkronizasyonunu açıkça listele.
6. Aynı test cihazında birden fazla uygulama ile consent sync davranışını doğrula (grant/deny/change).

## Reklam tuning (Remote Config) — yeni anahtarlar

Kod tarafında agresif gelir profili için RC destekli reklam policy anahtarları eklendi:

- `ads_banner_enabled`
- `ads_native_enabled`
- `ads_interstitial_frequency_cap_ms`
- `ads_app_open_cooldown_ms`
- `ads_rewarded_interstitial_min_interval_ms`
- `ads_rewarded_interstitial_max_per_session`
- `ads_native_pool_max`
- `ads_native_ttl_ms`
- `ads_banner_placements_disabled_csv`
- `ads_native_placements_disabled_csv`

Not:
- Premium ve rewarded ad-free kullanıcılar için reklam kapatma kuralı RC ile override edilmez.
- RC tuning sadece gösterim sıklığı/placement enablement optimizasyonu içindir.
- Play Console veri güvenliği formunu bu davranışla uyumlu doldurmak.

## Android gelir/teşhis referansları
- Rewarded: https://developers.google.com/admob/android/rewarded
- Ad Inspector: https://developers.google.com/admob/android/ad-inspector
- Response info: https://developers.google.com/admob/android/response-info
- Impression-level ad revenue: https://developers.google.com/admob/android/impression-level-ad-revenue

## Güvenlik notu
- OAuth `client_secret.json` ve refresh token dosyaları gizli kalmalı.
- Şüpheli sızıntıda client secret + refresh token rotate edilmeli.

## Rapor Sonrasi Operasyon Runbook (Mart 2026)

Bu bolum, `TEMP_FOLDER/admob-analiz-raporu.md` icindeki kod-disinda kalan 3 kritik operasyon maddesini kapatmak icindir.

### 1) Amenerrasulu anomalous version / IVT incelemesi

Amac:
- `142.1.8`, `24.4.13.1`, `production1` gibi beklenmeyen version stringlerinin kaynagini ayirmak.
- Gercek eski istemci mi, yoksa invalid traffic/sahte trafik mi netlestirmek.

Adimlar:
1. AdMob > Policy Center ve Traffic Quality ekranindan son 30 gunu package `com.parsfilo.amenerrasulu` icin filtrele.
2. AdMob report export ile su kirilimlari cikar:
   - app version
   - sdk version
   - country
   - ad unit
   - request / matched / impression / click
3. Asagidaki siniflandirmayi uygula:
   - `Legacy`: version patterni senin release semanigine yakin (`1.0.x`, `2.x` vb.)
   - `Suspicious`: semantik disi (`production1`, cok uzun build label, alakasiz major pattern)
4. `Suspicious` segmentte su iki kontrolu yap:
   - anormal yuksek request, dusuk impression, dusuk session quality
   - tek/az sayida country veya beklenmeyen geo yogunlasmasi
5. Yuksek riskte acil aksiyon:
   - ilgili ad unitleri AdMob tarafinda gecici disable et
   - gerekirse yeni ad unit acip uygulama tarafinda rotasyon planla
   - `ads_incident_mode=true` ile app-open/interstitial/rewarded-interstitial kapat

Karar esikleri (ops):
- `Suspicious requests / total requests > %10` => yuksek risk
- `Suspicious segment show rate < %5` ve request buyukse => IVT adayi
- Policy Center warning sayisi artiyorsa => incident moduna gec

### 2) Eski GMA SDK kullanicilarinin azaltilmasi

Amac:
- Eski istemcilerden gelen NPA agirligini azaltmak.
- Consent Mode v2 ve UMP 4.x davranisinin aktif istemci tabaninda oranini artirmak.

Adimlar:
1. Firebase/BigQuery veya internal analytics ile app version dagilimini haftalik raporla.
2. `min_supported_ad_sdk_cohort` listesi olustur:
   - `GMA < 23.x` olanlar legacy risk segmenti
3. Eski segment icin rollout:
   - force update (soft -> hard)
   - in-app update prompt
   - release note ve push kampanyasi
4. Haftalik KPI takibi:
   - `legacy_active_devices_30d`
   - `npa_impression_ratio`
   - `consent_granted_ratio`

Hedef:
- 30 gun icinde legacy cihaz oranini <%5 seviyesine dusurmek.

### 3) Yasin Suresi limited ads (%28) kok neden ayrisma

Amac:
- Limited Ads oraninin yas/TFUA kararindan mi, consent eksiginden mi, geo/policy etkisinden mi geldigini ayirmak.

Adimlar:
1. Package `com.parsfilo.yasinsuresi` icin kirilimli rapor al:
   - app version
   - country
   - age gate state (`UNDER_13`, `AGE_13_TO_15`, `AGE_16_OR_OVER`, `UNKNOWN`)
   - consent state (`granted`, `denied`, `not required`)
2. Su capraz kontrolleri yap:
   - `limited_ads_ratio` by app version
   - `limited_ads_ratio` by country
   - `limited_ads_ratio` by age bucket
3. Beklenen degilse:
   - age gate defaults ve migration davranisini tekrar denetle
   - ayarlar ekranindan privacy options / consent revoke akisini manuel QA et
4. Aksiyon:
   - belirli surumde patlama varsa o surume update kampanyasi
   - belirli geoda patlama varsa UMP mesaj yayini ve metin/publisher ayarlarini kontrol et

Karar esikleri (ops):
- `limited_ads_ratio > %20` 7 gun ust uste => P1 incident
- `%30+` ve gelir dususu birlikteyse => P0 incident

### 4) Haftalik kontrol rutini (zorunlu)

Her Cuma:
1. GitHub Actions `AdMob Weekly Health` workflow'unu calistir (veya schedule'i bekle).
2. Artifact `admob-health-report` icindeki `admob_today_report.json` ve summary'yi incele.
3. Lokal teyit gerekiyorsa `admob_checker.py` calistir, output'u sakla.
4. `Suspicious version` listesi guncelle.
5. `limited_ads_ratio` ve `npa_ratio` trendini onceki hafta ile karsilastir.
6. Esikler asildiysa `ADS_INCIDENT_RUNBOOK.md` adimlarini uygula.

### 4.1 Gunluk latest-version kontrolu (zorunlu)

Gunluk otomasyon:
1. GitHub Actions `AdMob Daily Latest Health` workflow'u calisir.
2. Bu workflow sadece `.ci/apps.json` + `app-versions.properties` ile eslesen en guncel surum satirlarini dahil eder.
3. Eski surum satirlari toplamlardan dislanir.
4. Artifact `admob-daily-latest-health-report` icindeki JSON/summary operasyon panelinde referans kabul edilir.

Not:
- Eger app label -> katalog eslesmesi saglanamazsa (`--strict`), job fail eder.
- Bu bilincli bir fail-fast davranisidir; eslesme duzeltilmeden metriklere guvenilmez.

### 5) Kapanis kriteri

Bu rapor maddeleri operasyonel olarak kapandi kabul edilmesi icin:
1. Anomalous version segmenti siniflandirilmis ve aksiyonlanmis olacak.
2. Legacy SDK cihaz oraninda haftalik dusus trendi gorulecek.
3. Yasin limited ads orani kalici olarak %20 altina indirilecek veya net kok neden belgelenmis olacak.
