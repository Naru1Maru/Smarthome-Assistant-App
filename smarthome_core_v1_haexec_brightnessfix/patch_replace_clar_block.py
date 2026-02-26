# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
needle = "        if self.fallback_to_rules and parsed.get(\"clarification\", {}).get(\"needed\") is True:\n            rule_parsed = parse_light_command_v1(\n                text,\n                context=context,\n                device_registry=device_registry,\n                area_synonyms=area_synonyms,\n                colors=colors,\n                modifiers=modifiers,\n            )\n            rule_parsed = _apply_context_defaults(rule_parsed, context=context)\n            try:\n"
replacement = "        if self.fallback_to_rules and parsed.get(\"clarification\", {}).get(\"needed\") is True:\n            rule_parsed = _run_rule_parser()\n            try:\n"
if needle not in text:
    raise SystemExit('clarification block not found')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
