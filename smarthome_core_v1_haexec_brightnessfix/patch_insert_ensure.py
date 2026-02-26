# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
needle = "        parsed = _apply_context_defaults(parsed, context=context)\n\n        # Final: schema check. If invalid, fallback or clarify.\n        try:\n"
replacement = "        parsed = _apply_context_defaults(parsed, context=context)\n        parsed = _ensure_target_or_clarify(parsed, context=context, area_options=areas)\n        if parsed.get(\"clarification\", {}).get(\"needed\"):\n            return parsed\n\n        # Final: schema check. If invalid, fallback or clarify.\n        try:\n"
if needle not in text:
    raise SystemExit('block not found for target ensure')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
