# app-ads.txt Checklist

Last reviewed: 2026-04-29

## Code-Verified State

- Checked-in file: `side-projects/firebase/mobil_web/public/app-ads.txt`
- Required AdMob seller line:

```text
google.com, pub-3312485084079132, DIRECT, f08c47fec0942fa0
```

- `ca-app-pub-` must not appear in `app-ads.txt`. App IDs and ad unit IDs stay in Android resources/config only.
- Seller rows must have 3 or 4 comma-separated fields.
- Blank lines and `#` comments are allowed.

Local validator:

```bash
python scripts/ci/validate_app_ads_txt.py --mode strict
python scripts/ci/validate_app_ads_txt_test.py
```

CI/release coverage:

- PR quality gate runs `validate_app_ads_txt.py --mode warn`.
- `release.yml`, `manual-ops.yml`, and `release-parallel.yml` run strict validation on Play/internal publish paths.
- Root Gradle `qualityCheck` depends on `validateAppAdsTxt`.

## Manual Release Checks

These cannot be fully verified from the repository:

1. Deploy the static Firebase Hosting site that serves `side-projects/firebase/mobil_web/public/app-ads.txt`.
2. Open the public root-domain URL directly, for example `https://<developer-domain>/app-ads.txt`.
3. Confirm the public file contains the exact AdMob `pub-3312485084079132` line above.
4. Confirm the Google Play developer website for every monetized flavor points at the same root domain.
5. In AdMob, check app-ads.txt crawl status and wait until it is `found` / `verified`.
6. Re-check after DNS, hosting, domain, seller, or Play listing changes.

## Source References

- Google AdMob app-ads.txt help: https://support.google.com/admob/answer/9363762
- IAB Authorized Sellers for Apps specification: https://iabtechlab.com/app-ads-txt/
