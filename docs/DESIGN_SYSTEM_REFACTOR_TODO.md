# Design System Refactor TODO

Bu dosya, ikinci fazda tamamlanacak design-system migrasyon işlerini görünür kılmak için eklendi.

## 1) Hardcoded `dp` kalan ekranlar
Kalan hedef dosyalarda (`AppBottomBar`, `SettingsScreen`, `RewardsScreen`) `LocalDimens.current` geçişi tamamlandı.

Bu başlık altında aktif kalan borç yok.

## 2) Wrapper component dönüşümü kalan yerler
Doğrudan Material bileşeni kalan yerlerde:

- `Button` -> `AppButton`
- `Card` -> `AppCard`
- `TopAppBar` -> `AppTopBar` (tamamlandı; kalan yok)
- `OutlinedTextField/TextField` -> `AppTextField` (Messages ekranında tamamlandı)

Kalan odak dosyaları:

- Kalan odak dosyası yok.

## 3) Kural hedefi
- UI katmanında sabit `dp` kullanımını sıfırlamak
- Tüm spacing/radius/elevation/icon-size değerlerini `LocalDimens` üzerinden okumak
- Stil kararlarını wrapper component içinde sabitlemek

## 4) Not
Bu TODO dosyası bilinçli olarak bırakıldı; böylece hiçbir varsayım yapılmadan kalan teknik borçlar şeffaf şekilde takip edilebilir.

## 5) Tamamlananlar (özet)
- `feature/content/**` ekranları token bazlı hale getirildi.
- `feature/ads/ui/NativeAdItem.kt` ve `NativeAdCompose.kt` tokenlaştırıldı.
- `feature/billing/ui/SubscriptionScreen.kt`, `feature/audio/ui/MiniAudioPlayer.kt`, `feature/messages/ui/MessageDetailScreen.kt`, `feature/otherapps/ui/OtherAppsScreen.kt`, `feature/notifications/**` dönüştürüldü.
- `app/src/main/java/com/parsfilo/contentapp/ui/AppBottomBar.kt`, `feature/settings/ui/SettingsScreen.kt`, `app/ui/RewardsScreen.kt` token/wrapper standardına taşındı.
