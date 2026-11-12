"""User can recover from UMP consent-info failure without restarting the app."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "feature/settings/src/main/java/com/parsfilo/contentapp/feature/settings/ui/SettingsScreen.kt"
AD_MANAGER = ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt"


def test_settings_shows_single_retry_only_for_missing_consent() -> None:
    source = SETTINGS.read_text(encoding="utf8")
    assert "val consentStatus by adManager.consentStatus.collectAsStateWithLifecycle()" in source
    assert "if (consentStatus == ConsentStatus.Missing)" in source
    assert "adManager.retryConsentFromSettings(activity)" in source
    assert "isConsentRetryInProgress" in source
    assert "enabled = !isConsentRetryInProgress" in source


def test_retry_uses_same_ump_form_flow_and_keeps_failed_eligibility_blocked() -> None:
    source = AD_MANAGER.read_text(encoding="utf8")
    assert "fun retryConsentFromSettings(" in source
    assert 'trigger = "settings_retry"' in source
    assert "UserMessagingPlatform.loadAndShowConsentFormIfRequired" in source
    assert "ConsentStatus.Missing" in source
    assert "applyConsentRuntimeState(" in source


def test_retry_copy_is_localized() -> None:
    for locale in ("values", "values-tr", "values-en", "values-de"):
        source = (ROOT / f"feature/settings/src/main/res/{locale}/strings.xml").read_text(encoding="utf8")
        for key in ("settings_privacy_retry_title", "settings_privacy_retry_desc", "settings_privacy_retry_button", "settings_privacy_retry_error"):
            assert f'name="{key}"' in source
