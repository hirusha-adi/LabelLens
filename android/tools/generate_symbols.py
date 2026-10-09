"""Rebuilds the Material Symbols subset font, SymbolGlyphs.kt and ShapePoints.kt.

Needs fonttools (`pip install fonttools`). Arguments:
  1. a folder holding symbols.ttf and symbols.codepoints, the Material Symbols Rounded
     variable font and its codepoints file from github.com/google/material-design-icons
     (variablefont/), saved under those two names
  2. the unzipped design handoff's `project` folder
  3. app/src/main/res/font
  4. app/src/main/kotlin/app/labellens/ui/theme

Icons used in the design files are picked up automatically. Add any others to the
list of extra names below.
"""
import json, re, sys, glob, os
from fontTools import subset
fonts, design, out_res, out_kt = sys.argv[1:5]
names = set()
for f in glob.glob(os.path.join(design, "LL*.html")):
    for m in re.finditer(r"Material Symbols Rounded'[^>]*>([a-z_0-9 ]*)<", open(f, encoding="utf-8").read()):
        names.update(m.group(1).split())
names.update("""mail key person_remove public edit delete image flash_on bookmark_remove keyboard_arrow_down
group schedule star language bubble_chart grain spa cookie labs icecream bakery_dining oil_barrel
arrow_drop_down check_box radio_button_checked cloud_done wifi_off photo sync""".split())
cp = dict(l.split() for l in open(os.path.join(fonts, "symbols.codepoints")) if l.strip())
missing = sorted(n for n in names if n not in cp)
print("missing:", missing)
names = sorted(n for n in names if n in cp)
opts = subset.Options()
opts.layout_features = []
opts.notdef_outline = True
font = subset.load_font(os.path.join(fonts, "symbols.ttf"), opts)
s = subset.Subsetter(opts)
s.populate(unicodes=[int(cp[n], 16) for n in names])
s.subset(font)
subset.save_font(font, os.path.join(out_res, "material_symbols_rounded.ttf"), opts)
def esc(h):
    c = int(h, 16)
    if c > 0xFFFF:
        c -= 0x10000
        return "\\u%04X\\u%04X" % (0xD800 + (c >> 10), 0xDC00 + (c & 0x3FF))
    return "\\u%04X" % c
kt = ["// Generated from the Material Symbols Rounded codepoints file. Do not edit by hand.",
      "package app.labellens.ui.theme", "",
      "/** Icon name to glyph for the bundled Material Symbols Rounded subset. */",
      "internal val SymbolGlyphs: Map<String, String> = mapOf("]
kt += ['    "%s" to "%s",' % (n, esc(cp[n])) for n in names]
kt += [")", ""]
open(os.path.join(out_kt, "SymbolGlyphs.kt"), "w").write("\n".join(kt))
shapes = json.load(open(os.path.join(design, "assets", "_shapes.json")))
kt = ["// Generated from the design tokens (assets/_shapes.json). Do not edit by hand.",
      "package app.labellens.ui.theme", "",
      "/** Outline points for the Expressive shapes, as x,y pairs in the 0..100 range. */",
      "internal object ShapePoints {"]
for k, v in shapes.items():
    pts = re.findall(r"([\d.]+)% ([\d.]+)%", v["poly"])
    flat = ", ".join("%sf, %sf" % p for p in pts)
    kt.append("    val %s = floatArrayOf(%s)" % (k, flat))
kt += ["}", ""]
open(os.path.join(out_kt, "ShapePoints.kt"), "w").write("\n".join(kt))
print(len(names), "icons")
