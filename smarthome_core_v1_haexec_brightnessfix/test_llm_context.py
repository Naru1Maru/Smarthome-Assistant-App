# -*- coding: utf-8 -*-
import json
from pathlib import Path
from smarthome_core.assets import AssetPaths
from smarthome_core.io import load_json
from smarthome_core.parser_llm import parse_light_command_llm_v1
from smarthome_core.llm_client import OpenAICompatibleClient

root = Path('.').resolve()
paths = AssetPaths(root)
assets = {
    'device_registry': load_json(paths.device_registry),
    'area_synonyms': load_json(paths.area_synonyms),
    'colors': load_json(paths.colors),
    'modifiers': load_json(paths.modifiers),
    'parsed_schema': load_json(paths.parsed_schema),
}
client = OpenAICompatibleClient(base_url='http://127.0.0.1:8080', model='qwen2.5-7b-instruct')
context = {'last_area_name': 'Спальня'}
res = parse_light_command_llm_v1(
    'сделай белый свет',
    context=context,
    device_registry=assets['device_registry'],
    area_synonyms=assets['area_synonyms'],
    colors=assets['colors'],
    modifiers=assets['modifiers'],
    parsed_schema=assets['parsed_schema'],
    client=client,
)
print(json.dumps(res, ensure_ascii=False, indent=2))
