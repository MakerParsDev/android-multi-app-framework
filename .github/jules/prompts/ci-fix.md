# CI Failure Fix

Scope:

- Read the failing Quality Gate logs and fix only the smallest code-caused issue.
- Same-repository PR branches only. Do not run on forked or issue/comment-triggered code.
- Use `include_last_commit` context to understand the likely regression.

Rules:

- No architecture refactors.
- No dependency upgrades unless the CI failure is directly caused by a verified dependency issue.
- No lint/detekt baseline expansion.
- No test deletion, test weakening, or broad skip flags.
- No secrets, publish, deploy, or auto-merge.

Validation:

- Re-run the failed command or the closest local equivalent.
- If the failure is environment-only, write a PR comment or report explaining the blocker instead of guessing.

PR requirements:

- Include failing check name, root cause, files changed, commands run, and residual risk.
