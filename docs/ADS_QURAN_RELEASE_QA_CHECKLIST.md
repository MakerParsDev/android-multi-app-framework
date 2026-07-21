# Ads + Quran + Release QA Checklist

## Amac ve Kapsam

Bu checklist, son kritik duzeltmelerden sonra manuel regresyon dogrulamasi icindir.

Kapsam:

- Ads lifecycle / rewarded rotasyon davranisi
- Consent / privacy options akislarinin stabilitesi
- Quran sure sync dayaniklilik davranisi (partial sync recovery)
- Release fail-fast dogrulama davranisi

Bu dokuman workflow patch veya otomasyon yerine manuel QA yurutumu icin kullanilir.

## On Kosullar

- `debug` test build cihaza kurulmus
- Test cihazi (varsa) AdMob test device olarak tanimli
- Internet baglantisi mevcut (kesme/geri getirme testi icin kontrol edilebilir)
- `.env` dosyasi hazir (release fail-fast ve local release smoke icin)
- `adb logcat` erisimi mevcut
- Gerekirse ikinci fiziksel cihaz (rotasyon + reward timing gozlemi icin)

## Test Cihaz / Ortam Matrisi

Asagidaki matris minimum kapsami saglar:

| Alan | Minimum | Not |
|---|---|---|
| Android surumu | 1 modern cihaz (Android 12+) | Munkunse bir eski cihaz daha ekleyin |
| Form factor | Telefon | Tablet opsiyonel |
| Network | Wi-Fi + kesme senaryosu | Quran sync recovery icin gerekli |
| Build | `Debug` | Ads/consent davranisi gozlemi icin |
| Flavor | `namazvakitleri` + 1 diger | Quran/ads davranisi kapsami icin |

## Ads Regresyon Checklist (Rewarded + Consent)

### 1. Rewarded normal akis

Adimlar:

1. Rewarded ad tetikleyen akisi baslat.
2. Ad yuklenmesini bekle.
3. Adi goster.
4. Reward earned olusana kadar tamamla.
5. Ad kapanisini bekle.

Beklenen:

- Reward logic tetiklenir (`onUserEarnedReward` davranisi gorulur)
- Ad kapanisi sonrasi preload/reload devam eder
- Crash/ANR olmaz

### 2. Rewarded rotasyon akisi (kritik fix dogrulamasi)

Adimlar:

1. Rewarded ad akisini baslat.
2. Ad acikken veya ad tamamlanma esiginde cihaz orientation degistir (portrait/landscape).
3. Rewarded completion ve dismiss akisini tamamla.

Beklenen:

- Reward durumu guncellenir
- Sessiz coroutine kaybi olmaz
- Sonraki rewarded/interstitial preload devam eder

### 3. Consent ilk acilis akisi

Adimlar:

1. Uygulamayi temiz durumdan ac (gerekiyorsa app data temizleyerek).
2. Consent gerekiyorsa formu tamamla/kapat.

Beklenen:

- Consent form gorunur (gereken bolgelerde)
- Consent kapanisi sonrasi ad request gating dogru calisir

### 4. Privacy options / consent refresh

Adimlar:

1. Ayarlar ekranina git.
2. Privacy options veya consent formunu yeniden ac.
3. Consent tercihlerini degistir.

Beklenen:

- Ayni oturumda ad preload/cleanup davranisi guncellenir
- Consent refresh sonrasi beklenmeyen crash olmaz

### 5. Consent reddi sonrasi ad davranisi

Adimlar:

1. Consent tercihini daha kisitlayici hale getir (test bolgesi senaryosu uygunsa).
2. Ad tetikleyen akislarin davranisini gozlemle.

Beklenen:

- Yeni ad requestler izin durumu ile uyumlu sekilde bloke edilir veya sinirlanir
- UI akisi bozulmaz

## Quran Sync Dayaniklilik Checklist

### 1. Ilk yukleme (tam ag)

Adimlar:

1. Quran sure icerigi ilk kez ac.
2. Sync tamamlanana kadar bekle.
3. Ayni sureyi tekrar ac.

Beklenen:

- Tum ayetler yuklenir
- Tekrar acilista gereksiz network fetch azalir/olmaz (fast-path)

### 2. Ag kesintisi sirasinda sync

Adimlar:

1. Sure sync baslat.
2. Sync sirasinda interneti kes (Wi-Fi kapat / ucak modu).
3. Uygulama davranisini gozlemle.

Beklenen:

- Sure kismi veriyle "tamamlandi" sayilmaz
- Sonraki denemeleri bloklayan sahte tamamlanma durumu olusmaz

### 3. Recovery / retry

Adimlar:

1. Agi geri ac.
2. Ayni sureyi tekrar ac.
3. Sync tamamlanana kadar bekle.

Beklenen:

- Tam veri gelir
- Onceki kismi durum retry akisini bozmaz

### 4. Zorunlu edition dogrulamasi (Arabic + TR)

Adimlar:

1. Mumkunse logcat uzerinden Quran sync loglarini izle.
2. Arabic/TR fetch problemli durumda davranisi gozlemle.

Beklenen:

- Required set eksikse DB write tamamlandi gibi davranmaz
- Kullanici davranisi tarafinda eksik/yarim sure "kalici" olmaz

## Release Fail-fast Checklist (Negatif + Pozitif)

### Negatif testler

1. `PUSH_REGISTRATION_URL` bos -> `validateReleaseConfig` fail etmeli
2. `KEYSTORE_FILE` gecersiz -> `bundle<Flavor>Release` fail etmeli
3. `PLAY_SERVICE_ACCOUNT_JSON` eksik + publish task -> fail etmeli
4. `assembleDebug` veya debug build config task -> fail etmemeli

### Pozitif testler

1. `./gradlew :app:validateFlavorVersions` gecer
2. `./gradlew :app:validateReleaseConfig` gecer (gercek `.env` ile)
3. En az 1 flavor `bundleRelease` basariyla uretilir
4. Artifact yolu dogrulanir: `app/build/outputs/bundle/...`


### Ads senaryolari

- Reward earned event/log goruldu
- Rewarded dismiss sonrasi preload istegi goruldu
- Consent refresh sonrasi ad request gate davranisi dogru
- Crash/ANR yok

### Quran senaryolari

- Kismi sync sonrasi tekrar fetch davranisi dogru
- Tam sync sonrasi stabil acilis dogrulandi

## Kanit Toplama Formati

Her senaryo icin tek satir kayit tutun:

- `Scenario`
- `Device/OS`
- `Build flavor/version`
- `Result (PASS/FAIL)`
- `Evidence` (screenshot/logcat dosya yolu / komut cikti yolu)
- `Notes`

Ornek:

```text
Scenario=Rewarded rotate; Device/OS=Pixel 7 / Android 14; Build=namazvakitleriDebug 1.2.3(45); Result=PASS; Evidence=TEMP_OUT/logcat_rewarded_rotate.txt; Notes=Reward earned + preload observed
```

## Exit Criteria (Go / No-Go)

Go:

- Ads rewarded normal + rotasyon senaryolari PASS
- Consent ilk acilis + refresh senaryolari PASS
- Quran sync kesinti + recovery senaryolari PASS
- Fail-fast negatif testleri beklenen sekilde fail
- En az 1 release bundle smoke PASS

No-Go:

- Rewarded rotasyon sonrasi reward/preload kaybi
- Quran sure sync kismi veriyle kalici tamamlanma
- Fail-fast kontrollerinin release/publish tasklarda calismamasi
- Crash/ANR veya bloklayici consent regressioni
