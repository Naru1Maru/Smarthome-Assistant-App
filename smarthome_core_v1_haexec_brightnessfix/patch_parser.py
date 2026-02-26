# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
if '_apply_context_defaults' in text:
    raise SystemExit('already patched')
marker = '    return {\n        "schema_version": "1.0",\n        "actions": [_unknown_action()],\n        "clarification": {\n            "needed": True,\n            "question": question,\n            "options": opts[:20],\n        },\n    }\n\n\n'
insert = """def _apply_context_defaults(parsed: Dict[str, Any], *, context: Dict[str, Any]) -> Dict[str, Any]:\n    \"\"\"Inject last_area_name defaults when target scope is unspecified.\n\n    If пользователь не указал новую комнату, используем context.last_area_name.\n    \"\"\"\n\n    last_area = context.get('last_area_name')\n    if not last_area:\n        return parsed\n\n    actions = parsed.get('actions')\n    if not isinstance(actions, list):\n        return parsed\n\n    for action in actions:\n        if not isinstance(action, dict):\n            continue\n        target = action.setdefault('target', {})\n        scope = target.get('scope') or 'UNSPECIFIED'\n        ent_ids = target.get('entity_ids') or []\n        area_name = target.get('area_name')\n\n        if ent_ids:\n            continue\n\n        if scope in {'UNSPECIFIED', 'AREA'} and not area_name:\n            target['scope'] = 'AREA'\n            target['area_name'] = last_area\n\n    return parsed\n\n\n"""
if marker not in text:
    raise SystemExit('marker not found')
text = text.replace(marker, marker + insert, 1)
path.write_text(text, encoding='utf-8')
