# Release Health Runbook

Bu runbook her Android flavor release'i icin `+24`, `+48` ve `+72` saat saglik kontrolunu tanimlar. Release kimligi asagidaki tuple ile takip edilir:

```text
package_name + flavor + version_code + version_name + release_revision + release_track
```

Esiklerin makine tarafindan okunan kaynagi `config/runtime-observability-policy.json` dosyasidir. Bu dokuman ile policy celisirse policy gecerlidir.

## Kaynak otoritesi

| Sinyal | Kaynak otoritesi | Kullanim |
|---|---|---|
| Crash ve ANR rate | Google Play Vitals | Rollout durdurma, hotfix ve rollback karari |
| Fatal stack trace ve non-fatal | Firebase Crashlytics | Kok neden, flavor/version/revision ayrimi |
| Consent ve urun funnel eventleri | GA4 | UMP, ads ve urun davranisi |
| Request, match, show, impression ve gelir | AdMob | Ad delivery ve monetization sagligi |
| Release surumu ve track | Git revision + Play track + app version | Her sinyali dogru release ile eslestirme |

Sentry bu repository'nin kaynak otoritesi degildir. Yeni telemetry saglayicisi eklenirse ayni sinyal icin cift alarm uretmemeli ve bu tablo guncellenmelidir.

## Runtime sinyal sozlesmesi

Crashlytics her process icin su custom key'leri tasir:

- `package_name`
- `flavor`
- `version_code`
- `version_name`
- `build_type`
- `release_revision`
- `release_track`

Asagidaki operasyonel failure gruplari ayri exception siniflariyla kaydedilir:

- `billing_purchase_verification_failure`
- `remote_config_fetch_failure`
- `ump_consent_failure`
- `mobile_ads_initialization_failure`

Push registration failure'lari mevcut Crashlytics non-fatal akisini kullanir. Ad load/show failure'lari GA4'te placement, format, route, error code ve backoff attempt ile kaydedilir. `AdMob error code 3` no-fill'dir; `ad_technical_failure_rate` hesabina dahil edilmez.

Token, authorization header, e-posta veya kullanici kimligi runtime attribute olarak kaydedilmez.

## Checkpoint hazirligi

Release baslamadan once:

1. Git revision, flavor, version code/name ve Play track'i release kaydina yazin.
2. `config/runtime-health-snapshot.example.json` dosyasini kopyalayin.
3. Dosyadaki butun kimlik alanlarini release ile degistirin.
4. Ilgili checkpoint icin sekiz metrigin tamamini gercek verilerle doldurun.
5. Snapshot'i Azure Secure Files altinda `release-health-snapshot.json` adiyla yukleyin veya farkli adi pipeline parametresinde belirtin.

Yerel degerlendirme:

```bash
python3 scripts/ci/validate_runtime_observability.py
python3 scripts/ci/evaluate_release_health.py \
  --snapshot /secure/path/release-health-snapshot.json \
  --expected-checkpoint 24 \
  --fail-on hotfix
```

Azure'da `azure-pipelines/release-health.yml` pipeline'ini `24`, `48` veya `72` checkpoint parametresiyle queue edin. JSON ve Markdown karar raporlari `release-health-<checkpoint>h` artifact'i olarak yayimlanir.

## Metrik tanimlari

| Metrik | Hesap |
|---|---|
| `crash_rate` | Play Vitals user-perceived crash rate |
| `anr_rate` | Play Vitals user-perceived ANR rate |
| `new_error_affected_users` | Bu versionCode'da yeni crash/ANR issue'larindan etkilenen benzersiz kullanici |
| `billing_verification_failure_rate` | Basarisiz server verification / toplam verification attempt |
| `ad_technical_failure_rate` | Error code 3 haric teknik ad-load failure / toplam ad request |
| `ump_consent_failure_rate` | `consent_error` / `consent_flow_started` |
| `push_registration_failure_rate` | Final registration failure / registration sync attempt |
| `remote_config_failure_rate` | Fetch-and-activate failure / fetch-and-activate attempt |

Payda sifirsa metrigi `0` olarak uydurmayin. Snapshot'i eksik birakin; evaluator `INCOMPLETE` karari vererek rollout ilerlemesini engeller.

## +24 saat kontrolu

- Play Vitals'ta versionCode + device/API kiriliminda crash ve ANR rate'i alin.
- Yeni grouped error issue'larini etkilenen kullanici sayisina gore siralayin.
- Crashlytics'te ayni versionCode, flavor ve revision icin fatal/non-fatal gruplari kontrol edin.
- Billing, UMP, push ve Remote Config failure oranlarini hesaplayin.
- AdMob/GA4 ad request ve technical failure oranlarini placement bazinda kontrol edin.
- Remote Config degisikligi release ile ayni zaman araligindaysa once/sonra karsilastirmasi ekleyin.
- Evaluator sonucunu issue/release kaydina ilistirin.

## +48 saat kontrolu

- +24 saat bulgularinin kapanip kapanmadigini dogrulayin.
- Yeni device/API cluster veya artan affected-user trendi arayin.
- Ad match/show/eCPM ve teknik failure trendini onceki release ile karsilastirin.
- `WATCH` sinyallerinin owner ve aksiyon kaydi yoksa rollout ilerletmeyin.

## +72 saat kontrolu

- Crash/ANR, billing, consent, push ve Remote Config sinyallerinin stabil oldugunu dogrulayin.
- Acik grouped error issue'larini backlog veya incident olarak siniflandirin.
- Release'i `healthy`, `accepted-risk` veya `incident-follow-up` olarak kapatin.
- Threshold degisikligi gerekiyorsa ayni PR'da policy, gerekce ve testleri guncelleyin.

## Play grouped error triage

1. Issue'nun yalniz yeni versionCode'da mi goruldugunu kontrol edin.
2. User-perceived, affected users, report count, son gorulme zamani, API level ve device model kirilimini kaydedin.
3. Stack trace'i Crashlytics issue'su ve release revision ile eslestirin.
4. Reproduction durumu bilinmiyorsa owner atayin ve `WATCH` kabul suresi icinde yeniden kontrol edin.
5. Ayni stack yeni release ile basladiysa regression olarak isaretleyin.
6. Fix dogrulamasi icin issue ID, duzeltme commit'i ve hedef versionCode'u birbirine baglayin.

## Karar proseduru

Evaluator en yuksek esik ihlalini secer:

- `HEALTHY`: Rollout devam eder; sonraki checkpoint zorunludur.
- `WATCH`: Owner atanir, release notuna kayit dusulur ve policy'deki acknowledgement suresi icinde yeniden olculur.
- `HOTFIX`: Rollout pause edilir; P0/P1 incident acilir; duzeltme build'i hazirlanir ve ayni health gate'ten gecirilir.
- `ROLLBACK`: Play rollout halt/rollback edilir ve son Remote Config degisikligi geri alinir. Etkilenen release icin postmortem zorunludur.
- `INCOMPLETE`: Eksik metrik tamamlanmadan rollout artirilmaz.

Tek bir yeni P0 crash/ANR cluster'i, kullanici kaybi veya odeme dogrulama guvenligi problemi yuzdesel esik altinda kalsa bile manuel olarak `HOTFIX` ya da `ROLLBACK` seviyesine yukselebilir.

## Incident kapanisi

Incident kaydinda en az sunlar bulunmalidir:

- release identity tuple
- ilk alarm ve acknowledgement zamani
- kaynak dashboard/report baglantilari
- etkilenen flavor, versionCode, API/device segmenti
- karar ve karar sahibi
- rollback/hotfix commit ve yeni versionCode
- +24/+48/+72 dogrulama sonuclari
- kalici onleyici aksiyon
