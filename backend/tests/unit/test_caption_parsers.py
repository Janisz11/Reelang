import json
from app.services.yt_dlp_service import _parse_json3, _parse_vtt, _parse_srt, _pick_language


class TestParseJson3:
    def test_basic_segment(self):
        raw = json.dumps({
            "events": [{"tStartMs": 0, "dDurationMs": 2000, "segs": [{"utf8": "Hello world"}]}]
        })
        result = _parse_json3(raw)
        assert len(result) == 1
        assert result[0]["original_text"] == "Hello world"
        assert result[0]["start_ms"] == 0
        assert result[0]["end_ms"] == 2000

    def test_consecutive_duplicate_text_is_deduplicated(self):
        raw = json.dumps({"events": [
            {"tStartMs": 0, "dDurationMs": 1000, "segs": [{"utf8": "Hello"}]},
            {"tStartMs": 500, "dDurationMs": 1000, "segs": [{"utf8": "Hello"}]},
        ]})
        assert len(_parse_json3(raw)) == 1

    def test_different_consecutive_texts_are_kept(self):
        raw = json.dumps({"events": [
            {"tStartMs": 0, "dDurationMs": 1000, "segs": [{"utf8": "Hello"}]},
            {"tStartMs": 1000, "dDurationMs": 1000, "segs": [{"utf8": "World"}]},
        ]})
        assert len(_parse_json3(raw)) == 2

    def test_newline_only_segment_is_skipped(self):
        raw = json.dumps({"events": [
            {"tStartMs": 0, "dDurationMs": 1000, "segs": [{"utf8": "\n"}]},
            {"tStartMs": 1000, "dDurationMs": 1000, "segs": [{"utf8": "Word"}]},
        ]})
        result = _parse_json3(raw)
        assert len(result) == 1
        assert result[0]["original_text"] == "Word"

    def test_event_without_segs_is_skipped(self):
        raw = json.dumps({"events": [
            {"tStartMs": 0, "dDurationMs": 1000},
            {"tStartMs": 1000, "dDurationMs": 1000, "segs": [{"utf8": "Word"}]},
        ]})
        assert len(_parse_json3(raw)) == 1

    def test_invalid_json_returns_empty(self):
        assert _parse_json3("not valid json") == []

    def test_empty_events_returns_empty(self):
        assert _parse_json3(json.dumps({"events": []})) == []

    def test_end_ms_defaults_to_start_plus_3000_when_no_duration(self):
        raw = json.dumps({"events": [{"tStartMs": 1000, "segs": [{"utf8": "Hi"}]}]})
        result = _parse_json3(raw)
        assert result[0]["end_ms"] == 4000


class TestParseVtt:
    def test_basic_segment(self):
        raw = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello world\n"
        result = _parse_vtt(raw)
        assert len(result) == 1
        assert result[0]["original_text"] == "Hello world"
        assert result[0]["start_ms"] == 1000
        assert result[0]["end_ms"] == 3000

    def test_html_tags_are_stripped(self):
        raw = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n<c>Hello</c> world\n"
        result = _parse_vtt(raw)
        assert result[0]["original_text"] == "Hello world"

    def test_multiline_text_is_joined_with_space(self):
        raw = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello\nworld\n"
        result = _parse_vtt(raw)
        assert result[0]["original_text"] == "Hello world"

    def test_consecutive_duplicate_text_is_deduplicated(self):
        raw = (
            "WEBVTT\n\n"
            "00:00:01.000 --> 00:00:02.000\nHello\n\n"
            "00:00:01.500 --> 00:00:03.000\nHello\n"
        )
        assert len(_parse_vtt(raw)) == 1

    def test_timestamp_conversion_with_hours(self):
        raw = "WEBVTT\n\n01:00:00.000 --> 01:00:01.000\nText\n"
        result = _parse_vtt(raw)
        assert result[0]["start_ms"] == 3600000
        assert result[0]["end_ms"] == 3601000

    def test_empty_input_returns_empty(self):
        assert _parse_vtt("") == []

    def test_segment_with_only_tags_is_skipped(self):
        raw = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n<c></c>\n\n00:00:03.000 --> 00:00:04.000\nReal text\n"
        result = _parse_vtt(raw)
        assert len(result) == 1
        assert result[0]["original_text"] == "Real text"


class TestParseSrt:
    def test_basic_segment(self):
        raw = "1\n00:00:01,000 --> 00:00:03,000\nHello world\n"
        result = _parse_srt(raw)
        assert len(result) == 1
        assert result[0]["original_text"] == "Hello world"
        assert result[0]["start_ms"] == 1000
        assert result[0]["end_ms"] == 3000

    def test_sequence_number_is_ignored(self):
        raw = "42\n00:00:01,000 --> 00:00:03,000\nHello\n"
        result = _parse_srt(raw)
        assert result[0]["original_text"] == "Hello"

    def test_html_tags_are_stripped(self):
        raw = "1\n00:00:01,000 --> 00:00:03,000\n<i>Hello</i> world\n"
        result = _parse_srt(raw)
        assert result[0]["original_text"] == "Hello world"

    def test_multiple_text_lines_are_joined(self):
        raw = "1\n00:00:01,000 --> 00:00:03,000\nHello\nworld\n"
        result = _parse_srt(raw)
        assert result[0]["original_text"] == "Hello world"

    def test_multiple_blocks(self):
        raw = "1\n00:00:01,000 --> 00:00:02,000\nFirst\n\n2\n00:00:03,000 --> 00:00:04,000\nSecond\n"
        result = _parse_srt(raw)
        assert len(result) == 2

    def test_empty_input_returns_empty(self):
        assert _parse_srt("") == []

    def test_block_without_timestamp_is_skipped(self):
        raw = "header only\n\n1\n00:00:01,000 --> 00:00:02,000\nActual text\n"
        result = _parse_srt(raw)
        assert len(result) == 1
        assert result[0]["original_text"] == "Actual text"


class TestPickLanguage:
    def test_prefers_auto_captions_over_manual(self):
        info = {"automatic_captions": {"es": []}, "subtitles": {"es": []}}
        assert _pick_language(info) == "es"

    def test_picks_highest_priority_preferred_lang_from_auto(self):
        info = {"automatic_captions": {"de": [], "es": []}, "subtitles": {}}
        assert _pick_language(info) == "es"

    def test_falls_back_to_first_auto_when_no_preferred_lang_present(self):
        info = {"automatic_captions": {"zh": []}, "subtitles": {}}
        assert _pick_language(info) == "zh"

    def test_falls_back_to_manual_when_no_auto_captions(self):
        info = {"automatic_captions": {}, "subtitles": {"fr": []}}
        assert _pick_language(info) == "fr"

    def test_picks_preferred_lang_from_manual(self):
        info = {"automatic_captions": {}, "subtitles": {"zh": [], "es": []}}
        assert _pick_language(info) == "es"

    def test_returns_none_when_no_captions_available(self):
        info = {"automatic_captions": {}, "subtitles": {}}
        assert _pick_language(info) is None

    def test_returns_none_when_keys_missing(self):
        assert _pick_language({}) is None
