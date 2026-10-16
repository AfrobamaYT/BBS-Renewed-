# NanoVG Vulkan verification, 2026-10-10

Base: BBS `afe496ff5b50ce5370959b3d908063e5464f3148`.
Worktree: `/home/afrobama/Work/bbs-26.3-nanovg-20261010`.

**Native library built. Actual Vulkan NanoUI drawing has not been verified.**
The six-platform extension uses native ABI 2 and LWJGL 3.4.3; its current status
and evidence boundary are described in `PLATFORMS.md`. Earlier evidence below
belongs to the first ABI 1 Linux build.
No Minecraft client, Vulkan drawing job or foreign application was started for these checks.

## Recording-buffer capacity correction, 2026-10-10

Artifact acceptance: the unchanged
[six-platform CI run 38072232588](https://github.com/AfrobamaYT/bbs-neu2/actions/runs/38072232588)
succeeded for Linux x64/ARM64, Windows x64/ARM64 and macOS x64/ARM64, including
each actual JNI/LWJGL3.4.3 load and the strict all-six packaging job. All seven ZIP
digests were checked against GitHub's artifact digests, and all six package-binary
digests match the native manifest. A local rerun of `package.py` also passed.

New Root archive pin:

- Frozen source commit: `b64a986cd1f32f0e837116d625728164f68d976e`.
- Canonical source fingerprint: `c7de679e1ee90fc56d21134f2a2d40a03c93cc4c3709e596cb93fb3e682b01af`.
- All-platform ZIP SHA256: `e5aa18ef3489474a432a4f3df8ea82df12416285b69554e2f8519e5e5412d888`.
- Local ZIP: `build/native-ci-38072232588/bbs-nanovg-all-platforms.zip`.
- Manifest: `build/native-ci-38072232588/bbs-nanovg-all-platforms/native-manifest.json`.
- Download evidence: `build/native-ci-38072232588/download-verification.json`.

| Target | Actual package binary SHA256 |
| --- | --- |
| linux-x64 | `43e165f26a04e13afbe4e37d850f4f67eebd6f8fb5942f95ac938e0f6e061c55` |
| linux-arm64 | `031aebe3668731b0954bfea552627444f76506549c4dd36d553e01a2f7f7d52a` |
| windows-x64 | `fe2c9ad73252f074107bb8a295c2724adf9fe69fdf9c75ba990cfd4e4e5784a1` |
| windows-arm64 | `143d307babb396b564873f498183894c1927d93ecb11aff0a119e0123c11baeb` |
| macos-x64 | `139ab50f24f35dc8c13448ab21f64fb7e95ea170fc48a93ddc3fc5e3f5cc8f78` |
| macos-arm64 | `b5d8ee02229b98403387daa2d7d134c38601a8148b498620e906c12db1bd6a1a` |

All six probes created **no GPU**. Linux ARM64's runner had no Vulkan provider;
the other five loaded their existing LWJGL provider, including bundled MoltenVK
on macOS. The actual GPU growth/retirement/pixel reproduction below remains open.
The separate local Linux/JBR25 check loaded the existing `libvulkan.so.1`, confirmed
ABI2/core addresses and invalid-recording rejection, also without a GPU device.

The original `VKNVGBuffer.size` contained `VkMemoryRequirements.size` even though
`VkBufferCreateInfo.size` was only the requested number of bytes. Reusing that buffer
for more bytes inside the memory rounding exceeded the actual Vulkan resource.
The buffer now stores `capacity` and `allocationSize` separately. Growth compares
against the actual capacity. The full allocation remains mapped and flushed with
offset zero / `VK_WHOLE_SIZE`; destruction resets both values and frees once.
No JNI ABI change and no new Vulkan loader/device.

This is reachable through the actual ABI2 adapter: `NanoVkRenderer.end` executes the
command buffer and queues `releaseRecording`. That callback destroys only the
framebuffer and puts the same recording, including its vertex/SSBO buffers, in
`bridge.available`. `bbs_renderFlush` selects an available recording, attaches
`recording->frame` to `vk->frames`, and calls the real `vknvg_renderFlush` /
`vknvg_UpdateBuffer`. GPU completion protects against premature reuse, but does not
turn allocation rounding into a larger VkBuffer.

Private CPU counterprobe (Linux host C compiler, actual adapter C body; recording
Vulkan function pointers, **no GPU**):

```sh
python3 native/nanovg-vulkan/tests/probe_capacity.py \
  --jdk '/home/afrobama/Dokumente/Claude-Projects/BBS optimieren/jbr25-dcevm-home' \
  --baseline-ref 9b9d1b7ab4ef5c2dad568162b61ec3ab84e322ae
# expected exit1: CAPACITY_FAIL requested=704 actualVkBuffer=528 allocation=768
python3 native/nanovg-vulkan/tests/probe_capacity.py \
  --jdk '/home/afrobama/Dokumente/Claude-Projects/BBS optimieren/jbr25-dcevm-home'
# expected exit0: CAPACITY_PASS; gpu_created=false
```

Both compile and invoke the same actual buffer create/update/destroy functions.
The old source SHA is `4dda86d6ec2367b3f222104924d50f09de8a654cdba423e0843b2459a1e5ee21`;
the fixed source SHA is `54416c20e2d1948620a1ec997d7469968ef200d74a32a88bf13520e7e83e2c99`.
Evidence is under `build/test-evidence/nano-capacity/<sourceSHA>`. The fixture's
256-byte rounding is explicit test data, not an asserted GPU property. It checks
528->704 growth inside allocation768, shrink reuse, exact capacity/rounding
boundaries, full map/flush and exactly-once destruction.

Actual GPU reproduction for Root:

1. Draw three separate convex rectangles with three `nvgBeginPath` / `nvgRect` /
   `nvgFill` calls into one fixed-size Nano target. They need three 176-byte fragment
   blocks (528 bytes). Capture the actual `VkBufferCreateInfo.size` and memory
   requirements/allocation size, rather than assuming the fixture's rounding.
2. Allow actual submissions to complete so that the recording's retirement callback
   puts it back in `available`. Keep the target dimensions unchanged; redraw the
   three-rectangle case through the pool before the growth case.
3. Draw four separate convex rectangles (704 SSBO bytes) with that reused recording.
   If allocation528 >=704, the old code skips buffer growth and writes descriptor
   range704 to the actual buffer528. The expected Vulkan error is
   `VUID-VkDescriptorBufferInfo-range-00342`. If the real allocation gap differs,
   choose actual oldBytes < newBytes <= oldAllocation for vertex or SSBO growth.
4. With the fixed library the actual VkBuffer is recreated to newBytes; shrink and
   exact-capacity draws reuse it. Check validation, pixels and retirement while
   several Nano targets and frames remain in flight. Follow with resize/reload/close.

This remains a **prepared reproduction**, not a measured GPU validation result.
All six frozen binaries must be rebuilt and their existing JNI/ABI/source-fingerprint
checks must pass before Root updates its archive pin. The earlier 28c04c39 / run
38063516566 archive remains an immutable historical baseline.

Read-only shutdown review also found an actual separate Start6 observation:
`vkDestroyDevice-device-05137` reported 67 leaked objects at shutdown. Nano ownership
is not established from the first ten VkDeviceMemory handles. Source order is
compatible with cleanup: Fabric CLIENT_STOPPING is injected after the `Stopping!`
log in `Minecraft.exitWorldAndClose`, before `Minecraft.close` destroys RenderSystem;
NanoKontext registers `verwerfen`, NanoVkRenderer queues its native destroy, and the
Vulkan encoder waits idle and drains DestructionQueue before vkDestroyDevice.
Actual hook/queued-native-destroy invocation must be measured in Root's next normal
shutdown; no speculative cleanup change is included here.

Verified:

- The C11 Linux x64 shared library links with `-Wl,-z,defs` and builds without
  warnings under `-Wall -Wextra`.
- The three NanoVk adapter classes compile with `javac --release 25 -proc:none`
  against the real local Minecraft 26.3 client JAR and LWJGL 3.4.1. No product
  stubs or substitute Minecraft classes were used. This is not a full mod build.
- The actual library loads into a fresh JVM. The existing LWJGL NanoVG
  `nvgCreateInternal`, `nvgDeleteInternal` and `nvgInternalParams` addresses are nonzero.
- Actual JNI calls return an empty recording for a null context, allow an empty
  destroy, and reject an invalid prepared recording with
  `IllegalStateException: Invalid or overlapping NanoVG Vulkan recording`.
- `git diff --check` passes. The normal Blender provenance gate reports zero
  errors; its existing `legacy-ik-solver` release warning remains.

Build command used:

```sh
python native/nanovg-vulkan/build.py --output build/nano-native/libbbs_nanovg_vk.so
```

Linux binary SHA-256 after the native fixes:
`589733bf9e0275360165b64852d51ce47519a70fdb7b5ed6aa3e4d8f5bcf33e4`.
The checked binary is a build artifact, not a committed distribution binary.

Runtime acceptance still required:

| Case | Required evidence |
| --- | --- |
| Drawing parity | Actual GL/Vulkan captures of paths, concave fills/holes, overlapping stencil strokes, gradients, text/metrics, icons, alpha and clipping |
| Several surfaces in one submission | Different size/content surfaces retain their own pixels and do not share overwritten vertex or uniform buffers |
| Frames in flight | Animated/tick-redrawn surfaces and newly introduced glyphs remain correct with two pending GPU submissions |
| Resize | Old targets, framebuffers and stencil attachments retire after GPU completion; new dimensions match the displayed image |
| Reload | Context/font/icon caches rebuild while prior recordings remain pending; no stale image ids or device loss |
| Shutdown | The client-stopping hook fires, and both engine-owned images and native pooled objects retire without queue mutation during destruction |
| Interaction | Hover, menus, click, drag, text input, scrolling and graph/timeline scrubbing preserve the existing Nano zones |
| Integration | Full 26.3 public/companion build and actual BBS `Texture` port; the current base still requires GL textures |
| Distribution | Package the native library under the matching client resource path; other platform binaries need their own builds |

The source keeps the existing OpenGL branch as the reference and contains no
software-rendering fallback. GPU correctness and end-user compatibility are open,
even though the native library and narrow Java API checks pass.
