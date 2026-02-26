# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
needle = '        try:\n            raw = self.client.generate_json(system=system, user=user, temperature=0.0, max_tokens=700)\n        except Exception as e:\n            if self.fallback_to_rules:\n                return parse_light_command_v1(\n                    text,\n                    context=context,\n                    device_registry=device_registry,\n                    area_synonyms=area_synonyms,\n                    colors=colors,\n                    modifiers=modifiers,\n                )\n            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")\n'
replacement = '        def _run_rule_parser() -> Dict[str, Any]:\n            parsed_rule = parse_light_command_v1(\n                text,\n                context=context,\n                device_registry=device_registry,\n                area_synonyms=area_synonyms,\n                colors=colors,\n                modifiers=modifiers,\n            )\n            return _apply_context_defaults(parsed_rule, context=context)\n\n        try:\n            raw = self.client.generate_json(system=system, user=user, temperature=0.0, max_tokens=700)\n        except Exception as e:\n            if self.fallback_to_rules:\n                return _run_rule_parser()\n            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")\n'
if needle not in text:
    raise SystemExit('needle for helper not found')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
