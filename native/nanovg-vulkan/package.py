#!/usr/bin/env python3
"""Package all six verified native classifiers together, or fail loudly."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
from build import TARGETS, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    records = []
    for target, (_, _, filename) in TARGETS.items():
        directory = args.input / target
        binary = directory / filename
        manifest = json.loads((directory / "manifest.json").read_text())
        probe = json.loads((directory / "probe.json").read_text())
        if manifest["target"] != target or manifest["file"] != filename or manifest["abi_version"] != 2:
            raise RuntimeError(f"Native classifier/ABI mismatch: {target}")
        if manifest["source_tree_dirty"]:
            raise RuntimeError(f"Native source was not frozen: {target}")
        if hashlib.sha256(binary.read_bytes()).hexdigest() != manifest["sha256"]:
            raise RuntimeError(f"Native binary checksum mismatch: {target}")
        if probe["target"] != target or not probe["jni_loaded"] or probe["native_abi"] != 2 or probe["lwjgl"] != "3.4.3":
            raise RuntimeError(f"Missing actual JNI/LWJGL check: {target}")
        records.append(manifest)
    if len({item["source_commit"] for item in records}) != 1 or len({item["source_sha256"] for item in records}) != 1:
        raise RuntimeError("Native classifiers were built from different source states")
    for record in records:
        destination = args.output / "assets/bbs/natives" / record["target"]
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copy2(args.input / record["target"] / record["file"], destination / record["file"])
    license_target = args.output / "META-INF/licenses/NANOVG-VULKAN.txt"
    license_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "src/client/resources/META-INF/licenses/NANOVG-VULKAN.txt", license_target)
    (args.output / "native-manifest.json").write_text(json.dumps(records, indent=2) + "\n")
    print("All six native ABI classifiers packaged; GPU rendering acceptance remains separate.")


if __name__ == "__main__":
    main()
