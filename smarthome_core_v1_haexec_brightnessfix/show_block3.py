# -*- coding: utf-8 -*-
from pathlib import Path
text = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py').read_text(encoding='utf-8')
seg = text[text.index('            rule_parsed = '): text.index('            try:\n', text.index('            rule_parsed = '))]
print(seg)
