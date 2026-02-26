# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
text = text.replace('            if self.fallback_to_rules:\n                return parse_light_command_v1(\n                    text,\n                    context=context,\n                    device_registry=device_registry,\n                    area_synonyms=area_synonyms,\n                    colors=colors,\n                    modifiers=modifiers,\n                )\n            return _parsed_clarification(question="Я не смог понять команду. Скажи иначе.")\n', '            if self.fallback_to_rules:\n                return _run_rule_parser()\n            return _parsed_clарification(question="Я не смог понять команду. Скажи иначе.")\n', 2)
if text.count('_run_rule_parser()') < 4:
    pass
path.write_text(text, encoding='utf-8')
