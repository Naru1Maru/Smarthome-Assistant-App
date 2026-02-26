# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
text = text.replace('                return parse_light_command_v1(\n                    text,\n                    context=context,\n                    device_registry=device_registry,\n                    area_synonyms=area_synonyms,\n                    colors=colors,\n                    modifiers=modifiers,\n                )', '                return _run_rule_parser()', 3)
path.write_text(text, encoding='utf-8')
