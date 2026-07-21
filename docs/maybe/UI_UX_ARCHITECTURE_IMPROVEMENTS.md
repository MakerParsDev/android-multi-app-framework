# Maybe Backlog: UI/UX + Mimari İyileştirme Fikirleri

Bu belge, mevcut yol haritasına eklenebilecek ama zorunlu olmayan iyileştirme fikirlerini içerir.

## 1) Tasarım Sistemi Tekleştirme (P1)
- Hedef: Tüm flavor'larda spacing, typography, component davranışlarını standartlamak.
- Etki: Tutarlı görünüm + düşük bakım maliyeti.
- Odak: `core/designsystem`, ortak token ve component kontratları.

## 2) UI State Kontratı Sertleştirme (P1)
- Hedef: Her ekranda `Loading / Success / Error / Empty` state modelini zorunlu hale getirmek.
- Etki: Sessiz UI bozulmalarını ve edge-case hatalarını azaltır.
- Odak: Feature ViewModel ve ekran state modelleri.

## 3) Navigation + Deep Link Contract Testleri (P1)
- Hedef: Bildirimden açılış, detail route ve back-stack davranışını testle güvenceye almak.
- Etki: Push tıklaması sonrası yanlış ekrana düşme regresyonlarını önler.
- Odak: `AppNavigation`, bildirim intent akışları, route testleri.

## 4) Sync/Worker Yapısını Tek Motor Haline Getirme (P1)
- Hedef: Push registration, content sync ve retry/backoff işlerini ortak bir sync patterninde toplamak.
- Etki: Daha az dağınık kod, daha kolay debug, daha öngörülebilir davranış.
- Odak: `core/firebase`, WorkManager orkestrasyonu, retry politikaları.

## 5) Baseline Profile + Startup Optimizasyonu (P2)
- Hedef: En çok kullanılan flavor'larda cold start ve first frame sürelerini azaltmak.
- Etki: Kullanıcı deneyimine direkt pozitif etki.
- Odak: Baseline Profile, startup trace, ağır init noktalarının ertelenmesi.

## 6) Screenshot/Golden Testler (P2)
- Hedef: Kritik ekranların görsel regresyonlarını PR aşamasında yakalamak.
- Etki: UI değişikliklerinde sürpriz kırılmaları azaltır.
- Odak: Ana ekranlar, içerik/detail ekranları, tema varyasyonları.

## 7) Operasyonel Dashboard (P1)
- Hedef: Admin panelde flavor sağlık görünümü (crash, push başarı, aktif cihaz, ad show rate).
- Etki: Teknik ve operasyon ekipleri için hızlı karar alma.
- Odak: `side-projects/admin-notifications` + Firebase Functions agregasyon endpointleri.

---

## Uygulama Notu
- Bu maddeler “zorunlu sprint” değil, uygun zamanda alınacak "maybe backlog" adaylarıdır.
- Seçim önerisi: Önce P1 maddeleri (1, 2, 3, 4, 7), sonra P2 maddeleri (5, 6).
