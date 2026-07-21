# DELIVERY - ROI Improvements

## 1) Kısa Özet
Bu çalışma ile overengineering yapmadan 6 yüksek ROI iyileştirme uygulandı:

1. Release öncesi flavor version doğrulaması zorunlu hale getirildi.
2. Push registration çağrısı timeout/log/retry ile dayanıklı ve gözlemlenebilir yapıldı.
3. Backend recurrence akışında `sentTimezones` manuel reset ihtiyacı kaldırıldı.
4. Endpoint yönetimi Remote Config + default fallback ile tek merkezde toplandı.
5. 2 adet temel instrumentation smoke test eklendi.
6. PR pipeline kalite kapısı `ktlint + validateFlavorVersions + unit test` ile netleştirildi; connected smoke test koşullu job olarak eklendi.

## 2) Commit Listesi (Hash + Mesaj)
1. `283def355972f62e1431d8f0e2884fcd6e0cb579` - `build: validate flavor versions`
2. `cddf0a1af10256b06e1145eed7246b371d7b3696` - `push: add timeout/logging/retry`
3. `5b5b7722942f5a6e0bebce7ae3a325b5128e6072` - `backend: auto-reset sentTimezones`
4. `120e5cc105c9998180eddd67991b94cf0f490e86` - `config: centralize endpoints via Remote Config`
5. `f93fccbed11dc306b23459e7b88a171e16e90927` - `test: add instrumentation smoke tests`
6. `4a75e122c38012f3717c1002ed5850806f151c2c` - `ci: enforce ktlint on PR (+ optional connected tests)`

## 3) Commit Bazlı Detaylar

### Commit #1 - build: validate flavor versions
**Ne değişti?**
- `:app:validateFlavorVersions` task’i eklendi.
- Flavor kaynağı `AppFlavors.all` olacak şekilde tüm flavor’lar için:
  - `<flavor>.versionCode`
  - `<flavor>.versionName`
  zorunlu doğrulandı.
- Eksik key’lerde kullanıcı dostu `GradleException` mesajı (eksik liste + örnek format + dosya yolu).
- `assemble*Release`, `bundle*Release`, `publish*Release*` görevleri bu doğrulamaya bağlandı.

**Değişen dosyalar**
- `app/build.gradle.kts`

**Nasıl test edilir?**
- `./gradlew :app:validateFlavorVersions`
- (Manual) `app-versions.properties` içinden geçici key silip tekrar çalıştır.

**Kabul kriteri doğrulaması**
- Eksik key senaryosu: FAIL (beklenen).
- Tüm key’ler mevcut: PASS.
- Release task graph’ında validate bağımlılığı doğrulandı (`bundle...Release -m`).

---

### Commit #2 - push: add timeout/logging/retry
**Ne değişti?**
- Push sender timeout/retry/logging güçlendirildi:
  - `CONNECT_TIMEOUT_MS = 10_000`
  - `READ_TIMEOUT_MS = 15_000`
  - `MAX_RETRY_COUNT = 1`
  - `MAX_ERROR_BODY_CHARS = 2048`
- Retry kuralları:
  - Retry: `IOException` türevleri, HTTP `5xx`
  - Retry yok: HTTP `4xx`
  - Backoff jitter: `300-900ms`
  - Cancellation: retry yok, exception propagate
- 4xx/5xx response body (sınırlandırılmış) loglandı, token/installId benzeri alanlar maskelendi.
- Son başarısızlıkta Crashlytics log/non-fatal kaydı bırakıldı.
- Retry policy için unit test eklendi.

**Değişen dosyalar**
- `core/firebase/build.gradle.kts`
- `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/push/HttpPushRegistrationSender.kt`
- `core/firebase/src/test/java/com/parsfilo/contentapp/core/firebase/push/HttpPushRegistrationSenderRetryPolicyTest.kt`

**Dependency ekleri (minimum + gerekçe + modül)**
- Modül: `core:firebase`
- `testImplementation(libs.junit)`
- `testImplementation(libs.kotlinx.coroutines.test)`
- Gerekçe: Retry/backoff/cancellation davranışını deterministik unit test ile doğrulamak.

**Nasıl test edilir?**
- `./gradlew :core:firebase:test`

**Kabul kriteri doğrulaması**
- 500 -> 1 retry, 400 -> no retry testleri PASS.
- Timeout değerleri sabitlerden uygulanıyor.
- Hata görünürlüğü (status/body/mask) ve crash event akışı eklendi.

---

### Commit #3 - backend: auto-reset sentTimezones
**Ne değişti?**
- Event şemasına opsiyonel `lastResetAt` eklendi.
- Recurrence için periyot dolum kontrolü eklendi:
  - `daily` -> 1 gün
  - `weekly:*` -> 7 gün
- Periyot dolduğunda transaction içinde:
  - `sentTimezones` temizlenir
  - `lastResetAt` güncellenir
- `lastResetAt` yoksa backward-compatible şekilde initialize edilir.
- `markTimezonesAsSent` transaction tabanlı güncellemeye geçirildi.
- Dokümantasyon manuel reset gereksinimi kalkacak şekilde güncellendi.
- Reset mantığı için helper script test eklendi.

**Değişen dosyalar**
- `docs/NOTIFICATION_SYSTEM.md`
- `side-projects/firebase/functions/package.json`
- `side-projects/firebase/functions/scripts/test-reset-logic.js`
- `side-projects/firebase/functions/src/dispatchNotifications.ts`

**Nasıl test edilir?**
- `cd side-projects/firebase/functions && npm run build`
- `cd side-projects/firebase/functions && npm run test:reset-logic`

**Kabul kriteri doğrulaması**
- Manual reset ihtiyacı kaldırıldı.
- Backward compatibility (`lastResetAt` yoksa init) uygulandı.
- Periyot doldu/dolmadı senaryoları helper test ile doğrulandı.

---

### Commit #4 - config: centralize endpoints via Remote Config
**Ne değişti?**
- Endpoint’ler `EndpointsProvider` içinde merkezileştirildi.
- Remote Config key + fallback yaklaşımı eklendi.
- Uygulama açılışında `prefetchAsync()` çağrısı eklendi (`App.onCreate`).
- Audio ve OtherApps call-site’ları provider kullanacak şekilde güncellendi.
- Dağınık hard-coded/BuildConfig endpoint kullanımları kaldırıldı.
- Remote Config key listesi README’de belgelendi.

**Değişen dosyalar**
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/parsfilo/contentapp/App.kt`
- `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/config/EndpointsProvider.kt`
- `core/firebase/src/test/java/com/parsfilo/contentapp/core/firebase/config/EndpointsProviderTest.kt`
- `feature/audio/build.gradle.kts`
- `feature/audio/src/main/java/com/parsfilo/contentapp/feature/audio/data/AudioCachePrefetcher.kt`
- `feature/audio/src/main/java/com/parsfilo/contentapp/feature/audio/ui/AudioPlayerViewModel.kt`
- `feature/otherapps/build.gradle.kts`
- `feature/otherapps/src/main/java/com/parsfilo/contentapp/feature/otherapps/data/NetworkCachedOtherAppsRepository.kt`

**Dependency ekleri (minimum + gerekçe + modül)**
- Harici yeni kütüphane yok.
- Modül bağımlılıkları:
  - `feature:audio -> implementation(project(":core:firebase"))`
  - `feature:otherapps -> implementation(project(":core:firebase"))`
- Gerekçe: Mevcut Firebase Remote Config sağlayıcısını endpoint provider üzerinden tüketmek.

**Nasıl test edilir?**
- `./gradlew :feature:audio:test :feature:otherapps:test :app:assembleAmenerrasuluDebug`
- `./gradlew :core:firebase:test`

**Kabul kriteri doğrulaması**
- Endpoint erişimi tek merkezden sağlandı.
- RC boş/fail olduğunda default fallback korunuyor.
- Davranış mevcut default URL’lerle geriye uyumlu.

---

### Commit #5 - test: add instrumentation smoke tests
**Ne değişti?**
- Compose root’a `testTag("app_root")` eklendi.
- 2 smoke test eklendi:
  - `AppLaunchSmokeTest`: launch + `app_root` var mı
  - `SimpleInteractionSmokeTest`: `activity.recreate()` sonrası `app_root` var mı
- Android test dependencies’e minimum runner/rules eklendi.

**Değişen dosyalar**
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/parsfilo/contentapp/AppLaunchSmokeTest.kt`
- `app/src/androidTest/java/com/parsfilo/contentapp/SimpleInteractionSmokeTest.kt`
- `app/src/main/java/com/parsfilo/contentapp/ui/ContentApp.kt`

**Dependency ekleri (minimum + gerekçe + modül)**
- Modül: `app`
- `androidTestImplementation("androidx.test:runner:1.7.0")`
- `androidTestImplementation("androidx.test:rules:1.7.0")`
- Gerekçe: Stabil instrumentation smoke altyapısı.

**Nasıl test edilir?**
- `./gradlew :app:compileAmenerrasuluDebugAndroidTestKotlin`
- `./gradlew :app:connectedAmenerrasuluDebugAndroidTest` (ortam uygunsa)

**Kabul kriteri doğrulaması**
- Smoke test kaynakları derleniyor.
- Connected test çalıştırma girişimi mevcut ortamda UTP dependency çözümleme problemi nedeniyle fail (aşağıdaki Final Doğrulama bölümünde detay var).

---

### Commit #6 - ci: enforce ktlint on PR (+ optional connected tests)
**Ne değişti?**
- Root ktlint davranışı PR’a göre güncellendi:
  - PR ise `ignoreFailures=false`
  - PR değilse mevcut CI davranışı korunur
- PR pipeline’a `ValidateAndUnitTest` job eklendi:
  - `./gradlew :app:validateFlavorVersions test`
- Optional connected smoke job eklendi (`ConnectedSmokeTests`):
  - varsayılan `RUN_CONNECTED_TESTS=false`
  - true olduğunda `:app:connectedAmenerrasuluDebugAndroidTest` koşar
  - cihaz/emulator yoksa bilinçli fail
- GitHub Actions setup dokümantasyonu PR gate davranışları ile güncellendi.
- Ktlint script ihlallerini kırmamak için script dosyalarında minimal newline/blank-line düzeltmeleri yapıldı.

**Değişen dosyalar**
- `app/build.gradle.kts`
- `build.gradle.kts`
- `docs/CI_RELEASE_VALIDATION_PLAN.md`
- `feature/counter/build.gradle.kts`
- `feature/qibla/build.gradle.kts`
- `.github/workflows/quality-gate.yml`

**Nasıl test edilir?**
- `./gradlew :app:validateFlavorVersions ktlintCheck test`

**Kabul kriteri doğrulaması**
- PR’da ktlint ihlali artık build’i kırar.
- PR’da `validateFlavorVersions` ve unit test zorunlu job ile çalışır.
- Connected smoke test koşullu job olarak eklendi (default kapalı).

## 4) Remote Config Key Listesi + Defaultlar + Yönetim Noktası
Yönetim sınıfı:
- `core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/config/EndpointsProvider.kt`

Key’ler ve defaultlar:
1. `audio_base_url` -> `https://contentapp-content-api.oaslananka.workers.dev/api/audio`
2. `audio_manifest_url` -> `https://contentapp-content-api.oaslananka.workers.dev/api/audio-manifest`
3. `other_apps_url` -> `https://contentapp-content-api.oaslananka.workers.dev/api/other-apps`

Davranış:
- RC boş/blank/fetch fail ise default değerler otomatik kullanılır.
- `App.onCreate` içinde `prefetchAsync()` ile cache odaklı aktivasyon tetiklenir.

## 5) Backend Yeni Alan(lar) + Backward Compatibility
Yeni alan:
- `lastResetAt` (`Timestamp`, optional)

Akış:
- Recurrence eventlerde periyot dolduysa `sentTimezones` transaction içinde sıfırlanır ve `lastResetAt` güncellenir.
- `lastResetAt` yoksa ilk çalışmada initialize edilir.

Backward-compat:
- Eski event dokümanları migrasyon gerektirmeden çalışır.

## 6) CI’da PR Davranışı
Dosya:
- `.github/workflows/quality-gate.yml`

PR gate job’ları:
1. `StaticAnalysis`: `detekt` + `ktlintCheck`
2. `ValidateAndUnitTest`: `:app:validateFlavorVersions` + `test`
3. `AndroidLint`: dinamik flavor lint
4. `ConnectedSmokeTests` (opsiyonel): `RUN_CONNECTED_TESTS=true` ise çalışır

Ktlint fail davranışı:
- `build.gradle.kts` içinde PR environment detect edilip `ignoreFailures=false` uygulanır.

## 7) Final Doğrulama Komutları Sonuçları
1. `./gradlew :app:validateFlavorVersions` -> **PASS**
2. `./gradlew lint` -> **FAIL**
   - Sebep: `feature:counter` lint uyarıları nedeniyle baseline dosyası otomatik üretilip build kesiliyor (`MissingPermission` uyarısı ve baseline creation abort).
3. `./gradlew test` -> **PASS**
4. `./gradlew :app:connectedAndroidTest` -> **FAIL**
   - Sebep: UTP dependency çözümleme hatası (`com.google.testing.platform:android-device-provider-local:0.0.9-alpha03`).
5. `./gradlew clean build` -> **FAIL**
   - Sebep: `lint` aşaması aynı baseline-creation abort ile kırılıyor.

Not:
- `lint` çalıştırması sonrası oluşan `feature/counter/lint-baseline.xml` dosyası commit dışı tutuldu ve temizlendi.
