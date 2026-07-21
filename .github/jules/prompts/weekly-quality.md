# Weekly Quality and Security

Scope:

- Review hardcoded secrets, local config leakage, AdMob ID misuse, app-ads.txt format, lint/detekt/test failures, lifecycle leaks, `GlobalScope`, Compose side effects, database main-thread work, and R8/ProGuard regressions.
- Keep changes focused and reversible.
- Do not add or expand lint/detekt baselines.
- Do not remove, skip, or weaken tests to get green CI.
- Do not write `.env`, `local.properties`, keystores, service-account JSON, tokens, or personal data.

Validation:

- Run `python scripts/ci/validate_app_ads_txt.py --mode strict`.
- Run `python scripts/ci/validate_ci_apps_catalog.py --mode warn --target-flavors all`.
- Run `python scripts/ci/validate_admob_inventory.py --mode warn --target-flavors all`.
- Run relevant Gradle checks for changed modules.

PR requirements:

- Explain each real defect fixed and each issue intentionally left as manual.
- Include command results and residual risks.
- Do not publish, deploy, or auto-merge.
