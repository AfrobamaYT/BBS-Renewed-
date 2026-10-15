# NanoVG Vulkan for BBS

The renderer is copied from
[SubiyaCryolite/nanovg_vulkan](https://github.com/SubiyaCryolite/nanovg_vulkan)
at commit `0f7110feafa1f293580eb134e5ff7a9368021ac2`, under the Zlib license
in `upstream/LICENSE.txt`. Original source comments remain in place.

The context is created, mutated and freed by the NanoVG core bundled with
LWJGL 3.4.3, commit `30fac9b95f99cda97312232be25ba55297bf9951`.
Its three existing internal-core pointer getters avoid mixing LWJGL's allocator
with a separately compiled NanoVG core. Five context-independent matrix
functions were first copied from LWJGL 3.4.1 and are byte-identical to the exact
3.4.3 source, under the retained Mikko Mononen Zlib notice. The 3.4.3 NVG and FONS
context/parameter/vertex layouts and the three pointer getters were independently
checked against the pinned renderer headers.

Original downloaded SHA-256:

| File | SHA-256 |
| --- | --- |
| src/nanovg_vk.c | b77ba180d6ab3d4081c63d6fe27fe616ae60a99a3313497be7d4be2ca47d0b74 |
| src/nanovg_vk.h | 22d29be6d51c79c5237911aaf2315aad985c5fc74c2f3d26753736dbb1f7bbbd |
| src/nanovg.h | 72bba7ecbbc96a4d4f43928daf124506c3bc701bad2822a7f86563cba5bb8a82 |
| src/shader/fill.vert.inc | 4b6bf1561befa594dac4ef9cc52fb5cef1db42f6703abae632fe74a5307c915a |
| src/shader/fill.frag.inc | 14c21cde50b2c371b5f00ccd9c17282b1305562cfa282a5a40e3f850f114244a |

BBS modifications dated 2026-10-10:

- Textures, samplers, uploads and mipmaps use Minecraft's GPU objects and command
  ordering. The original host-linear images and separate upload queue are unused.
- Each flush owns its vertex/uniform buffers and descriptor sets until Minecraft's
  destruction queue says its submission has completed. Idle recordings are pooled.
- An explicit Vulkan offscreen pass keeps the original antialiasing, eight-bit
  stencil fills and overlapping stencil strokes. Minecraft owns the color texture;
  the adapter owns the stencil attachment, whose format is checked on the real GPU.
- Host-visible buffers are flushed correctly on non-coherent memory heaps.
- The actual VkBuffer capacity is stored separately from the larger VkDeviceMemory
  allocation. Reused recordings grow the resource whenever their uploaded bytes
  exceed VkBufferCreateInfo.size, including growth inside memory-allocation rounding.
- VkResult failures become Java exceptions at native entry/flush boundaries.
- Vulkan is not linked. Instance and device function pointers come from the
  existing LWJGL/Minecraft instance/device and are retained per bridge. No other
  Vulkan loader or MoltenVK library is opened by the adapter.
- The original OpenGL NanoVG branch is retained as the visual reference.

No software renderer is used. Native source and Java compilation are separate
from full Minecraft runtime acceptance.

Build for the current host:

```sh
python native/nanovg-vulkan/build.py
```

This writes the platform library into client resources for inclusion in the public
BBS artifact. Only Vulkan headers are needed, not a second runtime library.
See `PLATFORMS.md` for all six classifiers and the review-artifact workflow.
