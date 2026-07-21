# CI Release Validation Plan (Workflow Patchsiz)

## Amac

Bu dokuman, mevcut CI workflow'larini degistirmeden release/publish dogrulamasini guvenli sekilde nasil yurutulecegini tarif eder.

Hedefler:

- Fail-fast (`validateReleaseConfig`) davranisini operasyonel olarak dogrulamak
- Signed AAB build smoke testini guvenli sekilde kosmak
- Production publish riskini kontrol altinda tutmak

## Mevcut Workflow Gercekleri (Current-State Constraints)

Mevcut repo workflow'larina gore:

- `release.yml` workflow `workflow_dispatch` ile calisir
- `release.yml` icinde publish adimlari `publish*ReleaseBundle` tasklariyla calisir
- Publish komutu `-PPLAY_TRACK=production` kullanir
- `publish_to_play=true` gercek Play publish riskini tetikler
- `quality-gate.yml` PR odaklidir; release publish smoke yerine kalite kapisi gorevi gorur

Sonuc:

- Gercek publish smoke "dry-run" olarak guvenli kabul edilemez (production track riski)
- Publish positive validation sadece release penceresinde ve onayli sekilde yapilmalidir

## Guvenli Dogrulama Akislari (Publishsiz)

### Akis-A: PR / branch guvenli kalite kapisi

Kullanim:

- `quality-gate.yml` otomatik tetiklenir

Beklenen:

- Unit testler
- Static analysis
- Lint baseline summary (`scripts/ci/summarize_lint_baseline.py`) gorunurlugu

Not:

- Release fail-fast pozitif testi bu akista zorunlu degildir

### Akis-B: Manual release smoke (publish kapali)

Kullanim:

- `release.yml` -> `workflow_dispatch`
- `publish_to_play=false`
- `target_flavors=<tek flavor veya kucuk set>`

Beklenen:

- Signed AAB build
- Artifact upload (`app/build/outputs/bundle/`)
- Publish job calismaz

Bu akis, fail-fast + signing + release bundle uretilmesi icin guvenli smoke testtir.

### Akis-C: Gercek publish (release window)

Kullanim:

- `release.yml` -> `workflow_dispatch`
- `publish_to_play=true`
- Onayli release/change window icinde

Beklenen:

- Auto-bump marker/guard adimlari calisir
- Build artifacts + bumped `app-versions.properties` olusur
- `publish-production` job Play Console publish islemini calistirir

## Publish Dogrulama Politikasi (Production Track Riski Nedeniyle)

Bu fazda workflow patch yapilmadigi icin:

- `publish_to_play=true` yalniz gercek release penceresinde kullanilir
- Routine smoke testlerde `publish_to_play=false` zorunlu tercih edilir
- Positive publish smoke, production degisikligi etkileyebilecegi icin "test" amacli rastgele kosulmaz

## Negatif Fail-fast Smoke Matrisi

Bu testler lokal veya ephemeral runner uzerinde yapilir.

### Senaryo 1: Bos push registration URL

Komut:

```powershell
./gradlew :app:validateReleaseConfig -PPUSH_REGISTRATION_URL=
```

Beklenen:

- Anlasilir hata mesaji ile fail

### Senaryo 2: Gecersiz / eksik keystore

Komut (ornek):

```powershell
$env:KEYSTORE_FILE='C:\\missing\\release.jks'
./gradlew :app:bundleAmenerrasuluRelease
```

Beklenen:

- `validateReleaseConfig` kaynakli fail-fast hata

### Senaryo 3: Publish task + eksik Play service account

Komut (ornek):

```powershell
Remove-Item Env:PLAY_SERVICE_ACCOUNT_JSON -ErrorAction SilentlyContinue
./gradlew :app:publishAmenerrasuluReleaseBundle -m
```

Not:

- `-m` (dry run / task graph) ortami gore farkli davranabilir.
- Lokal negatif smoke'ta amac, publish task adi secildiginde `validateReleaseConfig` gereksinimini dogrulamaktir.
- Gerekirse gecici no-op ortami veya `-x` ile task graph manipulasyonu kullanilarak preflight tetiklenmesi izole dogrulanir.

Beklenen:

- Play service account eksikligi nedeniyle fail

### Senaryo 4: Debug task etkilenmez

Komut:

```powershell
./gradlew :app:assembleAmenerrasuluDebug
```

Beklenen:

- Fail-fast release kontrolu debug taski bloklamaz

## Pozitif Release Build Smoke Matrisi

### Lokal smoke (onerilen)

1. `./gradlew :app:validateFlavorVersions`
2. `./gradlew :app:validateReleaseConfig`
3. `./gradlew :app:bundle<Flavor>Release`

Beklenen:

- Basarili AAB uretimi
- Artifact `app/build/outputs/bundle/<flavor>Release/` altinda mevcut

### CI smoke (publishsiz, guvenli)

Workflow:

- `release.yml` manual dispatch
- `publish_to_play=false`
- `target_flavors=<tek flavor veya kucuk set>`

Beklenen:

- `build-release` job basarili
- AAB artifact upload basarili
- `publish-production` job kosmaz

## Log / Artifact Kabul Kriterleri

- `build-release` job logs icinde bundle tasklari basarili
- `Upload AAB Artifacts` adimi artifact uretiyor
- Publish kapali smoke'ta production publish adimi calismiyor
- Fail-fast negatif testlerinde hata nedeni config eksikligi olarak acik gorunuyor

## Operasyon Notlari ve Riskler

### Risk: Production track publish

- `release.yml` publish tasklari `-PPLAY_TRACK=production` ile calisir
- Test amacli positive publish smoke risklidir

### Risk: VersionCode reuse

- `publish_to_play=false` akisinda workflow zaten artifact versionCode reuse riski icin warning basar
- Bu artifact daha sonra manuel publish icin kullanilacaksa versionCode tekrar kontrol edilmelidir

### Risk: Secret drift

- Lokal smoke ve CI smoke ayni secret setini kullanmiyorsa farkli sonuc alinabilir
- `validateReleaseConfig` lokal/CI preflight standardi olarak kullanilmalidir

## Gelecek Faz Workflow Patch Onerileri (Bu Fazda Patch Yok)

1. `release.yml` icine ayri `preflight-release-config` step (`./gradlew :app:validateReleaseConfig`)
2. `publish_to_play=true` icin opsiyonel `validate_only` / `dry_run` input
3. `PLAY_TRACK` override input (default `production`, smoke icin `internal`)
4. `quality-gate.yml` icine lint baseline stale check (`scripts/ci/prune_lint_baseline.py --mode check`) warning/job entegrasyonu

## Onerilen Yurutum Sirasi (Pratik)

1. Lokal negatif fail-fast smoke (3 senaryo)
2. Lokal pozitif `bundle<Flavor>Release` smoke
3. CI `release.yml` manual dispatch (`publish_to_play=false`)
4. Gercek release window'da `publish_to_play=true` production publish
