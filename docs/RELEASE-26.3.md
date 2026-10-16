# BBS Renewed 2.5.0 for Minecraft 26.3

Publication status: preview. The integrated snapshot is provided for testing; the checks
below do not establish full production stability, shader compatibility or cross-editor parity.

Prepared on 2026-10-16 from the clean integrated product snapshot
`cf99f2f828b0d74d1642fac7c5791ad81970d44f`. This public tree contains the product,
its Blender add-ons, Resolve script, addon API snapshot, native renderer sources and
the build inputs. The private development companion, automation servers, test footage,
local configuration and private repository history are excluded.

The public 26.2 installation and licence packaging fixes are retained. Packaging changes
set the public name/links, release version and portable Java configuration. The addon API
checker keeps javap diagnostics on stderr; the API snapshot itself remains unchanged.
The optional Blender add-on retains the existing public `BBS_ASSETS_DIR` environment
configuration. Local native-development records and machine-specific asset paths are omitted.

## Verification

The public build and required server GameTest pass locally on Java 25. The API check also
passes with `_JAVA_OPTIONS=-Dbbs.release.api_probe=true`, and `apiDump` reproduces the
original snapshot byte for byte. The native gate checks the ABI, source fingerprint and
binary checksum of all six bundled NanoVG Vulkan libraries. The Blender provenance release
gate passes. Gitleaks found no secrets in the staged public source tree. The actual product
JAR passes the public/private companion boundary check.

The frozen product JAR is
`728b603a2a9f5e31b4a8b32c5aa62d11b34aea95ac6553702175eb0ae99fc066`.
Its source JAR is
`5e1b9a910bb460c0dc526adbe66187a93209db4919a46f563db6a37370774874`.
The complete buildable source is this repository at the release tag; the source JAR alone
does not contain the full native renderer build inputs.

On Linux/NVIDIA 615.71.09, this exact product JAR passed a fresh OpenGL production launch
and two fresh Vulkan production launches: world creation, BBS dashboard and screenshots.
The product checksum stayed unchanged across these tests. The Windows/Mesa production
test at [run 38967559800](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/38967559800)
passed; its dashboard screenshot was also reviewed. The Linux job in that run could not
create a GLX visual and was cancelled; it is not a passing test. The CI now records GLX
capabilities, supplies explicit Mesa GLX/software-renderer setup and has a bounded test
with native JVM thread dumps on a hang. Its result is recorded separately below.

Final cross-platform acceptance:
[run 38971400388](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/38971400388),
commit `074f3a388a8a8904d4551eb89a2571204330e0f2`, is green on both Windows and Linux.
Windows uses Mesa 26.0.3; Ubuntu uses Mesa 25.2.8 through SDL's supported EGL context path.
Both create a real world and render the BBS dashboard; the Linux and Windows dashboard
images were reviewed. The diagnostic files now live outside Loom's reset game directory.
The sRGB/EGL setup follows SDL's documented
[`SDL_VIDEO_FORCE_EGL`](https://wiki.libsdl.org/SDL3/SDL_HINT_VIDEO_FORCE_EGL) control.

The previous public 26.3 source branch was version 2.4.4 at
`c7f78afce9353a03611adbd38fdcb726ad25126d`. This release tag supplies the newer standalone
26.3 snapshot and full build inputs; it does not claim that the older version branches
already contain the same features.

A preliminary Vulkan launch with the development build selector failed with a five-second
Vulkan semaphore timeout. That selector also changed the compilation classpath. Production
backend selection is now independent of compilation, and both final Vulkan runs passed;
the earlier timeout's cause is not established and is not claimed to be fixed.

An extracted-file comparison against the previously accepted product JAR
`d28ab53c5b74a07c2f6f0a561829979ae861d56a4c5d1ce32b48da6c58318c5f`
finds packaging/name/version/licence differences, the first-launch category-settings guard,
and eleven anonymous-class binary differences whose `javap -p -c` output is identical.
All other product files are byte-identical. This preserves the integrated feature snapshot;
it does not substitute for broader production, rig, shader or cross-application testing.

This release targets 26.3. It does not claim feature parity for the older Minecraft versions.
Bundling six native libraries does not establish six-platform gameplay acceptance. Historical
FPS figures and the August feature inventory are not measurements of this release.

## Inherited assets and notices

The BBS models retain their upstream names and credits. The Alex/Steve Bends files were
originally added by McHorse with the explicit credit to MichaelCreeper_ in commit
[`fa4f1441da6e7b4c233add16790b6342106f520f`](https://github.com/mchorse/bbs-mod/commit/fa4f1441da6e7b4c233add16790b6342106f520f).
The files included here are byte-identical to BBS-FS commit
[`13bd62dab1980b974b8ea0f78d037e8e82684b84`](https://github.com/Wemppy4/bbs-fs/tree/13bd62dab1980b974b8ea0f78d037e8e82684b84),
whose repository [licence](https://github.com/Wemppy4/bbs-fs/blob/13bd62dab1980b974b8ea0f78d037e8e82684b84/LICENSE.md)
is MIT and whose tree has no separate asset licence exception. Git blob IDs:

- Alex Bends: `4bf4d97e46060df8f9d3619c6079d3701f0dfe39`
- Steve Bends: `27df9a91d89a9018838ea4d47f349eb53b9114f3`

This release relies on that upstream MIT distribution and preserves the authors' notices;
it does not relicense these assets as original GPL artwork by this fork. Other inherited
assets keep their upstream notices. Fonts retain their bundled SIL Open Font Licences.
NanoVG Vulkan retains its Zlib notice and pinned source provenance. The Blender IK port
retains GPL-2.0-or-later, the complete GPL text and its exact upstream source references.

The old private repository's history rewrite is not used to publish this tree. No private
repository visibility is changed. Physics Mod Pro and the proprietary NVIDIA binary are
absent from this source snapshot and its product JAR.
