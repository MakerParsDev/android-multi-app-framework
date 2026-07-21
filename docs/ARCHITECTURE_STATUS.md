# Architecture Status

Bu doküman refactor sonrası mevcut mimari durumun kısa özetidir.

## Tamamlanan Ana Geçişler

### 1. Product definition katmanı var

Ana runtime ürün tanımı:
- `app/src/main/java/com/parsfilo/contentapp/product/AppProductDefinition.kt`

Bu model artık şu kararların ana kaynağıdır:

- `flavorId`
- `displayName`
- `contentFamily`
- `monetizationProfile`
- `notificationProfile`
- `billingProfile`
- `themeTokenKey`
- `audioFileName`
- `useAssetPackAudio`
- capability flag'ler

### 2. Runtime package-name branching büyük ölçüde temizlendi

Kaldırılan/taşınan örnekler:

- audio prefetch package kontrolü
- prayer times variant package fallback'i
- legacy prayer-times UI ayrımı için raw flavor string kontrolü

### 3. Runtime observability eklendi

- ad runtime telemetry
- push registration health persistence
- admin panelde device coverage + runtime diagnostics

### 4. CI bootstrap tekrarları azaltıldı

Yeni ortak action seti:

- `export-firebase-override-env`
- `resolve-release-secrets`
- `verify-google-signin-config`
- `decode-play-service-account`
- `verify-env-contract`

## Bilinçli Olarak Build-Time Bırakılanlar

Refactor sırasında her `BuildConfig` kullanımını ürün modeline taşımadık. Aşağıdakiler şu an bilinçli olarak build-time bridge veya debug/runtime sabiti olarak bırakıldı:

1. `DEBUG`
2. `BUILD_TYPE`
3. `USE_TEST_ADS`
4. AdMob ad unit id build config alanları
5. `PUSH_REGISTRATION_URL`
6. `PURCHASE_VERIFICATION_URL`
7. `VERSION_CODE`

Sebep:

- bunlar ürün kimliğinden çok build varyantı, ortam veya dış servis entegrasyonu ile ilgili
- ürün modeli ile build-time environment contract'ını karıştırmamak daha temiz

## Kalan Mimari Borç

### 1. Firebase Functions parity deploy

Kod hazır olsa da canlı deploy için Blaze/billing gerekir.

### 2. Runtime suppress telemetry ingestion

Android tarafı sinyali üretiyor. Bunun backend ingestion tarafı hâlâ genişletilebilir.

### 3. CI / env contract raporlama

`verify-env-contract` drift yakalıyor, ama daha okunur bir summary çıktısı üretmesi ileride faydalı olabilir.

### 4. Gradle 10 readiness, plugin-side blocker

`./gradlew help --warning-mode all --no-configuration-cache` çıktısında görülen Gradle 10 uyarısı şu an repo DSL'den değil, Detekt plugin'inin `ReportingExtension.file(String)` kullanımından geliyor. Wrapper'ı Gradle 10 hattına taşımadan önce plugin tarafındaki uyumluluk tekrar doğrulanmalı.

## Remaining Migration Checklist

### Runtime / Android

- [x] Product definition modeli oluşturuldu
- [x] Prayer times variant package fallback kaldırıldı
- [x] Audio prefetch package bağı capability tabanlı hale getirildi
- [x] Audio file / asset pack bridge ürün modeline taşındı
- [ ] Runtime suppress telemetry ingestion hattı Android → backend → admin panel zincirinde tamamlanacak
- [ ] Kalan ad runtime kararlarının tek policy state modelinde daha da daraltılması gözden geçirilecek

### Push / Notifications

- [x] Registration self-healing retry akışı eklendi
- [x] Registration health alanları kalıcılaştırıldı
- [x] Developer mode üzerinden manuel retry yüzeyi eklendi
- [ ] Firebase Functions parity deploy billing açılınca tamamlanacak

### Product / Flavor Consolidation

- [x] Ana ürün metadata alanları BuildConfig bridge + runtime model ile toplandı
- [x] Birkaç kritik UI/runtime branch ürün modeline taşındı
- [ ] Kalan feature modüllerinde ürün modeli yerine doğrudan build sabiti okuyan kararlar periyodik olarak gözden geçirilecek

### CI / Ops

- [x] Ktlint advisory varsayılan davranışa çekildi
- [x] Ortak bootstrap action seti çıkarıldı
- [x] Env/secret contract dokümante edildi
- [x] Ops runbook seti eklendi
- [ ] `verify-env-contract` çıktısı zaman içinde machine-readable summary ile zenginleştirilebilir

## Pratik Yorum

Bu repo şu an “dağınık flavor branching” evresinden çıktı ve “tek ürün modeli + build-time bridge” yönüne girmiş durumda.

En doğru sonraki teknik hedef:

1. runtime suppress telemetry ingestion'ı tamamlamak
2. Firebase Functions parity deploy'u billing açılınca bitirmek
3. release/ops runbook setini ekip kullanımına göre rafine etmek
