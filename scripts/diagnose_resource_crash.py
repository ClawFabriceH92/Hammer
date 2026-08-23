#!/usr/bin/env python3
"""Bisects the merged values.xml AGP failed to compile to find the exact
resource element that trips aaptcompiler's "Can not extract resource from
ParsedResource" bug — diagnostic only, not part of the normal build."""

import glob
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

MERGED_GLOB = "app/build/intermediates/incremental/*/mergeDebugResources/merged.dir/values/values.xml"
NS_ATTRS = 'xmlns:ns1="http://schemas.android.com/tools" xmlns:ns2="urn:oasis:names:tc:xliff:document:1.2"'
SUBSET_DIR = "/tmp/diagnose_res/values"
SUBSET_XML = os.path.join(SUBSET_DIR, "values.xml")
OUT_DIR = "/tmp/diagnose_res_out"


def find_merged_file():
    matches = glob.glob(MERGED_GLOB)
    if not matches:
        print("No merged values.xml found — nothing to diagnose.")
        sys.exit(0)
    return matches[0]


def find_aapt2():
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not android_home:
        print("ANDROID_HOME/ANDROID_SDK_ROOT not set.")
        sys.exit(1)
    candidates = sorted(glob.glob(os.path.join(android_home, "build-tools", "*", "aapt2")))
    if not candidates:
        print("No aapt2 binary found under build-tools.")
        sys.exit(1)
    return candidates[-1]


def build_subset_xml(elements):
    body = "".join(ET.tostring(e, encoding="unicode") for e in elements)
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources {NS_ATTRS}>\n{body}\n</resources>\n'


def compiles_ok(aapt2, elements):
    os.makedirs(SUBSET_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(SUBSET_XML, "w", encoding="utf-8") as f:
        f.write(build_subset_xml(elements))
    result = subprocess.run(
        [aapt2, "compile", SUBSET_XML, "-o", OUT_DIR],
        capture_output=True, text=True,
    )
    return result.returncode == 0, result.stderr


def describe(element):
    return ET.tostring(element, encoding="unicode").strip().splitlines()[0][:200]


def main():
    merged_path = find_merged_file()
    aapt2 = find_aapt2()
    print(f"Merged file: {merged_path}")
    print(f"aapt2: {aapt2}")

    tree = ET.parse(merged_path)
    root = tree.getroot()
    elements = list(root)
    print(f"Total top-level resource elements: {len(elements)}")

    ok, err = compiles_ok(aapt2, elements)
    if ok:
        print("Full set compiled OK in isolation — could not reproduce the failure this way.")
        print("The bug may depend on merge-time state not captured by a standalone recompile.")
        sys.exit(0)
    print("Confirmed: full set reproduces the failure.")
    print(f"aapt2 stderr:\n{err}\n")

    current = elements
    while len(current) > 1:
        mid = len(current) // 2
        first_half, second_half = current[:mid], current[mid:]

        ok_first, _ = compiles_ok(aapt2, first_half)
        if not ok_first:
            current = first_half
            print(f"Narrowed to first half: {len(current)} elements")
            continue

        ok_second, _ = compiles_ok(aapt2, second_half)
        if not ok_second:
            current = second_half
            print(f"Narrowed to second half: {len(current)} elements")
            continue

        print("Neither half fails alone in isolation — likely an interaction between elements")
        print("split across the two halves (e.g. a duplicate name). Listing all remaining")
        print("candidates instead of continuing to bisect:")
        for e in current:
            print(f"  - {describe(e)}")
        sys.exit(0)

    print("\n=== CULPRIT RESOURCE ELEMENT ===")
    print(ET.tostring(current[0], encoding="unicode"))

    # A duplicate (type, name) pair is the single most common real-world trigger for this
    # bug — surface that explicitly if it applies to the isolated element.
    culprit = current[0]
    culprit_name = culprit.get("name")
    culprit_type = culprit.tag
    same_name = [
        e for e in elements
        if e.get("name") == culprit_name and e is not culprit
    ]
    if same_name:
        print(f"\n{len(same_name)} other element(s) share the name '{culprit_name}':")
        for e in same_name:
            print(f"  - {describe(e)}")


if __name__ == "__main__":
    main()
