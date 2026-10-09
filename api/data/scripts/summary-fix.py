"""
The script should be run from the parent directory as follows:

    python3 scripts/summary-fix.py
    
This script processes JSON data from an original file and modifies its structure
by concatenating certain fields, removing unnecessary fields, and saving the modified
data to a new file.

The data is taken from the 'food-e' project, specifically from the assets section 
available at: https://github.com/nodef/food-e/tree/master/assets. The script reads 
the JSON data from 'ins-summary-original.json', processes it, and writes the output 
to 'ins-summary.json'.

The modifications made to each item in the JSON data are:
1. Concatenates the 'code' and 'names' fields to create a new 'name' field.
2. Removes the 'names', 'type', and 'status' fields from each item.

Example of input item:
{
    "code": "E100",
    "names": "Curcumin (from turmeric)",
    "type": "color (Yellow-orange)",
    "status": "e u"
}

Example of output item:
{
    "code": "E100",
    "name": "E100 - Curcumin (from turmeric)"
}
"""

import json, os

with open(
    os.path.join("development", "ins-summary-original.json"), "r", encoding="utf-8"
) as file:
    data = json.load(file)

for item in data:
    item["name"] = f"{item['code']} - {item['names']}"
    del item["names"]
    del item["type"]
    del item["status"]

with open(os.path.join("dist", "ins-summary.json"), "w") as file:
    json.dump(data, file, indent=4)
