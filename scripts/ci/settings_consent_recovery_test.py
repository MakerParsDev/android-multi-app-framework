"""User can recover from UMP consent-info failure without restarting the app."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "feature/settings/src/main/java/com/parsfilo/contentapp/feature/settings/ui/SettingsScreen.kt"
AD_MANAGER = ROOT / "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt"


def test_settings_shows_explicit_retry_for_missing_and_recoverable_ump_error() -> None:
    source = SETTINGS.read_text(encoding="utf8")
    assert "val consentStatus by adManager.consentStatus.collectAsStateWithLifecycle()" in source
    assert "if (consentStatus == ConsentStatus.Missing || consentStatus is ConsentStatus.Error)" in source
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


def test_age_gate_change_uses_one_orchestrated_ump_refresh() -> None:
    # Age-gate selection must not fetch UMP consent twice before reloading ads.
    settings = SETTINGS.read_text(encoding="utf8")
    start = settings.index("ageGateOptions.forEach")
    end = settings.index("HorizontalDivider(", start)
    age_gate_action = settings[start:end]
    assert "onAdsAgeGateChanged(status) {" in age_gate_action
    assert age_gate_action.count("onPrivacyOptionsUpdated()") == 1

    view_model = (ROOT / "feature/settings/src/main/java/com/parsfilo/contentapp/feature/settings/ui/SettingsViewModel.kt").read_text(encoding="utf8")
    age_gate_save = view_model.split("fun setAdsAgeGateStatus(", 1)[1].split(
        "fun setConsentDebugGeography(", 1
    )[0]
    assert age_gate_save.index("setAdsAgeGateStatus(status.storageValue)") < age_gate_save.index(
        "setAdsAgeGatePromptCompleted(true)"
    ) < age_gate_save.index("onPersisted()")
    assert "adManager.onAdsConfigChanged(" not in age_gate_action

    activity = (ROOT / "app/src/main/java/com/parsfilo/contentapp/MainActivity.kt").read_text(encoding="utf8")
    orchestrator = (
        ROOT / "app/src/main/java/com/parsfilo/contentapp/monetization/AdOrchestrator.kt"
    ).read_text(encoding="utf8")
    assert "onPrivacyOptionsUpdated = {" in activity
    assert "adOrchestrator.refreshConsent(" in activity
    assert "adManager.refreshConsent(activity)" in orchestrator
