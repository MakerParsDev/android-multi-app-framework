# Autonomous Repository Maintenance Architecture

## 1. Architecture & Philosophy

This repository implements a **safe, fail-closed, highly autonomous maintenance system** designed to keep dependencies, toolchains, security postures, and CI workflows up to date with minimal manual engineering intervention.

```mermaid
flowchart TD
    subgraph Generators["PR Generators"]
        D[Dependabot<br/>Grouped minor/patch]
        J[Jules Fleet<br/>Remediations & issues]
        H[Human Contributors]
    end

    subgraph Validation["Deterministic Validation Gates"]
        CI[CI Required<br/>Unit tests, Lint, Detekt, Ktlint, Kover]
        SEC[Security Gate<br/>Gitleaks, Secret scan, Semgrep, Review]
        CQL[CodeQL<br/>Java & Kotlin Analysis]
        SONAR[SonarCloud<br/>Quality Gate]
        COV[Codecov<br/>Informational Patch Visibility]
        RC[PR Risk Classifier<br/>LOW_RISK vs PROTECTED]
    end

    subgraph ControlPlane["Mergify Control Plane"]
        MP[Merge Protections<br/>Check all gates pass]
        AMC{Auto-Merge Conditions<br/>Class A: Dev patch/minor or Low-Risk Fleet<br/>Class B: Strict manual gate<br/>Class C: Never auto-merge}
        MQ[Mergify Merge Queue<br/>Latest main verification]
    end

    subgraph HardBoundary["GitHub Hard Boundary"]
        RS[GitHub Ruleset<br/>Branch protections on main]
        MAIN[main branch]
    end

    D --> Validation
    J --> Validation
    H --> Validation

    Validation --> MP
    MP --> AMC
    AMC -- Qualified Low-Risk --> MQ
    AMC -- Major / High-Risk / Human --> HumanReview[Human Approval Required]
    HumanReview --> MQ
    MQ --> RS
    RS --> MAIN
```

---

## 2. Authority Boundaries & Single Merge Engine

To eliminate race conditions and split-brain states:
- **Single Merge Authority:** **Mergify** is the sole automated merge authority.
- **Jules Fleet Merge:** Kept strictly in diagnostic dry-run mode (`JULES_FLEET_AUTO_MERGE_ENABLED=false`).
- **Native GitHub Merge Queue:** Disabled to prevent conflicting dual-queue synchronization.

---

## 3. Dependabot Configuration

- **Authoritative Dependency Updater:** Dependabot runs weekly on Sundays (`schedule: interval: weekly`).
- **All Ecosystems Covered:**
  - `github-actions`: `/`
  - `gradle`: `/`
  - `pip`: `/scripts/ci`
  - `npm`:
    - `/side-projects/admin-notifications`
    - `/side-projects/cloudflare/workers/admin-api`
    - `/side-projects/cloudflare/workers/content-api`
    - `/side-projects/cloudflare/workers/ssv-callback`
    - `/side-projects/firebase/functions`
    - `/side-projects/firebase/rules-tests`
- **Rebase Strategy:** `rebase-strategy: disabled` is explicitly set to prevent excessive CI churn while PRs await Mergify queue processing.
- **Grouping:** Patch and minor updates are grouped per ecosystem. Semver-major updates are raised as standalone PRs.
- **Sensitive Dependencies Isolated:** Gradle, Kotlin, AGP, KSP, Hilt, Room, Firebase plugins, Play Billing, and Play Publisher are excluded from automated grouping.

---

## 4. Jules Fleet Automation

- **Goal-Driven Self-Healing:** Jules analyzes CI health, dependency posture, and security alerts, raising focused remediation issues and PRs.
- **Safety Boundaries:**
  - Automated PRs run the deterministic `classify_pr.py` classifier.
  - Changes are labeled `risk:low` / `fleet-merge-ready` only if they touch unprivileged app code or docs.
  - Any PR touching protected infrastructure receives `fleet-review-required` and cannot auto-merge.

---

## 5. Mergify Engine

Configured in `.mergify.yml` using the latest official schema:
- **`merge_protections`**: Enforces that hard gates pass before any PR is merge-eligible:
  - `CI Required`
  - `Repository Security`
  - `Security Gate`
  - `SonarCloud Code Analysis`
- **`auto_merge_conditions`**: Restricted solely to:
  - Dependabot dev patch and minor updates (excluding sensitive coordinates).
  - Jules Fleet PRs verified as `risk:low` with label `fleet-merge-ready`.
  - Circuit breaker label `-label = automerge:disabled`.
- **`queue_rules`**:
  - `name: default`, `merge_method: squash`, `batch_size: 1`.

---

## 6. GitHub Rulesets (Hard Security Boundary)

**Current State: BOOTSTRAP_PENDING** — The GitHub ruleset has not yet been applied. See Section 14 for bootstrap procedure.

Once activated, repository rulesets on `main` will enforce:
- Required PR before merging.
- No direct push.
- No force push.
- No branch deletion.
- Required status checks: `CI Required`, `Security Required`, `SonarCloud Code Analysis`, `Mergify Merge Protections`, `Analyze Java and Kotlin`.

**Do NOT enable GitHub native merge queue.** Mergify queue remains the only queue.

---

## 7. SonarQube Cloud Integration

- **Model:** GitHub App Automatic Analysis / Clean-as-You-Code.
- **Status:** Hard Quality Gate required for merge eligibility.
- **Protection:** External advisory bots with rate limits (e.g. CodeRabbit) are never treated as blocking hard gates.

---

## 8. Codecov Integration

- **Engine:** `codecov/codecov-action` pinned to full commit SHA (`0fb7174895f61a3b6b78fc075e0cd60383518dac`, v5.5.5).
- **Authentication:** OIDC tokenless upload (`use_oidc: true` with `id-token: write` permission). No `CODECOV_TOKEN` secret required for public repositories.
- **Policy:** Informational (`informational: true` in `codecov.yml`, `fail_ci_if_error: false` in workflows).
- **Local Source of Truth:** Kover remains the authoritative coverage validator.
- **Bootstrap:** If OIDC fails (e.g., private repo), create `CODECOV_TOKEN` secret manually in GitHub repo settings.

---

## 9. CodeQL & Kotlin Compatibility Ceiling

- **Ceiling Policy:** Recorded in `config/codeql-compatibility-policy.json`.
- **Enforcement:** `scripts/ci/codeql_kotlin_compatibility_test.py` validates that `gradle/libs.versions.toml` Kotlin compiler stays below the CodeQL ceiling (currently `< 2.4.10` for CodeQL bundle 2.26.1).

---

## 10. Autonomy Risk Tiers

| Tier | PR Types | Policy |
|---|---|---|
| **Class A (Autonomous)** | Dependabot dev patch/minor, Action patch/minor, Jules Fleet `risk:low` | Auto-queued and merged by Mergify after all gates pass |
| **Class B (Enhanced Guarded)** | Side-project prod patch/minor, non-breaking runtime library patches | Allowed only if full CI and integration tests pass |
| **Class C (Manual Approval Required)** | Any semver-major bump, Gradle/Kotlin/AGP toolchain, Auth, Billing, DB migrations, `.github/**`, `.mergify.yml` | Never auto-merged; human approval required |

---

## 11. Circuit Breakers & Kill Switches

1. **Global Maintenance Kill Switch:**
   - Variable `AUTONOMOUS_MAINTENANCE_ENABLED=false` disables automated Fleet issue/PR creation.
2. **Global Auto-Merge Kill Switch:**
   - Variable `AUTONOMOUS_MERGE_ENABLED=false` disables automated Mergify qualification.
3. **Per-PR Circuit Breaker Label:**
   - Adding label `automerge:disabled`, `hold`, or `do-not-merge` immediately disqualifies the PR from auto-merge.

---

## 12. Maintenance Health Controller & Dashboard

- **Workflow:** `.github/workflows/maintenance-health.yml` runs daily at 06:00 UTC.
- **Script:** `scripts/ci/maintenance_health_controller.py`.
- **Dashboard Issue:** Automatically creates or updates the single consolidated issue: `"Autonomous Maintenance Dashboard"`. No issue spam.

---

## 13. How to Safely Disable Automation

To completely suspend all autonomous actions:
```bash
gh variable set AUTONOMOUS_MAINTENANCE_ENABLED --body "false"
gh variable set AUTONOMOUS_MERGE_ENABLED --body "false"
gh variable set JULES_FLEET_ENABLED --body "false"
gh variable set JULES_FLEET_AUTO_MERGE_ENABLED --body "false"
```
To re-enable:
```bash
gh variable set AUTONOMOUS_MAINTENANCE_ENABLED --body "true"
gh variable set AUTONOMOUS_MERGE_ENABLED --body "true"
```

---
## 14. GitHub Ruleset Bootstrap Procedure

The GitHub ruleset is **not yet active** (BOOTSTRAP_PENDING). After this PR merges, an administrator with repo admin permissions must apply the ruleset.

### 14.1 Post-Merge Bootstrap Command

```bash
# Apply the ruleset (requires admin:repo_hook permission)
gh api --method POST repos/MakerParsDev/android-multi-app-framework/rulesets \
  --input scripts/ci/github-ruleset-payload.json
```

### 14.2 Ruleset Payload (scripts/ci/github-ruleset-payload.json)

```json
{
  "name": "main-branch-protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "exclude": [],
      "include": ["~main"]
    }
  },
  "rules": [
    {"type": "pull_request", "parameters": {"required_approving_review_count": 0}},
    {"type": "required_status_checks", "parameters": {
      "required_checks": [
        {"context": "CI Required"},
        {"context": "Security Required"},
        {"context": "SonarCloud Code Analysis"},
        {"context": "Mergify Merge Protections"},
        {"context": "Analyze Java and Kotlin"}
      ],
      "strict_required_status_checks_policy": true
    }},
    {"type": "non_fast_forward"},
    {"type": "deletion"}
  ],
  "bypass_actors": []
}
```

### 14.3 Required Variables (Set After Ruleset Applied)

After successful ruleset activation, set these variables in order:

```bash
# 1. Enable maintenance automation (issue/PR creation)
gh variable set AUTONOMOUS_MAINTENANCE_ENABLED --body "true"

# 2. Enable auto-merge qualification (requires ruleset active)
gh variable set AUTONOMOUS_MERGE_ENABLED --body "true"

# 3. Enable Jules Fleet analysis/dispatch (dry-run only)
gh variable set JULES_FLEET_ENABLED --body "true"

# 4. JULES_FLEET_AUTO_MERGE_ENABLED MUST REMAIN false
#    Jules Fleet must NEVER independently auto-merge
#    Mergify is the SOLE merge authority
```

### 14.4 Variables That Must Remain Disabled

| Variable | Value | Reason |
|----------|-------|--------|
| `JULES_FLEET_AUTO_MERGE_ENABLED` | `false` | Jules Fleet must NEVER auto-merge; Mergify is sole authority |
| GitHub native merge queue | Disabled | Prevents dual-queue race conditions |

---
## 15. Auto-Merge Control Plane

The `automerge-control.yml` workflow runs hourly on `main` and manages the `automerge:enabled` label on all open PRs based on the `AUTONOMOUS_MERGE_ENABLED` repository variable.

- **`AUTONOMOUS_MERGE_ENABLED=true`** → Adds `automerge:enabled` label to all open PRs
- **`AUTONOMOUS_MERGE_ENABLED=false`** (or missing) → Removes `automerge:enabled` label from all open PRs
- **Fail-closed**: Variable missing or not explicitly `true` = no auto-merge

This provides a **positive authorization** model: PRs must have `automerge:enabled` label to be eligible for Mergify auto-merge, rather than relying on a negative-only block.
