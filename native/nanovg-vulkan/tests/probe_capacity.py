#!/usr/bin/env python3
"""Invoke the actual C buffer path with private Vulkan recording functions; no GPU or Java runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

SOURCE = Path(__file__).resolve().parents[1]
ROOT = SOURCE.parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--baseline-ref", help="Use this actual Git upstream C source as the red counterprobe")
    args = parser.parse_args()
    original = SOURCE / "upstream/nanovg_vk.c"
    data = original.read_bytes() if not args.baseline_ref else subprocess.check_output(
        ["git", "show", args.baseline_ref + ":native/nanovg-vulkan/upstream/nanovg_vk.c"], cwd=ROOT)
    digest = hashlib.sha256(data).hexdigest()
    evidence = ROOT / "build/test-evidence/nano-capacity" / digest
    source = evidence / "source"
    shutil.copytree(SOURCE, source, dirs_exist_ok=True, ignore=shutil.ignore_patterns("__pycache__"))
    (source / "upstream/nanovg_vk.c").write_bytes(data)
    flags = shlex.split(subprocess.check_output(["pkg-config", "--cflags", "vulkan"], text=True))
    command = ["cc", "-std=c11", "-O2", "-Wall", "-Wextra", "-Wno-unused-function", "-Wno-unused-parameter",
               "-Wno-missing-field-initializers", "-I" + str(args.jdk / "include"),
               "-I" + str(args.jdk / "include/linux"), *flags, str(source / "tests/capacity_probe.c"),
               "-o", str(evidence / "capacity-probe"), "-lm"]
    compiled = subprocess.run(command, capture_output=True, text=True)
    (evidence / "compile.log").write_text(compiled.stdout + compiled.stderr)
    if compiled.returncode:
        print(compiled.stdout + compiled.stderr, end=""); return compiled.returncode
    result = subprocess.run([str(evidence / "capacity-probe")], capture_output=True, text=True)
    (evidence / "probe.log").write_text(result.stdout + result.stderr)
    (evidence / "manifest.json").write_text(json.dumps({"source_sha256": digest,
        "baseline_ref": args.baseline_ref, "command": command, "exit_code": result.returncode,
        "gpu_created": False}, indent=2) + "\n")
    print(result.stdout + result.stderr, end="")
    print("evidence=" + str(evidence))
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
