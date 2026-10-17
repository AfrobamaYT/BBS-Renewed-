# Complete GPL copy release gate

The provenance checker validates the recorded Blender source and SPDX notices.
Its former release result did not check the distributed GPL text. During the
2026-10-17 audit, a real product/exporter copy had17337 bytes and SHA256
`aaf135472f81c5b4a0dca9367e5bb5e9750032b5bebe5442b36e4c0a47430df3`.
It omitted part of the application appendix. This is a packaging finding,
independent of solver, rig and GUI acceptance.

For GPL-2.0 ports, `python tools/pruefe-blender-herkunft.py --release` now
requires the complete unmodified copy in
`src/main/resources/META-INF/LICENSE-BLENDER-IK.txt`:18002 bytes, SHA256
`231f7edcc7352d7734a96eef0b8030f77982678c516876fcb81e25b32d68564c`.
The pinned source is [GNU GCC COPYING](https://github.com/gcc-mirror/gcc/blob/b3a3ecc460237948d924bca6cb8fe15622e5025b/COPYING).

The full CLI was exercised against an isolated archive of the real public
source. The old checker accepted the actual truncated exporter copy (exit0).
The new checker rejects it (exit1), accepts the complete copy (exit0), and
rejects a missing copy (exit1). The source tree used for distribution was never
modified by the negative controls. Raw results are retained locally in
`build/test-evidence/release-audit-20261017/gpl-copy-gate-controls.json` in the
public release worktree.

This source gate does not inspect a built JAR or ZIP. Distribution still needs
an explicit checksum of the licence entry in each final package. The current
public candidate has the complete text in both its COPYING and META-INF
entries. Changes to this checker do not change product class bytes or prove
new gameplay behavior.
