# AdMob 2026 Console Checklist

Bu belge, 5 Nisan 2026 itibariyla resmi Google AdMob dokumantasyonu ve bu repodaki reklam implementasyonuna gore hazirlanmis pratik uygulama listesidir.

## Repo tarafinda zaten tamamlananlar

- Banner'lar artik adaptive sizing kullaniyor.
- Native reklamlarda acik `Reklam` attribution etiketi var.
- App open reklamlari cold-start aninda bloke ediliyor.
- UMP consent ve privacy options akisi kodda mevcut.

## Simdi dogrudan uygulaman gereken ayarlar

### 1) App readiness ve `app-ads.txt`

Zorunlu durum:
- Tum monetized uygulamalarda `app-ads.txt` durumu `Verified` olmali.
- Play Store developer website domain'i ile hosted `app-ads.txt` root domain'i birebir eslesmeli.
- Uygulama AdMob tarafinda `Ready` durumuna gecmeli.

Uygulama:
1. `Apps` > ilgili uygulama > `app-ads.txt` ekraninda status'u kontrol et.
2. `Not found` veya `Unauthorized` goruyorsan once Play Store developer website alanini duzelt.
3. `Check for updates` ile crawl tetikle.

Minimum dosya satiri:
```txt
google.com, pub-XXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
```

Not:
- Birden fazla demand source kullanacaksan onlarin seller satirlari da ayni dosyada yer almali.

### 2) Banner ad unit ayari

Onerilen ayar:
- Tum banner ad unit'lerde `Automatic refresh` = `Google optimized`

Gerekce:
- Google resmi yardim sayfasi optimized refresh'i banner icin tavsiye ediyor.
- Kod tarafinda adaptive banner'a gectigimiz icin bu ayar artik daha anlamli.

Uyari:
- Short-lived ekranlar icin custom agresif refresh kullanma.
- `30s` gibi sert custom refresh yerine optimized kullan.

### 3) High-engagement ads

Onerilen baslangic:
- `High-engagement ads` = `ON`

Gerekce:
- Google yardim sayfasina gore bu ayar standart ad unit'lerde yuksek performansli interstitial ve rewarded varyantlarini acar.
- Repoda cold-start app-open kapatildi ve tam-screen akislari zaten policy gate'lerle sinirli.

Ne zaman kapatilir:
- Policy Center uyarisi artarsa
- accidental click / invalid traffic sinyali gelirse
- kullanici geri bildirimi bariz bozulursa

Not:
- Google dokumanina gore partner bidding ad unit'leri bu switch'ten etkilenmez.

### 4) Frequency capping

Bu repo icin onerilen baslangic:
- AdMob app-level frequency capping = `OFF`

Gerekce:
- Kod tarafinda zaten route, cooldown, session cap ve premium/rewarded-free gate'leri var.
- AdMob app-level cap; interstitial, rewarded ve app-open formatlarini birlikte kisar.
- Bu, ozellikle rewarded geliri gereksiz azaltabilir.

Ne zaman acilir:
- Eger canli trafikte hala fazla tam-screen reklam hissi olusursa, yalnizca gevsek bir safety-net olarak ac.

Gevsek safety-net ornegi:
- `3 impressions / 60 minutes` app-level

Not:
- Bu deger resmi zorunlu bir Google ayari degildir; repo davranisina gore yapilmis temkinli operasyonel oneridir.

### 5) Blocking controls

Temel prensip:
- Google, gelir icin gereksiz bloklamadan kacinilmasini onerir.
- Bu yuzden kategori bloklarini minimum tut.

Bu portfoy icin minimum onerilen blok listesi:
- `Dating`
- `Get rich quick`
- `Reference to sex`
- `Social Casino Games`
- `Sensationalism`
- `Astrology & esoteric`

Varsayilan olarak bloklu kalmasi gereken restricted sensitive kategoriler:
- `Alcohol`
- `Gambling & Betting (18+)`

Opsiyonel brand-safety bloklari:
- `Birth control`
- `Sexual and reproductive health`
- `Weight loss`

Uyari:
- Her ek blok fill ve eCPM dusurebilir.
- Bu nedenle once minimum liste ile basla, sonra gerekirse `Ad review center` ve URL block ile noktasal blok yap.

### 6) Ad review center

Onerilen ayar:
- `Enable`

Kullanim sekli:
- Toplu kategori blok yerine once review center ve advertiser URL block kullan.
- Google bu araclarin sparingly kullanilmasini oneriyor; aksi halde auction rekabeti azalir.

Noktasal block icin iyi kullanim:
- Marka ile acik catisan yaraticilar
- kalitesiz veya clickbait creative'ler
- istenmeyen advertiser URL'leri

### 7) Mediation / bidding

Bu repo icin bugunku net durum:
- Kodda su an yalnizca Google Mobile Ads SDK var.
- Ucuncu taraf Android mediation adapter bagimliliklari yok.

Bu nedenle su anki dogru ayar:
- `AdMob Network only` ile devam et
- Console'da tek basina ucuncu taraf source'u live'a alma

Neden:
- Google mediation dokumanina gore, ad source eklemeden once ilgili formati ve adapter entegrasyonunu uygulama icinde tamamlamak gerekir.
- Mediation partner eklenirse Privacy & messaging tarafinda bu partnerlerin de eklenmesi gerekir.

Ne zaman gecilir:
- Ayrica Android adapter entegrasyonlari eklendiginde
- test cihazlarinda adapter init ve fill goruldugunde
- ILRD / responseInfo ile partner bazli gelir izlenebildiginde

### 8) Privacy & messaging

Canliya almadan once kontrol:
- GDPR/EEA mesaji published
- US states mesaji published
- Privacy options form aciliyor
- Consent degisikligi ayni oturumda etkili oluyor

Eger mediation acilacaksa:
- AdMob Privacy & messaging icinde mediation partnerlerini de ekle

### 9) Manual QA

Canli oncesi zorunlu:
- Home ve content ekranlarinda banner accidental click riski
- Native ad kartlarinda attribution gorunurlugu
- App open reklamlarinda sadece gercek resume/loading benzeri akislarda gosterim
- Ad Inspector ile request/fill/adapter/revenue callback kontrolu

### 10) Sonraki iterasyon

Bu turda bilincli olarak bekletilen konu:
- `setContentUrl` / `setNeighboringContentUrls`

Neden:
- Repoda dogrulanabilir crawlable kanonik icerik URL zinciri gorunmuyor.
- Sahte veya uydurma URL ile content mapping acilmamali.

Bu ancak su durumda acilsin:
- Her icerik tipi icin public, crawlable ve dogrulanmis web URL'si oldugunda

## Bu repo icin kisa karar ozeti

Su an uygula:
- `app-ads.txt` = Verified
- `Automatic refresh` = Google optimized
- `High-engagement ads` = ON
- `App-level frequency capping` = OFF
- `Ad review center` = ON
- `Blocking controls` = minimum liste
- `Mediation` = su an acma

## Resmi kaynaklar

- https://support.google.com/admob/answer/9363762
- https://support.google.com/admob/answer/10564477
- https://support.google.com/admob/answer/3245199
- https://support.google.com/admob/answer/15525707
- https://support.google.com/admob/answer/6244508
- https://support.google.com/admob/answer/3150953
- https://support.google.com/admob/answer/3480906
- https://support.google.com/admob/answer/3150172
- https://developers.google.com/admob/android/mediation
