# Native build and loader matrix

The adapter is built for every previous LWJGL native classifier:

| Classifier | Native toolchain | Hosted build runner | Resource filename |
| --- | --- | --- | --- |
| linux-x64 | GNU C or Clang, JDK JNI headers | ubuntu-22.04 | libbbs_nanovg_vk.so |
| linux-arm64 | Native GNU C or Clang, ARM64 JDK | ubuntu-22.04-arm | libbbs_nanovg_vk.so |
| windows-x64 | Visual Studio environment and clang-cl, x64 JDK | windows-2025 | bbs_nanovg_vk.dll |
| windows-arm64 | Visual Studio ARM64 environment and clang-cl, ARM64 JDK | windows-11-arm | bbs_nanovg_vk.dll |
| macos-x64 | Apple Clang/Xcode, Intel JDK | macos-15-intel | libbbs_nanovg_vk.dylib |
| macos-arm64 | Apple Clang/Xcode, ARM64 JDK | macos-15 | libbbs_nanovg_vk.dylib |

All resources live under `assets/bbs/natives/<classifier>/`. The loader checks
native ABI version 2 before creating a context, so an older library cannot silently
interpret the changed instance/device/procedure-address arguments.

The workflow only uploads review artifacts; it does not create a public release.
Its actions are commit-pinned. Headers are checked out from the official
[Khronos Vulkan-Headers](https://github.com/KhronosGroup/Vulkan-Headers) repository
at `6802bb4733b63ed5efd3adb308a6c885ef180ea1` (SDK 1.4.363.0). JDK 25 comes from
the official GitHub setup-java action: Adoptium on five targets and
[Microsoft OpenJDK on Windows ARM64](https://learn.microsoft.com/en-us/java/openjdk/download).
The first actual CI run found no Temurin 25 Windows ARM64 distribution; that
runner therefore uses Microsoft's published ARM64 JDK 25. No third-party SDK setup action
or independent MoltenVK binary is used.

The runner labels are from [GitHub's supported runner matrix](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
Windows build environments use [Microsoft's installed developer command script](https://learn.microsoft.com/en-us/cpp/build/building-on-the-command-line).

## Shared Vulkan/MoltenVK runtime

`VK_NO_PROTOTYPES` prevents static Vulkan imports. The exact
`vkGetInstanceProcAddr` pointer comes from `VK.getFunctionProvider()`;
`vkGetDeviceProcAddr` is queried through Minecraft's existing VkInstance. Every
required instance/device entry point is checked for nonzero before resource creation.
The table belongs to its bridge and is selected again for delayed destruction,
so a second context does not overwrite the first device's dispatch.

This follows the [Khronos instance/device dispatch contract](https://github.com/KhronosGroup/Vulkan-Loader/blob/main/docs/LoaderInterfaceArchitecture.md).
Device procedure addresses only belong to the queried device. On macOS the
same mechanism uses Minecraft/LWJGL's already loaded MoltenVK, preserving its
handles and implementation; the adapter does not load an SDK MoltenVK.
[MoltenVK also documents function-pointer use when static symbols are hidden](https://github.com/KhronosGroup/MoltenVK/blob/main/README.md#hiding-vulkan-api-symbols).

Compiler/binary checks:

- Native target machine is inspected with readelf, LLVM/dumpbin or lipo.
- All six JNI exports, including the version handshake, must exist.
- Imported Vulkan/MoltenVK libraries and undefined vk functions are rejected.
- macOS libraries are ad-hoc signed after linking. Windows uses its own static C
  runtime; LWJGL still owns and frees the original NanoVG context.
- Each manifest stores binary checksum, exact commit, normalized source digest,
  and whether the source tree was dirty.
- A fresh JVM compiles the actual three adapter classes against Minecraft 26.3,
  then loads the target native library and real LWJGL 3.4.3 natives.
- Packaging fails if any classifier, JNI proof, checksum, ABI, frozen source or
  matching source digest is missing.

## Evidence boundary

Local Linux x64 ABI 2 build and JNI probe passed with real LWJGL 3.4.3. The existing
provider was `libvulkan.so.1`; the adapter's dependency list contains no Vulkan
library. No VkInstance, VkDevice or rendered frame was created by this probe.

Windows, ARM64 and macOS builds are unverified until their actual workflow jobs
finish. A successful build or JNI load remains separate from drawing correctness.
GPU admission still requires several Nano surfaces, pending submissions, atlas
updates, antialiasing/stencil/text/icon parity, resize, reload and shutdown on the
relevant platform. A hosted runner lacking a Vulkan driver reports its unavailable
provider; that is not a Vulkan runtime pass.

Stencil format is selected from the real device's supported eight-bit stencil
formats. MoltenVK feature, image format and GPU differences remain runtime tests.
The adapter never creates a second device or supplies a CPU-rendering fallback.
