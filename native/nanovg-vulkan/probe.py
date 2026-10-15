#!/usr/bin/env python3
"""Compile the real three NanoVk classes and check native loading with LWJGL 3.4.3."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request

SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parent.parent
MC = "26.3"
LWJGL = "3.4.3"


def json_url(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def fetch(url, target, sha1):
    if target.exists() and hashlib.sha1(target.read_bytes()).hexdigest() == sha1:
        return target
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    if hashlib.sha1(data).hexdigest() != sha1:
        raise RuntimeError(f"Official artifact checksum mismatch: {url}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def maven(artifact, classifier, cache):
    filename = f"{artifact}-{LWJGL}" + (f"-{classifier}" if classifier else "") + ".jar"
    url = f"https://repo.maven.apache.org/maven2/org/lwjgl/{artifact}/{LWJGL}/{filename}"
    with urllib.request.urlopen(url + ".sha1", timeout=60) as response:
        checksum = response.read().decode().strip().split()[0]
    return fetch(url, cache / filename, checksum)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True)
    parser.add_argument("--library", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--classpath", help="Use an existing real Minecraft/LWJGL classpath without downloads")
    args = parser.parse_args()
    cache = ROOT / "build/nano-probe/dependencies"
    classes = ROOT / "build/nano-probe/classes"
    classes.mkdir(parents=True, exist_ok=True)
    if args.classpath:
        classpath = args.classpath
    else:
        manifest = json_url("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        version = next(item for item in manifest["versions"] if item["id"] == MC)
        metadata = json_url(version["url"])
        client = metadata["downloads"]["client"]
        jars = [fetch(client["url"], cache / f"minecraft-{MC}.jar", client["sha1"])]
        for library in metadata["libraries"]:
            if library["name"].startswith(("org.joml:joml:", "org.slf4j:slf4j-api:", "org.jetbrains:annotations:")):
                artifact = library["downloads"]["artifact"]
                jars.append(fetch(artifact["url"], cache / Path(artifact["path"]).name, artifact["sha1"]))
        native = {"linux-x64": "natives-linux", "linux-arm64": "natives-linux-arm64",
                  "windows-x64": "natives-windows", "windows-arm64": "natives-windows-arm64",
                  "macos-x64": "natives-macos", "macos-arm64": "natives-macos-arm64"}[args.target]
        for artifact in ("lwjgl", "lwjgl-nanovg", "lwjgl-vulkan"):
            jars.append(maven(artifact, None, cache))
            if artifact != "lwjgl-vulkan" or args.target.startswith("macos"):
                jars.append(maven(artifact, native, cache))
        classpath = os.pathsep.join(map(str, jars))
    sources = sorted((ROOT / "src/client/java/com/mcstudio/nano").glob("NanoVk*.java"))
    sources.append(SOURCE / "tests/NativeLoaderProbe.java")
    subprocess.run(["javac", "--release", "25", "-proc:none", "-classpath", classpath,
                    "-d", str(classes), *map(str, sources)], check=True)
    result = subprocess.run(["java", "--enable-native-access=ALL-UNNAMED", "-cp",
                             str(classes) + os.pathsep + classpath,
                             "dev.bbs.nanovg.probe.NativeLoaderProbe", str(args.library.resolve())],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, end="")
    if result.returncode != 0 or "NATIVE_ABI_PASS" not in result.stdout:
        raise RuntimeError(f"Native ABI probe failed (exit {result.returncode})")
    report = {"target": args.target, "native_abi": 2, "lwjgl": LWJGL,
              "minecraft_api": MC, "jni_loaded": True, "gpu_created": False,
              "vulkan_provider_available": "LWJGL_VULKAN_PROVIDER " in result.stdout,
              "output": result.stdout}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
