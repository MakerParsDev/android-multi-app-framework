#!/usr/bin/env python3
"""Contract and drift tests for the autonomous maintenance control plane."""

from __future__ import annotations

import json
from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]


class AutonomousMaintenanceContractTest(unittest.TestCase):
    def test_mergify_configuration_contract(self) -> None:
        mergify_path = ROOT / ".mergify.yml"
        self.assertTrue(
            mergify_path.is_file(), ".mergify.yml must exist at repository root"
        )

        content = mergify_path.read_text(encoding="utf-8")
        self.assertNotIn(
            "autoqueue",
            content,
            "Deprecated autoqueue key must not be used in .mergify.yml",
        )

        data = yaml.safe_load(content)
        self.assertIn(
            "merge_protections", data, ".mergify.yml must define merge_protections"
        )
        self.assertIn(
            "merge_protections_settings",
            data,
            ".mergify.yml must define merge_protections_settings",
        )
        self.assertIn("queue_rules", data, ".mergify.yml must define queue_rules")

        auto_merge_conditions = data["merge_protections_settings"].get(
            "auto_merge_conditions"
        )
        self.assertNotEqual(
            auto_merge_conditions,
            True,
            "auto_merge_conditions must not be unconditionally set to true",
        )
        self.assertIsInstance(
            auto_merge_conditions,
            list,
            "auto_merge_conditions must be a restricted list of condition rules",
        )

        # Verify semver-major is blocked
        mergify_str = yaml.dump(auto_merge_conditions)
        self.assertIn(
            "dependabot-update-type != version-update:semver-major", mergify_str
        )

        # Verify circuit breaker label is respected
        self.assertIn("-label = automerge:disabled", mergify_str)

        # Verify positive authorization: automerge:enabled label required
        self.assertIn("label = automerge:enabled", mergify_str)

        # Verify CodeQL check in queue merge_conditions
        queue_rules = data.get("queue_rules", [])
        self.assertGreaterEqual(len(queue_rules), 1)
        merge_conditions = queue_rules[0].get("merge_conditions", [])
        self.assertIn(
            "check-success = Analyze Java and Kotlin",
            merge_conditions,
            "CodeQL 'Analyze Java and Kotlin' must be in queue_rules merge_conditions",
        )

        # Verify CodeQL check in merge_protections success_conditions
        merge_protections = data.get("merge_protections", [])
        self.assertGreaterEqual(len(merge_protections), 1)
        success_conditions = merge_protections[0].get("success_conditions", [])
        self.assertIn(
            "check-success = Analyze Java and Kotlin",
            success_conditions,
            "CodeQL 'Analyze Java and Kotlin' must be in merge_protections success_conditions",
        )

        # Verify the whole CI control-plane directory is protected from auto-merge
        self.assertIn("scripts/ci/", mergify_str)
        self.assertIn("codecov", mergify_str)
        self.assertIn(".fleet/", mergify_str)

    def test_dependabot_manifest_coverage_contract(self) -> None:
        dependabot_path = ROOT / ".github/dependabot.yml"
        self.assertTrue(dependabot_path.is_file(), ".github/dependabot.yml must exist")

        data = yaml.safe_load(dependabot_path.read_text(encoding="utf-8"))
        self.assertEqual(data.get("version"), 2)

        updates = data.get("updates", [])
        self.assertGreaterEqual(
            len(updates),
            9,
            "Dependabot must cover all monorepo ecosystems and side projects",
        )

        ecosystem_dirs = {(u["package-ecosystem"], u["directory"]) for u in updates}

        expected = {
            ("github-actions", "/"),
            ("gradle", "/"),
            ("pip", "/scripts/ci"),
            ("npm", "/side-projects/admin-notifications"),
            ("npm", "/side-projects/cloudflare/workers/admin-api"),
            ("npm", "/side-projects/cloudflare/workers/content-api"),
            ("npm", "/side-projects/cloudflare/workers/ssv-callback"),
            ("npm", "/side-projects/firebase/functions"),
            ("npm", "/side-projects/firebase/rules-tests"),
        }
        for exp in expected:
            self.assertIn(exp, ecosystem_dirs, f"Missing Dependabot coverage for {exp}")

        for u in updates:
            self.assertNotEqual(
                u.get("rebase-strategy"),
                "disabled",
                f"Dependabot automatic rebasing must remain enabled in {u.get('directory')}",
            )

    def test_no_competing_renovate_configuration_exists(self) -> None:
        forbidden = [
            ROOT / "renovate.json",
            ROOT / "renovate.json5",
            ROOT / ".github/renovate.json",
            ROOT / ".github/renovate.json5",
            ROOT / ".renovaterc",
            ROOT / ".renovaterc.json",
        ]
        for path in forbidden:
            self.assertFalse(
                path.exists(), f"Competing Renovate config must not exist: {path}"
            )

    def test_codecov_action_pin_and_non_blocking_policy(self) -> None:
        pinned_path = ROOT / "config/pinned-github-actions.json"
        manifest = json.loads(pinned_path.read_text(encoding="utf-8"))
        self.assertIn(
            "codecov/codecov-action",
            manifest,
            "codecov/codecov-action must be pinned in manifest",
        )
        self.assertEqual(
            manifest["codecov/codecov-action"]["sha"],
            "0fb7174895f61a3b6b78fc075e0cd60383518dac",
        )

        codecov_yml = ROOT / "codecov.yml"
        self.assertTrue(codecov_yml.is_file(), "codecov.yml must exist")
        cc_data = yaml.safe_load(codecov_yml.read_text(encoding="utf-8"))
        self.assertTrue(
            cc_data.get("coverage", {})
            .get("status", {})
            .get("project", {})
            .get("default", {})
            .get("informational"),
            "Codecov project status must initially be informational to avoid deadlocking non-code PRs",
        )

    def test_protected_paths_include_mergify_and_codecov(self) -> None:
        from fleet_pr_risk import PROTECTED_EXACT_FILES, classify_file

        self.assertIn(".mergify.yml", PROTECTED_EXACT_FILES)
        self.assertIn("codecov.yml", PROTECTED_EXACT_FILES)
        self.assertEqual(classify_file(".mergify.yml"), "PROTECTED")
        self.assertEqual(classify_file("codecov.yml"), "PROTECTED")

    def test_all_workflow_jobs_have_timeouts_and_least_privilege(self) -> None:
        workflows_dir = ROOT / ".github/workflows"
        for wf_path in workflows_dir.glob("*.yml"):
            wf_text = wf_path.read_text(encoding="utf-8")
            wf_data = yaml.safe_load(wf_text)

            # Check top-level permissions
            self.assertIn(
                "permissions",
                wf_data,
                f"Workflow {wf_path.name} must declare explicit top-level permissions",
            )
            top_perms = wf_data["permissions"]
            if isinstance(top_perms, dict):
                self.assertEqual(
                    top_perms.get("contents"),
                    "read",
                    f"Workflow {wf_path.name} must have top-level contents: read",
                )

            # Check timeouts on each job
            jobs = wf_data.get("jobs", {})
            for job_name, job_data in jobs.items():
                self.assertIn(
                    "timeout-minutes",
                    job_data,
                    f"Job '{job_name}' in {wf_path.name} must declare timeout-minutes",
                )
                self.assertLessEqual(
                    job_data["timeout-minutes"],
                    360,
                    f"Job '{job_name}' in {wf_path.name} timeout must not exceed 360 minutes",
                )

    def test_semgrep_is_advisory_not_hard_gate(self) -> None:
        """Verify Semgrep is configured as advisory (continue-on-error: true) and not a hard merge gate."""
        security_wf = ROOT / ".github/workflows/security.yml"
        self.assertTrue(security_wf.is_file())
        content = security_wf.read_text(encoding="utf-8")
        # Semgrep job should have continue-on-error: true
        self.assertIn(
            "continue-on-error: true",
            content,
            "Semgrep must be advisory with continue-on-error: true",
        )
        # Semgrep should NOT be in Mergify merge conditions
        mergify_path = ROOT / ".mergify.yml"
        mergify_content = mergify_path.read_text(encoding="utf-8")
        self.assertNotIn(
            "Semgrep",
            mergify_content,
            "Semgrep must not be a hard merge gate in Mergify",
        )

    def test_security_required_aggregate_exists(self) -> None:
        """Verify there's a Security Required aggregate check for in-workflow security gates."""
        # The Security Required job in ci-pr.yml aggregates in-workflow security checks
        ci_pr_wf = ROOT / ".github/workflows/ci-pr.yml"
        self.assertTrue(ci_pr_wf.is_file())
        content = ci_pr_wf.read_text(encoding="utf-8")
        # Security Required job exists and aggregates security-gate + repository-security
        self.assertIn("name: Security Required", content)
        self.assertIn("security-gate", content)
        self.assertIn("repository-security", content)

    def test_external_security_checks_documented(self) -> None:
        """Verify external security checks (from security.yml) are documented as separate hard checks."""
        # These run in security.yml workflow and produce separate check contexts
        security_wf = ROOT / ".github/workflows/security.yml"
        self.assertTrue(security_wf.is_file())
        content = security_wf.read_text(encoding="utf-8")

        # Hard security checks (must pass for merge)
        self.assertIn("name: Secret Scan", content)
        self.assertIn("name: Workflow Audit", content)
        self.assertIn("name: Dependency Review", content)

        # Advisory check (not hard gate)
        self.assertIn("name: Semgrep SAST", content)
        self.assertIn("continue-on-error: true", content)

        # Mergify should require the hard checks independently
        mergify_path = ROOT / ".mergify.yml"
        mergify_content = mergify_path.read_text(encoding="utf-8")
        # These check contexts should be required in Mergify (exact names from workflow)
        hard_checks = [
            "Secret Scan",
            "Workflow Audit",
            "Dependency Review",
        ]
        for check in hard_checks:
            # Check may be in queue merge_conditions or merge_protections success_conditions
            self.assertIn(
                check,
                mergify_content,
                f"External hard check '{check}' must be required in Mergify",
            )

        # Semgrep should NOT be a hard gate in Mergify
        self.assertNotIn("Semgrep", mergify_content)

    def test_dependabot_sensitive_dependency_logic(self) -> None:
        """Verify sensitive dependencies are excluded from auto-merge regardless of dependency-type."""
        mergify_path = ROOT / ".mergify.yml"
        content = mergify_path.read_text(encoding="utf-8")
        # Sensitive denylist should apply outside dependency-type OR logic
        sensitive_patterns = [
            "com\\.android",
            "org\\.jetbrains\\.kotlin",
            "com\\.google\\.devtools\\.ksp",
            "com\\.google\\.dagger",
            "androidx\\.room",
            "com\\.google\\.gms",
            "com\\.google\\.firebase",
            "com\\.github\\.triplet\\.play",
            "com\\.android\\.billingclient",
        ]
        for pattern in sensitive_patterns:
            self.assertIn(
                pattern,
                content,
                f"Sensitive dependency pattern {pattern} must be in Mergify denylist",
            )

        # Verify the denylist is at the top level of the AND condition (outside dependency-type OR)
        # Parse the YAML to verify structure
        data = yaml.safe_load(content)
        auto_merge_conditions = data["merge_protections_settings"][
            "auto_merge_conditions"
        ]
        # Structure: auto_merge_conditions[0]["or"][0]["and"] contains the dependabot condition
        dependabot_and = auto_merge_conditions[0]["or"][0]["and"]

        # The sensitive denylist should be a direct child of the AND (not nested inside dependency-type OR)
        # This means it should appear as a string in the AND list
        denylist_found_at_top_level = False
        for cond in dependabot_and:
            if (
                isinstance(cond, str)
                and "-dependabot-dependency-name" in cond
                and "kotlin" in cond
            ):
                denylist_found_at_top_level = True
                break

        self.assertTrue(
            denylist_found_at_top_level,
            "Sensitive dependency denylist must be at top level of AND condition (outside dependency-type OR)",
        )

    def test_dependabot_grouping_aligns_with_automerge_boundaries(self) -> None:
        """Keep grouped updates from being poisoned by manual-only dependencies."""
        config = yaml.safe_load(
            (ROOT / ".github/dependabot.yml").read_text(encoding="utf-8")
        )
        updates = config["updates"]

        for update in updates:
            self.assertNotEqual(
                update.get("rebase-strategy"),
                "disabled",
                "Dependabot automatic rebasing must remain enabled so candidates do not go stale",
            )

        gradle = next(
            update
            for update in updates
            if update["package-ecosystem"] == "gradle"
            and update["directory"] == "/"
        )
        gradle_manual_only = {
            "com.android.*",
            "org.jetbrains.kotlin.*",
            "com.google.devtools.ksp",
            "com.google.dagger.*",
            "androidx.room.*",
            "androidx.credentials.*",
            "com.google.gms.*",
            "com.google.firebase.*",
            "com.google.crypto.tink.*",
            "com.github.triplet.play",
            "com.android.billingclient.*",
            "io.netty:*",
            "org.bouncycastle:*",
            "com.google.guava:guava",
            "org.jdom:jdom2",
            "org.bitbucket.b_c:jose4j",
            "org.apache.commons:commons-lang3",
            "ch.qos.logback:*",
            "org.apache.httpcomponents:httpclient",
            "com.squareup.wire:*",
            "gradle-wrapper",
        }
        gradle_dev = gradle["groups"]["gradle-dev-patch-minor"]
        gradle_prod = gradle["groups"]["gradle-prod-patch"]
        self.assertEqual(gradle_dev["dependency-type"], "development")
        self.assertEqual(set(gradle_dev["update-types"]), {"patch", "minor"})
        self.assertEqual(gradle_prod["dependency-type"], "production")
        self.assertEqual(gradle_prod["update-types"], ["patch"])
        for group_name, group in (
            ("gradle-dev-patch-minor", gradle_dev),
            ("gradle-prod-patch", gradle_prod),
        ):
            excluded = set(group["exclude-patterns"])
            self.assertTrue(
                gradle_manual_only.issubset(excluded),
                f"{group_name} must isolate every manual/protected Gradle dependency",
            )

        npm_manual_only = {
            "wrangler",
            "@cloudflare/*",
            "firebase-admin",
            "firebase-functions",
            "firebase-functions-test",
            "firebase-tools",
            "jose",
            "jsonwebtoken",
        }
        npm_updates = [
            update for update in updates if update["package-ecosystem"] == "npm"
        ]
        self.assertTrue(npm_updates)
        for update in npm_updates:
            groups = update.get("groups", {})
            dev = groups["npm-dev-patch-minor"]
            prod = groups["npm-prod-patch"]
            self.assertEqual(dev["dependency-type"], "development")
            self.assertEqual(set(dev["update-types"]), {"patch", "minor"})
            self.assertEqual(prod["dependency-type"], "production")
            self.assertEqual(prod["update-types"], ["patch"])
            for group_name, group in (
                ("npm-dev-patch-minor", dev),
                ("npm-prod-patch", prod),
            ):
                excluded = set(group.get("exclude-patterns", []))
                self.assertTrue(
                    npm_manual_only.issubset(excluded),
                    f"{update['directory']} {group_name} must isolate deployment/auth-sensitive npm packages",
                )

    def test_circuit_breaker_variables_documented(self) -> None:
        """Verify circuit breaker variables are documented with fail-closed defaults."""
        doc_path = ROOT / "docs/AUTONOMOUS_MAINTENANCE.md"
        self.assertTrue(doc_path.is_file())
        content = doc_path.read_text(encoding="utf-8")
        required_vars = [
            "AUTONOMOUS_MAINTENANCE_ENABLED",
            "AUTONOMOUS_MERGE_ENABLED",
            "JULES_FLEET_ENABLED",
            "JULES_FLEET_AUTO_MERGE_ENABLED",
        ]
        for var in required_vars:
            self.assertIn(
                var, content, f"Circuit breaker variable {var} must be documented"
            )

    def test_health_controller_has_bootstrap_pending_state(self) -> None:
        """Verify health controller implements BOOTSTRAP_PENDING state."""
        health_path = ROOT / "scripts/ci/maintenance_health_controller.py"
        self.assertTrue(health_path.is_file())
        content = health_path.read_text(encoding="utf-8")
        self.assertIn(
            "BOOTSTRAP_PENDING",
            content,
            "Health controller must implement BOOTSTRAP_PENDING state",
        )

    def test_github_ruleset_bootstrap_documented(self) -> None:
        """Verify GitHub ruleset bootstrap is documented."""
        doc_path = ROOT / "docs/AUTONOMOUS_MAINTENANCE.md"
        content = doc_path.read_text(encoding="utf-8")
        self.assertIn(
            "BOOTSTRAP_PENDING",
            content,
            "Documentation must mention BOOTSTRAP_PENDING for ruleset",
        )

    def test_maintenance_dashboard_failure_propagation(self) -> None:
        """Verify maintenance dashboard script propagates failures."""
        health_path = ROOT / "scripts/ci/maintenance_health_controller.py"
        content = health_path.read_text(encoding="utf-8")
        # Should return non-zero on GitHub API failures
        self.assertIn(
            "return 1",
            content,
            "Maintenance controller must return non-zero on failures",
        )

    def test_codecov_auth_model_explicit(self) -> None:
        """Verify Codecov authentication model is explicit."""
        codecov_yml = ROOT / "codecov.yml"
        self.assertTrue(codecov_yml.is_file())
        content = codecov_yml.read_text(encoding="utf-8")
        # Should have informational mode
        self.assertIn("informational: true", content)
        ci_pr_wf = ROOT / ".github/workflows/ci-pr.yml"
        ci_content = ci_pr_wf.read_text(encoding="utf-8")
        # Should use CODECOV_TOKEN or OIDC
        self.assertTrue(
            "${{ secrets.CODECOV_TOKEN }}" in ci_content
            or "oidc" in ci_content.lower(),
            "Codecov must use explicit authentication (CODECOV_TOKEN or OIDC)",
        )

    def test_automerge_control_workflow_exists(self) -> None:
        """Verify automerge-control workflow exists for label management."""
        wf_path = ROOT / ".github/workflows/automerge-control.yml"
        self.assertTrue(wf_path.is_file(), "automerge-control.yml workflow must exist")
        content = wf_path.read_text(encoding="utf-8")
        self.assertIn("AUTONOMOUS_MERGE_ENABLED", content)
        self.assertIn("automerge:enabled", content)
        self.assertIn("sync_automerge_label.py", content)

    def test_structured_ci_security_and_codecov_contract(self) -> None:
        """Parse workflows and verify exact hard/advisory and OIDC contracts."""
        ci_pr = yaml.safe_load(
            (ROOT / ".github/workflows/ci-pr.yml").read_text(encoding="utf-8")
        )
        jobs = ci_pr["jobs"]
        security_required = jobs["security-required"]
        self.assertEqual(
            set(security_required["needs"]), {"security-gate", "repository-security"}
        )
        kover = jobs["kover-coverage"]
        self.assertEqual(kover["permissions"]["contents"], "read")
        self.assertEqual(kover["permissions"]["id-token"], "write")
        codecov_step = next(
            step for step in kover["steps"] if step.get("name") == "Upload coverage to Codecov"
        )
        self.assertTrue(codecov_step["with"]["use_oidc"])
        self.assertNotIn("token", codecov_step["with"])

        event_config = ci_pr.get("on", ci_pr.get(True))
        self.assertIsInstance(event_config, dict)
        pull_request = event_config["pull_request"]
        self.assertNotIn(
            "paths", pull_request, "CI Required producer must run for every PR to main"
        )

        security = yaml.safe_load(
            (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        )
        sec_jobs = security["jobs"]
        semgrep_step = next(
            step
            for step in sec_jobs["semgrep"]["steps"]
            if step.get("name") == "Run Semgrep"
        )
        self.assertTrue(semgrep_step["continue-on-error"])
        workflow_steps = sec_jobs["workflow-audit"]["steps"]
        self.assertTrue(
            any(
                step.get("name") == "Run actionlint" and step.get("continue-on-error")
                for step in workflow_steps
            )
        )
        policy_step = next(
            step
            for step in workflow_steps
            if step.get("name") == "Enforce repository workflow policy"
        )
        self.assertEqual(policy_step["run"], "python3 scripts/ci/workflow_policy.py --repo .")

        codeql = yaml.safe_load(
            (ROOT / ".github/workflows/codeql.yml").read_text(encoding="utf-8")
        )
        codeql_job = codeql["jobs"]["analyze-java-kotlin"]
        self.assertNotIn(
            "if",
            codeql_job,
            "Required CodeQL check must run for Dependabot pull requests too",
        )

    def test_control_plane_paths_and_sensitive_dependencies_are_manual(self) -> None:
        data = yaml.safe_load((ROOT / ".mergify.yml").read_text(encoding="utf-8"))
        auto = data["merge_protections_settings"]["auto_merge_conditions"]
        dependabot_and = auto[0]["or"][0]["and"]
        conditions = [item for item in dependabot_and if isinstance(item, str)]
        protected = next(item for item in conditions if item.startswith("-files ~="))
        self.assertIn("scripts/ci/", protected)
        self.assertIn("\\.github/", protected)
        self.assertIn("\\.mergify\\.yml", protected)
        self.assertIn("build\\.gradle\\.kts", protected)
        self.assertIn("settings\\.gradle\\.kts", protected)
        self.assertIn("gradle\\.properties", protected)
        self.assertIn("gradle/wrapper/", protected)

        denylist = next(
            item for item in conditions if item.startswith("-dependabot-dependency-name")
        )
        for pattern in (
            "org\\.bouncycastle",
            "org\\.bitbucket\\.b_c",
            "androidx\\.credentials",
            "com\\.android\\.billingclient",
            "org\\.jetbrains\\.kotlin",
            "firebase-admin$",
            "firebase-functions$",
            "firebase-tools$",
            "jose$",
            "jsonwebtoken$",
            "wrangler$",
            "@cloudflare/",
        ):
            self.assertIn(pattern, denylist)

        dependency_type = next(
            item["or"] for item in dependabot_and if isinstance(item, dict) and "or" in item
        )
        dev = next(
            item["and"]
            for item in dependency_type
            if isinstance(item, dict)
            and "dependabot-dependency-type ~= :development$" in item.get("and", [])
        )
        self.assertIn("-dependabot-dependency-type ~= :production$", dev)

        prod = next(
            item["and"]
            for item in dependency_type
            if isinstance(item, dict)
            and "dependabot-dependency-type ~= :production$" in item.get("and", [])
        )
        self.assertIn("-dependabot-dependency-type ~= :development$", prod)
        self.assertIn("dependabot-update-type = version-update:semver-patch", prod)
        self.assertIn("dependabot-update-type != version-update:semver-minor", prod)

        fleet_and = auto[0]["or"][1]["and"]
        self.assertIn("head ~= -[0-9]{10,}$", fleet_and)
        self.assertIn("-from-fork", fleet_and)
        self.assertIn("label = fleet-merge-ready", fleet_and)
        self.assertIn("label = risk:low", fleet_and)

    def test_ruleset_contract_is_complete(self) -> None:
        payload = json.loads(
            (ROOT / "scripts/ci/github-ruleset-payload.json").read_text(encoding="utf-8")
        )
        self.assertEqual(payload["conditions"]["ref_name"]["include"], ["~DEFAULT_BRANCH"])
        pull = next(rule for rule in payload["rules"] if rule["type"] == "pull_request")
        params = pull["parameters"]
        self.assertEqual(params["allowed_merge_methods"], ["squash"])
        for key in (
            "dismiss_stale_reviews_on_push",
            "require_code_owner_review",
            "require_last_push_approval",
            "required_approving_review_count",
            "required_review_thread_resolution",
        ):
            self.assertIn(key, params)
        status = next(
            rule for rule in payload["rules"] if rule["type"] == "required_status_checks"
        )["parameters"]
        self.assertFalse(
            status["strict_required_status_checks_policy"],
            "Mergify merge queue is incompatible with GitHub strict required-status policy",
        )
        self.assertFalse(status["do_not_enforce_on_create"])


if __name__ == "__main__":
    unittest.main()
