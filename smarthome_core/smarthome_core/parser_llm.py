"""LLM-based parser (v1) for light commands.

Key principles:
- The LLM outputs a ParsedCommand JSON object (schema_version=1.0).
- We validate the output against the ParsedCommand schema.
- If output is invalid or unsafe, we either:
  - fallback to the robust rule parser (recommended for production), or
  - return a clarification error (LLM-only mode, for evaluation).

This is intentionally *not* tuned to the gold dataset:
- The prompt contains schema + lexicon + policy, not per-example expected outputs.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Dict, Optional, Tuple

from .llm_client import LLMClient
from .schema_utils import validate_with_schema
from .parser import parse_light_command_v1

MAX_HINT_ITEMS = 15
PRIORITY_COLORS = ["белый", "теплый белый", "холодный белый", "желтый", "оранжевый"]


_JSON_RE = re.compile(r"\{.*\}", flags=re.S)


def _extract_first_json_object(text: str) -> Optional[str]:
    """Extract the first top-level JSON object from a string.

    Handles common cases where the model adds commentary.
    Conservative: returns None if no balanced object found.
    """
    if not text:
        return None

    # Fast path: if it already looks like JSON object.
    stripped = text.strip()
    if stripped.startswith("{") and stripped.endswith("}"):
        return stripped

    # Bracket counting approach
    start = stripped.find("{")
    if start == -1:
        return None

    depth = 0
    in_str = False
    esc = False
    for i in range(start, len(stripped)):
        ch = stripped[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        else:
            if ch == '"':
                in_str = True
                continue
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return stripped[start : i + 1]
    return None


def _unknown_action() -> Dict[str, Any]:
    return {
        "domain": "light",
        "intent": "UNKNOWN",
        "target": {"scope": "UNSPECIFIED", "area_name": None, "entity_ids": []},
        "params": {
            "brightness": None,
            "brightness_delta": None,
            "color": None,
            "color_temp_kelvin": None,
            "color_temp_delta_k": None,
            "transition_s": None,
        },
    }


def _parsed_clarification(
    *, question: str, options: Optional[list[str]] = None
) -> Dict[str, Any]:
    opts = options or ["Повтори команду другими словами."]
    return {
        "schema_version": "1.0",
        "actions": [_unknown_action()],
        "clarification": {
            "needed": True,
            "question": question,
            "options": opts[:20],
        },
    }


def _clarification_from_freeform(text: str, *, options: Optional[list[str]] = None) -> Optional[Dict[str, Any]]:
    """Try to reuse a natural-language reply from the LLM as clarification."""
    if not text:
        return None

    cleaned = text.strip()
    if not cleaned:
        return None

    # Remove service tokens that sometimes leak from chat templates.
    cleaned = cleaned.replace("<|im_end|>", " ").replace("</s>", " ").strip()
    cleaned = re.sub(r"^assistant\s*[:\-]\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s+", " ", cleaned)

    if not cleaned:
        return None

    # If there is no question mark and the text is too long, treat it as noise.
    if "?" not in cleaned and len(cleaned) > 160:
        return None

    if len(cleaned) > 280:
        cleaned = cleaned[:277].rstrip() + "..."

    return _parsed_clarification(question=cleaned, options=options)


def _collect_color_hints(colors: Dict[str, Any]) -> Tuple[list[Dict[str, Any]], list[Dict[str, Any]], list[str]]:
    """Extract RGB palette entries and white temperatures for the prompt."""

    palette_rgb: list[Dict[str, Any]] = []
    for entry in colors.get("palette_rgb") or []:
        name = str(entry.get("name") or "").strip()
        rgb = entry.get("rgb")
        if not name or not isinstance(rgb, list) or len(rgb) != 3:
            continue
        palette_rgb.append({"name": name, "rgb": [int(v) for v in rgb]})

    whites: list[Dict[str, Any]] = []
    for entry in colors.get("whites_color_temp") or []:
        name = str(entry.get("name") or "").strip()
        kelvin = entry.get("color_temp_kelvin")
        if not name or not isinstance(kelvin, (int, float)):
            continue
        whites.append({"name": name, "color_temp_kelvin": int(kelvin)})

    def _order(items: list[Dict[str, Any]], priorities: list[str]) -> list[Dict[str, Any]]:
        def norm(s: str) -> str:
            return s.lower().replace("ё", "е")

        ordered: list[Dict[str, Any]] = []
        used: set[str] = set()
        for p in priorities:
            for item in items:
                if item["name"] in used:
                    continue
                if norm(p) in norm(item["name"]):
                    ordered.append(item)
                    used.add(item["name"])
        for item in items:
            if item["name"] not in used:
                ordered.append(item)
        return ordered

    ordered_palette = _order(palette_rgb, PRIORITY_COLORS)
    ordered_whites = _order(whites, PRIORITY_COLORS)
    names = [entry["name"] for entry in ordered_palette] + [entry["name"] for entry in ordered_whites]
    return ordered_palette, ordered_whites, names


def _collect_brightness_hints(modifiers: Dict[str, Any]) -> Dict[str, list[str]]:
    """Flatten brightness-related phrases for the prompt."""

    def _gather(entries: Optional[list[Dict[str, Any]]]) -> list[str]:
        phrases: list[str] = []
        if not entries:
            return phrases
        for entry in entries:
            for p in entry.get("patterns") or []:
                s = str(p).strip()
                if s and s not in phrases:
                    phrases.append(s)
        return phrases

    brightness = modifiers.get("brightness") or {}
    return {
        "dim_words": _gather(brightness.get("relative_down"))[:MAX_HINT_ITEMS],
        "brighten_words": _gather(brightness.get("relative_up"))[:MAX_HINT_ITEMS],
        "absolute_words": _gather(brightness.get("absolute"))[:MAX_HINT_ITEMS],
    }


def _apply_context_defaults(parsed: Dict[str, Any], *, context: Dict[str, Any]) -> Dict[str, Any]:
    """Inject last_area_name defaults when target scope is unspecified.

    If пользователь не указал новую комнату, используем context.last_area_name.
    """

    last_area = context.get('last_area_name')
    if not last_area:
        return parsed

    actions = parsed.get('actions')
    if not isinstance(actions, list):
        return parsed

    for action in actions:
        if not isinstance(action, dict):
            continue
        target = action.setdefault('target', {})
        scope = target.get('scope') or 'UNSPECIFIED'
        ent_ids = target.get('entity_ids') or []
        area_name = target.get('area_name')

        if ent_ids:
            continue

        if scope in {'UNSPECIFIED', 'AREA'} and not area_name:
            target['scope'] = 'AREA'
            target['area_name'] = last_area

    return parsed


def _ensure_target_or_clarify(
    parsed: Dict[str, Any],
    *,
    context: Dict[str, Any],
    area_options: list[str],
) -> Dict[str, Any]:
    '''Ensure that each action has a resolvable target or ask the user.'''
    actions = parsed.get('actions')
    if not isinstance(actions, list):
        return parsed

    for action in actions:
        if not isinstance(action, dict):
            continue
        target = action.get('target') or {}
        ent_ids = target.get('entity_ids') or []
        area_name = (target.get('area_name') or '').strip()

        if ent_ids or area_name:
            continue

        last_area = (context or {}).get('last_area_name')
        if last_area:
            target['scope'] = 'AREA'
            target['area_name'] = last_area
            continue

        question = 'В какой комнате выполнить команду?'
        opts = area_options[:5] if area_options else []
        return _parsed_clarification(question=question, options=opts or None)

    return parsed


@dataclass(frozen=True)
class LLMParserV1:
    """LLM parser wrapper."""

    client: LLMClient
    parsed_schema: Dict[str, Any]
    # If True: fallback to rule parser on any LLM failure/invalid output.
    fallback_to_rules: bool = True

    def parse(
        self,
        text: str,
        *,
        context: Dict[str, Any],
        device_registry: Dict[str, Any],
        area_synonyms: Dict[str, Any],
        colors: Dict[str, Any],
        modifiers: Dict[str, Any],
    ) -> Dict[str, Any]:
        system = (
            "Ты — модуль NLU для умного дома. Твоя задача: преобразовать русскоязычную команду в JSON. "
            "Верни ТОЛЬКО один JSON-объект без Markdown и без пояснений. "
            "JSON должен соответствовать схеме ParsedCommand v1 (schema_version=1.0). "
            "Если нужна уточняющая информация, верни intent=\"UNKNOWN\" и поле clarification (needed/question/options) согласно схеме."
        )

        # Keep prompt relatively compact: include only the useful parts.
        allowed_intents = [
            "TURN_ON",
            "TURN_OFF",
            "SET_BRIGHTNESS",
            "ADJUST_BRIGHTNESS",
            "SET_COLOR",
            "SET_COLOR_TEMP",
            "ADJUST_COLOR_TEMP",
            "SCENE_ON",
            "SCENE_OFF",
            "CANCEL",
            "UNKNOWN",
        ]

        # Canonical areas list (keys are normalized in area_synonyms).
        canonical_areas = area_synonyms.get("canonical_areas", []) or []
        areas = [a.get("name") for a in canonical_areas if isinstance(a, dict) and a.get("name")]
        areas = areas[:MAX_HINT_ITEMS]
        # Canonical modifier names (keys in modifiers map)
        mod_keys = list(modifiers.keys())[:MAX_HINT_ITEMS]
        palette_entries, white_entries, color_names = _collect_color_hints(colors)
        color_palette = palette_entries[:MAX_HINT_ITEMS]
        white_palette = white_entries[:MAX_HINT_ITEMS]
        color_keys_full = list(colors.keys())
        priority = [c for c in PRIORITY_COLORS if c in color_keys_full]
        remainder = [c for c in color_keys_full if c not in priority]
        color_keys = (priority + remainder)[:MAX_HINT_ITEMS]
        brightness_hints = _collect_brightness_hints(modifiers)

        rules_summary = (
            "target.scope: UNSPECIFIED/AREA/ENTITY; area_name: null или название комнаты; entity_ids: [] или список. "
            "params: brightness 0..100, brightness_delta -100..100, color in color_keys, color_temp_kelvin 2000..6500, "
            "color_temp_delta_k -2000..2000, transition_s 0..60. "
            "Команды вроде «синий свет», «сделай зелёный свет», «сделай белый свет в спальне» → включи свет (TURN_ON) и добавь SET_COLOR с rgb из color_palette_rgb. "
            "Если прозвучал цвет (\"зелёный\", \"розовый\" и т.д.) — используй intent SET_COLOR и возьми rgb из color_palette_rgb. "
            "Оттенки белого (\"тёплый/холодный/дневной\") → intent SET_COLOR_TEMP с kelvin из white_color_temps. "
            "Фразы из brightness_hints.dim_words означают ADJUST_BRIGHTNESS с отрицательной дельтой; "
            "из brighten_words — ADJUST_BRIGHTNESS с положительной дельтой; конкретные значения/проценты → SET_BRIGHTNESS. "
            "policy: минимум уточнений, используй context.last_area_name если область не указана."
        )

        user = json.dumps(
            {
                "utterance": text,
                "context": {"last_area_name": context.get("last_area_name")},
                "domain": "light",
                "allowed_intents": allowed_intents,
                "areas": areas,
                "modifier_keys": mod_keys,
                "color_keys": color_keys,
                "color_palette_rgb": color_palette,
                "white_color_temps": white_palette,
                "brightness_hints": brightness_hints,
                "rules": rules_summary,
                "output_format": "single JSON object",
            },
            ensure_ascii=False,
        )

        def _run_rule_parser() -> Dict[str, Any]:
            parsed_rule = parse_light_command_v1(
                text,
                context=context,
                device_registry=device_registry,
                area_synonyms=area_synonyms,
                colors=colors,
                modifiers=modifiers,
            )
            return _apply_context_defaults(parsed_rule, context=context)

        try:
            raw = self.client.generate_json(system=system, user=user, temperature=0.0, max_tokens=700)
        except Exception as e:
            if self.fallback_to_rules:
                return _run_rule_parser()
            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")

        json_str = _extract_first_json_object(raw)
        if json_str is None:
            if self.fallback_to_rules:
                return _run_rule_parser()
            clar = _clarification_from_freeform(raw, options=areas[:5] or None)
            if clar:
                return clar
            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")

        try:
            parsed = json.loads(json_str)
        except Exception:
            if self.fallback_to_rules:
                return _run_rule_parser()
            clar = _clarification_from_freeform(raw, options=areas[:5] or None)
            if clar:
                return clar
            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")

        parsed = _apply_context_defaults(parsed, context=context)
        parsed = _ensure_target_or_clarify(parsed, context=context, area_options=areas)
        if parsed.get("clarification", {}).get("needed"):
            return parsed

        # Final: schema check. If invalid, fallback or clarify.
        try:
            validate_with_schema(parsed, self.parsed_schema)
        except Exception:
            if self.fallback_to_rules:
                return _run_rule_parser()
            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")

        # In safe mode, if the LLM asks for clarification, try the rule parser.
        # This reduces unnecessary questions and aligns with the "minimum clarifications" policy.
        if self.fallback_to_rules and parsed.get("clarification", {}).get("needed") is True:
            rule_parsed = _run_rule_parser()
            try:
                validate_with_schema(rule_parsed, self.parsed_schema)
            except Exception:
                return parsed

            # Prefer rules if it produced a non-UNKNOWN intent and no clarification.
            if (rule_parsed.get("clarification") is None) and (rule_parsed.get("actions") or [{}])[0].get("intent") != "UNKNOWN":
                return rule_parsed

        return parsed

def parse_light_command_llm_v1(
    text: str,
    *,
    context: Dict[str, Any],
    device_registry: Dict[str, Any],
    area_synonyms: Dict[str, Any],
    colors: Dict[str, Any],
    modifiers: Dict[str, Any],
    parsed_schema: Dict[str, Any],
    client: LLMClient,
    fallback_to_rules: bool = True,
) -> Dict[str, Any]:
    """Functional wrapper (for CLI/eval)."""
    return LLMParserV1(client=client, parsed_schema=parsed_schema, fallback_to_rules=fallback_to_rules).parse(
        text,
        context=context,
        device_registry=device_registry,
        area_synonyms=area_synonyms,
        colors=colors,
        modifiers=modifiers,
    )
