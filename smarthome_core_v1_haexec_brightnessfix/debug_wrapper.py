# -*- coding: utf-8 -*-
from pathlib import Path
import json
import importlib
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
orig = pl._apply_context_defaults

def wrapper(parsed, *, context):
    res = orig(parsed, context=context)
    print('WRAP context', context)
    print('WRAP result', json.dumps(res, ensure_ascii=False))
    return res

pl._apply_context_defaults = wrapper
parser = pl.LLMParserV1(client=client, parsed_schema=assets['parsed_schema'])
res = parser.parse(
    'сделай белый свет',
    context={'last_area_name': 'Спальня'},
    device_registry=assets['device_registry'],
    area_synonyms=assets['area_synonyms'],
    colors=assets['colors'],
    modifiers=assets['modifiers'],
)
print('FINAL', json.dumps(res, ensure_ascii=False))
