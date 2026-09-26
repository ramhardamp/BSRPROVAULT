#!/usr/bin/env python3
"""Static Android resource-reference verifier used before Gradle build."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java"
RES = ROOT / "app" / "src" / "main" / "res"

def files(exts, root):
    return [p for p in root.rglob("*") if p.is_file() and p.suffix in exts]

xml_files = files({".xml"}, RES)
kotlin_files = files({".kt", ".java"}, JAVA)

xml_text = {p: p.read_text(encoding="utf-8", errors="ignore") for p in xml_files}
kotlin_text = {p: p.read_text(encoding="utf-8", errors="ignore") for p in kotlin_files}

declared_ids = set()
for p, text in xml_text.items():
    for m in re.finditer(r'@(?:\+)?id/([A-Za-z_][A-Za-z0-9_]*)', text):
        declared_ids.add(m.group(1))

refs = {}
for p, text in kotlin_text.items():
    for m in re.finditer(r'(?<!android\.)\bR\.id\.([A-Za-z_][A-Za-z0-9_]*)', text):
        refs.setdefault(m.group(1), []).append(p)

missing = []
for rid, paths in sorted(refs.items()):
    if rid not in declared_ids:
        missing.append((rid, paths))

duplicate_ids = {}
for p, text in xml_text.items():
    ids = re.findall(r'@\+id/([A-Za-z_][A-Za-z0-9_]*)', text)
    seen = set()
    for rid in ids:
        if rid in seen:
            duplicate_ids.setdefault(rid, []).append(str(p.relative_to(ROOT)))
        seen.add(rid)

print("Android resource reference verification")
print("XML files:", len(xml_files), "Kotlin/Java files:", len(kotlin_files))
print("R.id references:", len(refs), "declared IDs:", len(declared_ids))

if missing:
    print("\nMISSING R.id DECLARATIONS:")
    for rid, paths in missing:
        print(" -", rid, "referenced by", ", ".join(str(p.relative_to(ROOT)) for p in paths))
    sys.exit(1)

if duplicate_ids:
    print("\nDUPLICATE @+id DECLARATIONS:")
    for rid, paths in duplicate_ids.items():
        print(" -", rid, "declared in", ", ".join(paths))
    sys.exit(1)

print("PASS: every Kotlin/Java R.id reference has an XML id declaration.")
print("PASS: no duplicate @+id declarations were found.")
