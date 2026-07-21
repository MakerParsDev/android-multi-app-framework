# Admin Backend Smoke Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the production admin/purchase backend traceable to an exact Worker version and prevent Android release publication when the canonical backend contract is unavailable.

**Architecture:** Extract the Worker health handler into a focused module that reports service, environment, source commit, and Cloudflare version metadata. Add a secret-free Python smoke client used by Azure release automation to verify health, unauthenticated purchase rejection, and App Check rejection before any release build or publish task. Keep production deployment separate and inject the merged commit SHA at deploy time.

**Tech Stack:** TypeScript, Cloudflare Workers, Node test runner, Python 3 standard library, Bash, Azure Pipelines, Wrangler.

## Global Constraints

- Do not log response bodies, credentials, tokens, service-account JSON, or secret values.
- Do not weaken Firebase Auth or App Check enforcement.
- Do not deploy Firestore rules as part of this work.
- Production deployment must retain the current Worker rollback reference until post-deploy smoke tests pass.
- Merge only after local verification and all bot/agent PR comments and checks have been reviewed.

---

### Task 1: Versioned Worker health contract

**Files:**
- Create: `side-projects/cloudflare/workers/admin-api/src/health.ts`
- Create: `side-projects/cloudflare/workers/admin-api/test/health.test.mjs`
- Modify: `side-projects/cloudflare/workers/admin-api/src/index.ts`
- Modify: `side-projects/cloudflare/workers/admin-api/tsconfig.test.json`
- Modify: `side-projects/cloudflare/workers/admin-api/wrangler.toml`

**Interfaces:**
- Consumes: Worker `Request` and optional environment/version metadata values.
- Produces: `handleHealthCheck(request, metadata)` returning a JSON `Response` with `ok`, `service`, `environment`, `gitSha`, `workerVersionId`, `workerVersionTag`, `workerVersionTimestamp`, `region`, and `timestamp`.

- [ ] **Step 1: Write failing health tests**

Test GET/POST success, unsupported method rejection, and metadata normalization. Require a valid 7–40 character hexadecimal source SHA and expose nullable Cloudflare version fields.

- [ ] **Step 2: Run tests and verify RED**

Run: `npm test`

Expected: failure because `.test-dist/health.js` does not exist.

- [ ] **Step 3: Implement the health module and Worker integration**

Extract `handleHealthCheck`, add `SERVICE_ENVIRONMENT`, `SERVICE_GIT_SHA`, and `CF_VERSION_METADATA` to `Env`, route `/health` and `/healthCheck` through the extracted handler, and configure Wrangler version metadata.

- [ ] **Step 4: Run Worker verification and verify GREEN**

Run: `npm run verify`

Expected: TypeScript check succeeds and all Worker tests pass.

- [ ] **Step 5: Commit**

```bash
git add side-projects/cloudflare/workers/admin-api
git commit -m "feat(worker): expose traceable health metadata"
```

### Task 2: Production-safe backend smoke client

**Files:**
- Create: `scripts/ci/admin_backend_smoke.py`
- Create: `scripts/ci/admin_backend_smoke_test.py`

**Interfaces:**
- Consumes: `--purchase-url`, `--push-url`, and optional `--expected-git-sha`.
- Produces: exit code `0` only when `/health` is traceable and protected endpoints reject unauthenticated/invalid requests with the expected statuses.

- [ ] **Step 1: Write failing smoke-client tests**

Use an in-process HTTP server. Cover healthy success, missing metadata, source-SHA mismatch, unauthenticated purchase status mismatch, and invalid App Check status mismatch.

- [ ] **Step 2: Run tests and verify RED**

Run: `python3 scripts/ci/admin_backend_smoke_test.py`

Expected: failure because `admin_backend_smoke.py` does not exist.

- [ ] **Step 3: Implement the minimal smoke client**

Use only Python standard-library networking. Derive `/health` from the canonical backend origin, require HTTPS unless `--allow-http-localhost` is explicitly used by tests, enforce bounded timeouts, and print only endpoint names/statuses and metadata identifiers.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `python3 scripts/ci/admin_backend_smoke_test.py`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/ci/admin_backend_smoke.py scripts/ci/admin_backend_smoke_test.py
git commit -m "test(release): add admin backend smoke contract"
```

### Task 3: Block release automation on unhealthy backend

**Files:**
- Modify: `scripts/azure/release.sh`
- Modify: `side-projects/cloudflare/workers/admin-api/README.md`
- Modify: `side-projects/cloudflare/docs/migration-plan.md`

**Interfaces:**
- Consumes: existing `PUSH_REGISTRATION_URL`, `PURCHASE_VERIFICATION_URL`, and optional `EXPECTED_ADMIN_BACKEND_GIT_SHA`.
- Produces: release execution that stops before signing/build/publish when the backend contract is unhealthy, plus deploy/rollback documentation.

- [ ] **Step 1: Add a failing repository contract test**

Extend `admin_backend_smoke_test.py` with assertions that `scripts/azure/release.sh` invokes the smoke client before Gradle build/publish execution.

- [ ] **Step 2: Run tests and verify RED**

Run: `python3 scripts/ci/admin_backend_smoke_test.py`

Expected: repository-contract assertion fails.

- [ ] **Step 3: Wire the smoke client into release automation and document operations**

Run the smoke client for Release operations after required URL checks and before writing signing credentials or running Gradle. Document `SERVICE_GIT_SHA` injection, Worker version metadata, smoke commands, and rollback to the prior deployment/version.

- [ ] **Step 4: Run verification and verify GREEN**

Run:

```bash
python3 scripts/ci/admin_backend_smoke_test.py
bash -n scripts/azure/release.sh
shellcheck scripts/azure/release.sh
npm run verify --prefix side-projects/cloudflare/workers/admin-api
./gradlew --no-daemon --max-workers=2 staticQualityCheck
```

Expected: all commands exit `0`.

- [ ] **Step 5: Commit**

```bash
git add scripts/azure/release.sh side-projects/cloudflare/workers/admin-api/README.md side-projects/cloudflare/docs/migration-plan.md scripts/ci/admin_backend_smoke_test.py
git commit -m "fix(release): block publish on backend smoke failure"
```

### Task 4: PR review, production deploy, and evidence

**Files:**
- No additional source files unless review findings require changes.

**Interfaces:**
- Consumes: merged commit SHA, current Cloudflare deployment ID, production Doppler values.
- Produces: new Worker deployment with traceable health metadata and recorded rollback/smoke evidence.

- [ ] **Step 1: Run final fresh verification**

Run Worker verification, Python tests, shell validation, static quality, `git diff --check`, and Wrangler dry-run.

- [ ] **Step 2: Push and open PR**

Include test evidence and `Closes #103` only if every acceptance criterion that can be automated is complete; otherwise update #103 without closing it.

- [ ] **Step 3: Review all bot/agent output**

Inspect status checks, issue comments, review comments, submitted reviews, and suggestions. Resolve all critical/important findings before merge.

- [ ] **Step 4: Merge and deploy**

Record the previous Worker deployment/version, deploy the merged Worker with `SERVICE_GIT_SHA=<merge commit>`, and keep Firestore rules unchanged.

- [ ] **Step 5: Run production smoke and record rollback evidence**

Run the new smoke client against production with the expected merged SHA. Record deployment ID, version ID, URL, timestamp, test statuses, and rollback reference in #103 and #112.
