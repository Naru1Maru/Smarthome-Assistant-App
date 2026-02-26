# -*- coding: utf-8 -*-
from pathlib import Path
import json
import smarthome_core.parser_llm as pl
from smarthome_core.assets import AssetPaths
from smarthome_core.io import load_json
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

def debug(parsed, *, context):
    print('DEBUG before apply:', parsed)
    print('context:', context)
    return parsed

pl._apply_context_defaults = debug
parser = pl.LLMParserV1(client=client, parsed_schema=assets['parsed_schema'])
res = parser.parse(
    'сделай белый свет',
    context={'last_area_name': 'Спальня'},
    device_registry=assets['device_registry'],
    area_synonyms=assets['area_synonyms'],
    colors=assets['colors'],
    modifiers=assets['modifiers'],
)
print('final', json.dumps(res, ensure_ascii=False, indent=2))
