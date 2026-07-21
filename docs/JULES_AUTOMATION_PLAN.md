# Jules Automation Plan

Jules automation is used for scheduled maintenance, quality checks, dependency review support, and same-repository CI fix workflows.

## Scope

- Weekly dependency maintenance
- Weekly quality maintenance
- Monthly AdMob compliance audit
- Monthly performance audit
- Same-repository PR Quality Gate failure fixer

## CI/CD assumptions

- Android SDK, JDK, Firebase, Play, AdMob, signing, and Cloudflare values are supplied through GitHub Actions secrets or repository variables.
- Workflows must not claim publish or console state verification unless the relevant API call succeeds.
- Missing secrets must be reported as configuration blockers.

## Operational rule

Jules must classify blockers precisely and avoid pretending that publish, Play Console, Firebase, AdMob, or Cloudflare state was verified when credentials or APIs were unavailable.
