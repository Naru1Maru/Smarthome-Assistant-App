# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
needle = "        # Canonical areas list (keys are normalized in area_synonyms).\n        areas = list(area_synonyms.get(\"canonical\", {}).keys())[:MAX_HINT_ITEMS]\n"
replacement = "        # Canonical areas list (keys are normalized in area_synonyms).\n        canonical_areas = area_synonyms.get(\"canonical_areas\", []) or []\n        areas = [a.get(\"name\") for a in canonical_areas if isinstance(a, dict) and a.get(\"name\")]\n        areas = areas[:MAX_HINT_ITEMS]\n"
if needle not in text:
    raise SystemExit('areas block not found')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
