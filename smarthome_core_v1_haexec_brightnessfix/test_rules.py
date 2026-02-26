# -*- coding: utf-8 -*-
import json
from pathlib import Path
from smarthome_core.assets import AssetPaths
from smarthome_core.io import load_json
from smarthome_core.parser import parse_light_command_v1

root = Path('.').resolve()
paths = AssetPaths(root)
assets = {
    'device_registry': load_json(paths.device_registry),
    'area_synonyms': load_json(paths.area_synonyms),
    'colors': load_json(paths.colors),
    'modifiers': load_json(paths.modifiers),
    'parsed_schema': load_json(paths.parsed_schema),
}
context = {'last_area_name': None}
res = parse_light_command_v1(
    'сделай белый свет',
    context=context,
    device_registry=assets['device_registry'],
    area_synonyms=assets['area_synonyms'],
    colors=assets['colors'],
    modifiers=assets['modifiers'],
)
print(json.dumps(res, ensure_ascii=False, indent=2))
