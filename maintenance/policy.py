"""Canonical Autonomy Policy Loader and Accessor."""

import os
from typing import Any, Dict, List
import yaml

DEFAULT_POLICY_PATH = "config/autonomy-policy.yaml"

class AutonomyPolicy:
    def __init__(self, data: Dict[str, Any], path: str = DEFAULT_POLICY_PATH):
        self.data = data
        self.path = path

    @classmethod
    def load(cls, path: str = DEFAULT_POLICY_PATH) -> "AutonomyPolicy":
        if not os.path.exists(path):
            raise FileNotFoundError(f"Autonomy policy file not found: {path}")
        with open(path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
        return cls(data, path)

    @property
    def version(self) -> int:
        return self.data.get("version", 1)

    @property
    def protected_gradle_patterns(self) -> List[str]:
        return self.data.get("protected_dependencies", {}).get("gradle_patterns", [])

    @property
    def protected_npm_patterns(self) -> List[str]:
        return self.data.get("protected_dependencies", {}).get("npm_patterns", [])

    @property
    def active_canaries_and_exceptions(self) -> List[Dict[str, Any]]:
        return self.data.get("active_canaries_and_exceptions", [])

    @property
    def dependabot_directories(self) -> List[str]:
        return self.data.get("dependabot_grouping", {}).get("npm", {}).get("directories", [])

    @property
    def jules_config(self) -> Dict[str, Any]:
        return self.data.get("jules_fleet_dispatch", {})

    @property
    def recovery_config(self) -> Dict[str, Any]:
        return self.data.get("post_merge_recovery", {})
