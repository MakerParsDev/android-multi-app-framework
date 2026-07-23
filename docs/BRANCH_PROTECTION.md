# Main Branch Protection Policy

Bu belge GitHub issue #39 kapsamında `main` dalına uygulanan repository ruleset politikasını tanımlar.

## Uygulama

Ruleset tanımı `config/main-ruleset.json` dosyasında sürümlenir. Repository yöneticisi, aktif GitHub hesabı `MakerParsDev` iken aşağıdaki komutu çalıştırır:

```bash
bash scripts/ci/apply_main_ruleset.sh
```

Betik aynı isimli repository ruleset mevcutsa günceller, yoksa oluşturur ve sunucudaki nihai alanları yerel politika ile karşılaştırır.

## Zorunlu kurallar

- `main` yalnız pull request üzerinden güncellenir.
- En az bir onay gerekir.
- Yeni review edilebilir commit geldiğinde eski onaylar düşer.
- Tüm review konuşmaları çözülmeden merge yapılamaz.
- Dal silme ve force-push engellenir.
- PR branch'i güncel `main` ile test edilir.
- Şu kontroller zorunludur:
  - `CI Required`
  - `Analyze Java and Kotlin`
  - `Secret Scan`
  - `Semgrep SAST`
  - `Workflow Audit`
  - `Dependency Review`
  - `Instrumentation Tests`

Ruleset içinde kalıcı bypass actor tanımlanmaz.

## Acil bypass

Acil bypass yalnız üretim güvenliği veya hizmet sürekliliği için, normal PR akışının zararı büyüttüğü doğrulanmış olaylarda kullanılabilir.

1. Olay için GitHub issue açılır; gerekçe, etki, operatör, başlangıç zamanı ve geri dönüş planı yazılır.
2. Mevcut ruleset JSON'u ve ruleset kimliği kanıt olarak kaydedilir.
3. Ruleset geçici olarak `disabled` durumuna alınır. Doğrudan push yerine mümkün olduğunda yine PR kullanılır.
4. Acil değişiklikten hemen sonra `bash scripts/ci/apply_main_ruleset.sh` çalıştırılarak sürümlü politika yeniden etkinleştirilir.
5. Repository **Rule Insights** ekranından bypass/değişiklik kayıtları incelenir ve olay issue'suna eklenir.
6. Olay sonrası inceleme tamamlanmadan issue kapatılmaz.

Kalıcı yönetici, kullanıcı, deploy key veya uygulama bypass'ı eklenmez. Bypass ihtiyacının tekrarlanması ayrı bir politika değişikliği ve code review gerektirir.

## Dependabot ve bot PR politikası

- Dependabot PR'ları da aynı required check ve review kurallarına tabidir.
- High/critical güvenlik güncellemeleri öncelikli incelenir ancak kontroller atlanmaz.
- Lockfile değişiklikleri beklenen paket zinciriyle sınırlandırılır.
- Otomatik merge ancak tüm zorunlu kontroller geçtikten ve gerekli onay verildikten sonra kullanılabilir.
- Bot PR'ında beklenmeyen script, workflow veya source-code değişikliği varsa otomatik merge kullanılmaz.

## Değişiklik yönetimi

Ruleset değişiklikleri önce `config/main-ruleset.json`, testler ve bu belgede yapılır. `scripts/ci/main_ruleset_policy_test.py` geçmeden GitHub ayarı değiştirilmez. Uygulama sonrasında sunucu cevabı betik tarafından doğrulanır.
