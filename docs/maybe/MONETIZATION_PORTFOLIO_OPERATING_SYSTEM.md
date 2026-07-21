# Maybe Backlog: Monetization & Portfolio Operating System

Bu belge, çok-flavor uygulama portföyünü gelir + kalite + operasyon dengesiyle yönetmek için önerilen işletim modelini içerir.

## 1) Portföy Yönetimi Modeli

### 1.1 Flavor Segmentasyonu
- `Scale`: yüksek büyüme ve yüksek gelir potansiyeli olanlar
- `Stabil`: orta gelir, düşük risk, düzenli bakım gerekenler
- `Sunset`: düşük kullanım/düşük gelir, minimum maliyet moduna alınacaklar

### 1.2 Haftalık Karar Kuralları
- En iyi 20% flavor’a yatırım artır (ASO + deney + feature)
- En kötü 20% flavor’da maliyet azalt (low-change mode)
- 2-3 sprint üst üste düşen flavor için yeniden konumlandırma veya sunset kararı

---

## 2) Gelir Ölçüm Sistemi

### 2.1 Zorunlu KPI Seti
- `ARPDAU`
- `Ad impressions / DAU`
- `Request -> Loaded -> Show -> Paid` dönüşümleri
- `Subscription conversion rate`
- `One-time purchase conversion rate`
- `D1/D7 retention`
- `Crash-free users`, `ANR rate`

### 2.2 Kırılım Boyutları
- Flavor
- Ülke / dil
- App version
- Trafik kaynağı (organik / kampanya)
- Reklam placement ve format

---

## 3) Reklam Geliri Maksimizasyonu (UX Koruyarak)

### 3.1 Placement Disiplini
- Önemli ekranlar için ad unit’leri ayrıştır
- Placement bazlı eCPM/Fill/Show karşılaştırması yap
- Düşük performanslı placement’leri RC ile kapat/aç

### 3.2 Deney Stratejisi
- Cap/cooldown değerlerini küçük cohort’larla test et
- Aynı anda en fazla 1-2 kritik deney çalıştır
- Karar kriteri: sadece gelir değil retention ve şikayet trendi

### 3.3 Güvenli Otomasyon
- No-fill spike veya crash artışında otomatik “safe profile”a dönüş
- Incident mode ile reklam baskısını geçici düşür

---

## 4) Abonelik + One-Time Gelir Sistemi

### 4.1 Paywall Operasyonu
- En az 2 paywall varyantı (fiyat/mesaj/yerleşim)
- Yıllık plan default, aylık plan alternatif
- Trial/grace period iletişimi ürün içinde net

### 4.2 One-Time Paketler
- Reklam kaldırma (tek seferlik)
- İçerik paket unlock (tema/özel içerik)
- “Bundle” testleri (abone olmayanlar için yüksek dönüşüm adayı)

### 4.3 Churn ve Win-back
- Pasif kullanıcı segmenti için sınırlı süreli teklif
- İptal eğilimi olan kullanıcılar için düşük sürtünmeli geri kazanım akışı

---

## 5) Operasyonel Yönetim Kolaylıkları

### 5.1 Admin Panel Genişletme
- Flavor health dashboard (crash, push başarı, aktif cihaz, ad show rate)
- Campaign templates (Cuma/Kandil/Bayram)
- Quiet hours ve kullanıcı yorgunluğu limitleri

### 5.2 Push Güvenilirliği
- Package bazlı device coverage SLA
- Coverage düşüşünde otomatik alarm
- Gönderim sonrası teslimat/tıklama raporu

---

## 6) Mimari ve Kalite Koruma

### 6.1 Test Bariyerleri
- Navigation/deep-link contract testleri
- DB migration testleri zorunlu
- Kritik ekranlar için screenshot/golden testler

### 6.2 Performans Bariyerleri
- Baseline Profile
- Startup bütçesi (cold start hedefi)
- Ağır init süreçlerinin ertelenmesi

---

## 7) 90 Günlük Önerilen Uygulama Sırası

### Faz A (0-30 gün)
- Ölçüm ve dashboard temeli
- Release safety gate
- Push coverage görünürlüğü

### Faz B (31-60 gün)
- Reklam placement optimizasyonu
- Abonelik/paywall deneyleri
- One-time paket testleri

### Faz C (61-90 gün)
- Otomatik incident mode
- Portföy segmentasyonu (scale/stabil/sunset)
- Haftalık karar otomasyonu

---

## 8) Başarı Tanımı
- Gelir artışı retention düşürmeden sağlanmış olmalı
- Operasyon kararları dashboard verisiyle alınmalı
- Yayın süreci manuel sürprizlerden arındırılmış olmalı
