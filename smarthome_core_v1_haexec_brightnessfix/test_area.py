# -*- coding: utf-8 -*-
from pathlib import Path
from smarthome_core.assets import AssetPaths
from smarthome_core.io import load_json
from smarthome_core.executor_ha import _resolve_area_entities

root = Path('.').resolve()
paths = AssetPaths(root)
reg = load_json(paths.device_registry)
print(_resolve_area_entities('Спальня', reg))
