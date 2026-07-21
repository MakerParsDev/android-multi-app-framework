# VPS Bootstrap (Parsfilo)

Bu klasor, Android disi parcalari tek VPS uzerinde calistirmak icin ilk hazirlik setidir.

## Bu paket neyi tasir

- `admin.parsfilo.com` -> `side-projects/admin-notifications` (React/Vite build)
- `parsfilo.com` + `www.parsfilo.com` -> `side-projects/firebase/mobil_web/public` (statik site)
- `api.parsfilo.com` -> simdilik Firebase Functions bridge (`FIREBASE_FUNCTIONS_BASE_URL`)

Not: FCM/AdMob/Play/Crashlytics yine Google tarafinda kalir.

## Gereksinimler

1. Linux VPS (onerilen: Ubuntu 24.04 LTS)
2. Docker + Docker Compose plugin
3. DNS:
   - `A` kaydi: `parsfilo.com` -> VPS IP
   - `A` kaydi: `admin.parsfilo.com` -> VPS IP
   - `A` kaydi: `www.parsfilo.com` -> VPS IP
   - `A` kaydi: `api.parsfilo.com` -> VPS IP

## Kurulum

1. VPS'e repo cek:

```bash
git clone <repo-url>
cd android-multi-app-framework/side-projects/vps
```

2. Env dosyasini olustur:

```bash
cp .env.example .env
```

3. `.env` icindeki bos degerleri doldur:
   - `VITE_FIREBASE_API_KEY`
   - `VITE_FIREBASE_APP_ID`
   - `VITE_FIREBASE_MESSAGING_SENDER_ID`
   - gerekiyorsa `VITE_FUNCTIONS_BASE_URL`

4. Servisleri kaldir:

```bash
docker compose up -d --build
```

5. Durum kontrol:

```bash
docker compose ps
docker compose logs -f caddy
```

## Yeniden deploy

```bash
git pull
docker compose up -d --build
```

## Rollback

Bir onceki commit'e donup tekrar build:

```bash
git checkout <previous-commit>
docker compose up -d --build
```

## Operasyon notlari

- Ilk sertifika aliminda DNS propagasyonu tamamlanmadiysa SSL gec gelebilir.
- Cloudflare proxy kullanacaksan ilk kurulumda gecici olarak `DNS only` ile acmak daha stabil olur.
- Admin panelde sign-in hata alirsan Firebase Auth Authorized domains listesinde `admin.parsfilo.com` ve `parsfilo.com` oldugunu dogrula.

