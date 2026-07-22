#!/usr/bin/env python3
from __future__ import annotations

import tempfile
from pathlib import Path
from typing import Any

from publish_play_internal import publish_aab, sha256_file


class Request:
    def __init__(self, value: Any, calls: list[tuple[str, dict[str, Any]]], name: str, kwargs: dict[str, Any]) -> None:
        self.value = value
        self.calls = calls
        self.name = name
        self.kwargs = kwargs

    def execute(self, num_retries: int = 0) -> Any:
        self.calls.append((self.name, {**self.kwargs, "num_retries": num_retries}))
        return self.value


class Bundles:
    def __init__(self, uploaded: dict[str, Any], calls: list[tuple[str, dict[str, Any]]]) -> None:
        self.uploaded = uploaded
        self.calls = calls

    def upload(self, **kwargs: Any) -> Request:
        return Request(self.uploaded, self.calls, "bundles.upload", kwargs)


class Tracks:
    def __init__(self, calls: list[tuple[str, dict[str, Any]]]) -> None:
        self.calls = calls

    def update(self, **kwargs: Any) -> Request:
        return Request({}, self.calls, "tracks.update", kwargs)


class Edits:
    def __init__(self, uploaded: dict[str, Any]) -> None:
        self.calls: list[tuple[str, dict[str, Any]]] = []
        self.uploaded = uploaded

    def insert(self, **kwargs: Any) -> Request:
        return Request({"id": "edit-1"}, self.calls, "edits.insert", kwargs)

    def bundles(self) -> Bundles:
        return Bundles(self.uploaded, self.calls)

    def tracks(self) -> Tracks:
        return Tracks(self.calls)

    def validate(self, **kwargs: Any) -> Request:
        return Request({}, self.calls, "edits.validate", kwargs)

    def commit(self, **kwargs: Any) -> Request:
        return Request({}, self.calls, "edits.commit", kwargs)

    def delete(self, **kwargs: Any) -> Request:
        return Request({}, self.calls, "edits.delete", kwargs)


class Service:
    def __init__(self, uploaded: dict[str, Any]) -> None:
        self.edits_api = Edits(uploaded)

    def edits(self) -> Edits:
        return self.edits_api


def test_exact_bundle_hash_is_published_and_committed() -> None:
    with tempfile.TemporaryDirectory() as raw:
        aab = Path(raw) / "app.aab"
        aab.write_bytes(b"signed-aab-fixture")
        digest = sha256_file(aab)
        service = Service({"versionCode": 123, "sha256": digest})
        report = publish_aab(
            service=service,
            package_name="com.parsfilo.kurankerim",
            aab_path=aab,
            track="internal",
            release_name="ci-abc123",
            media_factory=lambda path: path,
        )
        assert report["versionCode"] == 123
        assert report["sha256"] == digest
        names = [name for name, _ in service.edits_api.calls]
        assert names == [
            "edits.insert",
            "bundles.upload",
            "tracks.update",
            "edits.validate",
            "edits.commit",
        ]
        track_call = next(kwargs for name, kwargs in service.edits_api.calls if name == "tracks.update")
        assert track_call["track"] == "internal"
        assert track_call["body"]["releases"] == [
            {
                "name": "ci-abc123",
                "versionCodes": ["123"],
                "status": "completed",
            }
        ]


def test_hash_mismatch_deletes_edit_and_never_commits() -> None:
    with tempfile.TemporaryDirectory() as raw:
        aab = Path(raw) / "app.aab"
        aab.write_bytes(b"signed-aab-fixture")
        service = Service({"versionCode": 123, "sha256": "0" * 64})
        try:
            publish_aab(
                service=service,
                package_name="com.parsfilo.kurankerim",
                aab_path=aab,
                track="internal",
                release_name="ci-abc123",
                media_factory=lambda path: path,
            )
        except RuntimeError as error:
            assert "SHA-256 mismatch" in str(error)
        else:
            raise AssertionError("hash mismatch unexpectedly succeeded")
        names = [name for name, _ in service.edits_api.calls]
        assert "edits.delete" in names
        assert "edits.commit" not in names


def test_non_internal_track_is_rejected() -> None:
    with tempfile.TemporaryDirectory() as raw:
        aab = Path(raw) / "app.aab"
        aab.write_bytes(b"signed-aab-fixture")
        service = Service({"versionCode": 123, "sha256": sha256_file(aab)})
        try:
            publish_aab(
                service=service,
                package_name="com.parsfilo.kurankerim",
                aab_path=aab,
                track="production",
                release_name="ci-abc123",
                media_factory=lambda path: path,
            )
        except RuntimeError as error:
            assert "Only the 'internal' track" in str(error)
        else:
            raise AssertionError("production track unexpectedly accepted")


def main() -> int:
    for test in (
        test_exact_bundle_hash_is_published_and_committed,
        test_hash_mismatch_deletes_edit_and_never_commits,
        test_non_internal_track_is_rejected,
    ):
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
