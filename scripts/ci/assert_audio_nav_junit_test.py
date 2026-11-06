"""JUnit acceptance contract for the scheduled managed-device audio regression test."""

from __future__ import annotations

import importlib.util
from pathlib import Path

import pytest

SOURCE = Path(__file__).with_name("assert_audio_nav_junit.py")
spec = importlib.util.spec_from_file_location("assert_audio_nav_junit", SOURCE)
assert spec is not None and spec.loader is not None
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def report(tmp_path: Path, *, status: str = "", method: str | None = None, klass: str | None = None) -> Path:
    path = tmp_path / "ciPixel2Api30" / "TEST-audio.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    name = method or gate.REQUIRED_METHOD
    classname = klass or gate.REQUIRED_CLASS
    path.write_text(
        f'<testsuite tests="1"><testcase classname="{classname}" name="{name}">'
        f'{status}</testcase></testsuite>',
        encoding="utf-8",
    )
    return path


def test_required_audio_navigation_test_passes_with_real_junit_testcase(tmp_path: Path) -> None:
    file = report(tmp_path)
    assert str(file) in gate.verify_required_test(tmp_path)


@pytest.mark.parametrize("status", ["<skipped/>", "<failure/>", "<error/>"])
def test_skipped_or_failed_audio_navigation_is_not_accepted(tmp_path: Path, status: str) -> None:
    report(tmp_path, status=status)
    with pytest.raises(ValueError, match="Required audio navigation JUnit testcase is"):
        gate.verify_required_test(tmp_path)


def test_missing_report_is_not_accepted(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="No managed-device JUnit XML"):
        gate.verify_required_test(tmp_path)


def test_absent_audio_navigation_test_is_not_accepted(tmp_path: Path) -> None:
    report(tmp_path, method="someOtherMethod")
    with pytest.raises(ValueError, match="found 0"):
        gate.verify_required_test(tmp_path)


def test_wrong_class_is_not_accepted(tmp_path: Path) -> None:
    report(tmp_path, klass="com.example.OtherTest")
    with pytest.raises(ValueError, match="found 0"):
        gate.verify_required_test(tmp_path)


def test_duplicate_target_test_is_not_accepted(tmp_path: Path) -> None:
    original = report(tmp_path)
    (original.parent / "TEST-audio-duplicate.xml").write_bytes(original.read_bytes())
    with pytest.raises(ValueError, match="found 2"):
        gate.verify_required_test(tmp_path)


def test_malformed_report_is_not_accepted(tmp_path: Path) -> None:
    path = report(tmp_path)
    path.write_text("<testsuite>", encoding="utf-8")
    with pytest.raises(ValueError, match="Unreadable managed-device JUnit XML"):
        gate.verify_required_test(tmp_path)


def test_cli_exits_nonzero_on_missing_evidence(tmp_path: Path) -> None:
    assert gate.main([str(tmp_path)]) == 1
