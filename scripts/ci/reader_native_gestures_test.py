"""Source-level guard for shared 17-flavor reader gesture acceptance."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QURAN = ROOT / "feature/quran/src/main/java/com/parsfilo/contentapp/feature/quran/ui"
CONTENT = ROOT / "feature/content/src/main/java/com/parsfilo/contentapp/feature/content/ui"
CORE = ROOT / "core/designsystem/src/main/java/com/parsfilo/contentapp/core/designsystem/component"


def test_quran_verse_has_top_corner_bookmark_and_play_without_download_strip() -> None:
    source = (QURAN / "component/AyahItem.kt").read_text()
    assert 'testTag("quran_ayah_' in source and '_bookmark")' in source
    assert 'testTag("quran_ayah_' in source and '_play")' in source
    assert "Icons.Default.Download" not in source
    assert '_download")' not in source
    assert "SelectionContainer" in source


def test_reader_uses_pinch_and_no_visible_slider() -> None:
    source = (QURAN / "suradetail/QuranSuraDetailScreen.kt").read_text()
    assert "readerPinchFontSize(" in source
    assert 'testTag("quran_text_size_slider")' not in source
    assert "Slider(" not in source
    assert "CustomAccessibilityAction" in source


def test_shared_content_and_prayer_readers_support_pinch_and_selection() -> None:
    for screen in ("ContentScreen.kt", "prayer/PrayerDetailScreen.kt"):
        assert "readerPinchFontSize(" in (CONTENT / screen).read_text()
    for file in ("VerseItem.kt", "prayer/PrayerDetailScreen.kt"):
        assert "SelectionContainer" in (CONTENT / file).read_text()


def test_single_finger_scroll_does_not_get_consumed_by_pinch_listener() -> None:
    source = (CORE / "ReaderPinchFontSize.kt").read_text()
    assert "event.changes.count { it.pressed } >= 2" in source
    assert "calculateZoom()" in source
    assert "onCommitted" in source


def test_miracles_details_use_shared_reader_font_and_native_copy() -> None:
    screen = (CONTENT / "miracles/MiraclesDetailScreen.kt").read_text()
    vm = (CONTENT / "miracles/MiraclesDetailViewModel.kt").read_text()
    assert "readerPinchFontSize(" in screen
    assert "SelectionContainer" in screen
    assert "preferencesDataSource.setFontSize(" in vm
