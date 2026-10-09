"""
This script will initialize everything!
"""

import re
import os
import json
import typing as t

with open(os.path.join("development", "ins-original.json"), "r", encoding="utf-8") as _file:
    data: t.Dict = json.load(_file)

# for ./development dir
# ---------------------
for key, value in data.items():
    clean_key = re.sub(r"[()]", "", key)
    filename = f"E{clean_key}.json"
    with open(os.path.join("development", "original-split-single", filename), "w", encoding="utf-8",) as _file:
        if value:
            json.dump(value, _file, indent=4)

# for ./dist dir
# --------------
for key, value in data.items():
    clean_key = re.sub(r"[()]", "", key)
    filename = f"E{clean_key}.json"
    current_file = os.path.join("dist", "single", filename)
    if not os.path.isfile(current_file):
        with open(current_file, "w", encoding="utf-8",) as _file:
            if value:
                json.dump(value, _file, indent=4)
