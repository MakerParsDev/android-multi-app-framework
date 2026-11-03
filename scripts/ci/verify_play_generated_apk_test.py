#!/usr/bin/env python3
"""Offline contract tests for the generated APK Firebase-resource audit."""

from __future__ import annotations

from verify_play_generated_apk import (
    choose_universal, parse_resources, read_play_version_code, signed_apk_media_url,
    validate_firebase_resources, parse_manifest_badging, validate_manifest_badging,
    read_toolchain_sdk_values,
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
    "web_client_id": "123456789-test.apps.googleusercontent.com",
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


def test_reject_missing_or_invalid_embedded_google_signin_client():
    for invalid in (None, "", "example.apps.googleusercontent.com", "1234-invalid.example.com"):
        records = parse_resources(sample())
        if invalid is None:
            records.pop("web_client_id")
        else:
            records["web_client_id"] = [("", invalid)]
        raises(lambda: validate_firebase_resources(records, EXPECTED))


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



def example_badging(package="com.parsfilo.mucizedualar", code=81, min_sdk=24, target_sdk=37):
    return (
        f"package: name='{package}' versionCode='{code}' versionName='1.0.{code}'\n"
        f"minSdkVersion:'{min_sdk}'\n"
        f"targetSdkVersion:'{target_sdk}'\n"
    )


def test_manifest_package_version_and_sdk_match_release_build():
    assert parse_manifest_badging(example_badging())["minSdk"] == 24
    result = validate_manifest_badging(
        example_badging(), expected_package="com.parsfilo.mucizedualar",
        expected_version_code=81, expected_min_sdk=24, expected_target_sdk=37,
    )
    assert result == {"packageName": "com.parsfilo.mucizedualar", "versionCode": 81,
                      "minSdk": 24, "targetSdk": 37}


def test_reject_wrong_package_and_version_code():
    for bad in (example_badging(package="com.example.other"),
                example_badging(code=80)):
        raises(lambda bad=bad: validate_manifest_badging(
            bad, "com.parsfilo.mucizedualar", 81, 24, 37,
        ))


def test_manifest_supports_legacy_sdk_label_without_accepting_duplicates():
    legacy = example_badging().replace("minSdkVersion:", "sdkVersion:")
    result = validate_manifest_badging(
        legacy, "com.parsfilo.mucizedualar", 81, 24, 37,
    )
    assert result["minSdk"] == 24
    raises(lambda: validate_manifest_badging(
        legacy + "minSdkVersion:'24'\n",
        "com.parsfilo.mucizedualar", 81, 24, 37,
    ))


def test_reject_sdk_compatibility_drift():
    for bad in (example_badging(min_sdk=32), example_badging(target_sdk=35),
                example_badging(min_sdk=38)):
        raises(lambda bad=bad: validate_manifest_badging(
            bad, "com.parsfilo.mucizedualar", 81, 24, 37,
        ))


def test_reject_missing_duplicate_and_malformed_manifest_fields():
    valid = example_badging()
    for bad in (valid.replace("minSdkVersion:'24'\n", ""),
                valid.replace("targetSdkVersion:'37'\n", ""),
                valid.replace("versionCode='81'", ""),
                valid + "targetSdkVersion:'37'\n",
                valid + "package: name='com.other.app' versionCode='81'\n",
                valid.replace("minSdkVersion:'24'", "minSdkVersion:'abc'")):
        raises(lambda bad=bad: validate_manifest_badging(
            bad, "com.parsfilo.mucizedualar", 81, 24, 37,
        ))


def test_parse_sdk_settings_from_exact_release_properties():
    import tempfile
    from pathlib import Path
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "gradle.properties"
        path.write_text("toolchain.android.minSdk=24\n"
                        "toolchain.android.targetSdk=37\n")
        assert read_toolchain_sdk_values(path) == (24, 37)
        for bad in ("toolchain.android.minSdk=24\n",
                    "toolchain.android.minSdk=24\n"
                    "toolchain.android.targetSdk=37\n"
                    "toolchain.android.minSdk=25\n",
                    "toolchain.android.minSdk=38\n"
                    "toolchain.android.targetSdk=37\n"):
            path.write_text(bad)
            raises(lambda: read_toolchain_sdk_values(path))


def main() -> int:
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("PASS", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
