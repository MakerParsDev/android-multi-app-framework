# Play Publish Standards (GitHub Actions)

Bu doküman, bu repo için güvenli ve standart Play Console yayın akışını tanımlar.

## Temel Kural

Her flavor (`applicationId`) için `versionCode` monoton artmalıdır. Aynı veya daha düşük `versionCode` Play Console tarafından reddedilir.

Bu repo'da publish güvenliği şu modelle sağlanır:

1. Play API'den hedef flavor(lar) için mevcut en yüksek `versionCode` değerini oku (`all tracks`)
2. `+1` hesapla
3. `app-versions.properties` dosyasını CI runner workspace'inde güncelle
4. AAB'yi güncel değerle build et
5. Play'e publish et

## Neden CI Publish Job'unda Repo'ya Auto-Push Yok?

Publish pipeline içinde `git commit && git push` yapılmaz.

Gerekçe:

- branch protection / permission / audit karmaşası
- publish job'unun repo state mutasyonuna dönüşmesi
- yanlış konfigürasyonda CI loop riski
- publish güvenliği için gereksiz side-effect

Publish güvenliği için gerekli olan tek şey: **build/publish öncesi anlık auto-bump**.

## app-versions.properties Repo'da Neden Stale Kalabilir?

CI publish sırasında yapılan auto-bump repo'ya yazılmaz; runner workspace içinde kalır.

Bu nedenle repo'daki `app-versions.properties` zaman zaman Play'deki güncel max `versionCode` değerlerinden geri kalabilir. Bu beklenen bir durumdur.

### Repo Sync (Manual / Güvenli)

Repo içi görünürlük için ayrı workflow kullanılır:

- `.github/workflows/sync-play-version-codes.yml`

Bu workflow:

- Play'den `all tracks` max `versionCode` değerlerini çeker
- `app-versions.properties` dosyasını günceller
- artifact olarak çıktı üretir
- repo'ya otomatik push yapmaz

## GitHub Actions Publish Workflow Davranışı

### `release.yml`

- `publish_to_play=true` ise auto-bump çalışır
- `publish_to_play=false` ise auto-bump çalışmaz
- publish job, build job'da bump edilmiş `app-versions.properties` dosyasını artifact'ten restore eder
- publish koşuları `play-publish-global` concurrency ile serialize edilir

### `manual-ops.yml`

Auto-bump yalnız şu durumda çalışır:

- `build_type=Release`
- ve (`do_internal_test=true` veya `do_publish=true`)

## VersionName Senkronizasyonu

CI auto-bump script'i `--sync-version-names` ile çalışır. Böylece `versionCode` güncellenirken `versionName` drift'i azaltılır.

Not: `versionName` senkronizasyonu operasyonel görünürlük içindir; Play publish için kritik olan alan `versionCode`'dur.

## Non-publish Release Artifact Uyarısı

`release.yml` içinde `publish_to_play=false` ile üretilen release AAB artifact'leri:

- Play auto-bump uygulanmadan build edilmiş olabilir
- manuel Play upload sırasında `versionCode already used` hatası verebilir

Bu nedenle workflow artifact adı uyarı içerecek şekilde etiketlenir (`nonpublishable-versioncode`).

## Operasyon Checklist (Kısa)

- Publish için GH Actions `release.yml` veya `manual-ops.yml` kullan
- Auto-bump log satırlarını kontrol et:
  - `Auto-bump starting`
  - `Play versionCode summary`
  - `Auto-bump completed`
- Aynı anda birden fazla publish run başlatma (workflow concurrency zaten serialize eder)
