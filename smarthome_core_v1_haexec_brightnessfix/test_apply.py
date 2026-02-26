# -*- coding: utf-8 -*-
import json
from pathlib import Path
from smarthome_core.parser_llm import _apply_context_defaults

parsed = {
    'actions': [
        {
            'target': {'scope': 'UNSPECIFIED', 'area_name': None, 'entity_ids': []}
        }
    ]
}
context = {'last_area_name': 'Спальня'}
print(json.dumps(_apply_context_defaults(parsed, context=context), ensure_ascii=False, indent=2))
