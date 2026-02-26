from __future__ import annotations

from smarthome_core.llm_client import StubClient
from smarthome_core.parser_llm import LLMParserV1, _extract_first_json_object
from smarthome_core.schema_utils import load_schema
from smarthome_core.assets import AssetPaths
from smarthome_core.io import load_json


def test_extract_first_json_object_balanced():
    s = 'blah blah {"a": 1, "b": {"c": 2}} trailing'
    out = _extract_first_json_object(s)
    assert out == '{"a": 1, "b": {"c": 2}}'


def test_llm_parser_llm_only_returns_clarification_on_invalid_output(tmp_path):
    paths = AssetPaths(tmp_path)
    # Use real schema files from repo by constructing paths from package root instead.
    # Easiest: point AssetPaths to repo root.
    import pathlib
    repo_root = pathlib.Path(__file__).resolve().parents[1]
    paths = AssetPaths(repo_root)

    parsed_schema = load_schema(paths.parsed_schema)
    device_registry = load_json(paths.device_registry)
    area_synonyms = load_json(paths.area_synonyms)
    colors = load_json(paths.colors)
    modifiers = load_json(paths.modifiers)

    parser = LLMParserV1(client=StubClient(), parsed_schema=parsed_schema, fallback_to_rules=False)
    parsed = parser.parse(
        "сделай в кухне потише",
        context={"last_area_name": None},
        device_registry=device_registry,
        area_synonyms=area_synonyms,
        colors=colors,
        modifiers=modifiers,
    )

    assert parsed.get("actions") and parsed["actions"][0]["intent"] == "UNKNOWN"
    assert parsed.get("clarification") is not None and parsed["clarification"]["needed"] is True
    

def test_llm_parser_reuses_freeform_question(tmp_path):
    import pathlib

    class QuestionClient:
        def generate_json(self, *, system: str, user: str, temperature: float = 0.0, max_tokens: int = 512) -> str:
            return "В какой комнате включить свет?"

    repo_root = pathlib.Path(__file__).resolve().parents[1]
    paths = AssetPaths(repo_root)

    parsed_schema = load_schema(paths.parsed_schema)
    device_registry = load_json(paths.device_registry)
    area_synonyms = load_json(paths.area_synonyms)
    colors = load_json(paths.colors)
    modifiers = load_json(paths.modifiers)

    parser = LLMParserV1(client=QuestionClient(), parsed_schema=parsed_schema, fallback_to_rules=False)
    parsed = parser.parse(
        "включи свет",
        context={"last_area_name": None},
        device_registry=device_registry,
        area_synonyms=area_synonyms,
        colors=colors,
        modifiers=modifiers,
    )

    clarification = parsed.get("clarification") or {}
    assert clarification.get("needed") is True
    assert "какой комнате" in (clarification.get("question") or "").lower()


def test_llm_parser_llm_fallback_uses_rules_on_invalid_output(tmp_path):
    import pathlib
    repo_root = pathlib.Path(__file__).resolve().parents[1]
    paths = AssetPaths(repo_root)

    parsed_schema = load_schema(paths.parsed_schema)
    device_registry = load_json(paths.device_registry)
    area_synonyms = load_json(paths.area_synonyms)
    colors = load_json(paths.colors)
    modifiers = load_json(paths.modifiers)

    parser = LLMParserV1(client=StubClient(), parsed_schema=parsed_schema, fallback_to_rules=True)
    parsed = parser.parse(
        "сделай в кухне потише",
        context={"last_area_name": None},
        device_registry=device_registry,
        area_synonyms=area_synonyms,
        colors=colors,
        modifiers=modifiers,
    )

    # Rule fallback should produce at least one action.
    assert len(parsed.get("actions") or []) >= 1
