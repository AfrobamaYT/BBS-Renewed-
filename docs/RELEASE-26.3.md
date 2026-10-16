# BBS Renewed 2.5.0 for Minecraft 26.3

Prepared on 2026-10-16 from the clean integrated product snapshot
`cf99f2f828b0d74d1642fac7c5791ad81970d44f`. This public tree contains the product,
its Blender add-ons, Resolve script, addon API snapshot, native renderer sources and
the build inputs. The private development companion, automation servers, test footage,
local configuration and private repository history are excluded.

The public 26.2 installation and licence packaging fixes are retained. Packaging changes
set the public name/links, release version and portable Java configuration. The addon API
checker keeps javap diagnostics on stderr; the API snapshot itself remains unchanged.

## Verification

The public build and required server GameTest pass locally on Java 25. The API check also
passes with `_JAVA_OPTIONS=-Dbbs.release.api_probe=true`, and `apiDump` reproduces the
original snapshot byte for byte. The native gate checks the ABI, source fingerprint and
binary checksum of all six bundled NanoVG Vulkan libraries. The Blender provenance release
gate passes. Production client and cross-platform results will be recorded before release.

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
