# 🔐 Secrets & Environment Setup

> [!IMPORTANT]
> Canonical CI secret storage is Azure Secure Files/secret variable groups plus Doppler. GitHub repository secret sections below describe the legacy migration source, not the target architecture. Follow `docs/SECRET_OWNERSHIP_AND_ROTATION.md` and `docs/GITHUB_SECRET_CLEANUP_PLAN.md`.

Bu dokümanda projenin imzalama, Play Store yayınlama ve CI/CD için gereken tüm secret/env yapısı açıklanmaktadır.

---

## Genel Bakış

`app/build.gradle.kts` içindeki `pick()` fonksiyonu 3 kaynağı sırayla kontrol eder:

```
1. Gradle Property (-P ile)  →  2. Environment variable  →  3. .env dosyası
```

lokalde durabilen `.env` placeholder/path değerlerini override eder.

---

## Gerekli Secret'lar

| Secret | Açıklama | Kullanıldığı Yer |
|--------|----------|-----------------|
| `KEYSTORE_BASE64` | JKS dosyasının Base64 kodlanmış hali | CI/CD (GitHub Actions) |
| `KEYSTORE_FILE` | JKS dosyasının dosya yolu | Lokal geliştirme (.env) |
| `KEYSTORE_PASSWORD` | Keystore şifresi | Her yerde |
| `KEY_ALIAS` | İmza anahtarının alias adı | Her yerde |
| `KEY_PASSWORD` | Anahtar şifresi | Her yerde |
| `PLAY_SERVICE_ACCOUNT_JSON_BASE64` | Service account JSON dosyasının Base64 kodlanmış hali | CI/CD (publish) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Service account JSON dosya yolu | Lokal geliştirme (.env) |
| `FIREBASE_WEB_CLIENT_ID` | Google Sign-In Web OAuth client id (`*.apps.googleusercontent.com`) | CI Google Sign-In doğrulama |
| `PURCHASE_VERIFICATION_URL` | Play Billing doğrulama endpoint URL’i | Release/publish build + uygulama runtime |
| `ADMIN_ALLOWED_EMAILS` | Admin panel backend fallback allowlist (virgülle) | Firebase Functions (`adminAccessCheck`) |
| `ADMOB_CLIENT_ID` / `ADMOB_CLIENT_SECRET` / `ADMOB_REFRESH_TOKEN` / `ADMOB_PUBLISHER_ID` | AdMob health rapor API kimlik bilgileri | Firebase Functions (`adPerformance*`) |
| `GOOGLE_RECAPTCHA_SECRET_KEY` | reCAPTCHA secret (server-side verify, opsiyonel) | Firebase Functions (`recaptchaVerify`) |

---

## GitHub Actions — Repository Secrets

```
GitHub Repo → Settings → Secrets and variables → Actions → New repository secret
```

Eklenecek secretlar:

1. **`KEYSTORE_BASE64`** — JKS dosyasını Base64'e çevirerek
2. **`KEYSTORE_PASSWORD`** — Keystore şifresi
3. **`KEY_ALIAS`** — Genellikle `upload` veya `key0`
4. **`KEY_PASSWORD`** — Anahtar şifresi
5. **`PLAY_SERVICE_ACCOUNT_JSON_BASE64`** — Service account JSON dosyasını Base64'e çevirerek
6. **`PUSH_REGISTRATION_URL`** — release/publish için zorunlu endpoint
8. **`FIREBASE_WEB_CLIENT_ID`** — publish/internal akışlarda zorunlu Google Sign-In cross-check için

## GitHub Actions — Ortak Bootstrap Action'ları

Workflow tekrarlarını azaltmak için kritik bootstrap adımları artık composite action olarak tanımlıdır:

| Action | Amaç |
|--------|------|
| `.github/actions/export-firebase-override-env` | Firebase Web config + Cloudflare R2 override env export |
| `.github/actions/resolve-release-secrets` | release signing + publish için temel secret/env çözümü |
| `.github/actions/verify-google-signin-config` | `FIREBASE_WEB_CLIENT_ID` ile flavor `google-services.json` uyumunu kontrol eder |
| `.github/actions/decode-play-service-account` | Play service account'ı decode eder, JSON yapısını doğrular ve yolu export eder |

Bu action'lar şu workflow'larda kullanılır:

- `auto-debug-ops.yml`
- `manual-stacktrace-diagnostics.yml`
- `manual-stacktrace-diagnostics-parallel.yml`
- `manual-ops.yml`
- `release.yml`
- `release-parallel.yml`
- `quality-gate.yml`
- `sync-play-version-codes.yml`

### GitHub Environment (Zorunlu)

`release.yml` → `publish-production` job'u `environment: production` kullanır.

```
GitHub Repo → Settings → Environments → New → "production"
→ Required reviewers: kendinizi ekleyin
→ Save
```

Bu sayede Play Store'a yayınlamadan önce GitHub'da manuel onay gerekir.

---

## Lokal Geliştirme — `.env` Dosyası

Repo kökündeki `.env.template` dosyasını `.env` olarak kopyalayın ve doldurun
(`.gitignore`'da zaten tanımlı, repo'ya girmez):

```properties
KEYSTORE_FILE=C:/Users/KULLANICI/path/to/release.jks
KEYSTORE_PASSWORD=senin_keystore_sifren
KEY_ALIAS=upload
KEY_PASSWORD=senin_key_sifren
PUSH_REGISTRATION_URL=https://your-api.example.com/register-device
PURCHASE_VERIFICATION_URL=https://your-api.example.com/verify-purchase

# Sadece publishRelease* görevleri için gerekir
PLAY_SERVICE_ACCOUNT_JSON=C:/Users/KULLANICI/path/to/play-service-account.json
FIREBASE_WEB_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
ADMIN_ALLOWED_EMAILS=makerpars@gmail.com,oaslananka@gmail.com
GOOGLE_RECAPTCHA_SECRET_KEY=xxxxxxxx
```

> **Not:** Lokalde `KEYSTORE_BASE64` gerekmez — direkt dosya yolu kullanılır.


Tek komutla hem repo kökü `.env` hem de admin panel `.env` dosyasını eşitlemek için:

```bash
```

Bu komut:
- Kök `.env` dosyasını kanonik anahtar sırasıyla yazar,
- `side-projects/admin-notifications/.env` dosyasını `VITE_*` map ile günceller,
- `side-projects/admin-notifications/.env.example` kontrat dosyasını anahtar sırasıyla eşitler,
- `.env.template` ile kanonik sözleşme farkını raporlar.

---

## Keystore (JKS) Oluşturma

Eğer henüz yoksa:

```bash
keytool -genkeypair \
  -v \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass SIFRE \
  -keypass SIFRE \
  -alias upload \
  -keystore release.jks \
  -dname "CN=Parsfilo, O=Parsfilo, L=Istanbul, C=TR"
```

### Base64'e Çevirme

**PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

**Git Bash / Linux:**
```bash
base64 -w 0 release.jks
```

Çıktıyı `KEYSTORE_BASE64` secret'ına yapıştırın.

---

## Play Console Service Account Oluşturma

1. [Google Cloud Console](https://console.cloud.google.com) → **IAM & Admin → Service Accounts**
2. **Create Service Account** → İsim: `play-publisher`
3. **Keys** → Add Key → JSON → İndirin
4. [Google Play Console](https://play.google.com/console) → **Settings → API access**
5. Oluşturduğunuz Service Account'u **bağlayın**
6. **Permissions**: En az `Release manager` rolü verin
7. JSON dosyasını Base64'e çevirip `PLAY_SERVICE_ACCOUNT_JSON_BASE64` secret'ına koyun

**PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("service-account.json"))
```

---

## Env Contract Kaynak Sırası

Kanonik secret sözleşmesi şu kaynak sırasıyla düşünülmelidir:

1. Doppler `android-multi-app-framework/prod` — paylaşılan secret ve ortam değerlerinin kanonik kaynağı
2. `.env.template` — repo içindeki isim/contract referansı; gerçek değer içermez
3. Lokal dosya yolları — yalnızca geliştiricinin bilerek kullandığı gitignored dosyalar
4. CI bootstrap secret store — yalnızca Doppler'a erişmek için gereken `DOPPLER_TOKEN`

`KEYSTORE_BASE64` ve `PLAY_SERVICE_ACCOUNT_JSON_BASE64` Doppler'da tutulur. `KEYSTORE_FILE` ve `PLAY_SERVICE_ACCOUNT_JSON` kalıcı, makineye özel Doppler yolları olarak kullanılmaz; `scripts/doppler-run.sh` bunları her komut için özel geçici dosyalara dönüştürür.

## Repository-wide Doppler Wrapper

Herhangi bir build, doğrulama veya yayın komutunu Doppler ortamıyla çalıştırmak için:

```bash
scripts/doppler-run.sh -- <command> [arguments...]
```

Örnekler:

```bash
scripts/doppler-run.sh -- ./gradlew tasks
scripts/doppler-run.sh -- scripts/ci/verify_release_signing_config.sh
scripts/doppler-run.sh -- python3 scripts/ci/verify_play_service_account_project.py
```

Wrapper aşağıdaki işlemleri otomatik yapar:

- `KEYSTORE_BASE64` değerini `0600` izinli geçici dosyaya dönüştürür ve `KEYSTORE_FILE` olarak export eder.
- `PLAY_SERVICE_ACCOUNT_JSON_BASE64` değerini doğrular, `0600` izinli geçici dosyaya dönüştürür ve `PLAY_SERVICE_ACCOUNT_JSON` olarak export eder.
- Geçici dizini `0700` izinle oluşturur.
- Komutun gerçek exit code'unu korur.
- Başarı, hata veya sinyal durumunda geçici dosyaları siler.
- Secret değerlerini veya dosya içeriklerini loglamaz.

## Mimari Şema

```text
Doppler prod
  KEYSTORE_BASE64
  PLAY_SERVICE_ACCOUNT_JSON_BASE64
  diğer environment değerleri
          │
          ▼
scripts/doppler-run.sh
  ├─ private temp dir (0700)
  ├─ release-keystore.bin (0600) → KEYSTORE_FILE
  ├─ play-service-account.json (0600) → PLAY_SERVICE_ACCOUNT_JSON
  └─ wrapped command
          │
          ▼
cleanup trap → tüm geçici secret dosyaları silinir
```

Lokal geliştirmede bilerek kullanılan mevcut dosya yolları desteklenmeye devam eder. İlgili base64 değeri boşsa wrapper mevcut `KEYSTORE_FILE` veya `PLAY_SERVICE_ACCOUNT_JSON` değerini değiştirmez.

---

## Güvenlik Notları

- `.env`, `*.jks`, `*.keystore`, `service-account*.json` dosyaları `.gitignore`'da tanımlıdır
- CI/CD'de keystore ve service account dosyaları iş bitince otomatik silinir (`rm -f`)
- Secret'ları asla log'a yazdırmayın (`echo` ile bile olsa)
- Keystore şifresini ve key şifresini aynı yapabilirsiniz (Google Play bunu önerir)

---

## Project Migration Kisa Runbook

1. Yeni Firebase/GCP project id belirle (or: `makerpars-oaslananka-mobil`).
2. Tum flavor `google-services.json` dosyalarini yeni project'ten indirip guncelle.
3. `config/firebase-apps.json` icindeki `projectId` ve `appId` alanlarini yeni degerlerle esitle.
4. CI preflight:
   - `scripts/ci/verify_google_signin_config.py --flavors all --require-web-client-id --web-client-id <...>`
   - `scripts/ci/verify_play_service_account_project.py --expected-project-id <new-project-id>`
   - `PLAY_SERVICE_ACCOUNT_JSON_BASE64`
   - `FIREBASE_WEB_CLIENT_ID`
   - `ADMOB_*`
   - `ADMIN_ALLOWED_EMAILS`
   - `GOOGLE_RECAPTCHA_SECRET_KEY`

---

## Firebase Configuration (google-services.json)

17 farklı flavor olduğu için Firebase konfigürasyonunu hibrit modelle yönetiyoruz:

1. Lokal kaynak: Git dışı `app/src/*/google-services.json` dosyaları
2. CI/release kaynağı: `FIREBASE_CONFIGS_ZIP_BASE64` secret'ı
3. Ortak materializer: `scripts/ci/materialize_firebase_configs.py`

### Varsayılan Akış (Git-Tracked Secrets Yok)

- `google-services.json` dosyaları repo'ya commit edilmez; `.gitignore` bunu engeller.
- Secrets gerektirmeyen lokal doğrulama için modül seviyesinde görevleri çalıştırın:
  - `./gradlew qualityCheck -PdisableTests=true`
  - `./gradlew :feature:notifications:compileDebugKotlin`
- Tam `:app` derlemesi gerektiğinde flavor config'lerini lokal olarak indirip git dışı tutun.

### Opsiyonel Override (CI)

Gerekirse tüm flavor config'lerini zipleyip base64 olarak secret'a koyabilirsiniz:

```bash
zip -r firebase_configs.zip app/src/*/google-services.json
base64 -w 0 firebase_configs.zip > configs_base64.txt
```

GitHub Secret: `FIREBASE_CONFIGS_ZIP_BASE64`

CI artık zip'i doğrudan açmaz. Ortak materializer sadece whitelist edilen flavor dosyalarını yazar ve JSON/package/appId/projectId kontrolü yapar:

```bash
python3 scripts/ci/materialize_firebase_configs.py --flavors all --mode strict
python3 scripts/ci/verify_google_signin_config.py --flavors all --require-web-client-id
```

Zip formatı için desteklenen yollar:

```text
app/src/<flavor>/google-services.json
<flavor>/google-services.json
google-services/<flavor>.json
```

CI secret gereksinimleri:

```text
FIREBASE_CONFIGS_ZIP_BASE64
FIREBASE_WEB_CLIENT_ID
```

### Firebase'den Güncelleme (Lokal Yardımcı Script)

`scripts/download-firebase-configs.sh` script'i, Firebase CLI ile flavor dosyalarını lokal ortama indirmek için kullanılabilir.
Bu script zorunlu CI adımı değildir; tam uygulama derlemesi gerektiğinde geçici lokal bootstrap adımı olarak düşünülmelidir.


## GitHub protected attested release

The active `Attested Release Artifact` workflow is manual and uses the GitHub environment `production`.

GitHub stores only one bootstrap secret in that environment:

- `DOPPLER_TOKEN`: a read-only service token scoped to project `android-multi-app-framework`, config `prod`.

The workflow installs checksum-verified Doppler CLI 3.76.1, calls `scripts/doppler-run.sh`, materializes the signing keystore and Firebase config only for the requested flavor, builds one signed AAB, deletes temporary secret files, writes a SHA-256 checksum, uploads both files for 7/14/30 days, and creates a GitHub artifact attestation. It does not publish to Google Play.


## Google Play internal release

The manual `Play Internal Release` workflow uses the protected GitHub environment `production` and its single bootstrap secret `DOPPLER_TOKEN`. Doppler provides the signing keystore, Firebase archive, and Play service-account JSON. The workflow queries every Play track, updates the selected flavor to the next available versionCode in the ephemeral runner, builds one signed AAB, creates a SHA-256 checksum and GitHub attestation, uploads that exact file with the Android Publisher API, verifies Google Play returned the same SHA-256, and assigns the uploaded version only to the `internal` track. Production track publication is deliberately unsupported.

The environment allows deployments only from `main`. Rotate the read-only Doppler service token before its configured expiration and never store Play JSON or keystore material as GitHub repository secrets.
