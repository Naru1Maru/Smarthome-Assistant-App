# -*- coding: utf-8 -*-
from pathlib import Path
path = Path(r'C:\\Users\\narumaru\\workspace\\Diplom\\smarthome_core_v1_haexec_brightnessfix\\smarthome_core\\parser_llm.py')
text = path.read_text(encoding='utf-8')
insert = """

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
"""
marker = '\n\n@dataclass'
if marker not in text:
    raise SystemExit('marker not found for dataclass block')
if '_ensure_target_or_clarify' in text:
    raise SystemExit('helper already exists')
text = text.replace(marker, insert + marker, 1)
path.write_text(text, encoding='utf-8')
