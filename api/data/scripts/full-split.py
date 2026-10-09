"""
The script should be run from the directory where 'dist/' is located as follows:

    python3 scripts/full-split.py
    
This script processes JSON data from 'dist/ins-full.json' and creates a separate
file for each item in the JSON list, storing them inside the 'dist/single/' directory.
The filename for each item is based on the 'code' field of the item.

Example of input item:
[
    {
        "code": "E100",
        "names": "Curcumin (from turmeric)",
        "display_name": "E100 (Curcumin)",
        "type": "color (Yellow-orange)",
        "status": "e u",
        "icon": "mdi-check-circle",
        "more_info": { ... }
    },
    ...
]

This will create a file named 'E100.json' inside the 'dist/single/' directory.

Example of output file 'E100.json':
{
    "code": "E100",
    "names": "Curcumin (from turmeric)",
    "display_name": "E100 (Curcumin)",
    "type": "color (Yellow-orange)",
    "status": "e u",
    "icon": "mdi-check-circle",
    "more_info": { ... }
}
"""

import json, os

input_path = os.path.join("dist", "ins-full.json")
output_dir = os.path.join("dist", "single")
os.makedirs(output_dir, exist_ok=True)

# load input json file
with open(input_path, "r", encoding="utf-8") as file:
    data = json.load(file)

# save each obj
for item in data:
    output_file = os.path.join(output_dir, f"{item['code']}.json")
    with open(output_file, "w", encoding="utf-8") as outfile:
        json.dump(item, outfile, indent=4)
