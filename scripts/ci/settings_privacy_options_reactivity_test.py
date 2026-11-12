"""Protect live UMP privacy-options visibility in the shipped Settings screen.

The UMP requirement may arrive after Settings is already visible. Snapshotting
only on resume leaves the user's privacy controls hidden until another navigation.
"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
SETTINGS = (
    ROOT
    / "feature/settings/src/main/java/com/parsfilo/contentapp/feature/settings/ui/SettingsScreen.kt"
)


def test_privacy_options_visibility_collects_ump_state_with_lifecycle() -> None:
    screen = SETTINGS.read_text(encoding="utf-8")

    # An observed StateFlow triggers Compose recomposition while Settings is open.
    assert re.search(
        r"val\s+isPrivacyOptionsRequired\s+by\s+"
        r"adManager\.privacyOptionsRequired\.collectAsStateWithLifecycle\(\)",
        screen,
    )
    assert "var isPrivacyOptionsRequired by remember" not in screen

    # Never fall back to a stale snapshot from a lifecycle callback or click.
    assert not re.search(r"\bisPrivacyOptionsRequired\s*=", screen)


def test_live_eligibility_still_controls_required_user_privacy_action() -> None:
    screen = SETTINGS.read_text(encoding="utf-8")
    assert "if (isPrivacyOptionsRequired)" in screen
    assert "adManager.showPrivacyOptions(activity)" in screen
    assert "settings_privacy_options_desc_required" in screen
