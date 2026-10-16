#!/usr/bin/env python3
"""Offline contract tests for the generated APK Firebase-resource audit."""

from __future__ import annotations

from verify_play_generated_apk import (
    choose_universal, parse_resources, read_play_version_code, signed_apk_media_url,
    validate_firebase_resources,
)

EXPECTED = {
    "appId": "1:123456789:android:test",
    "projectId": "sample-firebase-project",
}
DATA = {
    "google_app_id": EXPECTED["appId"],
    "project_id": EXPECTED["projectId"],
    "gcm_defaultSenderId": "123456789",
    "google_api_key": "AIza" + "X" * 35,
    "google_storage_bucket": "sample.appspot.com",
    "firebase_database_url": "https://example.invalid",
    "admob_app_id": "ca-app-pub-fake",
    "web_client_id": "example.apps.googleusercontent.com",
}


def sample(overrides=None):
    output = []
    i = 0
    for key, value in DATA.items():
        output += [f"resource 0x7f180{i:03x} string/{key}", f'  () "{value}"']
        i += 1
    output += ["resource 0x7f180100 string/irrelevant", '  (tr) "irrelevant"']
    if overrides:
        output.extend(overrides)
    return "\n".join(output)


def raises(fn):
    try:
        fn()
    except ValueError:
        pass
    else:
        raise AssertionError("expected the audit to reject invalid metadata/resources")


def test_exact_default_values_and_no_locale_overrides():
    result = validate_firebase_resources(parse_resources(sample()), EXPECTED)
    assert len(result) == 8
    assert all(v == "verified" for v in result.values())


def test_reject_firebase_locale_override_even_if_same_value():
    lines = sample().replace(
        'resource 0x7f180000 string/google_app_id\n',
        'resource 0x7f180000 string/google_app_id\n  (tr) "legacy"\n',
    )
    raises(lambda: validate_firebase_resources(parse_resources(lines), EXPECTED))


def test_reject_localized_resource_reference_and_unsupported_default():
    lines = sample().replace(
        'resource 0x7f180000 string/google_app_id\n',
        'resource 0x7f180000 string/google_app_id\n'
        '  (tr) @string/legacy_app_id\n',
    )
    raises(lambda: validate_firebase_resources(parse_resources(lines), EXPECTED))
    records = parse_resources(sample())
    records["google_storage_bucket"] = [("", None)]
    raises(lambda: validate_firebase_resources(records, EXPECTED))


def test_reject_malformed_api_key_characters_and_length():
    for invalid in ("AIza" + " " * 35, "AIza" + "A" * 34,
                    "AIza" + "A" * 36, "AIza" + "A" * 34 + "!"):
        records = parse_resources(sample())
        records["google_api_key"] = [("", invalid)]
        raises(lambda: validate_firebase_resources(records, EXPECTED))


def test_reject_mismatched_default_project_and_app_id():
    for field in ("project_id", "google_app_id", "gcm_defaultSenderId"):
        corrupt = sample().replace(DATA[field], "wrong", 1)
        raises(lambda: validate_firebase_resources(parse_resources(corrupt), EXPECTED))


def test_reject_missing_core_and_invalid_key():
    for field in ("google_app_id", "google_api_key"):
        filtered = parse_resources(sample())
        del filtered[field]
        raises(lambda: validate_firebase_resources(filtered, EXPECTED))
    corrupt = sample().replace(DATA["google_api_key"], "INVALID_KEY", 1)
    raises(lambda: validate_firebase_resources(parse_resources(corrupt), EXPECTED))


def test_optional_tech_resources_may_be_absent():
    records = parse_resources(sample())
    records.pop("firebase_database_url")
    assert validate_firebase_resources(records, EXPECTED)["firebase_database_url"].startswith("absent")


def test_requires_exactly_one_universal_apk():
    assert choose_universal({"generatedApks": [
        {"generatedUniversalApk": {"downloadId": "safe-id"}}
    ]}) == "safe-id"
    for invalid in ({}, {"generatedApks": []},
                    {"generatedApks": [{"generatedUniversalApk": {"downloadId": "a"}},
                                       {"generatedUniversalApk": {"downloadId": "b"}}]}):
        raises(lambda: choose_universal(invalid))


def test_resolve_exact_build_time_version_code():
    from pathlib import Path
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        file = Path(tmp) / "app-versions.properties"
        file.write_text("amenerrasulu.versionCode=56\nother.versionCode=99\n")
        assert read_play_version_code(file, "amenerrasulu") == 56
        raises(lambda: read_play_version_code(file, "missing"))
        file.write_text("amenerrasulu.versionCode=56\namenerrasulu.versionCode=56\n")
        raises(lambda: read_play_version_code(file, "amenerrasulu"))
        file.write_text("amenerrasulu.versionCode=-2\n")
        raises(lambda: read_play_version_code(file, "amenerrasulu"))


def test_signed_apk_download_requests_media_contents():
    uri = signed_apk_media_url("https://androidpublisher.googleapis.com/example", "a/b+c=")
    assert uri.endswith("/downloads/a%2Fb%2Bc%3D:download?alt=media")


def test_validated_resources_never_echo_api_key():
    result = validate_firebase_resources(parse_resources(sample()), EXPECTED)
    assert DATA["google_api_key"] not in str(result)


def main() -> int:
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("PASS", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
