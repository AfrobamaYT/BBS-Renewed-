#!/usr/bin/env python3
"""Build a verified JNI binary using Vulkan headers and Minecraft's runtime dispatch."""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import shlex
import shutil
import subprocess

SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parent.parent
TARGETS = {
    "linux-x64": ("linux", "x64", "libbbs_nanovg_vk.so"),
    "linux-arm64": ("linux", "arm64", "libbbs_nanovg_vk.so"),
    "windows-x64": ("windows", "x64", "bbs_nanovg_vk.dll"),
    "windows-arm64": ("windows", "arm64", "bbs_nanovg_vk.dll"),
    "macos-x64": ("macos", "x64", "libbbs_nanovg_vk.dylib"),
    "macos-arm64": ("macos", "arm64", "libbbs_nanovg_vk.dylib"),
}


def host_target():
    system = {"linux": "linux", "darwin": "macos", "windows": "windows"}.get(platform.system().lower())
    arch = {"x86_64": "x64", "amd64": "x64", "aarch64": "arm64", "arm64": "arm64"}.get(platform.machine().lower())
    return f"{system}-{arch}"


def source_digest(paths, root):
    digest = hashlib.sha256()
    for path in paths:
        if path.is_file():
            digest.update(path.relative_to(root).as_posix().encode("utf-8") + b"\0"
                          + path.read_text(encoding="utf-8").encode("utf-8"))
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=TARGETS, default=host_target())
    parser.add_argument("--cc", default=os.environ.get("CC"), help="Exact compiler executable")
    parser.add_argument("--jdk", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--jni-platform-headers", type=Path, help="Target JNI OS headers for an explicit cross build")
    parser.add_argument("--vulkan-headers", type=Path, help="Vulkan SDK or pinned Vulkan-Headers include directory")
    parser.add_argument("--output", type=Path, help="Exact output library path")
    parser.add_argument("--manifest", type=Path, help="Write verified build metadata")
    args = parser.parse_args()
    if args.target not in TARGETS:
        parser.error(f"Unsupported host target: {args.target}")
    system, arch, name = TARGETS[args.target]
    cc = args.cc or ("cl" if platform.system() == "Windows" else "cc")
    compiler = shutil.which(cc)
    if compiler is None:
        parser.error(f"Compiler not found: {cc}")
    javac = shutil.which("javac")
    if args.jdk is None and javac is None:
        parser.error("A JDK with JNI headers is required")
    jdk = Path(args.jdk) if args.jdk else Path(javac).resolve().parent.parent
    include = jdk / "include"
    jni_os = args.jni_platform_headers or include / {"linux": "linux", "windows": "win32", "macos": "darwin"}[system]
    sdk = args.vulkan_headers
    if sdk is None and os.environ.get("VULKAN_SDK"):
        sdk = Path(os.environ["VULKAN_SDK"]) / "Include"
        if not sdk.exists():
            sdk = Path(os.environ["VULKAN_SDK"]) / "include"
    sdk_flags = [f"-I{sdk}"] if sdk else shlex.split(subprocess.check_output(["pkg-config", "--cflags", "vulkan"], text=True))
    if not (include / "jni.h").is_file() or not (jni_os / "jni_md.h").is_file():
        parser.error(f"Target JNI headers missing: {include}, {jni_os}")
    if sdk and not (sdk / "vulkan/vulkan.h").is_file():
        parser.error(f"Vulkan headers missing: {sdk}")
    output = (args.output or ROOT / "src/client/resources/assets/bbs/natives" / args.target / name).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    source = SOURCE / "bbs_nanovg_vk.c"
    msvc = Path(compiler).name.lower() in ("cl", "cl.exe", "clang-cl", "clang-cl.exe")
    if msvc:
        command = [compiler, "/nologo", "/std:c11", "/O2", "/W4", "/LD", "/MT",
                   "/wd4100", f"/I{include}", f"/I{jni_os}"]
        command.extend("/I" + flag[2:] for flag in sdk_flags if flag.startswith("-I"))
        if any(not flag.startswith("-I") for flag in sdk_flags):
            parser.error("MSVC accepts explicit Vulkan header include paths, not pkg-config compiler flags")
        if Path(compiler).name.lower().startswith("clang-cl"):
            command.append("/clang:--target=" + ("aarch64" if arch == "arm64" else "x86_64") + "-pc-windows-msvc")
        command.extend([str(source), f"/Fo{output.with_suffix('.obj')}", "/link",
                        "/MACHINE:" + ("ARM64" if arch == "arm64" else "X64"),
                        f"/OUT:{output}", f"/IMPLIB:{output.with_suffix('.lib')}"])
    else:
        command = [compiler, "-std=c11", "-O2", "-fvisibility=hidden", "-Wall", "-Wextra",
                   "-Wno-unused-function", "-Wno-unused-parameter", "-Wno-missing-field-initializers",
                   f"-I{include}", f"-I{jni_os}", *sdk_flags, str(source), "-o", str(output)]
        if system == "macos":
            command.extend(["-dynamiclib", "-arch", "arm64" if arch == "arm64" else "x86_64",
                            "-mmacosx-version-min=11.0", "-Wl,-install_name,@rpath/" + name])
        else:
            command.append("-shared")
            if system == "linux":
                command.extend(["-fPIC", "-Wl,-z,defs"])
            else:
                command.append("-static-libgcc")
        command.append("-lm")
    print(shlex.join(command), flush=True)
    subprocess.run(command, check=True)
    if system == "macos":
        subprocess.run(["codesign", "--force", "--sign", "-", str(output)], check=True)
    from verify_binary import inspect
    verified = inspect(output, args.target)
    verified["sha256"] = hashlib.sha256(output.read_bytes()).hexdigest()
    verified["abi_version"] = 2
    verified["source_commit"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    source_paths = [SOURCE / "bbs_nanovg_vk.c", SOURCE / "vulkan_dispatch.h"]
    source_paths.extend(sorted((SOURCE / "upstream").rglob("*")))
    source_paths.extend(sorted((ROOT / "src/client/java/com/mcstudio/nano").glob("NanoVk*.java")))
    verified["source_sha256"] = source_digest(source_paths, ROOT)
    verified["source_tree_dirty"] = bool(subprocess.check_output(
        ["git", "status", "--porcelain", "--", "native/nanovg-vulkan",
         "src/client/java/com/mcstudio/nano"], cwd=ROOT, text=True).strip())
    verified["gpu_verified"] = False
    if args.manifest:
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(json.dumps(verified, indent=2) + "\n")
    print(json.dumps(verified, indent=2))


if __name__ == "__main__":
    main()
