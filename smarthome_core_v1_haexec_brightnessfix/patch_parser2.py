# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
needle = '        try:\n            parsed = json.loads(json_str)\n        except Exception:\n            if self.fallback_to_rules:\n                return parse_light_command_v1(\n                    text,\n                    context=context,\n                    device_registry=device_registry,\n                    area_synonyms=area_synonyms,\n                    colors=colors,\n                    modifiers=modifiers,\n                )\n            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")\n\n        # Final: schema check. If invalid, fallback or clarify.\n        try:\n'
replacement = "        try:\n            parsed = json.loads(json_str)\n        except Exception:\n            if self.fallback_to_rules:\n                return parse_light_command_v1(\n                    text,\n                    context=context,\n                    device_registry=device_registry,\n                    area_synonyms=area_synonyms,\n                    colors=colors,\n                    modifiers=modifiers,\n                )\n            return _parsed_clarification(question=\"Я не смог понять команду. Скажи иначе.\")\n\n        parsed = _apply_context_defaults(parsed, context=context)\n\n        # Final: schema check. If invalid, fallback or clarify.\n        try:\n"
if needle not in text:
    raise SystemExit('needle not found')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
