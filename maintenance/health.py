"""Autonomy observability metrics and maintenance health rendering."""

from typing import Any, Dict
from maintenance.policy import AutonomyPolicy

class HealthReporter:
    def __init__(self, policy: AutonomyPolicy):
        self.policy = policy

    def generate_report(self) -> Dict[str, Any]:
        canaries = self.policy.active_canaries_and_exceptions
        jules_cfg = self.policy.jules_config
        recovery_cfg = self.policy.recovery_config

        metrics = {
            "policy_version": self.policy.version,
            "active_exceptions_count": len(canaries),
            "protected_gradle_patterns_count": len(self.policy.protected_gradle_patterns),
            "protected_npm_patterns_count": len(self.policy.protected_npm_patterns),
            "jules_dispatch_safety": {
                "max_active_sessions_per_key": jules_cfg.get("max_active_sessions_per_key"),
                "max_active_sessions_per_repo": jules_cfg.get("max_active_sessions_per_repo"),
                "global_kill_switch": jules_cfg.get("global_kill_switch")
            },
            "post_merge_recovery_mode": recovery_cfg.get("mode")
        }

        markdown_report = "# Autonomous Maintenance Observability Report\n\n"
        markdown_report += f"**Policy Version**: {self.policy.version}\n"
        markdown_report += f"**Active Blockers / Exceptions**: {len(canaries)}\n"
        markdown_report += f"**Protected Gradle Patterns**: {len(self.policy.protected_gradle_patterns)}\n"
        markdown_report += f"**Protected NPM Patterns**: {len(self.policy.protected_npm_patterns)}\n"
        markdown_report += f"**Jules Dispatch Safety**: Per-Key Max={jules_cfg.get('max_active_sessions_per_key')}, Per-Repo Max={jules_cfg.get('max_active_sessions_per_repo')}\n"
        markdown_report += f"**Post-Merge Recovery**: Mode={recovery_cfg.get('mode')}\n\n"

        markdown_report += "## Active Exceptions\n"
        for c in canaries:
            markdown_report += f"- **{c.get('id')}**: {c.get('advisory')} (expires {c.get('review_expires_on')})\n"

        return {
            "metrics": metrics,
            "markdown": markdown_report
        }
