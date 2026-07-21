# Monthly Performance

Scope:

- Inspect startup, Mobile Ads initialization, Firebase/App Check initialization, Room/database work, Compose recomposition risks, image loading, and obvious N+1 data access.
- Prefer measurement, traces, logs, or small reproducible evidence before changing behavior.
- If no benchmark exists, add lightweight measurement or a report before making broad changes.

Rules:

- Do not block app startup with new main-thread work.
- Do not move preflight/safety work into user-visible blocking paths.
- Do not increase ad frequency or preload volume unless policy and UX gates already allow it.
- Do not rewrite architecture without a separate plan.

Validation:

- Run relevant unit tests and compile checks.
- Add or update tests for changed policy or concurrency logic.
- Report measurements or explain why a change is risk-reduction rather than a claimed performance win.

PR requirements:

- Include commands, results, risk, rollback notes, and any missing local tooling.
- Do not publish, deploy, or auto-merge.
