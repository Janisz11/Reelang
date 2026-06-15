from unittest.mock import MagicMock, patch
from app.services.translation_service import translate_segments, translate_text

_PATCH = "deep_translator.GoogleTranslator"


class TestTranslateText:
    def test_same_language_returns_original_without_api_call(self):
        assert translate_text("hola", "es", "es") == "hola"

    def test_api_exception_returns_none(self):
        with patch(_PATCH) as mock_cls:
            mock_cls.return_value.translate.side_effect = Exception("API down")
            result = translate_text("hola", "es", "en")
        assert result is None


class TestTranslateSegments:
    def _make_segments(self, count: int):
        return [{"original_text": f"text {i}"} for i in range(count)]

    def test_120_segments_split_into_three_chunks(self):
        segments = self._make_segments(120)
        with patch(_PATCH) as mock_cls:
            mock_cls.return_value.translate_batch.side_effect = lambda b: ["t" for _ in b]
            translate_segments(segments, "es", "en")
        assert mock_cls.return_value.translate_batch.call_count == 3

    def test_each_chunk_creates_a_new_translator_instance(self):
        segments = self._make_segments(60)
        with patch(_PATCH) as mock_cls:
            mock_cls.return_value.translate_batch.side_effect = lambda b: ["t" for _ in b]
            translate_segments(segments, "es", "en")
        assert mock_cls.call_count == 2

    def test_all_segments_receive_translated_text(self):
        segments = self._make_segments(5)
        translations = ["T0", "T1", "T2", "T3", "T4"]
        with patch(_PATCH) as mock_cls:
            mock_cls.return_value.translate_batch.return_value = translations
            result = translate_segments(segments, "es", "en")
        for i, seg in enumerate(result):
            assert seg["translated_text"] == f"T{i}"

    def test_api_exception_sets_translated_text_to_none_for_all(self):
        segments = self._make_segments(3)
        with patch(_PATCH) as mock_cls:
            mock_cls.side_effect = Exception("no internet")
            result = translate_segments(segments, "es", "en")
        for seg in result:
            assert seg.get("translated_text") is None

    def test_empty_segments_returns_empty_list(self):
        with patch(_PATCH):
            result = translate_segments([], "es", "en")
        assert result == []

    def test_exactly_50_segments_is_one_chunk(self):
        segments = self._make_segments(50)
        with patch(_PATCH) as mock_cls:
            mock_cls.return_value.translate_batch.return_value = ["t"] * 50
            translate_segments(segments, "es", "en")
        assert mock_cls.return_value.translate_batch.call_count == 1

    def test_51_segments_splits_into_two_chunks(self):
        segments = self._make_segments(51)
        with patch(_PATCH) as mock_cls:
            mock_cls.return_value.translate_batch.side_effect = lambda b: ["t"] * len(b)
            translate_segments(segments, "es", "en")
        assert mock_cls.return_value.translate_batch.call_count == 2
