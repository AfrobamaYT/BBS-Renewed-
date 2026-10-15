#!/usr/bin/env python3
"""Inspect the actual binary architecture, exported JNI ABI and loader dependencies."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess

JNI = "Java_com_mcstudio_nano_NanoVkNative_"
EXPORTS = {JNI + name for name in ("abiVersion", "create", "prepare", "takeRecording", "releaseRecording", "destroy")}


def output(*command):
    return subprocess.check_output(command, text=True, stderr=subprocess.STDOUT)


def inspect(path, target):
    system, arch = target.split("-")
    path = Path(path).resolve()
    if system == "linux":
        headers = output("readelf", "-h", str(path))
        if "ELF64" not in headers or not re.search("Machine:.*" + ("X86-64" if arch == "x64" else "AArch64"), headers, re.I):
            raise RuntimeError(f"{target} architecture mismatch:\n{headers}")
        exports = output("nm", "-D", "--defined-only", str(path))
        dependencies = re.findall(r"Shared library: \[([^]]+)\]", output("readelf", "-d", str(path)))
        undefined = output("nm", "-D", "--undefined-only", str(path))
        if re.search(r"\bU\s+vk[A-Z]", undefined):
            raise RuntimeError("Native library still imports Vulkan functions instead of supplied dispatch")
    elif system == "macos":
        actual = output("lipo", "-archs", str(path)).strip().split()
        if actual != ["x86_64" if arch == "x64" else "arm64"]:
            raise RuntimeError(f"{target} architecture mismatch: {actual}")
        exports = output("nm", "-gU", str(path))
        dependencies = [line.split(" (", 1)[0].strip() for line in output("otool", "-L", str(path)).splitlines()[1:]]
    elif system == "windows":
        if shutil.which("llvm-readobj"):
            headers = output("llvm-readobj", "--file-headers", str(path))
            expected = "IMAGE_FILE_MACHINE_AMD64" if arch == "x64" else "IMAGE_FILE_MACHINE_ARM64"
            if expected not in headers:
                raise RuntimeError(f"{target} architecture mismatch:\n{headers}")
            exports = output("llvm-readobj", "--coff-exports", str(path))
            dependencies = re.findall(r"Name: ([^\r\n]+\.dll)", output("llvm-readobj", "--coff-imports", str(path)), re.I)
        else:
            headers = output("dumpbin", "/headers", str(path))
            if not re.search(("8664" if arch == "x64" else "AA64") + r"\s+machine", headers, re.I):
                raise RuntimeError(f"{target} architecture mismatch:\n{headers}")
            exports = output("dumpbin", "/exports", str(path))
            dependencies = re.findall(r"^\s+([\w.-]+\.dll)\s*$", output("dumpbin", "/dependents", str(path)), re.M | re.I)
    else:
        raise RuntimeError(f"Unsupported target: {target}")
    missing = sorted(name for name in EXPORTS if name not in exports)
    if missing:
        raise RuntimeError(f"Missing JNI exports: {missing}")
    if any(re.search(r"vulkan|moltenvk", name, re.I) for name in dependencies):
        raise RuntimeError(f"Unexpected second Vulkan/MoltenVK dependency: {dependencies}")
    return {"target": target, "file": path.name, "exports": sorted(EXPORTS), "dependencies": dependencies}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--target", required=True)
    args = parser.parse_args()
    print(json.dumps(inspect(args.binary, args.target), indent=2))
