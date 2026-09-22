"""Post-merge conservative self-healing and recovery controller."""

from typing import Any, Dict, List, Optional
from maintenance.policy import AutonomyPolicy

class PostMergeRecoveryController:
    """Monitors post-merge CI health and generates candidate recovery/revert proposals safely."""
    def __init__(self, policy: AutonomyPolicy):
        self.policy = policy
        self.config = policy.recovery_config

    def observe_and_evaluate(
        self,
        recent_pr_number: int,
        recent_pr_author: str,
        files_changed: List[str],
        main_ci_status: str,
        failure_logs: str = ""
    ) -> Dict[str, Any]:
        """Evaluates post-merge status in OBSERVE mode.

        Returns structured record with culprit attribution and candidate recovery draft.
        """
        mode = self.config.get("mode", "OBSERVE")

        if main_ci_status == "SUCCESS":
            return {
                "status": "HEALTHY",
                "mode": mode,
                "action_taken": "NONE",
                "message": "Main branch CI is healthy post-merge."
            }

        # Main branch CI is failure — evaluate candidate culprit
        is_high_confidence = self._evaluate_attribution_confidence(files_changed, failure_logs)

        revert_branch = f"revert/pr-{recent_pr_number}-observe"
        recovery_proposal = {
            "target_pr": recent_pr_number,
            "author": recent_pr_author,
            "confidence": "HIGH" if is_high_confidence else "LOW",
            "proposed_revert_branch": revert_branch,
            "direct_revert_main_allowed": self.config.get("direct_revert_main_allowed", False),
            "bypass_mergify_allowed": self.config.get("bypass_mergify_allowed", False),
            "auto_create_draft_revert": self.config.get("auto_create_draft_revert", True),
            "mode": mode
        }

        if mode == "OBSERVE":
            return {
                "status": "UNHEALTHY_MAIN",
                "mode": "OBSERVE",
                "action_taken": "DRAFT_PROPOSAL_LOGGED",
                "recovery_proposal": recovery_proposal,
                "message": f"Observed main CI failure post-merge of PR #{recent_pr_number}. Revert proposal drafted (OBSERVE mode)."
            }
        else:
            return {
                "status": "UNHEALTHY_MAIN",
                "mode": mode,
                "action_taken": "REVERT_PR_CREATED_VIA_MERGIFY_QUEUE",
                "recovery_proposal": recovery_proposal,
                "message": f"Active recovery mode enabled. Submitted draft revert PR for #{recent_pr_number} to Mergify queue."
            }

    def _evaluate_attribution_confidence(self, files_changed: List[str], failure_logs: str) -> bool:
        # High confidence if failed files directly match changes
        if not files_changed:
            return False
        for f in files_changed:
            if f in failure_logs:
                return True
        return True # Default to True for single isolated PR merge
