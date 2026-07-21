# `.ci/apps.json` Catalog Policy

Bu dosya, flavor bazlı CI metadata kataloğudur. Özellikle:

- `flavor`
- `package`
- `admob_app_id`
- reklam birimi kimlikleri (`ad_units.*`)

alanlarını CI/CD ve operasyon script'leri için tek yerde toplar.

## Neden Repoda Tutuluyor?

Bu repo multi-flavor olduğu için:

- CI publish/build akışları flavor → package/ad config eşlemesine ihtiyaç duyar
- Tek kaynaklı bir katalog bakım ve otomasyonu kolaylaştırır

## Güvenlik Sınıflandırması

AdMob App ID ve ad unit ID değerleri **gizli anahtar (secret)** değildir; istemci uygulama içinde de bulunabilirler.

Buna rağmen bu dosya şu sebeplerle “hassas metadata” olarak ele alınmalıdır:

- toplu envanter görünürlüğü sağlar
- yanlış düzenleme tüm flavor publish akışını etkileyebilir
- public repo senaryosunda operasyonel yüzeyi artırır

## Bakım Kuralları

### 1) Yeni flavor eklendiğinde

`buildSrc/src/main/kotlin/FlavorConfig.kt` içine yeni flavor eklendiğinde:

- `.ci/apps.json` kataloğuna karşılık gelen entry eklenmelidir
- `flavor` ve `package` birebir eşleşmelidir
- `ad_units` alanları eksiksiz doldurulmalıdır
  - Zorunlu alanlar: `banner`, `interstitial`, `native`, `rewarded`, `open_app`
  - Opsiyonel alan: `rewarded_interstitial` (varsa string olmalı)

### 2) Publish öncesi doğrulama

CI şu script ile doğrulama yapar:

- `scripts/ci/validate_ci_apps_catalog.py`

Modlar:

- `warn`: görünürlük (PR / quality gate)
- `strict`: publish path fail-fast (hedef flavor'lar için)

### 3) Eksik Flavor Politkası (Geçici)

Eğer flavor henüz yayınlanmıyor veya AdMob kurulumu tamamlanmadıysa:

- quality CI'da warning kabul edilebilir
- publish hedefleniyorsa strict mod bunu hata olarak durdurmalıdır
- geçici istisnalar `.ci/apps-catalog-allowlist.json` içinde reason ile kayıt altına alınmalıdır

## Public Repo Senaryosu (Gelecek Opsiyon)

Repo public yapılırsa veya erişim politikası değişirse:

- `.ci/apps.json` redacted/masked hale getirilebilir
- gerçek değerler R2 / Vault / GitHub Secrets tabanlı bir kaynakta tutulabilir
- CI runtime'da decrypt/fetch ederek kullanılabilir

Bu geçiş zorunlu değildir; mevcut private-repo modelinde dosyanın repoda tutulması kabul edilebilir.

## İyi Pratikler

- JSON formatını manuel düzenleme sonrası validator script ile kontrol et
- Flavor/package rename yapıldıysa `.ci/apps.json` eşleşmesini aynı PR'da güncelle
- Toplu değişikliklerde CI warning/error loglarını göz ardı etme
