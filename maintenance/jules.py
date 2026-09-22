"""Jules/Fleet dispatch deduplication, reservation lease, and session-storm prevention engine."""

import time
import hashlib
from typing import Any, Dict, Optional, Tuple
from maintenance.policy import AutonomyPolicy

class DispatchLeaseStore:
    """In-memory or state file persistent lease store for Jules dispatch operations."""
    def __init__(self):
        self.leases: Dict[str, Dict[str, Any]] = {}
        self.rolling_history: List[float] = []

    def compute_dedup_key(self, repo: str, goal_or_issue: str, base_sha: str, operation_class: str) -> str:
        raw = f"{repo}:{goal_or_issue}:{base_sha}:{operation_class}"
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    def try_acquire_lease(
        self,
        repo: str,
        goal_or_issue: str,
        base_sha: str,
        operation_class: str,
        policy: AutonomyPolicy,
        now: Optional[float] = None
    ) -> Tuple[bool, str, str]:
        """Attempts to acquire a dispatch lease for a work unit. Returns (acquired, dedup_key, reason)."""
        current_time = now if now is not None else time.time()
        cfg = policy.jules_config

        # 1. Kill switches
        if cfg.get("global_kill_switch", False):
            return False, "", "Global kill switch is ACTIVE."
        if cfg.get("repo_kill_switch", False):
            return False, "", "Repository kill switch is ACTIVE."

        # 2. Compute key
        dedup_key = self.compute_dedup_key(repo, goal_or_issue, base_sha, operation_class)

        # 3. Clean stale leases
        ttl_seconds = cfg.get("session_ttl_minutes", 60) * 60
        self.cleanup_stale_leases(current_time, ttl_seconds)

        # 4. Check active key lease
        if dedup_key in self.leases:
            lease = self.leases[dedup_key]
            if current_time - lease["created_at"] < ttl_seconds:
                return False, dedup_key, f"Active session lease already exists for key {dedup_key[:8]}."

        # 5. Check active sessions per repo
        max_repo = cfg.get("max_active_sessions_per_repo", 3)
        if len(self.leases) >= max_repo:
            return False, dedup_key, f"Active repository session quota reached ({len(self.leases)}/{max_repo})."

        # 6. Check 24h rolling quota
        rolling_limit = cfg.get("max_rolling_sessions_24h", 20)
        self.rolling_history = [t for t in self.rolling_history if current_time - t < 86400]
        if len(self.rolling_history) >= rolling_limit:
            return False, dedup_key, f"24h rolling session quota reached ({len(self.rolling_history)}/{rolling_limit})."

        # Acquire lease
        self.leases[dedup_key] = {
            "created_at": current_time,
            "repo": repo,
            "goal": goal_or_issue,
            "base_sha": base_sha,
            "operation": operation_class
        }
        self.rolling_history.append(current_time)
        return True, dedup_key, "Lease acquired successfully."

    def release_lease(self, dedup_key: str):
        if dedup_key in self.leases:
            del self.leases[dedup_key]

    def cleanup_stale_leases(self, now: float, ttl_seconds: float):
        stale_keys = [k for k, v in self.leases.items() if now - v["created_at"] >= ttl_seconds]
        for k in stale_keys:
            del self.leases[k]
