# Cloudflare Workspace

Bu klasor, Firebase yapisini bozmadan Cloudflare uzerinde kademeli gecis icin ayrildi.

## Klasorler
- `workers/content-api`: `other_apps` ve ses manifest/audio endpointleri icin Worker
- `workers/admin-api`: admin panel endpointleri + cron gorevleri (hourly dispatch, weekly ad report)
- `pages/mobil_web`: mobil web sayfalarinin Cloudflare Pages hedefi
- `docs`: gecis ve operasyon notlari

## Not
- Firebase tarafi aktif kalir.
- Cloudflare tarafi paralel ortam olarak ilerletilir.
